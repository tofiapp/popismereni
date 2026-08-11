# Měření v0.45.0

## Aktualizace
```bash
adb install -r artifacts/mereni-v0.45.0-debug.apk
```

## OneDrive (sdílení Intent)
**Uložit na OneDrive** otevře sdílení (OneDrive / Files).

Denní soubor: `YYMMDD_N_MD1.xlsx` (N = 1, 2, 3… ten den). Po ANO se místní záznamy vymažou.

### Struktura složek
```
Popis_měření_MD1/
  Popis_měření_MD1.xlsx      ← souhrn (skript)
  YYMMDD_N_MD1.xlsx         ← denní soubory sem
  MD1_popis_dny/            ← nebo do této podsložky
```

Merge tool bere soubory z `MD1_popis_dny`, pokud tam nějaké jsou;
jinak z hlavní složky `Popis_měření_MD1`.

## Sloučení denních souborů na PC

Nic se neinstaluje (Windows PowerShell):

1. Zkopíruj `tools\SloucitMereni.bat` a `tools\sloucit_mereni.ps1` do `Popis_měření_MD1`
2. Dvojklik na bat → vznikne `Popis_měření_MD1.xlsx`

Podrobnosti: [`tools/README-SloucitMereni.md`](tools/README-SloucitMereni.md).
