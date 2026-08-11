# Sloučení denních `*_MD1.xlsx` (bez instalace)

Appka ukládá dávky `YYMMDD_N_MD1.xlsx`. Na PC je sloučíš do jednoho
`Souhrn_mereni.xlsx` — **Windows PowerShell, nic se neinstaluje**.

## Rychle (Windows)

1. Stáhni / synchronizuj složku s denními soubory z OneDrive.
2. Zkopíruj sem z repa `SloucitMereni.bat` + `sloucit_mereni.ps1`  
   (oba soubory musí být ve stejné složce).
3. **Dvojklik** na `SloucitMereni.bat`,  
   nebo **přetáhni složku** s `*_MD1.xlsx` na bat.
4. Otevři vzniklé `Souhrn_mereni.xlsx` v Excelu.

V okně bat uvidíš u každého souboru počet načtených řádků.
Když je u všech **0 datových**, skript souhrn nevytvoří a napíše chybu.

**Tip:** Ber soubory přímo z OneDrive / z appky. Když je Excel otevře a znovu
uloží, formát se změní — nová verze skriptu to umí, ale originál z appky je jistější.

Nepotřebuješ Python, VBA ani admin práva — PowerShell je součást Windows.

## Ručně v PowerShellu

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\sloucit_mereni.ps1 -Folder "C:\Users\...\OneDrive\MD1_rozdeleno"
```

## Výstup = stejný vzhled jako appka

```
10.8.2026                 ← datum (modré, jen sloupec A)
                          ← prázdný řádek
Název stanice             ← stanice (oranžové, jen A)
koleje | výhybky | čas | poznámka   ← data A–D
```

List **Mereni**, 4 sloupce A–D.  
Bere `YYMMDD_N_MD1.xlsx` (víc dávek za den) i starší `*_MD1.xlsx`.  
`Souhrn_mereni.xlsx` se při dalším běhu přepíše.

## Zálohy v repu

- `sloucit_mereni.py` — stejná logika, kdybys měl Python
- `SloucitMereni.bas` — VBA (často blokované firemní politikou)
