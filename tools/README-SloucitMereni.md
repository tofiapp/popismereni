# Sloučení denních souborů (bez instalace)

## Struktura na OneDrive

```
Popis_měření_MD1/
  Popis_měření_MD1.xlsx      ← souhrn (vytvoří skript)
  MD1_popis_dny/
    260811_1_MD1.xlsx       ← denní dávky z appky
    260811_2_MD1.xlsx
    …
```

Appka ukládá `YYMMDD_N_MD1.xlsx` → ty ukládej do **`MD1_popis_dny`**.  
Souhrn má stejné jméno jako hlavní složka: **`Popis_měření_MD1.xlsx`**.

## Rychle (Windows)

1. V OneDrive vytvoř složku `Popis_měření_MD1` a v ní podsložku `MD1_popis_dny`.
2. Denní soubory z appky dávej do `MD1_popis_dny`.
3. Zkopíruj sem `SloucitMereni.bat` + `sloucit_mereni.ps1` (do `Popis_měření_MD1`).
4. Dvojklik na bat (nebo přetáhni složku `Popis_měření_MD1` na bat).
5. Otevři `Popis_měření_MD1.xlsx`.

V okně bat uvidíš u každého souboru počet načtených řádků.
Když je u všech **0 datových**, skript souhrn nevytvoří a napíše chybu.

Nepotřebuješ Python, VBA ani admin práva — PowerShell je součást Windows.

## Ručně v PowerShellu

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\sloucit_mereni.ps1 -Folder "C:\Users\...\OneDrive\Popis_měření_MD1"
```

## Výstup = stejný vzhled jako appka

```
10.8.2026                 ← datum (modré, jen sloupec A)
                          ← prázdný řádek
Název stanice             ← stanice (oranžové, jen A)
koleje | výhybky | čas | poznámka   ← data A–D
```

## Zálohy v repu

- `sloucit_mereni.py` — stejná logika, kdybys měl Python
- `SloucitMereni.bas` — VBA (často blokované firemní politikou)
