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
import shutil
import sys
import time
import zipfile
from datetime import datetime
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Iterable, List, Optional, Tuple
from xml.sax.saxutils import escape as xml_escape


MERGE_LOCK_NAME = ".sloucit_mereni.lock"
MERGE_LOCK_STALE_MINUTES = 45
BACKUP_FOLDER_NAME = "Zalohy"
BACKUP_KEEP = 5


class Role(Enum):
    DATE = "DATE"
    BLANK = "BLANK"
    STATION = "STATION"
    DATA = "DATA"
    UPDATED = "UPDATED"  # radek 1: Naposledy aktualizovano


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
STYLE_UPDATED = 4
STYLE_BUTTON = 5
UPDATE_PREFIX = "Naposledy aktualizováno:"
BUTTON_LABEL = "Aktualizovat"
UPDATE_BAT_NAME = "SloucitMereni.bat"


def file_uri(path: Path) -> str:
    """Absolutní file:///… — relativní .bat na OneDrive Excel otevírá jako https → 404."""
    return path.resolve().as_uri()


def sheet_rels_xml(bat_path: Path) -> str:
    target = xml_escape(file_uri(bat_path))
    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" '
        'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" '
        f'Target="{target}" TargetMode="External"/>'
        "</Relationships>"
    )


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
  <fonts count="5">
    <font><sz val="14"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
    <font><sz val="16"/><b/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
    <font><sz val="16"/><b/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
    <font><sz val="12"/><i/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
    <font><sz val="12"/><b/><color rgb="FFFFFFFF"/><name val="Calibri"/><family val="2"/></font>
  </fonts>
  <fills count="6">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFBBDEFB"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFE0B2"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFE8F5E9"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1565C0"/></patternFill></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="6">
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
    <xf numFmtId="0" fontId="3" fillId="4" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1">
      <alignment horizontal="left" vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="4" fillId="5" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1">
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
#     Dny/
#       YYMMDD_N_MD1.xlsx            ← nove denni davky
#       sloučeno/
#         YYMMDD_N_MD1.xlsx          ← uz sloučene
MAIN_FOLDER_NAME = "Popis_měření_MD1"
DAYS_SUBFOLDER_NAME = "Dny"
DAYS_SUBFOLDER_LEGACY = "MD1_popis_dny"
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
    if style == STYLE_UPDATED:
        return Role.UPDATED
    return Role.DATA


def is_update_text(text: str) -> bool:
    return text.strip().lower().startswith(UPDATE_PREFIX.lower())


def make_update_row() -> Row:
    stamp = datetime.now().strftime("%d.%m.%Y %H:%M")
    return Row(a=f"{UPDATE_PREFIX} {stamp}", role=Role.UPDATED)


def strip_update_rows(rows: List[Row]) -> List[Row]:
    """Odstraní horní řádek(y) 'Naposledy aktualizováno' (+ volitelný blank pod nimi)."""
    out = list(rows)
    while out and (
        out[0].role == Role.UPDATED
        or is_update_text(out[0].a)
    ):
        out.pop(0)
        if out and out[0].role == Role.BLANK:
            out.pop(0)
    return out


def with_update_stamp(rows: List[Row]) -> List[Row]:
    body = strip_update_rows(rows)
    stamped = [make_update_row(), Row(role=Role.BLANK)]
    stamped.extend(body)
    return stamped


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
        elif is_update_text(a) or style_a == STYLE_UPDATED:
            role = Role.UPDATED
        elif b == BUTTON_LABEL and not (c or d):
            # samotné tlačítko / zbytek řádku aktualizace
            role = Role.UPDATED
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
    if not path.is_file():
        return []
    size = path.stat().st_size
    if size < 64:
        raise OSError(f"Soubor je moc malý ({size} B) — OneDrive asi nestáhl obsah: {path}")
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
                    raise OSError(f"V xlsx chybí sheet XML: {path}")
                sheet_key = sheets[0]
            xml = zf.read(sheet_key).decode("utf-8", errors="replace")
            xml = re.sub(r"<(/?)([A-Za-z0-9._-]+):", r"<\1", xml)
            return parse_sheet(xml, shared)
    except zipfile.BadZipFile as exc:
        raise OSError(f"Soubor není platné xlsx/zip (zámek Excel/OneDrive?): {path}") from exc


def backup_summary_before_write(path: Path) -> Optional[Path]:
    """Záloha do Zalohy/. Vrátí cestu k záloze (obnova při chybě zápisu)."""
    if not path.is_file():
        return None
    bak_dir = path.parent / BACKUP_FOLDER_NAME
    bak_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    bak = bak_dir / f"{path.stem}_{stamp}.xlsx"
    try:
        shutil.copy2(path, bak)
        print(f"Záloha: {bak}")
    except OSError as exc:
        print(f"  ! Záloha se nepodařila: {exc}")
        return None
    pattern = f"{path.stem}_*.xlsx"
    old = sorted(bak_dir.glob(pattern), key=lambda p: p.stat().st_mtime, reverse=True)
    for p in old[BACKUP_KEEP:]:
        try:
            p.unlink()
        except OSError:
            pass
    return bak


def xlsx_looks_valid(path: Path) -> bool:
    if not path.is_file() or path.stat().st_size < 64:
        return False
    try:
        with path.open("rb") as fh:
            if fh.read(2) != b"PK":
                return False
        with zipfile.ZipFile(path) as zf:
            names = [n.replace("\\", "/").lower() for n in zf.namelist()]
            return any(
                n.startswith("xl/worksheets/sheet") and n.endswith(".xml") for n in names
            )
    except (OSError, zipfile.BadZipFile):
        return False


def restore_summary_from_backup(path: Path, bak: Optional[Path]) -> bool:
    if bak is None or not bak.is_file():
        return False
    try:
        shutil.copy2(bak, path)
        print(
            "Obnoven souhrn z této zálohy (zápis selhal, OneDrive nedostane starou verzi z cloudu):\n"
            f"  {bak}"
        )
        return True
    except OSError as exc:
        print(f"  ! Obnova ze zálohy selhala: {exc}")
        return False


def remove_orphan_merge_junk(summary_path: Path) -> None:
    parent = summary_path.parent
    if not parent.is_dir():
        return
    n = 0
    for p in parent.iterdir():
        if not p.is_file():
            continue
        name = p.name
        junk = (
            (name.startswith(".sloucit_tmp_") and name.endswith(".xlsx"))
            or name.endswith(".xlsx.bak")
            or name.startswith("~$")
        )
        if junk:
            try:
                p.unlink()
                n += 1
                print(f"  smazán odpad: {name}")
            except OSError:
                pass
    if n:
        print(f"Uklizeno dočasných/matoucích souborů: {n}")


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
    if role == Role.UPDATED:
        return STYLE_UPDATED
    return STYLE_DATA_CENTER


def sheet_xml(rows: Iterable[Row]) -> str:
    parts = [
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
        '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"'
        ' xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">',
        # Zmrazený 1. řádek (razítko + tlačítko) zůstane viditelný při scrollování
        '<sheetViews><sheetView workbookViewId="0">'
        '<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>'
        '<selection pane="bottomLeft" activeCell="A2" sqref="A2"/>'
        "</sheetView></sheetViews>",
        "<cols>",
        '<col min="1" max="1" width="36" customWidth="1"/>',
        '<col min="2" max="2" width="16" customWidth="1"/>',
        '<col min="3" max="3" width="14" customWidth="1"/>',
        '<col min="4" max="4" width="36" customWidth="1"/>',
        "</cols>",
        "<sheetData>",
    ]
    button_row = 0
    for idx, row in enumerate(rows):
        r = idx + 1
        ht = "12" if row.role == Role.BLANK else "24"
        parts.append(f'<row r="{r}" ht="{ht}" customHeight="1">')
        if row.role == Role.BLANK:
            pass
        elif row.role == Role.UPDATED:
            parts.append(cell_xml(f"A{r}", row.a, STYLE_UPDATED))
            parts.append(cell_xml(f"B{r}", BUTTON_LABEL, STYLE_BUTTON))
            if button_row == 0:
                button_row = r
        elif row.role in (Role.DATE, Role.STATION):
            parts.append(cell_xml(f"A{r}", row.a, style_index(row.role)))
        else:
            parts.append(cell_xml(f"A{r}", row.a, STYLE_DATA_CENTER))
            parts.append(cell_xml(f"B{r}", row.b, STYLE_DATA_CENTER))
            parts.append(cell_xml(f"C{r}", row.c, STYLE_DATA_CENTER))
            parts.append(cell_xml(f"D{r}", row.d, STYLE_DATA_CENTER))
        parts.append("</row>")
    parts.append("</sheetData>")
    if button_row:
        safe = xml_escape(BUTTON_LABEL)
        parts.append(
            f'<hyperlinks><hyperlink ref="B{button_row}" r:id="rId1" display="{safe}"/></hyperlinks>'
        )
    parts.append("</worksheet>")
    return "".join(parts)


def write_xlsx(path: Path, rows: List[Row]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    bak = backup_summary_before_write(path)
    bat = path.parent / UPDATE_BAT_NAME
    tmp = path.parent / f".sloucit_tmp_{os.getpid()}_{int(time.time())}.xlsx"
    try:
        with zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_DEFLATED) as zf:
            zf.writestr("[Content_Types].xml", CONTENT_TYPES)
            zf.writestr("_rels/.rels", RELS_ROOT)
            zf.writestr("xl/workbook.xml", WORKBOOK)
            zf.writestr("xl/_rels/workbook.xml.rels", RELS_WB)
            zf.writestr("xl/styles.xml", STYLES)
            zf.writestr("xl/worksheets/sheet1.xml", sheet_xml(rows))
            zf.writestr("xl/worksheets/_rels/sheet1.xml.rels", sheet_rels_xml(bat))
        if not xlsx_looks_valid(tmp):
            raise OSError("Dočasný soubor po zápisu není platné xlsx.")
        # overwrite na místě — NIKDY nemazat cíl před úspěchem
        shutil.copy2(tmp, path)
        if not xlsx_looks_valid(path):
            restore_summary_from_backup(path, bak)
            raise OSError(
                f"Po zápisu soubor není platné xlsx — obnoveno ze zálohy: {path}"
            )
    except Exception:
        if bak is not None and not xlsx_looks_valid(path):
            restore_summary_from_backup(path, bak)
        raise
    finally:
        try:
            if tmp.is_file():
                tmp.unlink()
        except OSError:
            pass


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


def days_folder(main: Path) -> Path:
    """Dny/ (nebo legacy MD1_popis_dny, pokud existuje a má soubory)."""
    dny = main / DAYS_SUBFOLDER_NAME
    legacy = main / DAYS_SUBFOLDER_LEGACY
    if dny.is_dir():
        return dny
    if legacy.is_dir() and list_md1_files(legacy):
        return legacy
    return dny


def resolve_layout(folder: Path) -> Tuple[Path, Path]:
    """
    Vrátí (složka_s_denními, cesta_k_souhrnu).

    Preferuje Dny/ (popř. legacy MD1_popis_dny), pokud v ní něco je.
    Jinak bere denní soubory přímo z hlavní složky.
    """
    folder = folder.resolve()
    summary = folder / SUMMARY_XLSX_NAME

    if folder.name in (DAYS_SUBFOLDER_NAME, DAYS_SUBFOLDER_LEGACY):
        parent = folder.parent
        return folder, parent / SUMMARY_XLSX_NAME

    days_sub = days_folder(folder)
    if days_sub.is_dir() and list_md1_files(days_sub):
        return days_sub, summary

    if list_md1_files(folder):
        return folder, summary

    if days_sub.is_dir():
        return days_sub, summary

    return folder, summary


def time_to_minutes(text: str) -> int:
    """HH:MM / H:MM / HH.MM → minuty od půlnoci, jinak -1."""
    t = (text or "").strip()
    m = re.match(r"^(\d{1,2})[:.](\d{2})$", t)
    if not m:
        return -1
    hh, mm = int(m.group(1)), int(m.group(2))
    if hh > 23 or mm > 59:
        return -1
    return hh * 60 + mm


def merge_folder(
    folder: Path,
    verbose: bool = True,
    base_rows: Optional[List[Row]] = None,
) -> Tuple[List[Row], int, int, List[Path]]:
    out: List[Row] = strip_update_rows(list(base_rows or []))
    current_date = ""
    last_station = ""
    last_time_min = -1
    data_under_station = False
    for row in out:
        if row.role == Role.DATE and row.a:
            current_date = row.a
            last_station = ""
            last_time_min = -1
            data_under_station = False
        elif row.role == Role.STATION and row.a:
            last_station = row.a
            last_time_min = -1
            data_under_station = False
        elif row.role == Role.DATA:
            data_under_station = True
            tm = time_to_minutes(row.c)
            if tm >= 0:
                last_time_min = tm
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
                    last_time_min = -1
                    data_under_station = False
                    wrote = True
                continue

            if row.role == Role.STATION:
                if not current_date and file_date_guess:
                    if out:
                        out.append(Row(role=Role.BLANK))
                    out.append(Row(a=file_date_guess, role=Role.DATE))
                    current_date = file_date_guess
                    last_station = ""
                    last_time_min = -1
                    data_under_station = False
                # Stanice může být v jednom dni víckrát (Nymburk → Poděbrady → Nymburk).
                same_name = row.a == last_station
                consecutive_dup = same_name and not data_under_station
                if not consecutive_dup:
                    if same_name and data_under_station and verbose:
                        print(f"    nová návštěva stanice: {row.a}")
                    out.append(Row(role=Role.BLANK))
                    out.append(Row(a=row.a, role=Role.STATION))
                    last_station = row.a
                    last_time_min = -1
                    data_under_station = False
                    wrote = True
                continue

            # DATA
            if not current_date and file_date_guess:
                if out:
                    out.append(Row(role=Role.BLANK))
                out.append(Row(a=file_date_guess, role=Role.DATE))
                current_date = file_date_guess
                last_station = ""
                last_time_min = -1
                data_under_station = False

            tm = time_to_minutes(row.c)
            if (
                last_station
                and data_under_station
                and tm >= 0
                and last_time_min >= 0
                and tm < (last_time_min - 15)
            ):
                if verbose:
                    print(
                        f"    čas skáče zpět ({row.c}) u {last_station} — nová návštěva"
                    )
                out.append(Row(role=Role.BLANK))
                out.append(Row(a=last_station, role=Role.STATION))
                last_time_min = -1
                data_under_station = False

            out.append(Row(a=row.a, b=row.b, c=row.c, d=row.d, role=Role.DATA))
            data_under_station = True
            if tm >= 0:
                last_time_min = tm
            wrote = True

        if wrote:
            ok += 1
            processed.append(path)
        else:
            skipped += 1

    return out, ok, skipped, processed


def archive_merged_files(files: List[Path], summary_path: Path) -> Path:
    """Přesune sloučené denní soubory do Popis_…/Dny/sloučeno/."""
    archive = days_folder(summary_path.parent) / ARCHIVE_FOLDER_NAME
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


class MergeLock:
    """Souborový zámek — dva uživatelé nesmí spustit Aktualizovat najednou."""

    def __init__(self, summary_path: Path) -> None:
        self.path = summary_path.parent / MERGE_LOCK_NAME
        self._fh = None

    def acquire(self) -> None:
        who = os.environ.get("USERNAME") or os.environ.get("USER") or "unknown"
        host = os.environ.get("COMPUTERNAME") or os.environ.get("HOSTNAME") or ""
        if host:
            who = f"{who}@{host}"
        body = (
            f"user={who}\n"
            f"started={datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n"
            f"pid={os.getpid()}\n"
        ).encode("utf-8")
        self.path.parent.mkdir(parents=True, exist_ok=True)
        for _ in range(3):
            if self.path.is_file():
                age_min = (time.time() - self.path.stat().st_mtime) / 60.0
                info_user = "?"
                try:
                    raw = self.path.read_text(encoding="utf-8", errors="replace")
                    for line in raw.splitlines():
                        if line.startswith("user="):
                            info_user = line[5:].strip()
                except OSError:
                    pass
                if age_min >= MERGE_LOCK_STALE_MINUTES:
                    print(
                        f"Starý zámek ({MERGE_LOCK_STALE_MINUTES} min+) od {info_user} — přebírám."
                    )
                    try:
                        self.path.unlink()
                    except OSError:
                        pass
                else:
                    print("Aktualizace už běží u jiného uživatele — zkus to za chvíli.")
                    print(f"  Zámek: {self.path}")
                    print(f"  Uživatel: {info_user}")
                    raise RuntimeError("merge lock held by another user")
            try:
                fd = os.open(str(self.path), os.O_CREAT | os.O_EXCL | os.O_RDWR)
                self._fh = os.fdopen(fd, "r+b")
                self._fh.write(body)
                self._fh.flush()
                print(f"Zámek aktualizace: {self.path} ({who})")
                return
            except FileExistsError:
                time.sleep(0.4)
            except OSError:
                time.sleep(0.4)
        raise RuntimeError("cannot acquire merge lock")

    def release(self) -> None:
        if self._fh is not None:
            try:
                self._fh.close()
            except OSError:
                pass
            self._fh = None
        if self.path.is_file():
            try:
                self.path.unlink()
            except OSError as exc:
                print(f"  ! Zámek se nepodařilo smazat: {exc}")


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

    lock = MergeLock(out_path)
    try:
        lock.acquire()
        remove_orphan_merge_junk(out_path)
        return _main_locked(source, out_path, list_md1_files(source))
    except RuntimeError as exc:
        print(f"CHYBA: {exc}", file=sys.stderr)
        return 4
    finally:
        lock.release()


def _main_locked(source: Path, out_path: Path, files: List[Path]) -> int:
    if not files:
        print(f"Není nic nového ke sloučení (žádné *_MD1.xlsx v {source}).")
        print("To není chyba — nové denní soubory z appky dej do Dny/.")
        print("Souhrn nepřepisuji (žádná nová data) — jen otevřu.")
        if out_path.is_file() and out_path.stat().st_size > 64:
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
        else:
            print(f"Souhrn zatím neexistuje: {out_path}")
        return 0

    print(f"Denní soubory: {source}")
    print(f"Souhrn:        {out_path}")
    base_rows: List[Row] = []
    if out_path.is_file() and out_path.stat().st_size > 64:
        summary_size = out_path.stat().st_size
        try:
            base_rows = strip_update_rows(read_xlsx(out_path))
            if not base_rows and summary_size > 2500:
                raise OSError(
                    f"Souhrn má {summary_size} B, ale načetlo se 0 řádků. "
                    "Soubor je pravděpodobně zamčený nebo poškozený."
                )
            if base_rows:
                print(f"Načten existující souhrn: {len(base_rows)} řádků")
        except OSError as exc:
            print("CHYBA: Nelze bezpečně načíst existující souhrn.", file=sys.stderr)
            print(f"  {exc}", file=sys.stderr)
            print("  Soubor NEBUDE přepsán.", file=sys.stderr)
            return 3
    rows, ok, skipped, processed = merge_folder(source, base_rows=base_rows)
    data_out = sum(1 for r in rows if r.role == Role.DATA)
    if data_out == 0:
        print(
            "CHYBA: do souhrnu se nedostala žádná data.\n"
            f"Zkontroluj originální soubory z appky v {DAYS_SUBFOLDER_NAME}/.",
            file=sys.stderr,
        )
        return 1

    rows = with_update_stamp(rows)
    write_xlsx(out_path, rows)
    print(f"Aktualizace: {rows[0].a}")

    print(f"Soubory OK: {ok}")
    print(f"Přeskočeno: {skipped}")
    print(f"Datových řádků: {data_out}")
    print(f"Uloženo: {out_path}")

    archive = archive_merged_files(processed, out_path)
    print(f"Přesunuto do {DAYS_SUBFOLDER_NAME}/{ARCHIVE_FOLDER_NAME}/: {len(processed)} souborů ({archive})")

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
