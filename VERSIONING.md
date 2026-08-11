# Verzování

Každá verze aplikace i souvisejících datových souborů musí být **číslovaná
a číslo verze musí být i v názvu souboru**.

## Zdroj pravdy

Soubor [`VERSION`](VERSION) v kořeni repa, formát `MAJOR.MINOR.PATCH`
(aktuálně `0.49.1`).
Z něj se odvozuje:

| Místo | Forma |
|---|---|
| Android `versionName` | `0.49.1` |
| Android `versionCode` | `MAJOR*105000 + MINOR*1000 + PATCH + 70000` → `117000` |
| APK artefakty | `mereni-v0.49.1-debug.apk`, `mereni-v0.49.1-release-unsigned.apk` |
| Pasport JSON v assets | `pasport_tpi_v0.49.1.json` (nebo nejbližší dostupný fallback) |
| Git tag / GitHub Release | `v0.49.1` |
| UI | zobrazení `v0.49.1` vedle názvu aplikace |

## Aktualizace na tabletu (bez přeinstalace)

Debug APK se vždy podepisuje sdíleným klíčem `app/keystore/mereni-debug.jks`
(stejný v CI i lokálně). Instaluj **debug** přes **debug**:

```bash
adb install -r artifacts/mereni-v0.49.1-debug.apk
```

`applicationId` = `cz.mereni.app`, `versionCode` vždy roste s `VERSION`.
Release-unsigned APK nelze spolehlivě aktualizovat přes starší instalaci — na tablety ber debug.

## Pravidla

1. Před releasem zvyš `VERSION`, přejmenuj exportní skript a JSON na novou verzi.
2. CI čte `VERSION` a pojmenuje APK vždy s `v{VERSION}`.
3. Tag `v*` spustí GitHub Release s APK přiloženými pod stejným číslem verze.
4. Necommitovat „nečíslované“ varianty artefaktů (`mereni-debug.apk`, `pasport_tpi.json`
   jako jediný soubor bez verze). Fallback `pasport_tpi.json` v kódu je jen nouzový.
