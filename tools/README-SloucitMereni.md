# VBA: sloučení `*_MD1.xlsx`

## Výstup = stejný vzhled jako appka

```
10.8.2026                 ← datum (modré, jen sloupec A)
                          ← prázdný řádek
Název stanice             ← stanice (oranžové, jen A)
koleje | výhybky | čas | poznámka   ← data A–D
```

List **Mereni**, 4 sloupce A–D.  
Bere `YYMMDD_N_MD1.xlsx` (víc dávek za den) i `mereni_MD1.xlsx`.

## Německý Excel — nahrát makro

1. **Alt+F11**
2. Starý modul: pravý klik → **Entfernen**
3. **Datei → Datei importieren…** → `SloucitMereni.bas`
4. Uprav `SOURCE_FOLDER` (v kódu přes `ChrW` / cestu z Exploreru)
5. **Alt+F8** → `SloucitVsechnaMereni` → **Ausführen**

Výsledek: `Souhrn_mereni.xlsx` ve stejné složce.
