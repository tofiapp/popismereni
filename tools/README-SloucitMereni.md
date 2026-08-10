# VBA: sloučení měření

## Proč tlačítko nefungovalo a F8 ano
Makro bylo v **jednom** sešitu, tlačítko v **druhém** (`Souhrn…`).  
F8 najde makro všude; tlačítko hledá makro **ve stejném souboru**.

## Správně = vše v jednom `Souhrn_mereni.xlsm`

1. Otevři **nový prázdný sešit** (ne starý Souhrn bez maker).
2. **Alt+F11** → smaž staré moduly `SloucitMereni` ve všech sešitech, ať není duplicita.
3. **Datei → Datei importieren…** → nový `SloucitMereni.bas`  
   (musí být uvnitř **tohoto** sešitu ve stromu vlevo).
4. **Alt+F8** → `VytvoritTlacitko` → **Ausführen**  
   - uloží **tento** sešit jako `…\MD1_rozdeleno\Souhrn_mereni.xlsm`  
   - vytvoří list **Start** + tlačítko  
   - list **Mereni** = výsledek  
5. Zavři ostatní zbytečné sešity (Sešit1 / Mappe1).
6. Příště: otevři jen `Souhrn_mereni.xlsm` → **Inhalt aktivieren** → klik **Sloucit mereni**.

## Download
https://raw.githubusercontent.com/tofiapp/popismereni/cursor/update-and-ui-tweaks-3a97/tools/SloucitMereni.bas
