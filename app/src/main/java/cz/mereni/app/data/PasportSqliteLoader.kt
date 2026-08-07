package cz.mereni.app.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

data class PasportLoadResult(
    val data: PasportData,
    val fromDeviceSqlite: Boolean,
    val sourceLabel: String,
    val error: String? = null,
    /** Počet stanic / kláves pro diagnostiku */
    val stats: String = "",
)

/**
 * Načítá DZS_PASPORT_TPI.sqlite ze zařízení.
 * Stanice načte najednou; klávesy vždy **až pro vybrané UDU** (rychlé a správné).
 */
object PasportSqliteLoader {

    const val DB_FILE_NAME = "DZS_PASPORT_TPI.sqlite"
    private const val PREFS = "pasport_prefs"
    private const val KEY_URI = "pasport_uri"
    private const val LOCAL_COPY = "DZS_PASPORT_TPI.sqlite"

    private const val TABLE_RO = "DZS_SUPER_RO_TPI"
    private const val TABLE_SL = "DZS_SUPER_MT_SL"

    data class DbSchema(
        val cCobjekt: String,
        val cIob: String?,
        val cPoloha: String?,
        val cTpi: String?,
        val cTudu: String,
        val cRepre: String,
        val cJmeno: String,
    )

    fun savedUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URI, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    fun rememberUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_URI, uri.toString()).apply()
    }

    fun normalizeUdu(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return ""
        return t.take(5)
    }

    /** Najde SQLite na zařízení a zkopíruje ji do filesDir (spolehlivé otevření). */
    fun ensureLocalDatabase(context: Context): File? {
        val local = File(context.filesDir, LOCAL_COPY)

        // 1) Veřejné / známé cesty + MediaStore (čerstvý soubor)
        findExternalDatabase(context)?.let { found ->
            runCatching {
                if (!local.exists() || local.length() != found.length() ||
                    local.lastModified() < found.lastModified()
                ) {
                    found.copyTo(local, overwrite = true)
                }
                return local
            }
        }

        // 2) Uložené URI (SAF)
        savedUri(context)?.let { uri ->
            runCatching {
                copyUriToLocal(context, uri)
                return local
            }
        }

        // 3) Už existující lokální kopie
        if (local.exists() && local.length() > 0L) return local

        return null
    }

    fun findExternalDatabase(context: Context): File? {
        val nameHints = listOf(
            DB_FILE_NAME,
            "DZS_PASPORT_TPI.SQLite",
            "DZS_PASPORT_TPI.db",
            "dzs_pasport_tpi.sqlite",
        )

        val dirs = buildList {
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { add(it) }
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let { add(it) }
            add(File("/storage/emulated/0/Download"))
            add(File("/storage/emulated/0/Downloads"))
            add(File("/storage/emulated/0/Documents"))
            add(File("/sdcard/Download"))
            add(File("/sdcard/Downloads"))
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { add(it) }
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { add(it) }
            context.getExternalFilesDir(null)?.let { add(it) }
        }

        for (dir in dirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            for (name in nameHints) {
                val f = File(dir, name)
                if (f.exists() && f.canRead() && f.length() > 0L) return f
            }
            // mělký průchod (max 2 úrovně) — soubor může ležet ve složce
            runCatching {
                dir.walkTopDown().maxDepth(2)
                    .firstOrNull { file ->
                        if (!file.isFile || !file.canRead() || file.length() <= 0L) return@firstOrNull false
                        val nameOk = file.name.contains("DZS_PASPORT", ignoreCase = true)
                        val extOk = file.extension.equals("sqlite", true) ||
                            file.extension.equals("db", true)
                        nameOk && extOk
                    }
            }.getOrNull()?.let { return it }
        }

        // MediaStore (Android 10+)
        findViaMediaStore(context)?.let { return it }

        return null
    }

    private fun findViaMediaStore(context: Context): File? {
        return runCatching {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
            )
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            context.contentResolver.query(
                uri, projection, selection, arrayOf("%DZS_PASPORT%"), null,
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val iData = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                while (c.moveToNext()) {
                    if (iData >= 0) {
                        val path = c.getString(iData)
                        if (!path.isNullOrBlank()) {
                            val f = File(path)
                            if (f.exists() && f.canRead() && f.length() > 0L) return@use f
                        }
                    }
                    val id = c.getLong(iId)
                    val contentUri = ContentUris.withAppendedId(uri, id)
                    val dest = File(context.cacheDir, "media_pasport.sqlite")
                    context.contentResolver.openInputStream(contentUri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (dest.exists() && dest.length() > 0L) return@use dest
                }
                null
            }
        }.getOrNull()
    }

    fun load(context: Context): PasportLoadResult {
        val file = ensureLocalDatabase(context)
            ?: return PasportLoadResult(
                data = PasportData("", emptyList(), emptyList()),
                fromDeviceSqlite = false,
                sourceLabel = "nenalezeno",
                error = "Soubor $DB_FILE_NAME na zařízení nebyl nalezen. Dej ho do Download.",
            )

        return runCatching {
            openDb(file).use { db ->
                val schema = resolveSchema(db)
                val stations = loadStations(db, schema)
                PasportLoadResult(
                    data = PasportData(
                        version = "device",
                        keys = emptyList(), // klávesy až po výběru stanice
                        stations = stations,
                    ),
                    fromDeviceSqlite = true,
                    sourceLabel = file.name,
                    stats = "${stations.size} stanic",
                )
            }
        }.getOrElse { e ->
            PasportLoadResult(
                data = PasportData("", emptyList(), emptyList()),
                fromDeviceSqlite = false,
                sourceLabel = file.absolutePath,
                error = e.message ?: "Nepodařilo se otevřít SQLite",
            )
        }
    }

    fun loadFromUri(context: Context, uri: Uri): PasportLoadResult {
        copyUriToLocal(context, uri)
        rememberUri(context, uri)
        return load(context).let {
            if (it.fromDeviceSqlite) {
                it.copy(sourceLabel = displayName(context, uri) ?: it.sourceLabel)
            } else it
        }
    }

    fun reload(context: Context): PasportLoadResult {
        File(context.filesDir, LOCAL_COPY).delete()
        // zkus znovu z URI / Download
        savedUri(context)?.let { uri ->
            runCatching { return loadFromUri(context, uri) }
        }
        return load(context)
    }

    /** Klávesy pro jednu stanici (UDU = 5 znaků). */
    fun loadKeysForUdu(context: Context, udu: String): List<PasportKey> {
        val code = normalizeUdu(udu)
        if (code.isEmpty()) return emptyList()
        val file = ensureLocalDatabase(context) ?: return emptyList()
        return runCatching {
            openDb(file).use { db ->
                val schema = resolveSchema(db)
                val rows = loadRowsForUdu(db, schema, code)
                PasportClassifier.buildKeys(rows)
            }
        }.getOrDefault(emptyList())
    }

    private fun openDb(file: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        )

    private fun resolveSchema(db: SQLiteDatabase): DbSchema {
        require(tableExists(db, TABLE_RO)) { "Chybí tabulka $TABLE_RO" }
        require(tableExists(db, TABLE_SL)) { "Chybí tabulka $TABLE_SL" }
        val ro = columnMap(db, TABLE_RO)
        val sl = columnMap(db, TABLE_SL)
        fun pick(map: Map<String, String>, vararg names: String): String? {
            for (n in names) map[n.uppercase()]?.let { return it }
            return null
        }
        return DbSchema(
            cCobjekt = pick(ro, "COBJEKT") ?: error("RO bez COBJEKT (sloupce: ${ro.keys})"),
            cIob = pick(ro, "IOB"),
            cPoloha = pick(ro, "POLOHA"),
            cTpi = pick(ro, "COBJEKT_TPI"),
            cTudu = pick(ro, "TUDU", "UDU") ?: error("RO bez TUDU"),
            cRepre = pick(sl, "REPRE_TUDU", "TUDU", "UDU") ?: error("SL bez REPRE_TUDU"),
            cJmeno = pick(sl, "JMENO", "NAZEV", "NAME") ?: error("SL bez JMENO"),
        )
    }

    private fun loadStations(db: SQLiteDatabase, schema: DbSchema): List<Station> {
        val byUdu = linkedMapOf<String, Station>()
        db.rawQuery(
            """SELECT "${schema.cRepre}", "${schema.cJmeno}" FROM "$TABLE_SL"""",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val repre = cell(c, schema.cRepre)
                val raw = cell(c, schema.cJmeno)
                val udu = normalizeUdu(repre)
                if (udu.isEmpty() || raw.isNullOrBlank()) continue
                val jmeno = StationNameCleaner.clean(raw)
                if (jmeno.isEmpty()) continue
                val prev = byUdu[udu]
                if (prev == null || jmeno.length > prev.jmeno.length) {
                    byUdu[udu] = Station(udu = udu, jmeno = jmeno, jmenoRaw = raw)
                }
            }
        }
        // Doplň UDU, které jsou v RO, ale chybí v SL
        db.rawQuery("""SELECT DISTINCT "${schema.cTudu}" FROM "$TABLE_RO"""", null).use { c ->
            while (c.moveToNext()) {
                val udu = normalizeUdu(cell(c, schema.cTudu))
                if (udu.isNotEmpty() && udu !in byUdu) {
                    byUdu[udu] = Station(udu = udu, jmeno = udu)
                }
            }
        }
        return byUdu.values.sortedBy { it.jmeno.lowercase() }
    }

    private fun loadRowsForUdu(
        db: SQLiteDatabase,
        schema: DbSchema,
        udu: String,
    ): List<PasportClassifier.RawRow> {
        val cols = listOfNotNull(
            schema.cCobjekt, schema.cIob, schema.cPoloha, schema.cTpi, schema.cTudu,
        )
        val select = cols.joinToString(", ") { "\"$it\"" }
        // TUDU může být delší — bereme prvních 5 znaků; zkus i přesnou shodu
        val sql = """
            SELECT $select FROM "$TABLE_RO"
            WHERE substr(trim(CAST("${schema.cTudu}" AS TEXT)), 1, 5) = ?
               OR trim(CAST("${schema.cTudu}" AS TEXT)) = ?
        """.trimIndent()

        val rows = mutableListOf<PasportClassifier.RawRow>()
        db.rawQuery(sql, arrayOf(udu, udu)).use { c ->
            while (c.moveToNext()) {
                rows += PasportClassifier.RawRow(
                    cobjekt = cell(c, schema.cCobjekt),
                    iob = schema.cIob?.let { cell(c, it) },
                    poloha = schema.cPoloha?.let { cell(c, it) },
                    cobjektTpi = schema.cTpi?.let { cell(c, it) },
                    udu = udu,
                )
            }
        }
        return rows
    }

    private fun cell(c: Cursor, column: String): String? {
        val idx = c.getColumnIndex(column)
        if (idx < 0 || c.isNull(idx)) return null
        return when (c.getType(idx)) {
            Cursor.FIELD_TYPE_INTEGER -> c.getLong(idx).toString()
            Cursor.FIELD_TYPE_FLOAT -> {
                val d = c.getDouble(idx)
                if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
            }
            Cursor.FIELD_TYPE_STRING -> c.getString(idx)?.trim()?.takeIf { it.isNotEmpty() }
            Cursor.FIELD_TYPE_BLOB -> null
            else -> c.getString(idx)?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun copyUriToLocal(context: Context, uri: Uri): File {
        val dest = File(context.filesDir, LOCAL_COPY)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Nelze otevřít $uri")
        require(dest.length() > 0L) { "Zkopírovaný soubor je prázdný" }
        return dest
    }

    private fun displayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull()

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
