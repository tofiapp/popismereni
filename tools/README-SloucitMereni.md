# VBA: sloučení `*_MD1.xlsx`

## Co se změnilo
- **Jeden soubor** `Souhrn_mereni.xlsm` — při každém běhu se **přepíše list Mereni**, nevzniká nový Book.
- Oprava **Laufzeitfehler 1004** u `SaveAs` (OneDrive) — otevře existující soubor a volá `.Save`.
- **Tlačítko** na listu `Start`.

## Jednorázové nastavení (německý Excel)

1. Stáhni `SloucitMereni.bas` z repa.
2. **Alt+F11** → starý modul **Entfernen**.
3. **Datei → Datei importieren…** → nový `.bas`.
4. **Alt+F8** → `VytvoritTlacitko` → **Ausführen**  
   (vytvoří `Souhrn_mereni.xlsm` ve složce OneDrive + tlačítko).
5. Pokud Excel hlásí makra: **Inhalt aktivieren**.

## Pak už jen
Otevři `Souhrn_mereni.xlsm` → klikni **Sloucit mereni**.

## Výstup (list Mereni)
Stejný vzhled jako appka: datum / stanice nahoře, data A–D pod nimi.  
Zdroje: `YYMMDD_N_MD1.xlsx` i `mereni_MD1.xlsx`.
