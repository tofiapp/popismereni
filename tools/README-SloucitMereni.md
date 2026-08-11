# Jak nastavit (bez zmatku)

## Pouze 1 makro ručně
**Alt+F8 → `Mereni_Nastavit` → Spustit**

Tohle samo:
1. otevře dialog **Uložit jako .xlsm** (vyber složku s `*_MD1.xlsx`)
2. uloží **stejný** sešit (s makry uvnitř)
3. vytvoří tlačítko **Sloucit ted**

**Nesmíš** nejdřív ukládat jako `.xlsx` — tím se makra smažou.

## Postup od nuly
1. Nový prázdný sešit
2. Alt+F11 → smaž staré `SloucitMereni` všude
3. Datei importieren → nový `.bas` (modul musí viset pod **tímto** sešitem)
4. Alt+F8 → **`Mereni_Nastavit`**
5. Ulož jako `Souhrn_mereni.xlsm` do OneDrive složky s měřením
6. Zavři ostatní sešity (Mappe1 / Sešit1)
7. Příště: jen tento `.xlsm` → Inhalt aktivieren → tlačítko

## Proč dřív nefungovalo
Makra byla v jednom souboru, tlačítko / „nový“ soubor v druhém.  
Teď `Mereni_Nastavit` ukládá **ThisWorkbook** (ten s makry) přímo jako `.xlsm`.

## Download
https://raw.githubusercontent.com/tofiapp/popismereni/cursor/update-and-ui-tweaks-3a97/tools/SloucitMereni.bas
