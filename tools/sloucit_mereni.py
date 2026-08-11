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
CELL_RE = re.compile(
    r'<c\b([^>]*)>(?:.*?<t[^>]*>(.*?)</t>.*?)?</c>|<c\b([^>]*)/>',
    re.DOTALL | re.IGNORECASE,
)
REF_RE = re.compile(r'\br="([A-Z]+)\d+"', re.IGNORECASE)
STYLE_RE = re.compile(r'\bs="(\d+)"', re.IGNORECASE)
DATE_NAME_RE = re.compile(r"^(\d{6})(?:_\d+)?_MD1\.xlsx$", re.IGNORECASE)
LOOKS_LIKE_DATE_RE = re.compile(r"^\d{1,2}[./]\d{1,2}[./]\d{4}$")


def xml_unescape(s: str) -> str:
    return (
        s.replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", '"')
        .replace("&apos;", "'")
        .replace("&amp;", "&")
    )


def role_from_style(style: Optional[int]) -> Role:
    if style == STYLE_DATE:
        return Role.DATE
    if style == STYLE_STATION:
        return Role.STATION
    return Role.DATA


def parse_sheet(xml: str) -> List[Row]:
    rows: List[Row] = []
    for row_match in ROW_RE.finditer(xml):
        cells = {}
        style_a: Optional[int] = None
        for cell_match in CELL_RE.finditer(row_match.group(1)):
            attrs = cell_match.group(1) or cell_match.group(3) or ""
            text = xml_unescape(cell_match.group(2) or "")
            ref_m = REF_RE.search(attrs)
            if not ref_m:
                continue
            col = ref_m.group(1).upper()
            cells[col] = text.strip()
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
        elif style_a is not None:
            role = role_from_style(style_a)
            if role == Role.DATA and a and not (b or c or d):
                role = Role.DATE if not rows else Role.STATION
        elif a and not (b or c or d):
            role = Role.DATE if (not rows or LOOKS_LIKE_DATE_RE.match(a)) else Role.STATION
        else:
            role = Role.DATA
        rows.append(Row(a, b, c, d, role))
    return rows


def read_xlsx(path: Path) -> List[Row]:
    if not path.is_file() or path.stat().st_size == 0:
        return []
    try:
        with zipfile.ZipFile(path) as zf:
            # Prefer sheet named via workbook; fall back to sheet1.xml
            name = "xl/worksheets/sheet1.xml"
            if name not in zf.namelist():
                sheets = [n for n in zf.namelist() if n.startswith("xl/worksheets/sheet")]
                if not sheets:
                    return []
                name = sorted(sheets)[0]
            xml = zf.read(name).decode("utf-8")
            return parse_sheet(xml)
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
        and p.suffix.lower() == ".xlsx"
        and p.name.lower().endswith("_md1.xlsx")
        and p.name.lower() != "souhrn_mereni.xlsx"
        and not p.name.startswith("~$")
    ]
    return sorted(files, key=lambda p: p.name.lower())


def merge_folder(folder: Path) -> Tuple[List[Row], int, int]:
    out: List[Row] = []
    current_date = ""
    last_station = ""
    ok = 0
    skipped = 0

    for path in list_md1_files(folder):
        rows = read_xlsx(path)
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
        else:
            skipped += 1

    return out, ok, skipped


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="Sloučí *_MD1.xlsx do Souhrn_mereni.xlsx (bez VBA / Excelu)."
    )
    parser.add_argument(
        "folder",
        nargs="?",
        default=".",
        help="Složka s denními soubory (výchozí: aktuální)",
    )
    parser.add_argument(
        "-o",
        "--output",
        default="Souhrn_mereni.xlsx",
        help="Jméno výstupního souboru (ve složce, výchozí Souhrn_mereni.xlsx)",
    )
    args = parser.parse_args(argv)

    folder = Path(args.folder).expanduser().resolve()
    if not folder.is_dir():
        print(f"Složka neexistuje: {folder}", file=sys.stderr)
        return 2

    files = list_md1_files(folder)
    if not files:
        print(f"Ve složce nejsou žádné *_MD1.xlsx:\n  {folder}", file=sys.stderr)
        return 1

    rows, ok, skipped = merge_folder(folder)
    out_path = folder / args.output
    write_xlsx(out_path, rows)

    print(f"Soubory OK: {ok}")
    print(f"Přeskočeno: {skipped}")
    print(f"Uloženo: {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
