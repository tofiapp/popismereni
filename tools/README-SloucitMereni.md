# VBA: sloučení měření (sdílený soubor)

## Funguje to kolegovi na jiném PC?
**Ano**, pokud:
1. Ve sdílené OneDrive složce je `Souhrn_mereni.xlsm` (makra + tlačítko uvnitř souboru).
2. Ve **stejné** složce leží i `*_MD1.xlsx` z appky.
3. Kolega soubor otevře, počká na sync (zelená fajfka), klikne **Inhalt aktivieren** / povolit makra, pak **Sloucit mereni**.

Makro bere cestu z `ThisWorkbook.Path` — **ne** z `C:\Users\hrubesk\...`, takže na každém PC sedí.

## Pozor (firma / IT)
OneDrive často označí `.xlsm` jako „z internetu“ a **zablokuje makra**.  
Pak F8/tlačítko nejde, dokud IT nepovolí makra z důvěryhodného umístění, nebo soubor neodblokujete (Vlastnosti souboru → Odblokovat), pokud to politika dovolí.

## Nastavení jednou u tebe
1. Nový sešit → import `SloucitMereni.bas`
2. **Ulož jako** `Souhrn_mereni.xlsm` do sdílené složky s `*_MD1.xlsx`
3. Alt+F8 → `VytvoritTlacitko`
4. Sdílej tenhle `.xlsm` (stejná OneDrive složka)

## Download
https://raw.githubusercontent.com/tofiapp/popismereni/cursor/update-and-ui-tweaks-3a97/tools/SloucitMereni.bas
