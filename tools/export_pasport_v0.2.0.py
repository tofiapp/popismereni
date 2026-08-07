#!/usr/bin/env python3
"""Export DZS_SUPER_RO_TPI → pasport_tpi_v{VERSION}.json

Použití:
  python3 tools/export_pasport_v0.2.0.py path/to/DZS_PASPORT_TPI.sqlite

Výstup vždy obsahuje verzi v názvu souboru (čti z VERSION v kořeni).
"""

from __future__ import annotations

import json
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = (ROOT / "VERSION").read_text(encoding="utf-8").strip()
OUT_NAME = f"pasport_tpi_v{VERSION}.json"


def main() -> int:
    if len(sys.argv) != 2:
        print(f"Usage: {Path(__file__).name} DZS_PASPORT_TPI.sqlite", file=sys.stderr)
        return 2

    db_path = Path(sys.argv[1])
    if not db_path.exists():
        print(f"Soubor neexistuje: {db_path}", file=sys.stderr)
        return 1

    con = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    con.row_factory = sqlite3.Row
    cur = con.cursor()

    cols = [r[1] for r in cur.execute("PRAGMA table_info(DZS_SUPER_RO_TPI)")]
    colmap = {c.upper(): c for c in cols}
    print("Sloupce:", ", ".join(cols))

    def col(*names: str) -> str | None:
        for n in names:
            if n.upper() in colmap:
                return colmap[n.upper()]
        return None

    c_cobjekt = col("COBJEKT")
    c_iob = col("IOB")
    c_poloha = col("POLOHA")
    c_tpi = col("COBJEKT_TPI")
    c_udu = col("UDU", "CUDU", "KOD_UDU", "UDU_KOD", "NAZEV_UDU")
    if not c_cobjekt:
        print("Tabulka DZS_SUPER_RO_TPI nemá sloupec COBJEKT", file=sys.stderr)
        return 1

    wanted = [c for c in [c_cobjekt, c_iob, c_poloha, c_tpi, c_udu] if c]
    select = ", ".join(f'"{c}"' for c in wanted)
    rows_out = []
    udu_set: set[str] = set()
    for row in cur.execute(f'SELECT {select} FROM "DZS_SUPER_RO_TPI"'):
        def get(name: str | None) -> str:
            if not name:
                return ""
            v = row[name]
            return "" if v is None else str(v).strip()

        udu = get(c_udu)
        if udu:
            udu_set.add(udu)
        rows_out.append(
            {
                "cobjekt": get(c_cobjekt),
                "iob": get(c_iob),
                "poloha": get(c_poloha),
                "cobjekt_tpi": get(c_tpi),
                "udu": udu,
            }
        )
    con.close()

    payload = {
        "version": VERSION,
        "source": f"{db_path.name} / DZS_SUPER_RO_TPI",
        "udu": sorted(udu_set),
        "rows": rows_out,
    }

    out_assets = ROOT / "app" / "src" / "main" / "assets" / OUT_NAME
    out_data = ROOT / "data" / OUT_NAME
    out_assets.parent.mkdir(parents=True, exist_ok=True)
    out_data.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(payload, ensure_ascii=False, indent=2)
    out_assets.write_text(text + "\n", encoding="utf-8")
    out_data.write_text(text + "\n", encoding="utf-8")
    print(f"Zapsáno {len(rows_out)} řádků, {len(udu_set)} UDU → {out_assets.relative_to(ROOT)}")
    print(f"Verze souboru: v{VERSION}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
