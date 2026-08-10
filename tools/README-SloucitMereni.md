# VBA: sloučení `*_MD1.xlsx`

## Německý Excel — jak nahrát

1. **Alt+F11**
2. **Datei → Datei importieren…** → `SloucitMereni.bas`  
   (ne Ctrl+V celého souboru — řádek `Attribute VB_Name` pak hlásí Syntaxfehler)
3. Uprav `SOURCE_FOLDER` v makru
4. **Alt+F8** → `SloucitVsechnaMereni` → **Ausführen**

## Výstupní sloupce (= appka)

| A | B | C | D | E | F |
|---|---|---|---|---|---|
| Datum | Stanice | Koleje | Výhybky | Čas | Poznámka |

Odpovídá Excelu z appky: A koleje/spojky, B výhybky od–do, C čas, D poznámka + datum/stanice z bloků.
