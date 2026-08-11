#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sloučí všechny *_MD1.xlsx ze složky do Souhrn_mereni.xlsx.

Stejný vzhled jako Android appka (list Mereni, 4 sloupce):
  datum (modré) → prázdný řádek → stanice (oranžové) → data A–D

Bez VBA, bez Excelu, bez pip — stačí Python 3.

Použití:
  python sloucit_mereni.py
  python sloucit_mereni.py "C:\\Users\\...\\OneDrive\\MD1_rozdeleno"
  python sloucit_mereni.py . -o Souhrn.xlsx
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import zipfile
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Iterable, List, Optional, Tuple
from xml.sax.saxutils import escape as xml_escape


class Role(Enum):
    DATE = "DATE"
    BLANK = "BLANK"
    STATION = "STATION"
    DATA = "DATA"


@dataclass
class Row:
    a: str = ""
    b: str = ""
    c: str = ""
    d: str = ""
    role: Role = Role.DATA

    def is_empty(self) -> bool:
        return not (self.a or self.b or self.c or self.d)


STYLE_DATE = 1
STYLE_STATION = 2
STYLE_DATA_CENTER = 3

CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

RELS_ROOT = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

RELS_WB = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Mereni" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="14"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
    <font><sz val="16"/><b/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
    <font><sz val="16"/><b/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFBBDEFB"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFE0B2"/></patternFill></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1">
      <alignment horizontal="left" vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1">
      <alignment horizontal="left" vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
  </cellXfs>
  <cellStyles count="1">
    <cellStyle name="Normal" xfId="0" builtinId="0"/>
  </cellStyles>
</styleSheet>"""

ROW_RE = re.compile(r"<row\b[^>]*>(.*?)</row>", re.DOTALL | re.IGNORECASE)
# Celá buňka včetně těla — kvůli sharedStrings (<v>0</v>) i inlineStr (<t>…)
CELL_RE = re.compile(
    r"<c\b([^>]*?)(?:/>|>(.*?)</c>)",
    re.DOTALL | re.IGNORECASE,
)
REF_RE = re.compile(r'\br="([A-Z]+)\d+"', re.IGNORECASE)
STYLE_RE = re.compile(r'\bs="(\d+)"', re.IGNORECASE)
TYPE_RE = re.compile(r'\bt="([^"]+)"', re.IGNORECASE)
V_RE = re.compile(r"<v[^>]*>(.*?)</v>", re.DOTALL | re.IGNORECASE)
T_RE = re.compile(r"<t[^>]*>(.*?)</t>", re.DOTALL | re.IGNORECASE)
SI_RE = re.compile(r"<si\b[^>]*>(.*?)</si>", re.DOTALL | re.IGNORECASE)
DATE_NAME_RE = re.compile(r"^(\d{6})(?:_\d+)?_MD1\.xlsx$", re.IGNORECASE)
LOOKS_LIKE_DATE_RE = re.compile(r"^\d{1,2}[./]\d{1,2}[./]\d{4}$")

# OneDrive layout:
#   Popis_měření_MD1/
#     Popis_měření_MD1.xlsx          ← souhrn
#     MD1_popis_dny/
#       YYMMDD_N_MD1.xlsx            ← denní dávky z appky
MAIN_FOLDER_NAME = "Popis_měření_MD1"
DAYS_SUBFOLDER_NAME = "MD1_popis_dny"
SUMMARY_XLSX_NAME = "Popis_měření_MD1.xlsx"
ARCHIVE_FOLDER_NAME = "sloučeno"
SUMMARY_EXCLUDE = {
    SUMMARY_XLSX_NAME.lower(),
    "souhrn_mereni.xlsx",
}


def xml_unescape(s: str) -> str:
    return (
        s.replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", '"')
        .replace("&apos;", "'")
        .replace("&amp;", "&")
    )


def parse_shared_strings(xml: str) -> List[str]:
    xml = re.sub(r"<(/?)([A-Za-z0-9._-]+):", r"<\1", xml)
    out: List[str] = []
    for m in SI_RE.finditer(xml):
        parts = T_RE.findall(m.group(1))
        out.append(xml_unescape("".join(parts)).strip())
    return out


def cell_text(attrs: str, body: str, shared: List[str]) -> str:
    body = body or ""
    ctype = (TYPE_RE.search(attrs).group(1).lower() if TYPE_RE.search(attrs) else "")
    if ctype == "s":
        vm = V_RE.search(body)
        if not vm:
            return ""
        try:
            idx = int(vm.group(1).strip())
        except ValueError:
            return ""
        return shared[idx] if 0 <= idx < len(shared) else ""
    if ctype in ("inlineStr", "str"):
        parts = T_RE.findall(body)
        if parts:
            return xml_unescape("".join(parts)).strip()
    # inlineStr bez t=, nebo číslo/text ve <v>
    parts = T_RE.findall(body)
    if parts:
        return xml_unescape("".join(parts)).strip()
    vm = V_RE.search(body)
    if vm:
        return xml_unescape(vm.group(1)).strip()
    return ""


def role_from_style(style: Optional[int]) -> Role:
    if style == STYLE_DATE:
        return Role.DATE
    if style == STYLE_STATION:
        return Role.STATION
    return Role.DATA


def parse_sheet(xml: str, shared: Optional[List[str]] = None) -> List[Row]:
    shared = shared or []
    rows: List[Row] = []
    for row_match in ROW_RE.finditer(xml):
        cells = {}
        style_a: Optional[int] = None
        for cell_match in CELL_RE.finditer(row_match.group(1)):
            attrs = cell_match.group(1) or ""
            body = cell_match.group(2) or ""
            ref_m = REF_RE.search(attrs)
            if not ref_m:
                continue
            col = ref_m.group(1).upper()
            cells[col] = cell_text(attrs, body, shared)
            if col == "A":
                sm = STYLE_RE.search(attrs)
                if sm:
                    style_a = int(sm.group(1))
        a = cells.get("A", "")
        b = cells.get("B", "")
        c = cells.get("C", "")
        d = cells.get("D", "")
        if not (a or b or c or d):
            role = Role.BLANK
        elif b or c or d:
            role = Role.DATA
        elif style_a == STYLE_DATE:
            role = Role.DATE
        elif style_a == STYLE_STATION:
            role = Role.STATION
        elif LOOKS_LIKE_DATE_RE.match(a):
            role = Role.DATE
        elif not rows:
            role = Role.DATE
        else:
            role = Role.STATION
        rows.append(Row(a, b, c, d, role))
    return rows


def read_xlsx(path: Path) -> List[Row]:
    if not path.is_file() or path.stat().st_size < 64:
        return []
    try:
        with zipfile.ZipFile(path) as zf:
            names = {n.replace("\\", "/").lower(): n for n in zf.namelist()}
            shared: List[str] = []
            ss = names.get("xl/sharedstrings.xml")
            if ss:
                shared = parse_shared_strings(
                    zf.read(ss).decode("utf-8", errors="replace")
                )
            sheet_key = names.get("xl/worksheets/sheet1.xml")
            if not sheet_key:
                sheets = sorted(
                    n for k, n in names.items() if k.startswith("xl/worksheets/sheet") and k.endswith(".xml")
                )
                if not sheets:
                    return []
                sheet_key = sheets[0]
            xml = zf.read(sheet_key).decode("utf-8", errors="replace")
            xml = re.sub(r"<(/?)([A-Za-z0-9._-]+):", r"<\1", xml)
            return parse_sheet(xml, shared)
    except (zipfile.BadZipFile, KeyError, OSError):
        return []


def cell_xml(ref: str, value: str, style: int) -> str:
    s_attr = f' s="{style}"'
    if not value:
        return f'<c r="{ref}"{s_attr}/>'
    safe = xml_escape(value.replace("\n", " ").replace("\r", " "))
    return f'<c r="{ref}"{s_attr} t="inlineStr"><is><t>{safe}</t></is></c>'


def style_index(role: Role) -> int:
    if role == Role.DATE:
        return STYLE_DATE
    if role == Role.STATION:
        return STYLE_STATION
    return STYLE_DATA_CENTER


def sheet_xml(rows: Iterable[Row]) -> str:
    parts = [
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
        '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">',
        "<cols>",
        '<col min="1" max="1" width="32" customWidth="1"/>',
        '<col min="2" max="2" width="18" customWidth="1"/>',
        '<col min="3" max="3" width="14" customWidth="1"/>',
        '<col min="4" max="4" width="36" customWidth="1"/>',
        "</cols>",
        "<sheetData>",
    ]
    for idx, row in enumerate(rows):
        r = idx + 1
        ht = "12" if row.role == Role.BLANK else "24"
        parts.append(f'<row r="{r}" ht="{ht}" customHeight="1">')
        if row.role == Role.BLANK:
            pass
        elif row.role in (Role.DATE, Role.STATION):
            parts.append(cell_xml(f"A{r}", row.a, style_index(row.role)))
        else:
            parts.append(cell_xml(f"A{r}", row.a, STYLE_DATA_CENTER))
            parts.append(cell_xml(f"B{r}", row.b, STYLE_DATA_CENTER))
            parts.append(cell_xml(f"C{r}", row.c, STYLE_DATA_CENTER))
            parts.append(cell_xml(f"D{r}", row.d, STYLE_DATA_CENTER))
        parts.append("</row>")
    parts.append("</sheetData></worksheet>")
    return "".join(parts)


def write_xlsx(path: Path, rows: List[Row]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("[Content_Types].xml", CONTENT_TYPES)
        zf.writestr("_rels/.rels", RELS_ROOT)
        zf.writestr("xl/workbook.xml", WORKBOOK)
        zf.writestr("xl/_rels/workbook.xml.rels", RELS_WB)
        zf.writestr("xl/styles.xml", STYLES)
        zf.writestr("xl/worksheets/sheet1.xml", sheet_xml(rows))


def date_from_filename(name: str) -> str:
    m = DATE_NAME_RE.match(name)
    if not m:
        return ""
    yymmdd = m.group(1)
    try:
        yy = int(yymmdd[0:2])
        mm = int(yymmdd[2:4])
        dd = int(yymmdd[4:6])
    except ValueError:
        return ""
    if not (1 <= mm <= 12 and 1 <= dd <= 31):
        return ""
    return f"{dd}.{mm}.{2000 + yy}"


def list_md1_files(folder: Path) -> List[Path]:
    files = [
        p
        for p in folder.iterdir()
        if p.is_file()
        and DATE_NAME_RE.match(p.name)
        and not p.name.startswith("~$")
    ]
    return sorted(files, key=lambda p: p.name.lower())


def resolve_layout(folder: Path) -> Tuple[Path, Path]:
    """
    Vrátí (složka_s_denními, cesta_k_souhrnu).

    Preferuje MD1_popis_dny, pokud v ní něco je.
    Jinak bere denní soubory přímo z hlavní složky.
    """
    folder = folder.resolve()
    summary = folder / SUMMARY_XLSX_NAME
    days_sub = folder / DAYS_SUBFOLDER_NAME

    if folder.name == DAYS_SUBFOLDER_NAME:
        parent = folder.parent
        return folder, parent / SUMMARY_XLSX_NAME

    if days_sub.is_dir() and list_md1_files(days_sub):
        return days_sub, summary

    if list_md1_files(folder):
        return folder, summary

    # Prázdná podsložka existuje → stejně na ni ukaž (jasná chyba)
    if days_sub.is_dir():
        return days_sub, summary

    return folder, summary


def merge_folder(
    folder: Path,
    verbose: bool = True,
    base_rows: Optional[List[Row]] = None,
) -> Tuple[List[Row], int, int, List[Path]]:
    out: List[Row] = list(base_rows or [])
    current_date = ""
    last_station = ""
    for row in out:
        if row.role == Role.DATE and row.a:
            current_date = row.a
            last_station = ""
        elif row.role == Role.STATION and row.a:
            last_station = row.a
    ok = 0
    skipped = 0
    processed: List[Path] = []

    files = list_md1_files(folder)
    if verbose:
        print(f"Složka: {folder}")
        print(f"Nalezeno souborů *_MD1.xlsx: {len(files)}")
        if out:
            print(f"Výchozí souhrn: {len(out)} řádků")

    for path in files:
        rows = read_xlsx(path)
        data_n = sum(1 for r in rows if r.role == Role.DATA and not r.is_empty())
        if verbose:
            print(f"  - {path.name}: {len(rows)} řádků XML, z toho {data_n} datových")
        wrote = False
        file_date_guess = date_from_filename(path.name)

        for row in rows:
            if row.is_empty() or row.role == Role.BLANK:
                continue

            if row.role == Role.DATE:
                if row.a != current_date:
                    if out:
                        out.append(Row(role=Role.BLANK))
                    out.append(Row(a=row.a, role=Role.DATE))
                    current_date = row.a
                    last_station = ""
                    wrote = True
                continue

            if row.role == Role.STATION:
                if not current_date and file_date_guess:
                    if out:
                        out.append(Row(role=Role.BLANK))
                    out.append(Row(a=file_date_guess, role=Role.DATE))
                    current_date = file_date_guess
                    last_station = ""
                if row.a != last_station:
                    out.append(Row(role=Role.BLANK))
                    out.append(Row(a=row.a, role=Role.STATION))
                    last_station = row.a
                    wrote = True
                continue

            # DATA
            if not current_date and file_date_guess:
                if out:
                    out.append(Row(role=Role.BLANK))
                out.append(Row(a=file_date_guess, role=Role.DATE))
                current_date = file_date_guess
                last_station = ""
            out.append(Row(a=row.a, b=row.b, c=row.c, d=row.d, role=Role.DATA))
            wrote = True

        if wrote:
            ok += 1
            processed.append(path)
        else:
            skipped += 1

    return out, ok, skipped, processed


def archive_merged_files(files: List[Path], summary_path: Path) -> Path:
    """Přesune sloučené denní soubory do Popis_…/sloučeno/."""
    archive = summary_path.parent / ARCHIVE_FOLDER_NAME
    archive.mkdir(parents=True, exist_ok=True)
    for src in files:
        if not src.is_file():
            continue
        dest = archive / src.name
        if dest.exists():
            stem, suf = src.stem, src.suffix
            n = 1
            while dest.exists():
                dest = archive / f"{stem}_{n}{suf}"
                n += 1
        src.rename(dest)
    return archive


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description=(
            f"Sloučí *_MD1.xlsx do {SUMMARY_XLSX_NAME} "
            f"(složka {MAIN_FOLDER_NAME}/{DAYS_SUBFOLDER_NAME}/)."
        )
    )
    parser.add_argument(
        "folder",
        nargs="?",
        default=".",
        help=f"Hlavní složka {MAIN_FOLDER_NAME} nebo přímo {DAYS_SUBFOLDER_NAME}",
    )
    parser.add_argument(
        "-o",
        "--output",
        default="",
        help=f"Volitelná cesta/jméno souhrnu (výchozí {SUMMARY_XLSX_NAME})",
    )
    args = parser.parse_args(argv)

    folder = Path(args.folder).expanduser().resolve()
    if not folder.is_dir():
        print(f"Složka neexistuje: {folder}", file=sys.stderr)
        return 2

    source, default_out = resolve_layout(folder)
    out_path = Path(args.output).expanduser() if args.output else default_out
    if not out_path.is_absolute():
        # -o jen jméno → vedle souhrnu ve výchozí hlavní složce
        out_path = default_out.parent / out_path.name
    out_path = out_path.resolve()

    files = list_md1_files(source)
    if not files:
        print(f"Ve složce nejsou žádné *_MD1.xlsx:\n  {source}", file=sys.stderr)
        print(
            f"Očekávaná struktura:\n"
            f"  {MAIN_FOLDER_NAME}/\n"
            f"    {SUMMARY_XLSX_NAME}\n"
            f"    {DAYS_SUBFOLDER_NAME}/\n"
            f"      YYMMDD_N_MD1.xlsx",
            file=sys.stderr,
        )
        return 1

    print(f"Denní soubory: {source}")
    print(f"Souhrn:        {out_path}")
    base_rows: List[Row] = []
    if out_path.is_file() and out_path.stat().st_size > 64:
        base_rows = read_xlsx(out_path)
        if base_rows:
            print(f"Načten existující souhrn: {len(base_rows)} řádků")
    rows, ok, skipped, processed = merge_folder(source, base_rows=base_rows)
    data_out = sum(1 for r in rows if r.role == Role.DATA)
    if data_out == 0:
        print(
            "CHYBA: do souhrnu se nedostala žádná data.\n"
            f"Zkontroluj originální soubory z appky v {DAYS_SUBFOLDER_NAME}/.",
            file=sys.stderr,
        )
        return 1

    write_xlsx(out_path, rows)

    print(f"Soubory OK: {ok}")
    print(f"Přeskočeno: {skipped}")
    print(f"Datových řádků: {data_out}")
    print(f"Uloženo: {out_path}")

    archive = archive_merged_files(processed, out_path)
    print(f"Přesunuto do {ARCHIVE_FOLDER_NAME}/: {len(processed)} souborů ({archive})")

    try:
        if sys.platform.startswith("win"):
            os.startfile(out_path)  # type: ignore[attr-defined]
            print("Otevírám Excel…")
        elif os.environ.get("SLOUCIT_NO_OPEN") != "1":
            import subprocess

            subprocess.Popen(["xdg-open", str(out_path)])
            print("Otevírám Excel…")
    except OSError as exc:
        print(f"Nepodařilo se otevřít soubor: {exc}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
