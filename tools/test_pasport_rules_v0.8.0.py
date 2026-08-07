#!/usr/bin/env python3
"""Spojka = IOB X/S; kolej = ostatní IOB včetně prázdného; zhl nerozhoduje."""

SPOJKA_IOB = {"X", "S"}


def norm(v):
    v = (v or "").strip()
    return "" if v in ("", "-", "—", ".", "null", "NULL") else v


def classify(cobjekt, iob, poloha, tpi):
    poloha = norm(poloha).upper()
    iob = norm(iob).upper()
    cobjekt = norm(cobjekt)
    if not cobjekt:
        return None
    if poloha:
        return "VYHYBKA"
    if iob in SPOJKA_IOB:
        return "SPOJKA"
    return "KOLEJ"


assert classify("2", "X", "", "zhl") == "SPOJKA"
assert classify("2", "S", "", "") == "SPOJKA"
assert classify("2", "A", "", "zhl") == "KOLEJ"  # kolej smí mít zhl
assert classify("2", "B", "", "") == "KOLEJ"
assert classify("10", "", "", "") == "KOLEJ"  # prázdné IOB = kolej
assert classify("10", "", "", "zhl") == "KOLEJ"
assert classify("1", "A", "JAP", "") == "VYHYBKA"
print("ok — v0.8.0")
