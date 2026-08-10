# VBA: sloučení `*_MD1.xlsx`

## Výstup = stejný vzhled jako appka

```
10.8.2026                 ← datum (modré, jen sloupec A)
                          ← prázdný řádek
Název stanice             ← stanice (oranžové, jen A)
koleje | výhybky | čas | poznámka   ← data A–D
                          ← prázdný řádek
Další stanice
…
```

List se jmenuje **Mereni**, 4 sloupce A–D.  
**Není** to plochá tabulka s Datum/Stanice ve sloupcích.

## Německý Excel — nahrát makro

1. **Alt+F11**
2. Starý modul: pravý klik → **Entfernen**
3. **Datei → Datei importieren…** → `SloucitMereni.bas`
4. Uprav `SOURCE_FOLDER`
5. **Alt+F8** → `SloucitVsechnaMereni` → **Ausführen**

Výsledek: `Souhrn_mereni.xlsx` ve stejné složce.
