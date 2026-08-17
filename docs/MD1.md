# Projekt MD1 — slučování exportů měření do jednoho přehledu

Dokumentace k předání. Obsahuje kontext, ověřená omezení prostředí
a seznam slepých uliček, aby se neopakovaly.

Kompletní Office Scripty k vložení do Excelu:

- [`docs/office-scripts/aktualizovat.ts`](office-scripts/aktualizovat.ts) — tlačítko **2. Aktualizovat**
- [`docs/office-scripts/kontrola.ts`](office-scripts/kontrola.ts) — tlačítko **1. Kontrola stavu**

Power Query M kód je v sekci 4 (beze změny).

---

## 1. Zadání

Android aplikace exportuje výsledky měření do OneDrivu jako jednotlivé `.xlsx` soubory.
Cílem je sloučit je do jednoho sešitu se čitelným přehledem.

**Omezení, která tvarují celé řešení:**

- Organizace blokuje stahování do tabletu → nelze slučovat na zařízení
- **VBA makra jsou zakázaná**
- Exporty vznikají u více uživatelů, každý ve svém osobním OneDrivu
- Sešit má být sdílený na SharePointu/OneDrivu organizace
- Původní stav: PowerShell + 6 souborů (1 Excel, 2 skripty, 2 složky) — kostrbaté

Dva lidé **nepopisují současně**, názvy `YYMMDD_N_MD1.xlsx` se tedy na stejném
dni nepotkají. Souběh je jen u **čtení / Aktualizovat** v sešitu, ne u zápisu
denních souborů.

---

## 2. Zvolená architektura

```
Android app → .xlsx do složky MD1 na OneDrivu uživatele
                    ↓
        Power Query (dotaz na SharePoint URL)
                    ↓
        tabulka "TabNove" na listu Dotaz1 (8 sloupců)
                    ↓
        Office Script (tlačítko "2. Aktualizovat")
                    ↓
        skrytý list "Archiv"  = rovná tabulka, zdroj pravdy
        list "Přehled"        = jen vykreslení z Archivu
```

**Klíčové rozhodnutí:** Archiv drží data jako tabulku (stejných 8 sloupců
jako TabNove). Přehled se při každém běhu smaže od řádku 4 a nakreslí znovu
z Archivu. Zdrojové soubory lze z OneDrivu mazat — data zůstanou v Archivu.

Rozlišení už zpracovaných souborů je přes sloupec `ZdrojovySoubor` v Archivu.
V Přehledu zůstává **skrytý sloupec E** (`nazev_souboru.xlsx|poradi`) jen jako
značka pro kontrolu, že vykreslení sedí. Číst se z něj už nemusí, kromě
jednorázové migrace.

**Migrace:** první běh nového skriptu, když list Archiv ještě neexistuje
(nebo je prázdný), načte stávající Přehled ze sloupce E a zapíše ho do Archivu.
Stará data se neztratí. Další běhy už Přehled jako zdroj nepoužívají.

**Souběh Aktualizovat:** 20s pauza (`PAUZA_S`) podle razítka v N2. Není to
zámek sešitu — zúží okno, kdy dva lidé přepíšou list naráz. Určit jednoho
člověka na tlačítko provozně nejde; tahle pauza je kompromis, který Excel
na SharePointu umí.

---

## 3. Struktura dat

### Zdrojový export (.xlsx z aplikace)

Hierarchický, ne tabulkový:

```
11.8.2026                      ← datum (sl. A, B prázdné)
Ústí nad Orlicí                ← nadpis stanice (sl. A, B prázdné)
    5, 2X      35 - 19  13:04  ← měření
Jičín                          ← nadpis stanice
    11B        21 - 25  13:06
```

Sloupce: **Hodnota → Rozsah → Čas → Popis** (popis nepovinný)

Rozlišovací pravidlo: **nadpis = prázdný sloupec B**. (Ne prázdné C — čas občas chybí.)

### Pojmenování souborů

`RRMMDD_poradi_MD1.xlsx` — např. `260814_2_MD1.xlsx`

Názvy se **nesmí** shodovat na stejném OneDrivu (přepsaly by se). Struktura
uvnitř musí být konzistentní.

### Tabulka TabNove (výstup Power Query)

| ZdrojovySoubor | Datum | Poradi | Stanice | Hodnota | Rozsah | Cas | Popis |
|---|---|---|---|---|---|---|---|

`Datum` je **text** ve tvaru `2026-08-14` — záměrně, kvůli spolehlivému řazení
a aby ho Excel nepřevedl.

### List Archiv (skrytý)

Stejných 8 sloupců jako TabNove, Excel tabulka `TabArchiv`. Žádné nadpisy
stanic, žádné barvy — jen rovná data. Skript ho při každém běhu přepíše celý.

Odkrytí: pravý klik na záložku listu → Zobrazit… → Archiv.
Běžně na něj nesahejte. Ruční úprava Přehledu se při Aktualizovat stejně
přepíše z Archivu.

### List Přehled

```
řádek 1:  (volný)
řádek 2:  [tlačítko Kontrola] C2=stav  F2=čas(skrytý)  G2=text stavu(skrytý)
          [tlačítko Aktualizovat] K2=razítko  N2=čas ISO(skrytý)
řádek 3:  (volný, ukotvené příčky pod ním)
řádek 4+: data — A=Hodnota, B=Rozsah, C=Čas, D=Popis, E=značka souboru (šířka 0)
```

Barvy: datum `#BDD7EE` (modrá), stanice `#FCE4D6` (oranžová), data `#1F3864` (tmavě modrá).
Nadpisy 14 b tučně, data 12 b, výška řádku 22.

---

## 4. Power Query — M kód

Jeden dotaz na každý zdrojový OneDrive, pak `Připojit dotazy jako nové`.
Zdrojové dotazy nastavit na **Pouze připojení**, načítá se jen spojený.

```
let
    Web = "https://szdc-my.sharepoint.com/personal/hrubesk_spravazeleznic_cz/",
    Podslozka = "MD1",

    Zdroj = SharePoint.Files(Web, [ApiVersion = 15]),

    JenSlozka = Table.SelectRows(Zdroj, each
        Text.Contains([Folder Path], Podslozka)),

    // prisny filtr: RRMMDD_cislo_MD1.xlsx
    // POZOR: bez nej se nacte i cilovy sesit (konci na _MD1) → smycka
    JenXlsx = Table.SelectRows(JenSlozka, each
        let
            Z = Text.BeforeDelimiter([Name], ".", {0, RelativePosition.FromEnd}),
            C = Text.Split(Z, "_")
        in
            Text.EndsWith(Text.Lower([Name]), ".xlsx")
            and not Text.StartsWith([Name], "~$")
            and List.Count(C) = 3
            and C{2} = "MD1"
            and Text.Length(C{0}) = 6
            and (try Number.FromText(C{0}) otherwise null) <> null
            and (try Number.FromText(C{1}) otherwise null) <> null),

    SDatem = Table.AddColumn(JenXlsx, "Datum", each
        let
            C = Text.Split(Text.BeforeDelimiter([Name], ".", {0, RelativePosition.FromEnd}), "_"),
            D = C{0}
        in
            try "20" & Text.Start(D, 2) & "-" & Text.Middle(D, 2, 2) & "-" & Text.End(D, 2)
            otherwise null,
        type nullable text),

    SPoradim = Table.AddColumn(SDatem, "Poradi", each
        let C = Text.Split(Text.BeforeDelimiter([Name], ".", {0, RelativePosition.FromEnd}), "_")
        in try Number.FromText(C{1}) otherwise null,
        Int64.Type),

    NacistData = Table.AddColumn(SPoradim, "Obsah", each
        try
        let
            Sesit    = Excel.Workbook([Content], null, true),
            Listy    = Table.SelectRows(Sesit, each [Kind] = "Sheet"),
            Data     = Listy{0}[Data],
            Sl       = List.FirstN(Table.ColumnNames(Data), 4),
            Prvni4   = Table.SelectColumns(Data, Sl),
            Prejm    = Table.RenameColumns(Prvni4,
                        List.Zip({Sl, List.FirstN({"Hodnota", "Rozsah", "Cas", "Popis"}, List.Count(Sl))})),
            SPopisem = if List.Contains(Table.ColumnNames(Prejm), "Popis")
                       then Prejm
                       else Table.AddColumn(Prejm, "Popis", each null, type nullable text),
            Oznacit  = Table.AddColumn(SPopisem, "Stanice", each
                        if [Rozsah] = null and [Hodnota] <> null
                        then Text.Trim(Text.From([Hodnota]))
                        else null,
                        type nullable text),
            Vyplnit  = Table.FillDown(Oznacit, {"Stanice"}),
            JenData  = Table.SelectRows(Vyplnit, each [Rozsah] <> null)
        in
            Table.SelectColumns(JenData, {"Stanice", "Hodnota", "Rozsah", "Cas", "Popis"})
        otherwise null),

    BezChyb = Table.SelectRows(NacistData, each [Obsah] <> null),

    Orezat = Table.SelectColumns(BezChyb, {"Name", "Datum", "Poradi", "Obsah"}),
    Prejm2 = Table.RenameColumns(Orezat, {{"Name", "ZdrojovySoubor"}}),

    Rozbalit = Table.ExpandTableColumn(Prejm2, "Obsah",
                {"Stanice", "Hodnota", "Rozsah", "Cas", "Popis"},
                {"Stanice", "Hodnota", "Rozsah", "Cas", "Popis"}),

    Finalni = Table.SelectColumns(Rozbalit,
                {"ZdrojovySoubor", "Datum", "Poradi", "Stanice", "Hodnota", "Rozsah", "Cas", "Popis"})
in
    Finalni
```

### Poznámky ke kódu

- `Listy{0}[Data]` bere **první list**, ne podle názvu — název listu se v exportech liší
- `try ... otherwise null` + `BezChyb` — poškozený soubor se přeskočí, nespadne celý dotaz
- `SPopisem` — pojistka, když export nemá 4. sloupec
- Filtr `Podslozka = "MD1"` je záměrně bez diakritiky; `SharePoint.Files` prochází
  **celý OneDrive**, filtrování je jen na tomto výrazu
- `Text.Contains(..., "MD1")` chytí i starou cestu `Popis_měření_MD1/Dny`.
  Nové exporty ukládat do složky **MD1**. Starý PowerShell (`Dny/sloučeno/`, `Zalohy/`)
  k tomu nepřidávat — jinak se do dotazu vrátí už zpracované soubory.

### Přidání dalšího uživatele

1. Kolega nasdílí složku MD1 (Spravovat přístup → Přímý přístup → `+`, bez e-mailu)
2. Duplikovat dotaz, přejmenovat na `Zdroj_Prijmeni`
3. Změnit `Web` na `https://szdc-my.sharepoint.com/personal/login_domena_cz/`
4. Nastavit na **Pouze připojení** (panel Dotazy a připojení → Načíst do…)
5. Přidat do `Table.Combine` ve spojeném dotazu

**Doporučení:** vytvořit skupinu Teams a sdílet jí místo jednotlivcům — pak se
nový člověk přidává jen do skupiny.

---

## 5. Office Script — Aktualizace

Tlačítko **2. Aktualizovat**. Zdroj kódu: [`office-scripts/aktualizovat.ts`](office-scripts/aktualizovat.ts).

Postup běhu:

1. Pauza 20 s, když N2 říká, že právě běželo
2. Najít tabulku **TabNove** (ne TabArchiv — proto se nebere `getTables()[0]`)
3. Načíst Archiv; když je prázdný, jednorázově naplnit z Přehledu (sl. E)
4. Přidat jen soubory, které Archiv ještě nezná
5. Doplnit datum, seřadit
6. **Zapsat Archiv** (až sem jsou data v bezpečí i při chybě kreslení)
7. Smazat Přehled od řádku 4 a vykreslit z Archivu
8. Razítko + stav

Vložení: Automatizér → otevřít stávající skript Aktualizovat → nahradit celý
obsah souborem `aktualizovat.ts` → Uložit. Tlačítko v listu přemapovat nemusíte,
pokud jméno skriptu zůstane.

---

## 6. Office Script — Kontrola stavu

Tlačítko **1. Kontrola stavu**. Zdroj: [`office-scripts/kontrola.ts`](office-scripts/kontrola.ts).
Nic nemění, porovná **Archiv × dotaz**. Přehled jen zkontroluje počtem
datových řádků (prázdné B = nadpis, nepočítá se).

Kategorie nálezů:

- **V dotazu, ne v Archivu** → nové, čekají na zpracování (normální)
- **V Archivu, ne v dotazu** → archivované, zdroj smazán (normální)
- **V obou, jiný počet řádků** → skutečný problém (soubor se změnil pod stejným názvem)
- **Přehled nesedí na Archiv** → někdo sahal do Přehledu, nebo kreslení spadlo.
  Stačí Aktualizovat — Archiv se nemaže.
- **Řádky bez značky souboru** → jen když Archiv ještě není a čte se starý Přehled

### Stav v C2 — kdy zmáčknout Aktualizovat

C2 hlásí **Klikněte Aktualizovat**, když je v Dotaz1 soubor, který ještě není
ve sloupci E na Přehledu. Po tlačítku 2. Aktualizovat zase **Aktuální**.

Vzorce jsou jen na Přehledu ve sloupci **AA** (mimo A–Z, skript je nemaže).
Na list Dotaz1 se nedávají — Power Query ho při obnovení přepíše.
List Dotaz1 (2) se k C2 nepřipojuje.

Postup: [`formulas/C2-stav.txt`](formulas/C2-stav.txt).
Skript `aktualizovat.ts` maže jen `A4:E`, ne celý list.

---

## 7. Ověřená omezení prostředí

Zjištěno praxí během vývoje. **Toto je nejcennější část dokumentu.**

### Office Scripts

| Omezení | Důsledek |
|---|---|
| Žádná událost „při otevření sešitu" | Skript nelze spustit automaticky |
| Žádný časovač / `setTimeout` | Nápisy nemohou samy mizet |
| Nelze spustit `RefreshAll` Power Query | Aktualizace dat a vykreslení jsou dva úkony |
| Žádný `alert` / dialog | Hlášky jen do buňky nebo do konzole |
| `.map(String)` zakázáno | Nutno `.map(v => String(v))` |
| `for...of` nad `Map`/`Set` zakázáno | Nutno `.forEach()` |
| `RangeFormat.setColumnHidden` neexistuje | Skrývat přes `setColumnWidth(0)` |
| `setNumberFormatLocal` čeká lokální formát | `"0,00000"` s čárkou, ne tečkou |
| Tlačítka v desktopovém Excelu nespolehlivá | Záložní cesta: Automatizér → Spustit |
| `getTables()[0]` po zavedení TabArchiv lže | Hledat tabulku podle jména `TabNove` |

### Excel / Power Query

- **Autodetekce typů**: `19.X` se změní na datum → nutné `setNumberFormatLocal("@")`
  **před** `setValues`
- **`getUsedRange()`** nezačíná na řádku 1, když jsou horní řádky prázdné →
  číst z pevných indexů
- **`SharePoint.Files`** prochází celý OneDrive, ne jen zadanou složku
- **Mezipaměť** Power Query drží smazané soubory → Domů → Aktualizovat náhled;
  případně Možnosti dotazu → Načítání dat → Vymazat mezipaměť
- **Formula.Firewall** vzniká při kombinaci SharePoint + lokální sešit v jednom dotazu.
  Řeší Možnosti dotazu → Ochrana osobních údajů → *Vždycky ignorovat…*
  **Nastavení je per uživatel a počítač, nelze uložit do souboru.**
- Tato instalace Excelu: **`KDYŽ` funguje, `TEĎ` NE, `DNES` ano.** Oddělovač středník.

### Sdílení a souběh

- Desktopový Excel soubor **zamkne** → druhý uživatel jen pro čtení, nemůže uložit
- Režim jen pro čtení **nedostává** změny živě — musí zavřít a otevřít, nebo si
  aktualizovat sám
- **Živý náhled bez práva zápisu neexistuje** — spoluautorství vyžaduje zápis
- Automatické ukládání + spoluautorství = riziko, že dva hromadné přepisy listu
  Excel sloučí nepředvídatelně
- Ochrana v kódu: 20s pauza mezi spuštěními (`PAUZA_S`). Zúží okno rizika,
  neodstraní ho. Razítko se dnes píše **až na konci** běhu — dva kliky v téže
  vteřině pauzu minou. Kdyby to začalo vadit, stačí zapsat N2 hned na začátku
  skriptu (po kontrole pauzy). Zatím nesahejte, dokud to v provozu nebude bolet.

---

## 8. Slepé uličky

Nemá smysl to zkoušet znovu:

1. **Power Automate** — konektory Excel Online nedostupné. Původní chyba
   `~default` byla zaseknutá relace prohlížeče (řeší anonymní okno), ale
   i po opravě zůstává otázka licence. Navíc plánovaný běh selže, když má
   soubor někdo otevřený v desktopovém Excelu.

2. **Trigger OneDrive „Když je soubor vytvořen"** — vidí jen OneDrive účtu,
   pod kterým tok běží. Do cizích nedosáhne ani při nasdílení.

3. **Kontingenční tabulka místo skriptu** — slučuje stejné hodnoty
   a přeuspořádává pořadí. U chronologického záznamu měření nepoužitelné.

4. **Zálohování Přehledu na samostatné listy** — nahrazeno listem Archiv.
   Přehled už není úložiště, jen pohled.

5. **Vzorec s `TEĎ()` v C2** — funkce v této instalaci není. `DNES()` dá jen
   denní rozlišení a **kontrolu stejně nespustí**, jen odliší dnešní údaj
   od staršího. Přínos oproti složitosti diskutabilní.

6. **Zpětné čtení Přehledu jako archivu** — křehké (prázdné B = nadpis,
   značka v E). Fungovalo, dokud se layout neměnil. Nahrazeno rovnou tabulkou
   Archiv. Parsování Přehledu zůstává jen jako migrace.

---

## 9. Provozní postup

**Nasazení nového skriptu (jednou):**

1. Otevřít sešit, zálohovat (historie verzí / stáhnout kopii)
2. Automatizér → skript Aktualizovat → nahradit kódem z `aktualizovat.ts`
3. Totéž pro Kontrolu z `kontrola.ts`
4. Kliknout **2. Aktualizovat** — vznikne skrytý list Archiv, Přehled se překreslí
5. **1. Kontrola stavu** — má hlásit Aktuální, zdroj „Archiv × dotaz“

**Běžné použití:**

1. Otevřít soubor (data se natáhnou samy, pokud je zapnuto
   *Aktualizovat data při otevírání souboru* ve vlastnostech dotazu)
2. Kliknout **2. Aktualizovat**
3. Uložit (nebo mít zapnuté Automatické ukládání)

**Když tlačítko nereaguje:** Automatizér → skript → Spustit.

**Když se Přehled rozsype:** jen spustit Aktualizovat. Překreslí se z Archivu.
Zdrojové soubory **nemusí** existovat. (Dřív se mazaly řádky 4→konec a data
ze smazaných zdrojů se ztratila — to už neplatí.)

**Když se rozsype i Archiv:** obnovit sešit z historie verzí OneDrivu.
Archiv je jediné místo, kde data žijí po smazání zdrojů.

**Pojistky:** zapnout historii verzí na OneDrivu.

---

## 10. Otevřené body

- **Sdílená knihovna místo osobních OneDrivů** — zásadní zjednodušení
  (jeden dotaz místo N, oprávnění přes skupinu). Blokuje to, že z mobilní
  aplikace OneDrive se špatně vybírá jako cíl exportu. Nevyzkoušeno:
  „Přidat zástupce do mých souborů" u sdílené knihovny.
- **Skupina Teams MD1** pro správu oprávnění — plán, nerealizováno.
- **Sdílený souběžný zápis** — odloženo. Řešilo by se přesunem archivu
  do SharePointového seznamu, kde je zápis po záznamech nezávislý.
- **Razítko N2 na začátku skriptu** — viz sekce 7, souběh. Dělat, až 20s
  pauza v praxi nestačí.

### Později, až to začne bolet — ne teď

Tyhle dvě věci dávají smysl až při objemu, ne při dnešním provozu.

**Roční sešit.** Každé Aktualizovat přepíše *celý* Archiv i *celý* Přehled
a obarví každý nadpis zvlášť. Office Script na Excel Online má řádově
minutový limit. `SharePoint.Files` navíc při každém obnovení prochází
celý OneDrive (to řeší mazání už zpracovaných zdrojů — na velikost
Přehledu to nemá vliv, Archiv roste dál). Až bude běh pomalý nebo začne
padat na timeout, zkopírovat sešit jako `MD1_2025.xlsx` (jen čtení) a v
ostrém sešitu nechat v Archivu jen aktuální rok. Jeden přehled za všechny
roky tím zanikne; prohledávání staršího roku = otevřít ten soubor. Dřív
to nedělat — zbytečné stěhování dat.

**Různý počet řádků i v tlačítku Aktualizovat.** Dnes to hlásí jen Kontrola.
Scénář: soubor `260814_1_MD1.xlsx` se jednou zpracuje (8 měření), pak ho
někdo ve zdroji přepíše opravou (10 měření, stejný název). Aktualizovat ho
přeskočí, protože jméno už v Archivu je. Kontrola ukáže „různý počet řádků“.
Kdo mačká jen Aktualizovat, nesrovnalost nevidí. Až to bude vadit, stačí
do razítka přidat větu „1 soubor má jiný počet řádků — Kontrola“.
Záměrně to v Aktualizovat není: přepsat už archivovaný soubor bez ptaní
by bylo horší než ho přeskočit.
