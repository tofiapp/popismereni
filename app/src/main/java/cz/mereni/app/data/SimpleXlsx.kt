package cz.mereni.app.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Minimální XLSX (4 sloupce) bez Apache POI — zip + sheet XML.
 */
object SimpleXlsx {
    data class Row(val a: String, val b: String = "", val c: String = "", val d: String = "") {
        fun isStationHeader(): Boolean =
            a.isNotBlank() && b.isBlank() && c.isBlank() && d.isBlank()
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

    fun toBytes(rows: List<Row>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
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
        return bos.toByteArray()
    }

    private fun sheetXml(rows: List<Row>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append(
            """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">"""
        )
        sb.append("<sheetData>")
        rows.forEachIndexed { idx, row ->
            val r = idx + 1
            sb.append("""<row r="$r">""")
            sb.append(cellXml("A$r", row.a))
            sb.append(cellXml("B$r", row.b))
            sb.append(cellXml("C$r", row.c))
            sb.append(cellXml("D$r", row.d))
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun cellXml(ref: String, value: String): String {
        if (value.isEmpty()) {
            return """<c r="$ref"/>"""
        }
        return """<c r="$ref" t="inlineStr"><is><t>${xmlEscape(value)}</t></is></c>"""
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
            """<c\b([^>]*)>(?:.*?<t[^>]*>(.*?)</t>.*?)?</c>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        val refRegex = Regex("""\br="([A-Z]+)(\d+)"""")
        for (rowMatch in rowRegex.findAll(xml)) {
            val cells = mutableMapOf<String, String>()
            for (cellMatch in cellRegex.findAll(rowMatch.groupValues[1])) {
                val attrs = cellMatch.groupValues[1]
                val text = xmlUnescape(cellMatch.groupValues[2])
                val ref = refRegex.find(attrs)?.groupValues?.get(1) ?: continue
                cells[ref] = text
            }
            rows += Row(
                a = cells["A"].orEmpty(),
                b = cells["B"].orEmpty(),
                c = cells["C"].orEmpty(),
                d = cells["D"].orEmpty(),
            )
        }
        return rows
    }

    private fun xmlUnescape(s: String): String =
        s.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

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

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
  <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
  <borders count="1"><border/></borders>
  <cellStyleXfs count="1"><xf/></cellStyleXfs>
  <cellXfs count="1"><xf/></cellXfs>
</styleSheet>"""
}
