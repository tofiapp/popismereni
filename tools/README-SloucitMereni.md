# Sloučení denních souborů (bez instalace)

## Struktura

```
Popis_měření_MD1/
  Popis_měření_MD1.xlsx   ← souhrn + tlačítko Aktualizovat (B1)
  SloucitMereni.bat
  sloucit_mereni.ps1
  Dny/
    YYMMDD_N_MD1.xlsx
    sloučeno/
```

Jen **dva** skripty: `SloucitMereni.bat` + `sloucit_mereni.ps1`.  
Žádný `Aktualizovat.cmd`.

## Použití

1. Bat + ps1 dej vedle souhrnu
2. Spusť bat (nebo v desktop Excelu klikni **Aktualizovat**)
3. Tlačítko míří přímo na `SloucitMereni.bat` (skript ho případně doplní sám)

Verze: **2026-08-11m**

Skript před zápisem zavře souhrn v Excelu a při zámku opakuje smazání/nahrazení.
