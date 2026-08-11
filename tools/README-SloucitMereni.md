# Sloučení denních souborů (bez instalace)

## Struktura na OneDrive

```
Popis_měření_MD1/          ← nebo Popis_mereni_MD1 (bez diakritiky)
  Popis_měření_MD1.xlsx
  Aktualizovat.cmd         ← ASCII launcher (tlačítko v Excelu)
  SloucitMereni.bat
  sloucit_mereni.ps1
  Dny/
    YYMMDD_N_MD1.xlsx
    sloučeno/
```

Správný název složky je **`Popis_měření_MD1`** (nebo ASCII `Popis_mereni_MD1`).  
Když v cestě vidíš `SprĂˇva` / `mÄ›Ĺ™enĂ­`, je to jen **špatné zobrazení kódování** — skutečná složka má háčky.

## Rychle (Windows)

1. Bat + ps1 dej do `Popis_měření_MD1` (vedle souhrnu)
2. Spusť `SloucitMereni.bat` (verze **2026-08-11h**) — vytvoří `Aktualizovat.cmd` a přepíše odkaz v Excelu
3. Souhrn otevírej v **desktop Excelu**
4. Klikni **Aktualizovat** (B1) — odkaz na `Aktualizovat.cmd` (ASCII), ne na web

Nepotřebuješ Python, VBA ani admin práva.
