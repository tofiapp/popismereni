# Office Scripty MD1

Zdroj pravdy pro kód tlačítek v sešitu Přehled.

| Soubor | Tlačítko | Účel |
|---|---|---|
| [`aktualizovat.ts`](aktualizovat.ts) | 2. Aktualizovat | Archiv ← nová data, Přehled se překreslí |
| [`kontrola.ts`](kontrola.ts) | 1. Kontrola stavu | Porovná Archiv × Power Query, nic nemění |

## Vložení do Excelu

1. Sešit na OneDrivu / SharePointu otevřít v Excelu (desktop nebo web)
2. **Automatizér** → vybrat stávající skript (nebo Nový skript)
3. Smazat obsah editoru, vložit celý soubor
4. Uložit
5. Tlačítko v listu Přehled má zůstat napojené na stejný skript

Po prvním Aktualizovat vznikne skrytý list **Archiv**. Stávající Přehled
se do něj jednorázově přelije ze sloupce E.

Kontext, omezení a Power Query: [`../MD1.md`](../MD1.md).
