# VBA: automatické sloučení

## Co dělá
- **Při otevření** `Souhrn_mereni.xlsm` → sloučí (bez MsgBox)
- **Každé 2 minuty** (zatímco je Excel otevřený) zkontroluje složku; když přibude/změní se `*_MD1.xlsx` → sloučí znovu
- **Tlačítko „Sloucit ted“** = okamžitě ručně (s hláškou)

Excel **musí běžet** s otevřeným souborem. Když je zavřený, sloučí se až při příštím otevření (ne Windows služba na pozadí).

## Nastavení
1. Import nového `SloucitMereni.bas` do sešitu
2. Ulož jako `Souhrn_mereni.xlsm` do sdílené složky s `*_MD1.xlsx`
3. Alt+F8 → `VytvoritTlacitko`
4. Kolega: otevřít stejný soubor → povolit makra

## Download
https://raw.githubusercontent.com/tofiapp/popismereni/cursor/update-and-ui-tweaks-3a97/tools/SloucitMereni.bas
