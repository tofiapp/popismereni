#!/usr/bin/env python3
"""Export pasportu TPI → pasport_tpi_v{VERSION}.json

- DZS_SUPER_RO_TPI.TUDU → UDU = prvních 5 znaků
- DZS_SUPER_MT_SL.REPRE_TUDU ↔ UDU, zobrazované jméno z JMENO
  (bez prefixů žst. / odb. / z.)

Použití:
  python3 tools/export_pasport_v0.3.0.py path/to/DZS_PASPORT_TPI.sqlite
"""

from __future__ import annotations

import json
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = (ROOT / "VERSION").read_text(encoding="utf-8").strip()
OUT_NAME = f"pasport_tpi_v{VERSION}.json"

PREFIX_RE = re.compile(r"^(žst\.|odb\.|z\.)\s*", re.IGNORECASE)


def clean_jmeno(raw: str) -> str:
    s = (raw or "").strip()
    while True:
        m = PREFIX_RE.match(s)
        if not m:
            break
        s = s[m.end() :].strip()
    return s


def colmap_for(cur: sqlite3.Cursor, table: str) -> dict[str, str]:
    cols = [r[1] for r in cur.execute(f'PRAGMA table_info("{table}")')]
    return {c.upper(): c for c in cols}


def pick(cmap: dict[str, str], *names: str) -> str | None:
    for n in names:
        if n.upper() in cmap:
            return cmap[n.upper()]
    return None


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

    tables = {r[0].upper(): r[0] for r in cur.execute(
        "SELECT name FROM sqlite_master WHERE type='table'"
    )}
    t_ro = tables.get("DZS_SUPER_RO_TPI")
    t_sl = tables.get("DZS_SUPER_MT_SL")
    if not t_ro:
        print("Chybí tabulka DZS_SUPER_RO_TPI", file=sys.stderr)
        return 1
    if not t_sl:
        print("Chybí tabulka DZS_SUPER_MT_SL", file=sys.stderr)
        return 1

    ro = colmap_for(cur, t_ro)
    sl = colmap_for(cur, t_sl)
    print("RO sloupce:", ", ".join(ro.values()))
    print("SL sloupce:", ", ".join(sl.values()))

    c_cobjekt = pick(ro, "COBJEKT")
    c_iob = pick(ro, "IOB")
    c_poloha = pick(ro, "POLOHA")
    c_tpi = pick(ro, "COBJEKT_TPI")
    c_tudu = pick(ro, "TUDU", "UDU")
    c_repre = pick(sl, "REPRE_TUDU", "TUDU", "UDU")
    c_jmeno = pick(sl, "JMENO", "NAZEV", "NAME")

    if not c_cobjekt or not c_tudu:
        print("RO musí mít COBJEKT a TUDU", file=sys.stderr)
        return 1
    if not c_repre or not c_jmeno:
        print("SL musí mít REPRE_TUDU a JMENO", file=sys.stderr)
        return 1

    # Stanice: REPRE_TUDU → JMENO
    stations_by_udu: dict[str, dict] = {}
    for row in cur.execute(f'SELECT "{c_repre}", "{c_jmeno}" FROM "{t_sl}"'):
        repre = "" if row[0] is None else str(row[0]).strip()
        jmeno_raw = "" if row[1] is None else str(row[1]).strip()
        if not repre:
            continue
        udu = repre[:5]
        jmeno = clean_jmeno(jmeno_raw)
        if not jmeno:
            continue
        # první výskyt vyhraje; případně delší jméno
        prev = stations_by_udu.get(udu)
        if prev is None or len(jmeno) > len(prev["jmeno"]):
            stations_by_udu[udu] = {
                "udu": udu,
                "jmeno": jmeno,
                "jmeno_raw": jmeno_raw,
            }

    wanted = [c for c in [c_cobjekt, c_iob, c_poloha, c_tpi, c_tudu] if c]
    select = ", ".join(f'"{c}"' for c in wanted)
    rows_out = []
    for row in cur.execute(f'SELECT {select} FROM "{t_ro}"'):
        def get(name: str | None) -> str:
            if not name:
                return ""
            v = row[name]
            return "" if v is None else str(v).strip()

        tudu = get(c_tudu)
        udu = tudu[:5] if tudu else ""
        rows_out.append(
            {
                "cobjekt": get(c_cobjekt),
                "iob": get(c_iob),
                "poloha": get(c_poloha),
                "cobjekt_tpi": get(c_tpi),
                "tudu": tudu,
                "udu": udu,
            }
        )
    con.close()

    # Jen stanice, které mají aspoň jeden řádek v RO
    used_udu = {r["udu"] for r in rows_out if r["udu"]}
    stations = [
        stations_by_udu[u]
        for u in sorted(used_udu, key=lambda x: stations_by_udu.get(x, {}).get("jmeno", x).lower())
        if u in stations_by_udu
    ]
    # UDU bez JMENO — přidej aspoň kód (nemělo by se ukazovat ideálně)
    for u in sorted(used_udu):
        if u not in stations_by_udu:
            stations.append({"udu": u, "jmeno": u, "jmeno_raw": u})

    stations.sort(key=lambda s: s["jmeno"].lower())

    payload = {
        "version": VERSION,
        "source": f"{db_path.name} / DZS_SUPER_RO_TPI + DZS_SUPER_MT_SL",
        "stations": stations,
        "rows": rows_out,
    }

    out_assets = ROOT / "app" / "src" / "main" / "assets" / OUT_NAME
    out_data = ROOT / "data" / OUT_NAME
    out_assets.parent.mkdir(parents=True, exist_ok=True)
    out_data.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(payload, ensure_ascii=False, indent=2)
    out_assets.write_text(text + "\n", encoding="utf-8")
    out_data.write_text(text + "\n", encoding="utf-8")
    print(f"Stanic: {len(stations)}, řádků: {len(rows_out)} → {out_assets.relative_to(ROOT)}")
    print(f"Verze souboru: v{VERSION}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
