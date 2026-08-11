# Sloučení denních `*_MD1.xlsx` (bez VBA)

Appka ukládá dávky `YYMMDD_N_MD1.xlsx`. Na PC je sloučíš do jednoho
`Souhrn_mereni.xlsx` — **Python skript, bez makra a bez Excelu**.

## Rychle (Windows)

1. Stáhni / synchronizuj složku s denními soubory z OneDrive.
2. Dvojklik na `SloucitMereni.bat` **ve složce se soubory**,  
   nebo **přetáhni složku** na `SloucitMereni.bat`.
3. Otevři vzniklé `Souhrn_mereni.xlsx` v Excelu (obyčejný xlsx, ne xlsm).

Potřebuješ [Python 3](https://www.python.org/downloads/) s „Add to PATH“
(nebo `py` launcher). Žádné `pip install`.

## Ručně

```bat
python sloucit_mereni.py "C:\Users\...\OneDrive\MD1_rozdeleno"
```

Výstup: `Souhrn_mereni.xlsx` ve stejné složce.

## Výstup = stejný vzhled jako appka

```
10.8.2026                 ← datum (modré, jen sloupec A)
                          ← prázdný řádek
Název stanice             ← stanice (oranžové, jen A)
koleje | výhybky | čas | poznámka   ← data A–D
```

List **Mereni**, 4 sloupce A–D.  
Bere `YYMMDD_N_MD1.xlsx` (víc dávek za den) i starší `mereni_MD1.xlsx` /
`YYMMDD_MD1.xlsx`. Soubor `Souhrn_mereni.xlsx` se při dalším běhu přepíše.

## Proč ne VBA

Firemní Excel často **blokuje makra** (žádný žlutý pruh „Aktivieren“).
Python čte/zapisuje xlsx přímo — politika makra ho neomezuje.

Starý modul `SloucitMereni.bas` v repu zůstává jen jako záloha, pokud bys
makra směl.