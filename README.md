# Měření v0.8.0

## Aktualizace na tabletu
Instaluj **stejný typ** APK jako dřív (debug přes debug):
```bash
adb install -r app/build/outputs/apk/debug/mereni-v0.8.0-debug.apk
```
`applicationId` = `cz.mereni.app`, `versionCode` roste s každou verzí.

## Klasifikace
- **Spojka:** prázdná POLOHA + `COBJEKT_TPI=zhl` + IOB `X`/`S`
- **Kolej:** prázdná POLOHA, není zhl, IOB ≠ X/S (včetně A/B)
- **Výhybka:** neprázdná POLOHA

## UI
- Světlá paleta
- Pořadí chipů: ‹ ›
- Čas bez − chipu, velký monospace picker bez ±1 min
- Po výběru SQLite hláška „Načítám…“ v horním řádku
