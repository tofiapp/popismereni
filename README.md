# Měření

Tabletová aplikace pro zapisování měření. Tři horní pole + vyhledávání stanice;
záznam se ukládá do CSV.

**Aktuální verze: `v0.3.0`** ([`VERSION`](VERSION), [`VERSIONING.md`](VERSIONING.md)).

## Obrazovka

```
Stanice [Meziměstí ▾]                             mereni.csv • 12
┌────────────────────┬────────────────┬──────────┐
│  [10] [2X]         │  [1A] [3]      │  [07:30] │  ← stejná výška
│  (širší)           │                │          │
└────────────────────┴────────────────┴──────────┘
```

- **Stanice:** vyhledávání podle `JMENO` (bez prefixů `žst.` / `odb.` / `z.`).
  Interně UDU = prvních 5 znaků `TUDU`, join na `REPRE_TUDU` v `DZS_SUPER_MT_SL`.
- **Pole 1** (širší): půl koleje / půl spojky (scroll).
- **Pole 2:** výhybky.
- **Pole 3:** čas (Teď, ±1 min, wheel picker).
- Obdélníčky kláves i chipů mají jednotnou větší výšku.

## Data

```bash
python3 tools/export_pasport_v0.3.0.py path/to/DZS_PASPORT_TPI.sqlite
```

Export čte:
- `DZS_SUPER_RO_TPI` — objekty + `TUDU` (UDU = `TUDU[:5]`)
- `DZS_SUPER_MT_SL` — `REPRE_TUDU` + `JMENO` → stanice v pickeru

Výstup: `app/src/main/assets/pasport_tpi_v0.3.0.json`

CSV `mereni.csv`: `zapsano;udu;pole1;pole2;cas_mereni`

## Aktualizace na tabletu

`applicationId` vždy `cz.mereni.app` → `adb install -r …mereni-v0.3.0-debug.apk`
