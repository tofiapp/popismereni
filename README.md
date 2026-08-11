# Měření v0.44.0

## Aktualizace
```bash
adb install -r artifacts/mereni-v0.44.0-debug.apk
```

## OneDrive (sdílení Intent)
**Uložit na OneDrive** otevře sdílení (OneDrive / Files).

Soubor je vždy `YYMMDD_N_MD1.xlsx` (N = 1, 2, 3… ten den). Po ANO se místní záznamy vymažou.
Graph/MSAL už není.

## Sloučení denních souborů na PC

Bez VBA (makra firemní Excel často blokuje):

```bat
tools\SloucitMereni.bat
```

nebo `python tools/sloucit_mereni.py "C:\cesta\ke\složce"`.  
Výsledek: `Souhrn_mereni.xlsx`. Podrobnosti v [`tools/README-SloucitMereni.md`](tools/README-SloucitMereni.md).
