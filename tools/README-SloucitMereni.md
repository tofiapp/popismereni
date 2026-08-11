# Sloučení denních souborů (bez instalace)

## Struktura na OneDrive

```
Popis_měření_MD1/
  Popis_měření_MD1.xlsx      ← souhrn (+ tlačítko Aktualizovat v B1)
  SloucitMereni.bat
  sloucit_mereni.ps1
  Dny/
    260811_1_MD1.xlsx       ← nové denní soubory z appky
    sloučeno/
      260810_1_MD1.xlsx     ← už sloučené (přesune skript)
```

Skript bere nové soubory z **`Dny/`**.  
Po úspěchu je přesune do **`Dny/sloučeno/`**, do 1. řádku souhrnu napíše
**Naposledy aktualizováno: …**, v **B1** je modré **Aktualizovat** (hyperlink na bat, bez VBA),
otevře Excel a zavře okno.

## Rychle (Windows)

1. V OneDrive: `Popis_měření_MD1/Dny/`
2. V appce ⚙ → **Vybrat složku Dny…** (jednou), pak **Uložit na OneDrive**
3. Zkopíruj sem `SloucitMereni.bat` + `sloucit_mereni.ps1` (do `Popis_měření_MD1`, vedle souhrnu)
4. Dvojklik na bat **nebo** v Excelu klikni **Aktualizovat** (B1)

Excel může zobrazit bezpečnostní upozornění u odkazu na `.bat` — potvrď.
Skript před přepsáním souhrn v Excelu zavře a po sloučení znovu otevře.

Nepotřebuješ Python, VBA ani admin práva — PowerShell je součást Windows.
