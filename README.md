# Měření

Jednoduchá tabletová aplikace pro zapisování měření. Záznam se skládá ze tří polí,
která se vyplňují klepáním na klávesnici v dolní části obrazovky. Uložený záznam
přibude jako řádek v CSV souboru.

## Obrazovka

```
┌──────────────────────┬──────────────────────┬──────────────┐
│ CO SE MĚŘÍ           │ ODKUD – KAM          │ ČAS MĚŘENÍ   │
│ [Teplota] [Tlak]     │ [Hala A] – [Sklad]   │ [7] [:] [30] │
└──────────────────────┴──────────────────────┴──────────────┘
  [Uložit záznam] [Vymazat]              mereni.csv • 12 záznamů
┌─────────────────────────────────────────────────────────────┐
│  klávesnice aktivního pole                                  │
└─────────────────────────────────────────────────────────────┘
```

- Do každého pole jde naklikat neomezený počet obdélníčků.
- Každý obdélníček má v levém horním rohu malé kulaté tlačítko **−** pro smazání.
- Ve druhém poli se obdélníčky oddělují pomlčkou.
- Klepnutím na pole se přepne klávesnice dole na jeho vlastní sadu kláves.

## Data

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

minSdk 24, cíl Android 14, Jetpack Compose, orientace na šířku.

## CI

`.github/workflows/android.yml` sestaví APK při každém pushi do `main`, u pull requestů
a na vyžádání (záložka Actions → Android CI → Run workflow).

Hotové APK najdeš dole na stránce běhu jako artefakt `apk` — obsahuje debug i nepodepsanou
release verzi. Debug APK jde rovnou nainstalovat na tablet, stačí povolit instalaci
z neznámých zdrojů.

Když pushneš tag začínající `v` (např. `git tag v0.1 && git push --tags`), workflow navíc
založí GitHub Release a APK do něj přiloží.

## Co ještě chybí

- Skutečné popisky kláves — zatím výplň v `ui/Keyboards.kt`.
- Přehled a editace už uložených záznamů.
- Sdílení / export CSV mimo aplikaci.
