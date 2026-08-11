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

Verze: **2026-08-11n**

Skript před čtením i zápisem zavře souhrn v Excelu. Pokud existující souhrn nejde bezpečně načíst, **nepřepíše** ho (dříve to vypadalo jako „sloučení“, ale vznikl nový soubor jen z denních). Před zápisem vytvoří `Popis_měření_MD1.xlsx.bak`.
