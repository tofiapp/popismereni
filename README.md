# Měření v0.10.0

## Aktualizace na tabletu (bez přeinstalace)
Od v0.10.0 se **debug APK** vždy podepisuje stejným klíčem (`app/keystore/mereni-debug.jks`).
Na tabletu instaluj vždy **debug** APK přes starší debug:

```bash
adb install -r artifacts/mereni-v0.10.0-debug.apk
```

`applicationId` = `cz.mereni.app`, `versionCode` roste s každou verzí.
Release-unsigned APK na tablety nepoužívej — podpis se neshoduje a Android vyžaduje odinstalaci.

Jednorázově: pokud máš starší instalaci z jiného podpisu, odinstaluj jednou a pak už půjde jen `adb install -r`.

## Klasifikace
- **Výhybka:** neprázdná POLOHA
- **Spojka:** prázdná POLOHA + IOB `X`/`S`
- **Kolej:** prázdná POLOHA + IOB ≠ X/S (včetně prázdného IOB)

## UI v0.10.0
- Popisky polí při výběru: **Koleje a spojky** (barvy typů), **od, do**, **čas**
- Poznámka + Uložit / Vymazat uprostřed (mezi poli a klávesnicí) — mimo systémové menu
- Přesun chipů: tlačítko `<>` v dolním rohu pole aktivuje šipky ‹ ›
- Picker koleje bez nápisu „hlavní“ / „vyber…“
- Klávesnice výhybek: stálá tlačítka **po vůz** a **kkk** vpravo
- CSV: sloupec `poznamka`
