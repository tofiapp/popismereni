#!/usr/bin/env python3
"""Jednoduchý test klasifikačních pravidel (bez Android SDK)."""

from __future__ import annotations

VYHYBKA_POLOHY = {
    "JAP", "JBP", "JAL", "JBL", "JCP", "JDP", "JCL", "JDL",
    "CA", "CB", "CC", "CD", "CE", "CF", "CG", "CH",
}
SPOJKA_IOB = {"X", "S"}


def classify(cobjekt, iob, poloha, cobjekt_tpi):
    cobjekt = (cobjekt or "").strip()
    iob = (iob or "").strip()
    poloha = (poloha or "").strip()
    tpi = (cobjekt_tpi or "").strip()
    if not cobjekt:
        return None
    if poloha and poloha.upper() in VYHYBKA_POLOHY:
        return "VYHYBKA"
    if not poloha and tpi.lower() == "zhl" and iob.upper() in SPOJKA_IOB:
        return "SPOJKA"
    if not poloha and not tpi and iob.upper() not in SPOJKA_IOB:
        return "KOLEJ"
    return None


def build_koleje(rows):
    from collections import defaultdict

    groups = defaultdict(list)
    for r in rows:
        if classify(*r) == "KOLEJ":
            groups[r[0]].append(r)
    result = []
    for cobjekt, group in groups.items():
        main = [g for g in group if not (g[1] or "").strip()]
        variants = [g for g in group if (g[1] or "").strip()]
        if main:
            result.append((cobjekt, "", [v[1] for v in variants]))
        else:
            for v in variants:
                result.append((cobjekt, v[1], []))
    return result


def main() -> None:
    assert classify("1", "A", "JAP", "") == "VYHYBKA"
    assert classify("2", "X", "", "zhl") == "SPOJKA"
    assert classify("2", "S", "", "zhl") == "SPOJKA"
    assert classify("10", "", "", "") == "KOLEJ"
    assert classify("10", "A", "", "") == "KOLEJ"
    assert classify("10", "X", "", "") is None  # X bez zhl ≠ spojka ani kolej
    assert classify("2", "X", "", "") is None

    koleje = build_koleje(
        [
            ("10", "", "", ""),
            ("10", "A", "", ""),
            ("10", "B", "", ""),
            ("11", "C", "", ""),
        ]
    )
    assert ("10", "", ["A", "B"]) in koleje
    assert ("11", "C", []) in koleje
    print("ok — klasifikace v0.1.0")


if __name__ == "__main__":
    main()
