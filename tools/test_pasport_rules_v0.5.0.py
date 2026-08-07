#!/usr/bin/env python3
"""Testy: čištění JMENO, UDU, klasifikace."""

from __future__ import annotations

import re

PREFIX_RE = re.compile(r"^(žst\.|odb\.|z\.)\s*", re.IGNORECASE)
SPOJKA_IOB = {"X", "S"}


def clean_jmeno(raw: str) -> str:
    s = (raw or "").strip()
    while True:
        m = PREFIX_RE.match(s)
        if not m:
            break
        s = s[m.end() :].strip()
    return s


def norm(v):
    v = (v or "").strip()
    if v in ("", "-", "—", ".", "null", "NULL"):
        return ""
    return v


def classify(cobjekt, iob, poloha, tpi):
    poloha = norm(poloha).upper()
    tpi = norm(tpi)
    iob = norm(iob).upper()
    cobjekt = norm(cobjekt)
    if not cobjekt:
        return None
    tpi_zhl = tpi.lower() == "zhl" or "zhl" in tpi.lower()
    if poloha:
        return "VYHYBKA"
    if tpi_zhl:
        return "SPOJKA"
    if not tpi and iob not in SPOJKA_IOB:
        return "KOLEJ"
    return None


def main() -> None:
    assert clean_jmeno("žst. Meziměstí") == "Meziměstí"
    assert clean_jmeno("odb. Hronov") == "Hronov"
    assert "12345XX"[:5] == "12345"
    assert classify("1", "A", "JAP", "") == "VYHYBKA"
    assert classify("2", "X", "", "zhl") == "SPOJKA"
    assert classify("10", "", "", "") == "KOLEJ"
    assert classify("10", "A", "", "") == "KOLEJ"
    assert classify("10", "X", "", "") is None
    print("ok — v0.5.0")


if __name__ == "__main__":
    main()
