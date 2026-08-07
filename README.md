# Měření v0.9.0

## Aktualizace na tabletu
Instaluj **stejný typ** APK jako dřív (debug přes debug):
```bash
adb install -r app/build/outputs/apk/debug/mereni-v0.9.0-debug.apk
```
`applicationId` = `cz.mereni.app`, `versionCode` roste s každou verzí.

## Klasifikace
- **Výhybka:** neprázdná POLOHA
- **Spojka:** prázdná POLOHA + IOB `X`/`S`
- **Kolej:** prázdná POLOHA + IOB ≠ X/S (včetně prázdného IOB)

## UI v0.9.0
- Prohozené barvy typů (kolej modrá, spojka měděná, výhybka zelená) — stejné barvy i na chipách nahoře
- Pořadí chipů: dlouhý stisk + táhnutí (bez ‹ ›)
- Podkoleje: klepnutí na celou klávesu otevře velký picker
- Čas vycentrovaný v poli
- Pasport (Vybrat / Obnovit) ve ⚙ vedle mereni.csv
- Spodní řádek: poznámka vlevo, menší Uložit / Vymazat vpravo
- CSV: sloupec `poznamka`
