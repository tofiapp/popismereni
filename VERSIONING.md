# Verzování

Každá verze aplikace i souvisejících datových souborů musí být **číslovaná
a číslo verze musí být i v názvu souboru**.

## Zdroj pravdy

Soubor [`VERSION`](VERSION) v kořeni repa, formát `MAJOR.MINOR.PATCH`
(aktuálně `0.3.0`).

Z něj se odvozuje:

| Místo | Forma |
|---|---|
| Android `versionName` | `0.3.0` |
| Android `versionCode` | `MAJOR*10000 + MINOR*300 + PATCH` → `300` |
| APK artefakty | `mereni-v0.3.0-debug.apk`, `mereni-v0.3.0-release-unsigned.apk` |
| Pasport JSON v assets | `pasport_tpi_v0.3.0.json` |
| Exportní skript | `tools/export_pasport_v0.3.0.py` |
| Git tag / GitHub Release | `v0.3.0` |
| UI | zobrazení `v0.3.0` vedle názvu aplikace |

## Pravidla

1. Před releasem zvyš `VERSION`, přejmenuj exportní skript a JSON na novou verzi.
2. CI čte `VERSION` a pojmenuje APK vždy s `v{VERSION}`.
3. Tag `v*` spustí GitHub Release s APK přiloženými pod stejným číslem verze.
4. Necommitovat „nečíslované“ varianty artefaktů (`mereni-debug.apk`, `pasport_tpi.json`
   jako jediný soubor bez verze). Fallback `pasport_tpi.json` v kódu je jen nouzový.
