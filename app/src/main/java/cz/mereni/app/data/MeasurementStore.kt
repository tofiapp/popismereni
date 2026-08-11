package cz.mereni.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Excel úložiště měření (.xlsx), 4 sloupce.
 *
 * Export na OneDrive: `YYMMDD_N_MD1.xlsx` do složky
 * `Popis_měření_MD1/Dny/` (SAF CreateDocument / zapamatovaný tree URI).
 * Po ANO se lokál vymaže.
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

    fun count(): Int =
        SimpleXlsx.read(workingFile).count { it.role == SimpleXlsx.Role.DATA && hasMeasurement(it) }

    fun dayRecordNumber(): Int = count()

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
        prefs.edit()
            .putBoolean(KEY_SYNCED, false)
            .putBoolean(KEY_PENDING_CONFIRM, false)
            .apply()
    }

    fun usedLabelsForStation(stationName: String): Pair<Set<String>, Set<String>> {
        val want = stationName.trim()
        if (want.isEmpty()) return emptySet<String>() to emptySet()
        val pole1 = linkedSetOf<String>()
        val pole2 = linkedSetOf<String>()
        val rows = SimpleXlsx.read(workingFile)
        var active = false
        for (row in rows) {
            when (row.role) {
                SimpleXlsx.Role.STATION -> active = row.a.trim() == want
                SimpleXlsx.Role.DATA -> if (active) {
                    row.a.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { pole1.add(it) }
                    row.b.split(Regex("""\s*-\s*""")).map { it.trim() }
                        .filter { it.isNotEmpty() }.forEach { pole2.add(it) }
                }
                else -> Unit
            }
        }
        return pole1 to pole2
    }

    fun isSyncedToOneDrive(): Boolean = prefs.getBoolean(KEY_SYNCED, false)

    fun isPendingOneDriveConfirm(): Boolean = prefs.getBoolean(KEY_PENDING_CONFIRM, false)

    fun getDnyTreeUri(): Uri? =
        prefs.getString(KEY_DNY_TREE_URI, null)?.takeIf { it.isNotBlank() }?.let(Uri::parse)

    fun getDnyFolderLabel(): String =
        prefs.getString(KEY_DNY_FOLDER_LABEL, null)?.takeIf { it.isNotBlank() } ?: ""

    fun getLastSaveUri(): Uri? =
        prefs.getString(KEY_LAST_SAVE_URI, null)?.takeIf { it.isNotBlank() }?.let(Uri::parse)

    /** Po úspěšném CreateDocument — příště otevře picker blízko stejné složky. */
    fun rememberLastSaveUri(uri: Uri) {
        prefs.edit().putString(KEY_LAST_SAVE_URI, uri.toString()).apply()
    }

    /**
     * Zapamatuje tree URI složky Dny (po OpenDocumentTree).
     * @return false pokud persist grant selhal (typicky OneDrive)
     */
    fun setDnyTreeFolder(uri: Uri, displayName: String, persisted: Boolean): Boolean {
        prefs.edit()
            .putString(KEY_DNY_TREE_URI, uri.toString())
            .putString(KEY_DNY_FOLDER_LABEL, displayName.ifBlank { "Dny" })
            .putBoolean(KEY_DNY_TREE_PERSISTED, persisted)
            .apply()
        return persisted
    }

    fun clearDnyTreeFolder() {
        prefs.edit()
            .remove(KEY_DNY_TREE_URI)
            .remove(KEY_DNY_FOLDER_LABEL)
            .remove(KEY_DNY_TREE_PERSISTED)
            .apply()
    }

    fun hasDnyTreeFolder(): Boolean = getDnyTreeUri() != null

    /**
     * Připraví soubor k uložení: `YYMMDD_N_MD1.xlsx` —
     * N roste s každým Uložit na OneDrive ten den.
     */
    fun prepareExportFile(): File {
        ensureReady()
        val day = DAY_FILE_FMT.format(Date())
        val n = prefs.getInt(KEY_DAY_COUNT_PREFIX + day, 0) + 1
        prefs.edit().putInt(KEY_DAY_COUNT_PREFIX + day, n).apply()
        val name = "${day}_${n}_MD1.xlsx"
        val dest = File(docsDir, name)
        val rows = SimpleXlsx.read(workingFile).toMutableList()
        ensureDateRowIn(rows)
        SimpleXlsx.write(workingFile, rows)
        workingFile.copyTo(dest, overwrite = true)
        lastExportFile = dest
        prefs.edit()
            .putBoolean(KEY_PENDING_CONFIRM, true)
            .putBoolean(KEY_SYNCED, false)
            .apply()
        return dest
    }

    /** Zapíše připravený export do cílového SAF URI (CreateDocument výsledek). */
    fun writeExportToUri(uri: Uri, file: File = lastExportFile ?: error("Chybí export")) {
        val bytes = file.readBytes()
        SafUris.writeAllBytes(appContext.contentResolver, uri, bytes)
        rememberLastSaveUri(uri)
    }

    /**
     * Pokus o zápis přímo do zapamatované složky Dny (bez pickeru).
     * @return true při úspěchu
     */
    fun tryWriteExportToDnyFolder(file: File? = null): Boolean {
        val target = file ?: lastExportFile ?: return false
        val tree = getDnyTreeUri() ?: return false
        return try {
            val dir = DocumentFile.fromTreeUri(appContext, tree) ?: return false
            if (!dir.canWrite()) return false
            // Stejný název — přepsat pokud už existuje
            dir.findFile(target.name)?.delete()
            val created = dir.createFile(MIME_XLSX, target.name) ?: return false
            writeExportToUri(created.uri, target)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun cancelPendingOneDriveConfirm() {
        prefs.edit().putBoolean(KEY_PENDING_CONFIRM, false).apply()
    }

    /** ANO po uložení — smaže lokál (nová dávka). */
    fun confirmOneDriveSavedAndClear() {
        resetWorkingFile()
        prefs.edit()
            .putBoolean(KEY_SYNCED, true)
            .putBoolean(KEY_PENDING_CONFIRM, false)
            .remove(KEY_LAST_STATION_UDU)
            .apply()
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
        private const val KEY_LAST_STATION_UDU = "last_station_udu"
        private const val KEY_SYNCED = "synced_onedrive"
        private const val KEY_PENDING_CONFIRM = "pending_onedrive_confirm"
        private const val KEY_DAY_COUNT_PREFIX = "export_count_"
        private const val KEY_DNY_TREE_URI = "dny_tree_uri"
        private const val KEY_DNY_FOLDER_LABEL = "dny_folder_label"
        private const val KEY_DNY_TREE_PERSISTED = "dny_tree_persisted"
        private const val KEY_LAST_SAVE_URI = "last_save_uri"
        private val DAY_FILE_FMT = SimpleDateFormat("yyMMdd", Locale.US)
        private val DATE_DISPLAY_FMT = SimpleDateFormat("d.M.yyyy", Locale("cs", "CZ"))

        const val MIME_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        const val DNY_HINT_PATH = "Popis_měření_MD1 / Dny"
    }
}
