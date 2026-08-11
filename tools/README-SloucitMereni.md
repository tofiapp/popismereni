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
**Naposledy aktualizováno: …**, v **B1** je modré **Aktualizovat**
(lokální odkaz `file:///…/SloucitMereni.bat`, bez VBA).

## Rychle (Windows)

1. Zkopíruj `SloucitMereni.bat` + `sloucit_mereni.ps1` do `Popis_měření_MD1` (vedle souhrnu)
2. Spusť bat jednou (zapíše tlačítko se správnou lokální cestou)
3. Souhrn otevírej v **desktop Excelu** (ne v prohlížeči / Excel Online)
4. Klikni **Aktualizovat** (B1) — nebo znovu dvojklik na bat

Když se otevře prohlížeč s 404, máš starý odkaz (relativní → OneDrive web).  
Znovu spusť `SloucitMereni.bat` (verze **2026-08-11g**), ať se odkaz přepíše na `file:///`.

Excel může zobrazit bezpečnostní upozornění u `.bat` — potvrď.

Nepotřebuješ Python, VBA ani admin práva — PowerShell je součást Windows.
