package cz.mereni.app.data

import android.content.Context
import java.io.File
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV úložiště měření.
 * Soubor: Documents/mereni.csv — oddělovač `;`, UTF-8 s BOM.
 */
class MeasurementStore(context: Context) {

    private val docsDir: File =
        File(context.getExternalFilesDir(null), "Documents").apply { mkdirs() }

    val csvFile: File = File(docsDir, CSV_NAME)

    fun ensureHeader() {
        if (!csvFile.exists() || csvFile.length() == 0L) {
            csvFile.writeText(BOM + HEADER + "\n", UTF8)
            return
        }
        val raw = csvFile.readText(UTF8)
        val lines = raw.removePrefix(BOM).lines().toMutableList()
        if (lines.isEmpty() || lines[0].isBlank()) {
            csvFile.writeText(BOM + HEADER + "\n", UTF8)
            return
        }
        val header = lines[0].trim()
        if (header == HEADER) return
        // Migrace ze staršího formátu bez poznámky
        if (header == HEADER_V1 || !header.contains("poznamka")) {
            lines[0] = HEADER
            for (i in 1 until lines.size) {
                val line = lines[i]
                if (line.isBlank()) continue
                val semis = line.count { it == ';' }
                if (semis == 4) lines[i] = "$line;"
            }
            csvFile.writeText(BOM + lines.joinToString("\n") + "\n", UTF8)
        }
    }

    fun count(): Int {
        if (!csvFile.exists()) return 0
        val lines = csvFile.readLines(UTF8)
        return (lines.size - 1).coerceAtLeast(0)
    }

    fun append(udu: String, pole1: String, pole2: String, casMereni: String, poznamka: String) {
        ensureHeader()
        val stamp = STAMP.format(Date())
        val line = listOf(stamp, udu, pole1, pole2, casMereni, poznamka)
            .joinToString(";") { escape(it) }
        csvFile.appendText(line + "\n", UTF8)
    }

    companion object {
        const val CSV_NAME = "mereni.csv"
        private const val HEADER_V1 = "zapsano;udu;pole1;pole2;cas_mereni"
        private const val HEADER = "zapsano;udu;pole1;pole2;cas_mereni;poznamka"
        private const val BOM = "\uFEFF"
        private val UTF8: Charset = Charsets.UTF_8
        private val STAMP = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        private fun escape(value: String): String =
            if (value.contains(';') || value.contains('"') || value.contains('\n')) {
                "\"" + value.replace("\"", "\"\"") + "\""
            } else {
                value
            }
    }
}
