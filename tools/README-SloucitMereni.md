# Laufzeitfehler 52 (Dateiname falsch)

Obvykle: soubor je otevřený přes **https:// SharePoint/OneDrive v prohlížeči**.  
VBA `Dir` umí jen **lokální** cestu (`C:\Users\...\OneDrive - ...\`).

## Oprav
1. Zavři Excel.
2. Otevři **Průzkumník Windows** → synchronizovaná OneDrive složka s měřením (zelená fajfka).
3. Dvojklik na `Souhrn_mereni.xlsm` (ne „Open in browser“).
4. **Inhalt aktivieren**.
5. Alt+F8 → `Mereni_Nastavit` (jednou) nebo tlačítko **Sloucit ted**.

## Nastavení od nuly
1. Nový sešit → import `.bas`
2. Alt+F8 → **`Mereni_Nastavit`** → uložit jako `.xlsm` do lokální OneDrive složky
3. Zavřít ostatní sešity

## Download
https://raw.githubusercontent.com/tofiapp/popismereni/cursor/update-and-ui-tweaks-3a97/tools/SloucitMereni.bas
