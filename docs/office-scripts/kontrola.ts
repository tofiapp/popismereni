/**
 * Office Script: 1. Kontrola stavu
 *
 * Nic nemění. Porovná tabulku z Power Query (TabNove) se skrytým Archivem.
 * Přehled jen ověří, jestli sedí počtem datových řádků na Archiv
 * (když ne, stačí kliknout Aktualizovat — data v Archivu zůstávají).
 *
 * Když Archiv ještě neexistuje, čte značky ze sloupce E v Přehledu
 * (stav před migrací).
 *
 * Vložit do Excelu: Automatizér → Nový skript → nahradit obsah.
 */
function main(workbook: ExcelScript.Workbook) {
  const SLOUPCE = ["ZdrojovySoubor", "Datum", "Poradi", "Stanice", "Hodnota", "Rozsah", "Cas", "Popis"];

  const cil = workbook.getWorksheet("Přehled");
  if (!cil) { console.log("Nenalezen list Přehled."); return; }

  const t = najdiTabulkuDat(workbook, SLOUPCE);
  if (!t) { vysledek(cil, "⚠ Chybí tabulka s daty"); return; }
  const h = t.getHeaderRowRange().getValues()[0].map(v => String(v));
  const idx = SLOUPCE.map(s => h.indexOf(s));
  if (idx.some(i => i < 0)) { vysledek(cil, "⚠ Chybí sloupce v datech"); return; }

  const rD = t.getRangeBetweenHeaderAndTotal();
  const dD = rD ? rD.getValues() : [];

  const vDotazu = new Map<string, number>();
  for (const r of dD) {
    const soubor = String(r[idx[0]] ?? "").trim();
    if (soubor === "") continue;
    vDotazu.set(soubor, (vDotazu.get(soubor) ?? 0) + 1);
  }

  const zArchivu = nactiArchiv(workbook, SLOUPCE);
  const vArchivu = new Map<string, number>();
  for (const r of zArchivu) {
    if (r[0] === "") continue;
    vArchivu.set(r[0], (vArchivu.get(r[0]) ?? 0) + 1);
  }

  let zdrojArchivu = zArchivu.length > 0 ? "Archiv" : "Přehled";
  let bezZnacky = 0;

  if (zArchivu.length === 0) {
    const pouzity = cil.getUsedRange();
    const posledni = pouzity ? pouzity.getLastRow().getRowIndex() + 1 : 0;
    if (posledni > 3) {
      const hodnoty = cil.getRangeByIndexes(3, 0, posledni - 3, 5).getValues();
      for (const radek of hodnoty) {
        const a = String(radek[0] ?? "").trim();
        const b = String(radek[1] ?? "").trim();
        const e = String(radek[4] ?? "").trim();
        if (a === "" && b === "") continue;
        if (b === "") continue;
        const soubor = e.split("|")[0] || "";
        if (soubor === "") { bezZnacky++; continue; }
        vArchivu.set(soubor, (vArchivu.get(soubor) ?? 0) + 1);
      }
    }
  }

  const radkuArchiv = pocetDatovych(vArchivu);
  const radkuPrehled = pocetDatovychVPrehledu(cil);
  const prehledNesedi = zArchivu.length > 0 && radkuPrehled !== radkuArchiv;

  const jenDotaz: string[] = [];
  const jenArchiv: string[] = [];
  const rozdilne: string[][] = [];

  // POZOR: Office Scripts nedovoli for...of nad Map → forEach
  vDotazu.forEach((n, s) => {
    if (!vArchivu.has(s)) jenDotaz.push(s);
    else if (vArchivu.get(s) !== n) rozdilne.push([s, String(n), String(vArchivu.get(s))]);
  });
  vArchivu.forEach((n, s) => {
    if (!vDotazu.has(s)) jenArchiv.push(s);
  });

  jenDotaz.sort();
  jenArchiv.sort();
  rozdilne.sort((a, b) => a[0] < b[0] ? -1 : 1);

  const vporadku = rozdilne.length === 0 && bezZnacky === 0 && !prehledNesedi;
  const ted = new Date();
  const cas = " (" + dvojcifry(ted.getHours()) + ":" + dvojcifry(ted.getMinutes()) + ")";

  let text = "";
  if (!vporadku) text = "⚠ Nesrovnalosti — viz list Kontrola" + cas;
  else if (jenDotaz.length > 0) {
    text = "⬤ Čeká " + jenDotaz.length +
           (jenDotaz.length === 1 ? " nový soubor" : jenDotaz.length < 5 ? " nové soubory" : " nových souborů") + cas;
  } else text = "✔ Aktuální" + cas;

  vysledek(cil, text);

  let list = workbook.getWorksheet("Kontrola");
  if (list) list.getRange("A:H").clear(ExcelScript.ClearApplyTo.all);
  else list = workbook.addWorksheet("Kontrola");

  const out: string[][] = [];
  const nadpisy: number[] = [];

  out.push(["Kontrola shody: " + zdrojArchivu + " × dotaz", "", ""]);
  out.push([dvojcifry(ted.getDate()) + "." + dvojcifry(ted.getMonth() + 1) + "." + ted.getFullYear() +
            " " + dvojcifry(ted.getHours()) + ":" + dvojcifry(ted.getMinutes()), "", ""]);
  out.push(["", "", ""]);
  out.push([vporadku ? "✔ Bez problémů" : "⚠ Nalezeny nesrovnalosti", "", ""]);
  out.push(["", "", ""]);
  out.push(["Souborů v dotazu:", String(vDotazu.size), ""]);
  out.push(["Souborů v Archivu:", String(vArchivu.size), ""]);
  out.push(["Řádků v Archivu:", String(radkuArchiv), ""]);
  out.push(["Datových řádků v Přehledu:", String(radkuPrehled), ""]);
  out.push(["Přehled sedí na Archiv:", prehledNesedi ? "NE — klikněte Aktualizovat" : "ano", ""]);
  out.push(["Řádků bez značky souboru:", String(bezZnacky), ""]);
  out.push(["", "", ""]);

  nadpisy.push(out.length);
  out.push(["PROBLÉM: různý počet řádků", "v dotazu", "v Archivu"]);
  if (rozdilne.length === 0) out.push(["(žádné)", "", ""]);
  else for (const r of rozdilne) out.push(r);
  out.push(["", "", ""]);

  nadpisy.push(out.length);
  out.push(["Nové – čekají na zpracování", "řádků", ""]);
  if (jenDotaz.length === 0) out.push(["(žádné)", "", ""]);
  else for (const s of jenDotaz) out.push([s, String(vDotazu.get(s)), ""]);
  out.push(["", "", ""]);

  nadpisy.push(out.length);
  out.push(["Archivované – zdroj už neexistuje", "řádků", ""]);
  if (jenArchiv.length === 0) out.push(["(žádné)", "", ""]);
  else for (const s of jenArchiv) out.push([s, String(vArchivu.get(s)), ""]);

  const rng = list.getRangeByIndexes(0, 0, out.length, 3);
  rng.setNumberFormatLocal("@");
  rng.setValues(out);
  rng.getFormat().getFont().setName("Calibri");
  rng.getFormat().getFont().setSize(11);

  const titulek = list.getRangeByIndexes(0, 0, 1, 3);
  titulek.getFormat().getFont().setBold(true);
  titulek.getFormat().getFont().setSize(14);

  const stav = list.getRangeByIndexes(3, 0, 1, 3);
  stav.getFormat().getFont().setBold(true);
  stav.getFormat().getFont().setColor(vporadku ? "#217346" : "#C00000");

  for (const i of nadpisy) {
    const b = list.getRangeByIndexes(i, 0, 1, 3);
    b.getFormat().getFill().setColor("#D9D9D9");
    b.getFormat().getFont().setBold(true);
  }

  list.getRangeByIndexes(0, 0, out.length, 1).getFormat().setColumnWidth(260);
  list.getRangeByIndexes(0, 1, out.length, 1).getFormat().setColumnWidth(90);
  list.getRangeByIndexes(0, 2, out.length, 1).getFormat().setColumnWidth(90);

  cil.activate();
  console.log("Nove: " + jenDotaz.length + ", archivovane: " + jenArchiv.length +
              ", rozdilne: " + rozdilne.length + ", bez znacky: " + bezZnacky +
              ", prehledNesedi: " + prehledNesedi);
}

function najdiTabulkuDat(workbook: ExcelScript.Workbook, sloupce: string[]): ExcelScript.Table | null {
  const tabulky = workbook.getTables();
  let kandidat: ExcelScript.Table | null = null;
  for (let i = 0; i < tabulky.length; i++) {
    const tab = tabulky[i];
    const nazev = tab.getName();
    if (nazev === "TabArchiv") continue;
    if (nazev === "TabNove") return tab;
    const list = tab.getWorksheet().getName();
    if (list === "Dotaz1") {
      kandidat = tab;
      continue;
    }
    if (kandidat !== null) continue;
    const hlavicky = tab.getHeaderRowRange().getValues()[0].map(v => String(v));
    if (sloupce.every(s => hlavicky.indexOf(s) >= 0)) kandidat = tab;
  }
  return kandidat;
}

function nactiArchiv(workbook: ExcelScript.Workbook, sloupce: string[]): string[][] {
  const vysledek: string[][] = [];
  const tabulky = workbook.getTables();
  let tab: ExcelScript.Table | null = null;
  for (let i = 0; i < tabulky.length; i++) {
    if (tabulky[i].getName() === "TabArchiv") {
      tab = tabulky[i];
      break;
    }
  }
  if (!tab) {
    const list = workbook.getWorksheet("Archiv");
    if (!list) return vysledek;
    const pouzity = list.getUsedRange();
    if (!pouzity) return vysledek;
    const hodnoty = pouzity.getValues();
    if (hodnoty.length < 2) return vysledek;
    const hlavicky = hodnoty[0].map(v => String(v));
    const idx = sloupce.map(s => hlavicky.indexOf(s));
    if (idx.some(i => i < 0)) return vysledek;
    for (let i = 1; i < hodnoty.length; i++) {
      const radek = idx.map(j => txt(hodnoty[i][j]));
      if (radek[0] === "" && radek[4] === "") continue;
      vysledek.push(radek);
    }
    return vysledek;
  }
  const h = tab.getHeaderRowRange().getValues()[0].map(v => String(v));
  const idx = sloupce.map(s => h.indexOf(s));
  if (idx.some(i => i < 0)) return vysledek;
  const telo = tab.getRangeBetweenHeaderAndTotal();
  const data = telo ? telo.getValues() : [];
  for (const r of data) {
    const radek = idx.map(i => txt(r[i]));
    if (radek[0] === "" && radek[4] === "") continue;
    vysledek.push(radek);
  }
  return vysledek;
}

function pocetDatovych(mapa: Map<string, number>): number {
  let n = 0;
  mapa.forEach(pocet => { n += pocet; });
  return n;
}

function pocetDatovychVPrehledu(cil: ExcelScript.Worksheet): number {
  const pouzity = cil.getUsedRange();
  const posledni = pouzity ? pouzity.getLastRow().getRowIndex() + 1 : 0;
  if (posledni <= 3) return 0;
  const hodnoty = cil.getRangeByIndexes(3, 0, posledni - 3, 2).getValues();
  let n = 0;
  for (const radek of hodnoty) {
    const a = String(radek[0] ?? "").trim();
    const b = String(radek[1] ?? "").trim();
    if (a === "" && b === "") continue;
    if (b === "") continue;
    n++;
  }
  return n;
}

function vysledek(list: ExcelScript.Worksheet, text: string) {
  const ted = new Date();

  const g = list.getRangeByIndexes(1, 6, 1, 1);
  g.setNumberFormatLocal("@");
  g.setValue(text);
  g.getFormat().getFont().setColor("#FFFFFF");
}

function dvojcifry(n: number): string {
  return String(n).padStart(2, "0");
}

function txt(v: string | number | boolean): string {
  if (v === null || v === undefined) return "";
  if (typeof v === "number" && v > 0 && v < 1) {
    const m = Math.round(v * 1440);
    return `${Math.floor(m / 60)}:${String(m % 60).padStart(2, "0")}`;
  }
  return String(v);
}
