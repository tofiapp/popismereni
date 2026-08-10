package cz.mereni.app.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Excel úložiště měření (.xlsx), 4 sloupce:
 * 1) spojky/koleje (čárkou)
 * 2) od–do (pomlčkou)
 * 3) čas
 * 4) poznámka
 *
 * Řádek 1 = dnešní datum. Při nové stanici prázdný řádek + název stanice.
 * Export na OneDrive: `YYMMDD_N_MD1.xlsx`, poté se lokální soubor vymaže.
 */
class MeasurementStore(context: Context) {

    private val appContext = context.applicationContext
    private val docsDir: File =
        File(appContext.getExternalFilesDir(null), "Documents").apply { mkdirs() }
    private val prefs =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val workingFile: File = File(docsDir, WORKING_NAME)

    var lastExportFile: File? = null
        private set

    fun ensureReady() {
        File(docsDir, "mereni.csv").takeIf { it.exists() }?.delete()
        if (!workingFile.exists()) {
            resetWorkingFile()
        } else {
            ensureDateRow()
        }
    }

    /** Počet datových záznamů (bez data / stanic / mezer). */
    fun count(): Int =
        SimpleXlsx.read(workingFile).count { it.role == SimpleXlsx.Role.DATA && hasMeasurement(it) }

    /**
     * Číslo u tlačítka OneDrive: kolikátý záznam v aktuálním lokálním souboru.
     * Prázdný soubor → 1.
     */
    fun dayRecordNumber(): Int = count().coerceAtLeast(1)

    fun append(
        stationName: String,
        stationUdu: String,
        pole1: String,
        pole2: String,
        casMereni: String,
        poznamka: String,
    ) {
        ensureReady()
        val rows = SimpleXlsx.read(workingFile).toMutableList()
        ensureDateRowIn(rows)

        val name = stationName.trim().ifBlank { stationUdu.trim() }
        val lastStation = rows.lastOrNull { it.role == SimpleXlsx.Role.STATION }?.a?.trim()
        if (name.isNotEmpty() && lastStation != name) {
            // Mezera po zvolení nové stanice, pak název
            rows += SimpleXlsx.Row(role = SimpleXlsx.Role.BLANK)
            rows += SimpleXlsx.Row(a = name, role = SimpleXlsx.Role.STATION)
            prefs.edit().putString(KEY_LAST_STATION_UDU, stationUdu.trim()).apply()
        }
        rows += SimpleXlsx.Row(
            a = pole1.trim(),
            b = pole2.trim(),
            c = casMereni.trim(),
            d = poznamka.trim(),
            role = SimpleXlsx.Role.DATA,
        )
        SimpleXlsx.write(workingFile, rows)
        prefs.edit().putBoolean(KEY_SYNCED, false).apply()
    }

    fun usedLabelsForStation(stationName: String): Set<String> {
        val want = stationName.trim()
        if (want.isEmpty()) return emptySet()
        val rows = SimpleXlsx.read(workingFile)
        val out = linkedSetOf<String>()
        var active = false
        for (row in rows) {
            when (row.role) {
                SimpleXlsx.Role.STATION -> active = row.a.trim() == want
                SimpleXlsx.Role.DATA -> if (active) {
                    row.a.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { out.add(it) }
                    row.b.split('-').map { it.trim() }.filter { it.isNotEmpty() }.forEach { out.add(it) }
                }
                else -> Unit
            }
        }
        return out
    }

    fun isSyncedToOneDrive(): Boolean = prefs.getBoolean(KEY_SYNCED, false)

    /**
     * Vytvoří `YYMMDD_N_MD1.xlsx`, vymaže lokální pracovní soubor (číslo → 1),
     * označí jako synchronizované (zelené tlačítko).
     */
    fun prepareExportFile(): File {
        ensureReady()
        val day = DAY_FILE_FMT.format(Date())
        val n = prefs.getInt(KEY_DAY_EXPORT_PREFIX + day, 0) + 1
        prefs.edit().putInt(KEY_DAY_EXPORT_PREFIX + day, n).apply()
        val name = "${day}_${n}_MD1.xlsx"
        val dest = File(docsDir, name)
        // Před exportem ještě jednou aktuální datum v řádku 1
        val rows = SimpleXlsx.read(workingFile).toMutableList()
        ensureDateRowIn(rows)
        SimpleXlsx.write(workingFile, rows)
        workingFile.copyTo(dest, overwrite = true)
        lastExportFile = dest
        resetWorkingFile()
        prefs.edit()
            .putBoolean(KEY_SYNCED, true)
            .remove(KEY_LAST_STATION_UDU)
            .apply()
        return dest
    }

    private fun resetWorkingFile() {
        SimpleXlsx.write(workingFile, listOf(dateRow()))
    }

    private fun ensureDateRow() {
        val rows = SimpleXlsx.read(workingFile).toMutableList()
        ensureDateRowIn(rows)
        SimpleXlsx.write(workingFile, rows)
    }

    private fun ensureDateRowIn(rows: MutableList<SimpleXlsx.Row>) {
        val today = dateRow()
        if (rows.isEmpty()) {
            rows += today
            return
        }
        if (rows[0].role == SimpleXlsx.Role.DATE || rows[0].role == SimpleXlsx.Role.STATION) {
            rows[0] = today
        } else {
            rows.add(0, today)
        }
    }

    private fun dateRow(): SimpleXlsx.Row =
        SimpleXlsx.Row(a = DATE_DISPLAY_FMT.format(Date()), role = SimpleXlsx.Role.DATE)

    private fun hasMeasurement(row: SimpleXlsx.Row): Boolean =
        row.a.isNotBlank() || row.b.isNotBlank() || row.c.isNotBlank() || row.d.isNotBlank()

    companion object {
        private const val PREFS = "measurement_xlsx"
        private const val WORKING_NAME = "mereni_working.xlsx"
        private const val KEY_DAY_EXPORT_PREFIX = "export_count_"
        private const val KEY_LAST_STATION_UDU = "last_station_udu"
        private const val KEY_SYNCED = "synced_onedrive"
        private val DAY_FILE_FMT = SimpleDateFormat("yyMMdd", Locale.US)
        private val DATE_DISPLAY_FMT = SimpleDateFormat("d.M.yyyy", Locale("cs", "CZ"))

        const val MIME_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
