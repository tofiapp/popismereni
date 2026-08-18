/**
 * Office Script: 2. Aktualizovat
 *
 * Zdroj pravdy je skrytý list Archiv (tabulka TabArchiv).
 * Přehled se z Archivu jen vykreslí. Zdrojové .xlsx lze mazat.
 *
 * První běh: když Archiv ještě není, načte se stávající Přehled (sloupec E).
 * Než sloučí, počká jen když je DataALL/Dotaz1 prázdný nebo se ještě mění.
 *
 * A1 = VŽDY prázdné (žádný nápis, žádný vzorec).
 * F2 = provozní stav (běží / razítko / chyba) — hodnota ze skriptu.
 * C2 = Aktuální. N2 = zámek jen během běhu, po úspěchu pryč.
 *
 * Vložit do Excelu: Automatizér → Nový skript → nahradit obsah.
 */
function main(workbook: ExcelScript.Workbook) {
  const SLOUPCE = ["ZdrojovySoubor", "Datum", "Poradi", "Stanice", "Hodnota", "Rozsah", "Cas", "Popis"];
  const PAUZA_S = 25;
  const SL_CAS = 13; // N2

  let cil = workbook.getWorksheet("Přehled");

  // --- zámek + F2 hned ---
  if (cil) {
    vycistiA1(cil);
    const posledniIso = String(cil.getRangeByIndexes(1, SL_CAS, 1, 1).getValue() ?? "").trim();
    if (posledniIso !== "") {
      const then = Date.parse(posledniIso);
      if (!isNaN(then)) {
        const rozdil = (Date.now() - then) / 1000;
        if (rozdil >= 0 && rozdil < PAUZA_S) {
          const f2 = String(cil.getRange("F2").getValue() ?? "");
          if (f2.indexOf("Aktualizováno") === 0) {
            console.log("Preruseno: zamek, razitko uz je.");
            return;
          }
          hlaska(cil, "⚠ Aktualizace ještě běží — neklikejte znovu", "#C00000");
          console.log("Preruseno: zamek N2, pred " + Math.round(rozdil) + " s.");
          return;
        }
      }
    }
    zapisZamek(cil, SL_CAS);
    hlaska(cil, "Aktualizace běží — neklikejte znovu", "#C00000");
  }

  const t = najdiTabulkuDat(workbook, SLOUPCE);
  if (!t) {
    if (cil) {
      hlaska(cil, "⚠ Chybí tabulka s daty", "#C00000");
      uvolniZamek(cil, SL_CAS);
    }
    return;
  }
  const h = t.getHeaderRowRange().getValues()[0].map(v => String(v));
  const idx = SLOUPCE.map(s => h.indexOf(s));
  if (idx.some(i => i < 0)) {
    if (cil) {
      hlaska(cil, "⚠ Chybí sloupce v datech", "#C00000");
      uvolniZamek(cil, SL_CAS);
    }
    console.log("Hlavicky: " + h.join(", "));
    return;
  }

  if (!cil) cil = workbook.addWorksheet("Přehled");
  vycistiA1(cil);
  let dNove = pockejNaNacteni(cil, t, idx[0]);
  if (!dNove) {
    hlaska(cil, "Počkejte na načtení dat a klikněte znovu", "#C00000");
    uvolniZamek(cil, SL_CAS);
    return;
  }
  const nDotaz = pocetNepradnych(dNove, idx[0]);

  // --- Archiv = zdroj pravdy; prazdny Archiv = jednorazova migrace z Prehledu ---
  let zaznamy = nactiArchiv(workbook, SLOUPCE);
  const znamSoubory = new Set<string>();
  for (const r of zaznamy) {
    if (r[0] !== "") znamSoubory.add(r[0]);
  }

  if (zaznamy.length === 0 && cil) {
    zaznamy = nactiZPrehledu(cil);
    for (const r of zaznamy) {
      if (r[0] !== "") znamSoubory.add(r[0]);
    }
    if (zaznamy.length > 0) {
      console.log("Migrace z Prehledu do Archivu: " + zaznamy.length + " radku.");
    }
  }

  // --- nove soubory + chybejici radky u uz znamych (Archiv se neprepisuje) ---
  let pridano = pridejNoveSoubory(zaznamy, znamSoubory, dNove, idx);

  if (zaznamy.length === 0) {
    hlaska(cil, "⚠ Žádná data", "#C00000");
    uvolniZamek(cil, SL_CAS);
    return;
  }

  doplnDatum(zaznamy);
  seradZaznamy(zaznamy);

  zapisArchiv(workbook, SLOUPCE, zaznamy);
  vykresliPrehled(cil, zaznamy);

  const dPozde = nactiTeloTabulky(t);
  const nPozde = pocetNepradnych(dPozde, idx[0]);
  if (nPozde > 0 && nPozde >= nDotaz) {
    const jeste = pridejNoveSoubory(zaznamy, znamSoubory, dPozde, idx);
    if (jeste > 0) {
      pridano += jeste;
      doplnDatum(zaznamy);
      seradZaznamy(zaznamy);
      zapisArchiv(workbook, SLOUPCE, zaznamy);
      vykresliPrehled(cil, zaznamy);
    }
    dNove = dPozde;
  }

  const ted = new Date();
  const razitko = "Aktualizováno " + dvojcifry(ted.getDate()) + "." +
    dvojcifry(ted.getMonth() + 1) + ". v " +
    dvojcifry(ted.getHours()) + ":" + dvojcifry(ted.getMinutes()) +
    "   (nově: " + pridano + ")";

  hlaska(cil, razitko, "#808080");
  uvolniZamek(cil, SL_CAS);
  zapisStavVzorce(cil, t);

  cil.activate();
  cil.setPosition(0);
  cil.getFreezePanes().freezeRows(3);
  console.log("Novych souboru: " + pridano + ", celkem radku v Archivu: " + zaznamy.length);
}

function zapisZamek(list: ExcelScript.Worksheet, slCas: number): void {
  list.getRangeByIndexes(1, slCas, 1, 1).setValue(new Date().toISOString());
  list.getRangeByIndexes(1, slCas, 1, 1).getFormat().getFont().setColor("#FFFFFF");
}

/** Po úspěchu / chybě — hned jde kliknout znovu (žádná minutová pauza). */
function uvolniZamek(list: ExcelScript.Worksheet, slCas: number): void {
  list.getRangeByIndexes(1, slCas, 1, 1).setValue("");
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
    // Preferuj aktuální PQ list (DataALL), starý Dotaz1 jen jako záloha
    if (list === "DataALL" || list === "DataAll" || list === "Dotaz1") {
      if (list === "DataALL" || list === "DataAll") return tab;
      kandidat = tab;
      continue;
    }
    if (kandidat !== null) continue;
    const hlavicky = tab.getHeaderRowRange().getValues()[0].map(v => String(v));
    if (sloupce.every(s => hlavicky.indexOf(s) >= 0)) kandidat = tab;
  }
  return kandidat;
}

function nactiTeloTabulky(t: ExcelScript.Table): (string | number | boolean)[][] {
  const r = t.getRangeBetweenHeaderAndTotal();
  return r ? r.getValues() : [];
}

function pocetNepradnych(data: (string | number | boolean)[][], idxSoubor: number): number {
  let n = 0;
  for (let i = 0; i < data.length; i++) {
    if (String(data[i][idxSoubor] ?? "").trim() !== "") n++;
  }
  return n;
}

function pockejNaNacteni(
  cil: ExcelScript.Worksheet,
  t: ExcelScript.Table,
  idxSoubor: number,
): (string | number | boolean)[][] | null {
  const MAX_POKUSU = 10;
  let lastN = -1;
  let stable = 0;
  let data: (string | number | boolean)[][] = [];

  for (let i = 0; i < MAX_POKUSU; i++) {
    data = nactiTeloTabulky(t);
    const n = pocetNepradnych(data, idxSoubor);

    if (n === 0) {
      lastN = 0;
      stable = 0;
      continue;
    }
    if (n === lastN) {
      stable++;
      if (stable >= 2) return data;
    } else {
      lastN = n;
      stable = 1;
    }
  }

  if (pocetNepradnych(data, idxSoubor) > 0) return data;
  return null;
}

function klicRadku(r: string[]): string {
  return r[0] + "|" + r[3] + "|" + r[4] + "|" + r[5] + "|" + r[6];
}

function pridejNoveSoubory(
  zaznamy: string[][],
  znamSoubory: Set<string>,
  data: (string | number | boolean)[][],
  idx: number[],
): number {
  const uzVArchivu = new Set<string>();
  const klice = new Set<string>();
  znamSoubory.forEach(s => { uzVArchivu.add(s); });
  for (let i = 0; i < zaznamy.length; i++) {
    klice.add(klicRadku(zaznamy[i]));
  }

  let pridano = 0;
  const doplnene = new Set<string>();
  for (let i = 0; i < data.length; i++) {
    const radek = idx.map(j => txt(data[i][j]));
    radek[0] = radek[0].trim();
    if (radek[0] === "") continue;
    const klic = klicRadku(radek);
    if (klice.has(klic)) continue;
    if (uzVArchivu.has(radek[0])) {
      zaznamy.push(radek);
      klice.add(klic);
      if (!doplnene.has(radek[0])) {
        doplnene.add(radek[0]);
        pridano++;
      }
      continue;
    }
    zaznamy.push(radek);
    klice.add(klic);
    if (!znamSoubory.has(radek[0])) {
      znamSoubory.add(radek[0]);
      pridano++;
    }
  }
  return pridano;
}

function doplnDatum(zaznamy: string[][]): void {
  const datumSouboru = new Map<string, string>();
  for (const r of zaznamy) if (r[1] !== "" && r[0] !== "") datumSouboru.set(r[0], r[1]);
  for (const r of zaznamy) {
    if (r[1] === "") {
      const z = datumSouboru.get(r[0]);
      if (z) r[1] = z;
      else if (r[0].length >= 6) r[1] = "20" + r[0].substring(0, 2) + "-" + r[0].substring(2, 4) + "-" + r[0].substring(4, 6);
    }
  }
}

function seradZaznamy(zaznamy: string[][]): void {
  const poradiVstupu = new Map<string[], number>();
  zaznamy.forEach((r, i) => poradiVstupu.set(r, i));
  zaznamy.sort((x, y) => {
    if (x[1] !== y[1]) return x[1] < y[1] ? -1 : 1;
    const px = Number(x[2]) || 0;
    const py = Number(y[2]) || 0;
    if (px !== py) return px - py;
    return (poradiVstupu.get(x) ?? 0) - (poradiVstupu.get(y) ?? 0);
  });
}

function vykresliPrehled(cil: ExcelScript.Worksheet, zaznamy: string[][]): void {
  cil.getRange("A4:E100000").clear(ExcelScript.ClearApplyTo.all);

  const START = 3;
  const SIRKA = 5;
  const out: string[][] = [];
  const radkyDatum: number[] = [];
  const radkyStanice: number[] = [];
  let datum = "";
  let stanice = "";

  const prazdny = (): string[] => new Array(SIRKA).fill("");

  for (const r of zaznamy) {
    if (r[1] !== datum) {
      if (datum !== "") { out.push(prazdny()); out.push(prazdny()); }
      datum = r[1];
      stanice = "";
      radkyDatum.push(START + out.length);
      const x = prazdny();
      x[0] = formatDatum(datum);
      out.push(x);
    }
    if (r[3] !== stanice) {
      if (stanice !== "") out.push(prazdny());
      stanice = r[3];
      radkyStanice.push(START + out.length);
      const x = prazdny();
      x[0] = stanice;
      out.push(x);
    }
    out.push([r[4], r[5], r[6], r[7], r[0] + "|" + r[2]]);
  }

  if (out.length === 0) return;

  const rng = cil.getRangeByIndexes(START, 0, out.length, SIRKA);
  rng.setNumberFormatLocal("@");
  rng.setValues(out);

  const viditelne = cil.getRangeByIndexes(START, 0, out.length, 4);
  viditelne.getFormat().getFont().setName("Calibri");
  viditelne.getFormat().getFont().setSize(12);
  viditelne.getFormat().getFont().setColor("#1F3864");
  viditelne.getFormat().setHorizontalAlignment(ExcelScript.HorizontalAlignment.center);
  viditelne.getFormat().setVerticalAlignment(ExcelScript.VerticalAlignment.center);

  for (const i of radkyDatum) obarvi(cil, i, "#BDD7EE");
  for (const i of radkyStanice) obarvi(cil, i, "#FCE4D6");

  cil.getRangeByIndexes(START, 0, out.length, 1).getFormat().setColumnWidth(160);
  cil.getRangeByIndexes(START, 1, out.length, 1).getFormat().setColumnWidth(90);
  cil.getRangeByIndexes(START, 2, out.length, 1).getFormat().setColumnWidth(70);
  cil.getRangeByIndexes(START, 3, out.length, 1).getFormat().setColumnWidth(160);
  cil.getRangeByIndexes(START, 4, out.length, 1).getFormat().setColumnWidth(0);
  cil.getRangeByIndexes(START, 0, out.length, SIRKA).getFormat().setRowHeight(22);
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

function nactiZPrehledu(cil: ExcelScript.Worksheet): string[][] {
  const zaznamy: string[][] = [];
  const pouzity = cil.getUsedRange();
  const posledni = pouzity ? pouzity.getLastRow().getRowIndex() + 1 : 0;
  if (posledni <= 3) return zaznamy;
  const hodnoty = cil.getRangeByIndexes(3, 0, posledni - 3, 5).getValues();
  let datum = "";
  let stanice = "";
  for (const radek of hodnoty) {
    const a = String(radek[0] ?? "").trim();
    const b = String(radek[1] ?? "").trim();
    const c = String(radek[2] ?? "").trim();
    const d = String(radek[3] ?? "").trim();
    const e = String(radek[4] ?? "").trim();
    if (a === "" && b === "") continue;
    if (b === "") {
      if (jeDatum(a)) { datum = naIso(a); stanice = ""; }
      else stanice = a;
      continue;
    }
    const casti = e.split("|");
    const soubor = casti[0] || "";
    const poradi = casti[1] || "0";
    zaznamy.push([soubor, datum, poradi, stanice, a, b, c, d]);
  }
  return zaznamy;
}

function zapisArchiv(workbook: ExcelScript.Workbook, sloupce: string[], zaznamy: string[][]): void {
  let list = workbook.getWorksheet("Archiv");
  if (!list) list = workbook.addWorksheet("Archiv");

  const tabulky = workbook.getTables();
  for (let i = tabulky.length - 1; i >= 0; i--) {
    if (tabulky[i].getName() === "TabArchiv") tabulky[i].delete();
  }

  list.getRange("A:H").clear(ExcelScript.ClearApplyTo.all);

  const out: string[][] = [sloupce];
  for (const r of zaznamy) out.push(r);

  const rng = list.getRangeByIndexes(0, 0, out.length, sloupce.length);
  rng.setNumberFormatLocal("@");
  rng.setValues(out);

  const table = list.addTable(rng, true);
  table.setName("TabArchiv");

  const listy = workbook.getWorksheets();
  list.setPosition(listy.length - 1);
  list.setVisibility(ExcelScript.SheetVisibility.hidden);
}

function hlaska(list: ExcelScript.Worksheet, text: string, barva: string) {
  // Jediný viditelný provozní nápis = F2 (hodnota). G2 vyčistit (starý překryv).
  list.getRange("G2").clear(ExcelScript.ClearApplyTo.all);
  list.getRange("H2").clear(ExcelScript.ClearApplyTo.all);
  list.getRange("G:G").getFormat().setColumnWidth(0);
  list.getRange("H:H").getFormat().setColumnWidth(0);

  const f2 = list.getRange("F2");
  const stare = f2.getConditionalFormats();
  for (let i = stare.length - 1; i >= 0; i--) stare[i].delete();
  f2.clear(ExcelScript.ClearApplyTo.all);
  f2.setNumberFormatLocal("@");
  f2.setValue(text);
  f2.getFormat().getFont().setName("Calibri");
  f2.getFormat().getFont().setSize(barva === "#C00000" ? 18 : 11);
  f2.getFormat().getFont().setBold(barva === "#C00000");
  f2.getFormat().getFont().setItalic(barva !== "#C00000");
  f2.getFormat().getFont().setColor(barva);
  f2.getFormat().setHorizontalAlignment(ExcelScript.HorizontalAlignment.left);
  f2.getFormat().setVerticalAlignment(ExcelScript.VerticalAlignment.center);
  list.getRange("F:F").getFormat().setColumnWidth(340);
}

function obarvi(list: ExcelScript.Worksheet, radek: number, barva: string) {
  const b = list.getRangeByIndexes(radek, 0, 1, 1);
  b.getFormat().getFill().setColor(barva);
  b.getFormat().getFont().setBold(true);
  b.getFormat().getFont().setSize(14);
  b.getFormat().getFont().setColor("#000000");
  b.getFormat().setHorizontalAlignment(ExcelScript.HorizontalAlignment.left);
  b.getFormat().setIndentLevel(1);
}

function dvojcifry(n: number): string {
  return String(n).padStart(2, "0");
}

function jeDatum(s: string): boolean {
  return /^\d{1,2}\.\d{1,2}\.\d{4}$/.test(s);
}

function naIso(s: string): string {
  const c = s.split(".");
  if (c.length !== 3) return s;
  return c[2] + "-" + String(Number(c[1])).padStart(2, "0") + "-" + String(Number(c[0])).padStart(2, "0");
}

function formatDatum(iso: string): string {
  const c = iso.split("-");
  if (c.length === 3) return `${Number(c[2])}.${Number(c[1])}.${c[0]}`;
  return iso;
}

/**
 * A1 musí zůstat prázdné — žádný stavový nápis ani vzorec.
 */
function vycistiA1(list: ExcelScript.Worksheet): void {
  const a1 = list.getRange("A1");
  const stare = a1.getConditionalFormats();
  for (let i = stare.length - 1; i >= 0; i--) stare[i].delete();
  a1.clear(ExcelScript.ClearApplyTo.all);
}

/**
 * C2 + AB1: stejný počet jako vzorec (sloupec tabulky).
 * A1 se jen vyčistí.
 */
function zapisStavVzorce(list: ExcelScript.Worksheet, t: ExcelScript.Table): void {
  vycistiA1(list);

  const vzorecPocet = vzorecPocetZdroje(t);
  const n = pocetZdroje(t);

  const ab = list.getRange("AB1");
  ab.setNumberFormat("General");
  ab.setValue(n);
  ab.getFormat().getFont().setColor("#FFFFFF");
  list.getRange("AB:AB").getFormat().setColumnWidth(0);

  const c2 = list.getRange("C2");
  const stare = c2.getConditionalFormats();
  for (let i = stare.length - 1; i >= 0; i--) stare[i].delete();
  c2.clear(ExcelScript.ClearApplyTo.formats);
  c2.clear(ExcelScript.ClearApplyTo.contents);
  c2.setNumberFormat("General");
  c2.setFormulaLocal(
    "=KDYŽ(" + vzorecPocet + ">AB1;\"Přehled není aktuální\";\"Aktuální\")"
  );
  c2.getFormat().getFont().setName("Calibri");
  c2.getFormat().getFont().setSize(18);
  c2.getFormat().getFont().setBold(true);
  c2.getFormat().setHorizontalAlignment(ExcelScript.HorizontalAlignment.left);
  c2.getFormat().setVerticalAlignment(ExcelScript.VerticalAlignment.center);
  list.getRange("C:C").getFormat().setColumnWidth(340);

  const zelena = c2.addConditionalFormat(ExcelScript.ConditionalFormatType.custom);
  zelena.getCustom().getRule().setFormula("=C2=\"Aktuální\"");
  zelena.getCustom().getFormat().getFont().setColor("#217346");
  zelena.getCustom().getFormat().getFont().setBold(true);

  const cervena = c2.addConditionalFormat(ExcelScript.ConditionalFormatType.custom);
  cervena.getCustom().getRule().setFormula("=C2=\"Přehled není aktuální\"");
  cervena.getCustom().getFormat().getFont().setColor("#C00000");
  cervena.getCustom().getFormat().getFont().setBold(true);
}

/** POČET2(TabNove[ZdrojovySoubor]) — rychlé; fallback na list!A:A jen když nejde sloupec. */
function vzorecPocetZdroje(t: ExcelScript.Table): string {
  const tab = t.getName();
  try {
    if (t.getColumnByName("ZdrojovySoubor")) {
      const tabRef = /[^A-Za-z0-9_]/.test(tab)
        ? "'" + tab.replace(/'/g, "''") + "'"
        : tab;
      return "POČET2(" + tabRef + "[ZdrojovySoubor])";
    }
  } catch (_e) { /* ignore */ }
  const odkaz = odkazListu(t.getWorksheet().getName());
  return "POČET2(" + odkaz + "!A2:A5000)";
}

function pocetZdroje(t: ExcelScript.Table): number {
  try {
    const col = t.getColumnByName("ZdrojovySoubor");
    if (col) {
      const body = col.getRangeBetweenHeaderAndTotal();
      if (body) {
        const vals = body.getValues();
        let n = 0;
        for (let i = 0; i < vals.length; i++) {
          if (String(vals[i][0] ?? "").trim() !== "") n++;
        }
        return n;
      }
    }
  } catch (_e) { /* ignore */ }
  const vals = t.getWorksheet().getRange("A2:A5000").getValues();
  let n = 0;
  for (let i = 0; i < vals.length; i++) {
    if (String(vals[i][0] ?? "").trim() !== "") n++;
  }
  return n;
}

function odkazListu(listDat: string): string {
  return /[\s'()]/.test(listDat) ? "'" + listDat.replace(/'/g, "''") + "'" : listDat;
}

function txt(v: string | number | boolean): string {
  if (v === null || v === undefined) return "";
  if (typeof v === "number" && v > 0 && v < 1) {   // Excel cas = desetinne cislo
    const m = Math.round(v * 1440);
    return `${Math.floor(m / 60)}:${String(m % 60).padStart(2, "0")}`;
  }
  return String(v);
}
