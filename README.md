# Měření

Jednoduchá tabletová aplikace pro zapisování měření. Záznam se skládá ze tří polí,
která se vyplňují klepáním na klávesnici v dolní části obrazovky. Uložený záznam
přibude jako řádek v CSV souboru.

**Aktuální verze: `v0.1.0`** (zdroj pravdy: soubor [`VERSION`](VERSION) —
podrobnosti v [`VERSIONING.md`](VERSIONING.md)).

## Obrazovka

```
┌──────────────────────┬──────────────────────┬──────────────┐
│ CO SE MĚŘÍ           │ ODKUD – KAM          │ ČAS MĚŘENÍ   │
│ [Teplota] [Tlak]     │ [10] – [1A]          │ [7] [:] [30] │
└──────────────────────┴──────────────────────┴──────────────┘
  [Uložit záznam] [Vymazat]              mereni.csv • 12 záznamů
┌─────────────────────────────────────────────────────────────┐
│  klávesnice aktivního pole                                  │
└─────────────────────────────────────────────────────────────┘
```

- Do každého pole jde naklikat neomezený počet obdélníčků.
- Každý obdélníček má v levém horním rohu malé kulaté tlačítko **−** pro smazání.
- Ve druhém poli se obdélníčky oddělují pomlčkou (ODKUD – KAM).
- Klepnutím na pole se přepne klávesnice dole na jeho vlastní sadu kláves.

### Klávesnice ODKUD – KAM (pasport TPI)

Data: tabulka `DZS_SUPER_RO_TPI` ze souboru `DZS_PASPORT_TPI.sqlite`,
exportovaná do `app/src/main/assets/pasport_tpi_v{VERSION}.json`.

| Obdélník | Obsah | Rozlišení |
|---|---|---|
| **1 (ODKUD)** | Spojky + koleje vedle sebe | jiné barvy |
| **2 (KAM)** | Výhybky | jiná barva než spojky/koleje |

- **Výhybky** — `POLOHA` ∈ JAP, JBP, JAL, JBL, JCP, JDP, JCL, JDL, CA…CH;
  číslo v `COBJEKT`, někdy písmeno v `IOB`; s IOB se zobrazují samostatně.
- **Spojky** — prázdná `POLOHA`, `COBJEKT_TPI` = `zhl`, `IOB` vždy `X` nebo `S`.
- **Koleje** — prázdná `POLOHA` i `COBJEKT_TPI`, `IOB` nikdy X/S;
  prázdné IOB = hlavní volba, varianty s IOB až po rozkliknutí (▾).

Export ze SQLite:

```bash
python3 tools/export_pasport_v0.1.0.py path/to/DZS_PASPORT_TPI.sqlite
```

## Data měření

Soubor `mereni.csv` v `Android/data/cz.mereni.app/files/Documents/`,
oddělovač `;`, UTF-8 s BOM (otevře se rovnou v Excelu).

| zapsano | co_se_meri | odkud_kam | cas_mereni |
|---|---|---|---|

## Sestavení

Otevřít v Android Studiu (Giraffe a novější) a spustit. Nebo z příkazové řádky:

```bash
gradle wrapper          # jednorázově, pokud chybí gradlew
./gradlew assembleDebug
```

Výstup: `app/build/outputs/apk/debug/mereni-v0.1.0-debug.apk`

minSdk 24, cíl Android 14, Jetpack Compose, orientace na šířku.

## CI

`.github/workflows/android.yml` sestaví APK při každém pushi do `main`, u pull requestů
a na vyžádání (záložka Actions → Android CI → Run workflow).

Artefakt se jmenuje `apk-v0.1.0` a obsahuje:

- `mereni-v0.1.0-debug.apk`
- `mereni-v0.1.0-release-unsigned.apk`

Debug APK jde rovnou nainstalovat na tablet (instalace z neznámých zdrojů).

Když pushneš tag začínající `v` (např. `git tag v0.1.0 && git push --tags`), workflow
navíc založí GitHub Release a APK do něj přiloží.

## Co ještě chybí

- Nahrazení ukázkového `pasport_tpi_v0.1.0.json` exportem z reálné `DZS_PASPORT_TPI.sqlite`.
- Skutečný katalog „CO SE MĚŘÍ“ (zatím výplň).
- Přehled a editace už uložených záznamů.
- Sdílení / export CSV mimo aplikaci.
