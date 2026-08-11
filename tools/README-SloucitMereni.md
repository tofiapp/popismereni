# Sloučení denních souborů (bez instalace)

## Struktura na OneDrive

```
Popis_měření_MD1/
  Popis_měření_MD1.xlsx      ← souhrn (vytvoří / přepíše skript)
  260811_1_MD1.xlsx         ← nové denní soubory (hlavní složka)
  MD1_popis_dny/            ← nebo tady
    260811_2_MD1.xlsx
  sloučeno/                 ← sem skript přesune už sloučené denní soubory
    260810_1_MD1.xlsx
```

Skript nejdřív kouká do `MD1_popis_dny` (když tam něco je).  
Když je podsložka prázdná / chybí, bere denní soubory **přímo z hlavní složky**.  
Po úspěchu je přesune do **`sloučeno/`**, otevře souhrn v Excelu a zavře okno.

## Rychle (Windows)

1. V OneDrive složka `Popis_měření_MD1` (+ volitelně `MD1_popis_dny`).
2. Nové `YYMMDD_N_MD1.xlsx` z appky dej do hlavní složky nebo do `MD1_popis_dny`.
3. Zkopíruj sem `SloucitMereni.bat` + `sloucit_mereni.ps1`.
4. Dvojklik na bat → sloučí, přesune staré do `sloučeno/`, otevře Excel, okno zmizí.

Nepotřebuješ Python, VBA ani admin práva — PowerShell je součást Windows.

## Ručně

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\sloucit_mereni.ps1 -Folder "C:\Users\...\OneDrive\Popis_měření_MD1"
```
