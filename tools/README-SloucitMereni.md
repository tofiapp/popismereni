# Sloučení denních souborů (bez instalace)

## Struktura na OneDrive

```
Popis_měření_MD1/
  Popis_měření_MD1.xlsx      ← souhrn (vytvoří skript)
  260811_1_MD1.xlsx         ← denní soubory můžou být přímo tady
  MD1_popis_dny/            ← NEBO v této podsložce
    260811_2_MD1.xlsx
```

Skript nejdřív kouká do `MD1_popis_dny` (když tam něco je).  
Když je podsložka prázdná / chybí, bere denní soubory **přímo z hlavní složky**.

Souhrn: **`Popis_měření_MD1.xlsx`** (stejné jméno jako hlavní složka).

## Rychle (Windows)

1. V OneDrive složka `Popis_měření_MD1` (+ volitelně `MD1_popis_dny`).
2. Denní `YYMMDD_N_MD1.xlsx` z appky dej do hlavní složky nebo do `MD1_popis_dny`.
3. Zkopíruj sem `SloucitMereni.bat` + `sloucit_mereni.ps1`.
4. Dvojklik na bat → vznikne `Popis_měření_MD1.xlsx`.

Nepotřebuješ Python, VBA ani admin práva — PowerShell je součást Windows.

## Ručně

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\sloucit_mereni.ps1 -Folder "C:\Users\...\OneDrive\Popis_měření_MD1"
```
