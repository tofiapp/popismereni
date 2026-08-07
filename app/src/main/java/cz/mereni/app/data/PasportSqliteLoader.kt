package cz.mereni.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import java.io.File

/**
 * Výsledek načtení pasportu ze zařízení.
 */
data class PasportLoadResult(
    val data: PasportData,
    /** true = živá SQLite na zařízení */
    val fromDeviceSqlite: Boolean,
    val sourceLabel: String,
    val error: String? = null,
)

/**
 * Načítá DZS_PASPORT_TPI.sqlite **ze zařízení** (ne z assets).
 *
 * Hledá soubor v běžných umístěních (Download, Documents, files aplikace)
 * a umí otevřít URI vybrané přes systémový picker (SAF).
 */
object PasportSqliteLoader {

    const val DB_FILE_NAME = "DZS_PASPORT_TPI.sqlite"
    private const val PREFS = "pasport_prefs"
    private const val KEY_URI = "pasport_uri"
    private const val LOCAL_COPY = "DZS_PASPORT_TPI.sqlite"

    private const val TABLE_RO = "DZS_SUPER_RO_TPI"
    private const val TABLE_SL = "DZS_SUPER_MT_SL"

    fun savedUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URI, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    fun rememberUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URI, uri.toString())
            .apply()
    }

    fun clearSavedUri(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_URI)
            .apply()
    }

    /**
     * Pořadí: uložené URI → lokální kopie → Download/Documents → null.
     */
    fun findDatabaseFile(context: Context): File? {
        val local = File(context.filesDir, LOCAL_COPY)
        if (local.exists() && local.length() > 0L) return local

        val candidates = buildList {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
                add(File(it, DB_FILE_NAME))
            }
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let {
                add(File(it, DB_FILE_NAME))
            }
            context.getExternalFilesDir(null)?.let {
                add(File(it, DB_FILE_NAME))
                add(File(it, "Documents/$DB_FILE_NAME"))
            }
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let {
                add(File(it, DB_FILE_NAME))
            }
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let {
                add(File(it, DB_FILE_NAME))
            }
            add(File("/sdcard/Download/$DB_FILE_NAME"))
            add(File("/storage/emulated/0/Download/$DB_FILE_NAME"))
            add(File("/storage/emulated/0/Documents/$DB_FILE_NAME"))
        }
        return candidates.firstOrNull { it.exists() && it.canRead() && it.length() > 0L }
    }

    fun load(context: Context): PasportLoadResult {
        // 1) Uložené URI (uživatel vybral soubor)
        savedUri(context)?.let { uri ->
            runCatching { loadFromUri(context, uri) }.getOrNull()?.let { return it }
        }

        // 2) Soubor na disku
        findDatabaseFile(context)?.let { file ->
            return runCatching {
                val data = readDatabase(file)
                PasportLoadResult(
                    data = data,
                    fromDeviceSqlite = true,
                    sourceLabel = file.absolutePath,
                )
            }.getOrElse { e ->
                PasportLoadResult(
                    data = PasportData("", emptyList(), emptyList()),
                    fromDeviceSqlite = false,
                    sourceLabel = file.absolutePath,
                    error = e.message ?: "Nepodařilo se otevřít SQLite",
                )
            }
        }

        return PasportLoadResult(
            data = PasportData("", emptyList(), emptyList()),
            fromDeviceSqlite = false,
            sourceLabel = "nenalezeno",
            error = "Soubor $DB_FILE_NAME na zařízení nebyl nalezen. Dej ho do Download nebo vyber ručně.",
        )
    }

    fun loadFromUri(context: Context, uri: Uri): PasportLoadResult {
        val copy = copyUriToLocal(context, uri)
        val data = readDatabase(copy)
        rememberUri(context, uri)
        val label = displayName(context, uri) ?: uri.toString()
        return PasportLoadResult(
            data = data,
            fromDeviceSqlite = true,
            sourceLabel = label,
        )
    }

    fun reload(context: Context): PasportLoadResult {
        // Preferuj znovu zkopírovat z uloženého URI (aktualizace DB na zařízení)
        savedUri(context)?.let { uri ->
            runCatching { return loadFromUri(context, uri) }
        }
        // Jinak smaž lokální kopii a hledej znovu
        File(context.filesDir, LOCAL_COPY).delete()
        return load(context)
    }

    private fun copyUriToLocal(context: Context, uri: Uri): File {
        val dest = File(context.filesDir, LOCAL_COPY)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Nelze otevřít $uri")
        require(dest.length() > 0L) { "Zkopírovaný soubor je prázdný" }
        return dest
    }

    private fun displayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull()
    }

    fun readDatabase(file: File): PasportData {
        val db = SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        try {
            return readOpenDatabase(db)
        } finally {
            db.close()
        }
    }

    private fun readOpenDatabase(db: SQLiteDatabase): PasportData {
        require(tableExists(db, TABLE_RO)) { "Chybí tabulka $TABLE_RO" }
        require(tableExists(db, TABLE_SL)) { "Chybí tabulka $TABLE_SL" }

        val roCols = columnMap(db, TABLE_RO)
        val slCols = columnMap(db, TABLE_SL)

        fun pick(map: Map<String, String>, vararg names: String): String? {
            for (n in names) {
                map[n.uppercase()]?.let { return it }
            }
            return null
        }

        val cCobjekt = pick(roCols, "COBJEKT") ?: error("RO bez COBJEKT")
        val cIob = pick(roCols, "IOB")
        val cPoloha = pick(roCols, "POLOHA")
        val cTpi = pick(roCols, "COBJEKT_TPI")
        val cTudu = pick(roCols, "TUDU", "UDU") ?: error("RO bez TUDU")
        val cRepre = pick(slCols, "REPRE_TUDU", "TUDU", "UDU") ?: error("SL bez REPRE_TUDU")
        val cJmeno = pick(slCols, "JMENO", "NAZEV", "NAME") ?: error("SL bez JMENO")

        val stationsByUdu = linkedMapOf<String, Station>()
        db.rawQuery(
            """SELECT "$cRepre", "$cJmeno" FROM "$TABLE_SL"""",
            null,
        ).use { c ->
            val iRepre = c.getColumnIndex(cRepre)
            val iJmeno = c.getColumnIndex(cJmeno)
            while (c.moveToNext()) {
                val repre = c.getString(iRepre)?.trim().orEmpty()
                val raw = c.getString(iJmeno)?.trim().orEmpty()
                if (repre.isEmpty() || raw.isEmpty()) continue
                val udu = repre.take(5)
                val jmeno = StationNameCleaner.clean(raw)
                if (jmeno.isEmpty()) continue
                val prev = stationsByUdu[udu]
                if (prev == null || jmeno.length > prev.jmeno.length) {
                    stationsByUdu[udu] = Station(udu = udu, jmeno = jmeno, jmenoRaw = raw)
                }
            }
        }

        val rows = mutableListOf<PasportClassifier.RawRow>()
        val select = listOfNotNull(cCobjekt, cIob, cPoloha, cTpi, cTudu)
            .joinToString(", ") { "\"$it\"" }
        db.rawQuery("""SELECT $select FROM "$TABLE_RO"""", null).use { c ->
            fun col(name: String?): String? {
                if (name == null) return null
                val idx = c.getColumnIndex(name)
                if (idx < 0) return null
                return c.getString(idx)?.trim()?.takeIf { it.isNotEmpty() }
            }
            while (c.moveToNext()) {
                val tudu = col(cTudu)
                val udu = tudu?.take(5)
                rows += PasportClassifier.RawRow(
                    cobjekt = col(cCobjekt),
                    iob = col(cIob),
                    poloha = col(cPoloha),
                    cobjektTpi = col(cTpi),
                    udu = udu,
                )
            }
        }

        val usedUdu = rows.mapNotNull { it.udu }.toSet()
        val stations = usedUdu.mapNotNull { stationsByUdu[it] }
            .sortedBy { it.jmeno.lowercase() }
            .ifEmpty {
                // když join nic nedá, alespoň kódy (nemělo by nastat)
                usedUdu.sorted().map { Station(udu = it, jmeno = it) }
            }

        return PasportData(
            version = "device",
            keys = PasportClassifier.buildKeys(rows),
            stations = stations,
        )
    }

    private fun tableExists(db: SQLiteDatabase, name: String): Boolean {
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=? COLLATE NOCASE",
            arrayOf(name),
        ).use { return it.moveToFirst() }
    }

    private fun columnMap(db: SQLiteDatabase, table: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        db.rawQuery("PRAGMA table_info(\"$table\")", null).use { c ->
            val iName = c.getColumnIndex("name")
            while (c.moveToNext()) {
                val n = c.getString(iName)
                map[n.uppercase()] = n
            }
        }
        return map
    }
}
