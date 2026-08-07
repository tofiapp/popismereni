#!/usr/bin/env python3
SPOJKA_IOB={"X","S"}

def norm(v):
    v=(v or "").strip()
    return "" if v in ("","-","—",".","null","NULL") else v

def classify(cobjekt,iob,poloha,tpi):
    poloha=norm(poloha).upper(); tpi=norm(tpi); iob=norm(iob).upper(); cobjekt=norm(cobjekt)
    if not cobjekt: return None
    tpi_zhl=tpi.lower()=="zhl"
    if poloha: return "VYHYBKA"
    if tpi_zhl and iob in SPOJKA_IOB: return "SPOJKA"
    if not tpi_zhl and iob not in SPOJKA_IOB: return "KOLEJ"
    return None

assert classify("2","X","","zhl")=="SPOJKA"
assert classify("2","A","","")=="KOLEJ"
assert classify("2","B","","")=="KOLEJ"
assert classify("2","A","","zhl") is None
assert classify("1","A","JAP","")=="VYHYBKA"
print("ok — v0.7.0")
