# Měření

Tabletová aplikace pro zapisování měření. Tři horní pole + UDU picker;
záznam se ukládá do CSV.

**Aktuální verze: `v0.2.0`** ([`VERSION`](VERSION), [`VERSIONING.md`](VERSIONING.md)).

## Obrazovka

```
UDU [picker]                                      mereni.csv • 12
┌────────────────────┬────────────────┬──────────┐
│  [10] [2X]         │  [1A] [3]      │  [07:30] │  ← stejná výška
│  (širší)           │                │          │
└────────────────────┴────────────────┴──────────┘
  [Uložit záznam] [Vymazat]
┌──────────────────────────┬─────────────────────┐
│ Koleje (scroll)          │ Spojky (scroll)     │  ← pole 1
└──────────────────────────┴─────────────────────┘
```

- **Pole 1** (širší): klávesnice půl koleje / půl spojky, obě scrollovatelné.
- **Pole 2**: výhybky ze SQLite (POLOHA ∈ JAP…CH).
- **Pole 3**: aktuální čas, ±1 min, wheel time picker.
- Obdélníčky (klávesy i chipy) mají stejnou výšku.
- Žádné „Teplota/Tlak“, žádné popisky „obdélník 1/2“ ani „ODKUD–KAM“.

## Data

Pasport: `DZS_PASPORT_TPI.sqlite` → `DZS_SUPER_RO_TPI` →
`app/src/main/assets/pasport_tpi_v0.2.0.json`

```bash
python3 tools/export_pasport_v0.2.0.py path/to/DZS_PASPORT_TPI.sqlite
```

CSV `mereni.csv`: `zapsano;udu;pole1;pole2;cas_mereni` (UTF-8 BOM, `;`).

## Aktualizace na tabletu

`applicationId` je vždy `cz.mereni.app` (debug i release). Nové APK
se stejnou signaturou jen aktualizuje stávající instalaci.

```bash
./gradlew assembleDebug
# app/build/outputs/apk/debug/mereni-v0.2.0-debug.apk
adb install -r app/build/outputs/apk/debug/mereni-v0.2.0-debug.apk
```

## CI

Artefakt `apk-v0.2.0` obsahuje verzované APK. Tag `v0.2.0` založí GitHub Release.
