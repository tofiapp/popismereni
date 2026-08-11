# Jeden soubor + tlačítko

`Souhrn_mereni.xlsm` ve složce `MD1_rozdeleno` (makra + tlačítko + list Mereni).

## Jednou: povolit makra v té složce
Excel → **Datei → Optionen → Trust Center → Einstellungen… → Vertrauenswürdige Speicherorte**  
→ **Neuen Speicherort hinzufügen** →  
`C:\Users\hrubesk\OneDrive - Správa železnic\...\MD1_rozdeleno`  
(zaškrtni i podložky) → OK → restart Excel.

Bez toho Excel OneDrive `.xlsm` zakáže — to nejde obejít kódem.

## Nastavení
1. Nový sešit → import `.bas`
2. Alt+F8 → **Nastavit** → ulož jako `Souhrn_mereni.xlsm` do `MD1_rozdeleno`
3. Otevirei **z Průzkumníka** (`C:\...`)
4. Tlačítko **Sloucit**

https://raw.githubusercontent.com/tofiapp/popismereni/cursor/update-and-ui-tweaks-3a97/tools/SloucitMereni.bas
