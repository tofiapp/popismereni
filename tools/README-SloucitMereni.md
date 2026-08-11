# Sloučení denních souborů (bez instalace)

## Struktura na OneDrive

```
Popis_měření_MD1/
  Popis_měření_MD1.xlsx      ← souhrn (skript, připojuje nová data)
  Dny/
    260811_1_MD1.xlsx       ← nové denní soubory z appky
    sloučeno/
      260810_1_MD1.xlsx     ← už sloučené (přesune skript)
```

Skript bere nové soubory z **`Dny/`**.  
Po úspěchu je přesune do **`Dny/sloučeno/`**, do 1. řádku souhrnu napíše
**Naposledy aktualizováno: …**, otevře Excel a zavře okno.

## Rychle (Windows)

1. V OneDrive: `Popis_měření_MD1/Dny/`
2. V appce ⚙ → **Vybrat složku Dny…** (jednou), pak **Uložit na OneDrive**
3. Zkopíruj sem `SloucitMereni.bat` + `sloucit_mereni.ps1` (do `Popis_měření_MD1`)
4. Dvojklik na bat

Nepotřebuješ Python, VBA ani admin práva — PowerShell je součást Windows.
