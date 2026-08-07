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
        }
    }

    fun count(): Int {
        if (!csvFile.exists()) return 0
        val lines = csvFile.readLines(UTF8)
        return (lines.size - 1).coerceAtLeast(0)
    }

    fun append(udu: String, pole1: String, pole2: String, casMereni: String) {
        ensureHeader()
        val stamp = STAMP.format(Date())
        val line = listOf(stamp, udu, pole1, pole2, casMereni)
            .joinToString(";") { escape(it) }
        csvFile.appendText(line + "\n", UTF8)
    }

    companion object {
        const val CSV_NAME = "mereni.csv"
        private const val HEADER = "zapsano;udu;pole1;pole2;cas_mereni"
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
