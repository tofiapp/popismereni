package cz.mereni.app.data

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Minimální XLSX (4 sloupce) se styly: datum, stanice, data.
 *
 * Role ze stylu buňky A. Datum/stanice jen ve sloupci A.
 * Styl 0 = výchozí Excelu (bez úprav) — centrovaná data mají vlastní index,
 * jinak Excel zarovnání na střed ignoruje.
 */
object SimpleXlsx {
    enum class Role { DATE, BLANK, STATION, DATA }

    data class Row(
        val a: String = "",
        val b: String = "",
        val c: String = "",
        val d: String = "",
        val role: Role = Role.DATA,
    ) {
        fun isEmpty(): Boolean =
            a.isBlank() && b.isBlank() && c.isBlank() && d.isBlank()
    }

    fun write(file: File, rows: List<Row>) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { fos ->
            ZipOutputStream(fos).use { zip ->
                fun put(name: String, body: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(body.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                put("[Content_Types].xml", CONTENT_TYPES)
                put("_rels/.rels", RELS_ROOT)
                put("xl/workbook.xml", WORKBOOK)
                put("xl/_rels/workbook.xml.rels", RELS_WB)
                put("xl/styles.xml", STYLES)
                put("xl/worksheets/sheet1.xml", sheetXml(rows))
            }
        }
    }

    fun read(file: File): List<Row> {
        if (!file.isFile || file.length() == 0L) return emptyList()
        return runCatching {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("xl/worksheets/sheet1.xml") ?: return emptyList()
                val xml = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
                parseSheet(xml)
            }
        }.getOrDefault(emptyList())
    }

    private fun sheetXml(rows: List<Row>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append(
            """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""",
        )
        sb.append("<cols>")
        sb.append("""<col min="1" max="1" width="32" customWidth="1"/>""")
        sb.append("""<col min="2" max="2" width="18" customWidth="1"/>""")
        sb.append("""<col min="3" max="3" width="14" customWidth="1"/>""")
        sb.append("""<col min="4" max="4" width="36" customWidth="1"/>""")
        sb.append("</cols>")
        sb.append("<sheetData>")
        rows.forEachIndexed { idx, row ->
            val r = idx + 1
            val ht = if (row.role == Role.BLANK) "12" else "24"
            sb.append("""<row r="$r" ht="$ht" customHeight="1">""")
            when (row.role) {
                Role.BLANK -> Unit
                Role.DATE, Role.STATION -> {
                    sb.append(cellXml("A$r", row.a, styleIndex(row.role)))
                }
                Role.DATA -> {
                    // Všechna data na střed (koleje, výhybky, čas, poznámka)
                    sb.append(cellXml("A$r", row.a, STYLE_DATA_CENTER))
                    sb.append(cellXml("B$r", row.b, STYLE_DATA_CENTER))
                    sb.append(cellXml("C$r", row.c, STYLE_DATA_CENTER))
                    sb.append(cellXml("D$r", row.d, STYLE_DATA_CENTER))
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun styleIndex(role: Role): Int = when (role) {
        Role.DATE -> STYLE_DATE
        Role.STATION -> STYLE_STATION
        Role.BLANK, Role.DATA -> STYLE_DATA_CENTER
    }

    private fun roleFromStyle(style: Int?): Role = when (style) {
        STYLE_DATE -> Role.DATE
        STYLE_STATION -> Role.STATION
        else -> Role.DATA // 0 výchozí / 3 centrovaná data / staré 0
    }

    private fun cellXml(ref: String, value: String, style: Int): String {
        val s = """ s="$style""""
        if (value.isEmpty()) {
            return """<c r="$ref"$s/>"""
        }
        return """<c r="$ref"$s t="inlineStr"><is><t>${xmlEscape(value)}</t></is></c>"""
    }

    private fun xmlEscape(s: String): String = buildString(s.length + 8) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                '\n', '\r' -> append(' ')
                else -> append(ch)
            }
        }
    }

    private fun parseSheet(xml: String): List<Row> {
        val rows = mutableListOf<Row>()
        val rowRegex = Regex("""<row\b[^>]*>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex(
            """<c\b([^>]*)>(?:.*?<t[^>]*>(.*?)</t>.*?)?</c>|<c\b([^>]*)/>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        val refRegex = Regex("""\br="([A-Z]+)\d+"""")
        val styleRegex = Regex("""\bs="(\d+)"""")
        for (rowMatch in rowRegex.findAll(xml)) {
            val cells = mutableMapOf<String, String>()
            var styleA: Int? = null
            for (cellMatch in cellRegex.findAll(rowMatch.groupValues[1])) {
                val attrs = cellMatch.groupValues[1].ifBlank { cellMatch.groupValues[3] }
                val text = xmlUnescape(cellMatch.groupValues[2])
                val ref = refRegex.find(attrs)?.groupValues?.get(1) ?: continue
                cells[ref] = text
                if (ref == "A") {
                    styleRegex.find(attrs)?.groupValues?.get(1)?.toIntOrNull()?.let { styleA = it }
                }
            }
            val a = cells["A"].orEmpty()
            val b = cells["B"].orEmpty()
            val c = cells["C"].orEmpty()
            val d = cells["D"].orEmpty()
            val role = when {
                a.isBlank() && b.isBlank() && c.isBlank() && d.isBlank() -> Role.BLANK
                styleA != null -> roleFromStyle(styleA)
                a.isNotBlank() && b.isBlank() && c.isBlank() && d.isBlank() ->
                    if (rows.isEmpty()) Role.DATE else Role.STATION
                else -> Role.DATA
            }
            rows += Row(a, b, c, d, role)
        }
        return rows
    }

    private fun xmlUnescape(s: String): String =
        s.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    // 0 = Excel default (nepoužívat pro střed — Excel styl 0 často ignoruje)
    // 1 = datum, 2 = stanice (stejné indexy jako dřív kvůli starým souborům)
    // 3 = data na střed
    private const val STYLE_DATE = 1
    private const val STYLE_STATION = 2
    private const val STYLE_DATA_CENTER = 3

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private const val RELS_ROOT = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val RELS_WB = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Mereni" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

    /**
     * 0 = výchozí Excel
     * 1 = datum — modré, vlevo
     * 2 = stanice — oranžové, vlevo
     * 3 = data — střed
     */
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="14"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
    <font><sz val="16"/><b/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
    <font><sz val="16"/><b/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFBBDEFB"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFE0B2"/></patternFill></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1">
      <alignment horizontal="left" vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1">
      <alignment horizontal="left" vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
  </cellXfs>
  <cellStyles count="1">
    <cellStyle name="Normal" xfId="0" builtinId="0"/>
  </cellStyles>
</styleSheet>"""
}
