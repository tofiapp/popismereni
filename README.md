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
  Popis_měření_MD1.xlsx      ← souhrn (skript, připojuje nová data)
  YYMMDD_N_MD1.xlsx         ← nové denní soubory
  MD1_popis_dny/            ← nebo sem
  sloučeno/                 ← už sloučené denní soubory
```

Skript bere nové soubory z hlavní složky / `MD1_popis_dny`,
po sloučení je přesune do `sloučeno/` a otevře souhrn v Excelu.

## Sloučení denních souborů na PC

Nic se neinstaluje (Windows PowerShell):

1. Zkopíruj `tools\SloucitMereni.bat` a `tools\sloucit_mereni.ps1` do `Popis_měření_MD1`
2. Dvojklik na bat → vznikne `Popis_měření_MD1.xlsx`

Podrobnosti: [`tools/README-SloucitMereni.md`](tools/README-SloucitMereni.md).
