# Sloučení denních souborů (bez instalace)

## Struktura (stejná i na sdíleném místě)

```
Popis_měření_MD1/
  Popis_měření_MD1.xlsx   ← JEDINÝ souhrn + tlačítko Aktualizovat (B1)
  SloucitMereni.bat
  sloucit_mereni.ps1
  Zalohy/                 ← automatické zálohy souhrnu (ne „kopie ke sloučení“)
  Dny/
    YYMMDD_N_MD1.xlsx     ← nové denní soubory z appky (čekají na sloučení)
    sloučeno/             ← už sloučené denní (sem se přesunou po Aktualizovat)
```

### Co není „nesloučená kopie“

| Soubor | Význam |
|---|---|
| `Popis_měření_MD1.xlsx` | jediný platný souhrn |
| `Zalohy/*.xlsx` | záloha před zápisem — neotvírat jako denní |
| `Dny/*.xlsx` | ještě nesloučené denní z appky |
| `Dny/sloučeno/*.xlsx` | už zpracované denní |
| `*.xlsx.bak`, `.sloucit_tmp_*.xlsx` | odpad — skript je maže |
| `…-PC.xlsx` / `…-kopie.xlsx` | OneDrive conflict — smazat po kontrole |

Složky a jména se **nemění**. Celou `Popis_měření_MD1/` dejte na **sdílený OneDrive / SharePoint**.

Každý pak:
1. Otevře tu samou složku u sebe v Průzkumníku
2. Denní export z appky uloží do `Dny/`
3. Spustí `SloucitMereni.bat` nebo v Excelu klikne **Aktualizovat**
4. Denní soubory zmizí z `Dny/` → přesunou se do `Dny/sloučeno/`

Jen **dva** skripty: `SloucitMereni.bat` + `sloucit_mereni.ps1`.

## Použití

1. Bat + ps1 dej vedle souhrnu (jednou na sdíleném místě)
2. Spusť bat (nebo v desktop Excelu klikni **Aktualizovat**)
3. Když v `Dny/` nic není, skript souhrn **nepřepisuje** — jen otevře Excel

## Stanice víckrát za den

Nymburk → Poděbrady → Nymburk = dva oranžové bloky. Skript bere i čas ve sloupci C.

## Pravidla pro více lidí

- Zámek `.sloucit_mereni.lock` — druhý uživatel počká (starý zámek po 45 min se převezme)
- Souhrn nesmí mít mezitím otevřený někdo jiný v Excelu
- OneDrive: **Keep on this device**
- Po sloučení počkej na sync
- Banner musí být `verze 2026-08-11p`

Verze: **2026-08-11p**
