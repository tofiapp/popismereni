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
 * Při změně stanice se před prvním záznamem zapíše řádek jen s názvem stanice.
 * Export na OneDrive: `YYMMDD_N_MD1.xlsx`.
 */
class MeasurementStore(context: Context) {

    private val appContext = context.applicationContext
    private val docsDir: File =
        File(appContext.getExternalFilesDir(null), "Documents").apply { mkdirs() }
    private val prefs =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Pracovní sešit (bez denního číslování). */
    val workingFile: File = File(docsDir, WORKING_NAME)

    /** Naposledy připravený soubor pro sdílení (pojmenovaný YYMMDD_N_MD1.xlsx). */
    var lastExportFile: File? = null
        private set

    fun ensureReady() {
        // Smaž starý CSV z dřívějších verzí
        File(docsDir, "mereni.csv").takeIf { it.exists() }?.delete()
        if (!workingFile.exists()) {
            SimpleXlsx.write(workingFile, emptyList())
        }
    }

    fun count(): Int =
        SimpleXlsx.read(workingFile).count { !it.isStationHeader() && hasMeasurement(it) }

    /**
     * Přidá řádek měření. [stationName] se zapíše jako samostatný řádek,
     * pokud je to první záznam dané stanice od poslední změny.
     */
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
        val name = stationName.trim().ifBlank { stationUdu.trim() }
        val lastHeader = rows.lastOrNull { it.isStationHeader() }?.a?.trim()
        if (name.isNotEmpty() && lastHeader != name) {
            rows += SimpleXlsx.Row(a = name)
            prefs.edit().putString(KEY_LAST_STATION_UDU, stationUdu.trim()).apply()
        }
        rows += SimpleXlsx.Row(
            a = pole1.trim(),
            b = pole2.trim(),
            c = casMereni.trim(),
            d = poznamka.trim(),
        )
        SimpleXlsx.write(workingFile, rows)
    }

    /**
     * Popisky už uložené pro stanici ([stationName] musí sedět na řádek názvu ve sheetu).
     */
    fun usedLabelsForStation(stationName: String): Set<String> {
        val want = stationName.trim()
        if (want.isEmpty()) return emptySet()
        val rows = SimpleXlsx.read(workingFile)
        val out = linkedSetOf<String>()
        var active = false
        for (row in rows) {
            if (row.isStationHeader()) {
                active = row.a.trim() == want
                continue
            }
            if (!active) continue
            row.a.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { out.add(it) }
            row.b.split('-').map { it.trim() }.filter { it.isNotEmpty() }.forEach { out.add(it) }
        }
        return out
    }

    /** @deprecated použij [usedLabelsForStation] */
    fun usedLabelsForUdu(udu: String): Set<String> {
        // Zpětná kompatibilita volání — bez mapování jména vrací prázdno.
        // Volající by měl používat usedLabelsForStation.
        if (udu.isBlank()) return emptySet()
        return emptySet()
    }

    /**
     * Vytvoří pojmenovanou kopii `YYMMDD_N_MD1.xlsx` a vrátí ji (pro FileProvider / sdílení).
     * Číslo N = kolikátý export daný den.
     */
    fun prepareExportFile(): File {
        ensureReady()
        val day = DAY_FMT.format(Date())
        val n = prefs.getInt(KEY_DAY_COUNT_PREFIX + day, 0) + 1
        prefs.edit().putInt(KEY_DAY_COUNT_PREFIX + day, n).apply()
        val name = "${day}_${n}_MD1.xlsx"
        val dest = File(docsDir, name)
        workingFile.copyTo(dest, overwrite = true)
        lastExportFile = dest
        // Úklid starších exportů stejného dne necháme — uživatel může chtít lokální kopie
        return dest
    }

    fun currentFileLabel(): String =
        lastExportFile?.nameWithoutExtension
            ?: run {
                val day = DAY_FMT.format(Date())
                val n = prefs.getInt(KEY_DAY_COUNT_PREFIX + day, 0)
                if (n <= 0) "$day (zatím neuloženo)"
                else "${day}_${n}_MD1 (poslední export)"
            }

    private fun hasMeasurement(row: SimpleXlsx.Row): Boolean =
        row.a.isNotBlank() || row.b.isNotBlank() || row.c.isNotBlank() || row.d.isNotBlank()

    companion object {
        private const val PREFS = "measurement_xlsx"
        private const val WORKING_NAME = "mereni_working.xlsx"
        private const val KEY_DAY_COUNT_PREFIX = "export_count_"
        private const val KEY_LAST_STATION_UDU = "last_station_udu"
        private val DAY_FMT = SimpleDateFormat("yyMMdd", Locale.US)

        /** MIME pro sdílení Excelu. */
        const val MIME_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
