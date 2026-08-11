# Sloučení denních souborů (bez instalace)

## Struktura (stejná i na sdíleném místě)

```
Popis_měření_MD1/
  Popis_měření_MD1.xlsx   ← souhrn + tlačítko Aktualizovat (B1)
  SloucitMereni.bat
  sloucit_mereni.ps1
  .sloucit_mereni.lock    ← dočasný zámek (vzniká jen během aktualizace)
  Dny/
    YYMMDD_N_MD1.xlsx
    sloučeno/
```

Složky a jména se **nemění**. Stačí celou `Popis_měření_MD1/` dát na **sdílený OneDrive / SharePoint**, ke kterému mají přístup všichni, kdo mají aktualizovat.

Každý pak:
1. Otevře tu samou složku u sebe v Průzkumníku (synced shared library)
2. Denní export z appky uloží do `Dny/`
3. Spustí `SloucitMereni.bat` nebo v Excelu klikne **Aktualizovat**

Jen **dva** skripty: `SloucitMereni.bat` + `sloucit_mereni.ps1`.  
Žádný `Aktualizovat.cmd`.

## Použití

1. Bat + ps1 dej vedle souhrnu (jednou na sdíleném místě)
2. Spusť bat (nebo v desktop Excelu klikni **Aktualizovat**)
3. Tlačítko míří přímo na `SloucitMereni.bat` (skript ho případně doplní sám)

## Stanice víckrát za den

Appka i sloučení počítají s návratem na stejnou stanici (např. Nymburk → Poděbrady → Nymburk).  
Každá návštěva zůstane samostatný oranžový blok; skript bere i čas ve sloupci C (skok zpět v čase = nová návštěva).

## Pravidla pro více lidí

- Aktualizaci blokuje soubor `.sloucit_mereni.lock` — **druhý uživatel dostane hlášku** a má zkusit později (starý zámek po 45 min se převezme).
- Souhrn nesmí mít mezitím otevřený někdo jiný v Excelu.
- U složky / souborů v OneDrive: **Keep on this device** (ne online-only).
- Po sloučení počkej na sync, než otevře souhrn další kolega.
- Verze skriptu musí být u všech stejná (banner `verze 2026-08-11o`).

Verze: **2026-08-11o**

Skript před čtením i zápisem zavře souhrn v Excelu. Pokud existující souhrn nejde bezpečně načíst, **nepřepíše** ho. Před zápisem vytvoří `Popis_měření_MD1.xlsx.bak`.
