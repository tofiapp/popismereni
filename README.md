# Měření v0.44.0

## Aktualizace
```bash
adb install -r artifacts/mereni-v0.44.0-debug.apk
```

## OneDrive (sdílení Intent)
**Uložit na OneDrive** otevře sdílení (OneDrive / Files).

Soubor je vždy `YYMMDD_N_MD1.xlsx` (N = 1, 2, 3… ten den). Po ANO se místní záznamy vymažou.
Graph/MSAL už není.

## Sloučení denních souborů na PC

Nic se neinstaluje (Windows PowerShell):

1. Zkopíruj `tools\SloucitMereni.bat` a `tools\sloucit_mereni.ps1` do složky s `*_MD1.xlsx`
2. Dvojklik na bat → vznikne `Souhrn_mereni.xlsx`

Podrobnosti: [`tools/README-SloucitMereni.md`](tools/README-SloucitMereni.md).
