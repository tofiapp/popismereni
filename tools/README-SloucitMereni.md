# Chyba 52 znovu / háčky v cestě

Makro už **nepoužívá `Dir`** (ten padá na `Správa železnic` i na `https://`).  
Používá **FileSystemObject**.

## Teď
1. Stáhni nový `.bas`  
   https://raw.githubusercontent.com/tofiapp/popismereni/cursor/update-and-ui-tweaks-3a97/tools/SloucitMereni.bas
2. Nový sešit → import `.bas`
3. Alt+F8 → **`Mereni_Nastavit`** → ulož `.xlsm` do OneDrive složky **přes Explorer**
4. Když něco nefunguje: Alt+F8 → **`Mereni_Diagnostika`**  
   - uvidíš `Path` / `FullName` / kolik `*_MD1.xlsx` FSO vidí  
   - když `FullName` začíná `https://` → otevři soubor z Průzkumníka, ne z webu

## Ručně spouštěj jen
- `Mereni_Nastavit` — jednou
- `SloucitVsechnaMereni` / tlačítko — sloučit
- `Mereni_Diagnostika` — když chyba
