package cz.mereni.app.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

data class PasportLoadResult(
    val data: PasportData,
    val fromDeviceSqlite: Boolean,
    val sourceLabel: String,
    val error: String? = null,
    val stats: String = "",
)

/**
 * DZS_PASPORT_TPI.sqlite ze zařízení.
 * Stanice z MT_SL; klávesy per UDU přes cílené SQL (na IO vlákně).
 */
object PasportSqliteLoader {

    const val DB_FILE_NAME = "DZS_PASPORT_TPI.sqlite"
    private const val PREFS = "pasport_prefs"
    private const val KEY_URI = "pasport_uri"
    private const val LOCAL_COPY = "DZS_PASPORT_TPI.sqlite"
    private const val INDEXED_FLAG = "pasport_indexed_v1"

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

    fun ensureLocalDatabase(context: Context): File? {
        val local = File(context.filesDir, LOCAL_COPY)

        // 1) MediaStore / Downloads (Android 10+ často jediná cesta)
        findAndCopyFromMediaStore(context, local)?.let {
            ensureIndexes(context, it)
            return it
        }

        // 2) Přímé cesty (starší API / když je oprávnění)
        findExternalFile()?.let { found ->
            runCatching {
                if (!local.exists() || local.length() != found.length() ||
                    local.lastModified() < found.lastModified()
                ) {
                    found.copyTo(local, overwrite = true)
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().remove(INDEXED_FLAG).apply()
                }
                ensureIndexes(context, local)
                return local
            }
        }

        // 3) Uložené SAF URI
        savedUri(context)?.let { uri ->
            runCatching {
                copyUriToLocal(context, uri)
                ensureIndexes(context, local)
                return local
            }
        }

        if (local.exists() && local.length() > 0L) {
            ensureIndexes(context, local)
            return local
        }
        return null
    }

    private fun findExternalFile(): File? {
        val names = listOf(
            DB_FILE_NAME, "DZS_PASPORT_TPI.SQLite", "DZS_PASPORT_TPI.db",
            "dzs_pasport_tpi.sqlite", "DZS_PASPORT_TPI",
        )
        val dirs = buildList {
            @Suppress("DEPRECATION")
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            @Suppress("DEPRECATION")
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
            add(File("/storage/emulated/0/Download"))
            add(File("/storage/emulated/0/Downloads"))
            add(File("/sdcard/Download"))
        }
        for (dir in dirs) {
            if (dir == null || !dir.isDirectory) continue
            for (n in names) {
                val f = File(dir, n)
                if (f.isFile && f.canRead() && f.length() > 0L) return f
            }
            runCatching {
                dir.listFiles()?.firstOrNull { f ->
                    f.isFile && f.canRead() && f.length() > 0L &&
                        f.name.contains("DZS_PASPORT", ignoreCase = true)
                }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    /** Najde DB přes MediaStore a zkopíruje do [dest]. */
    private fun findAndCopyFromMediaStore(context: Context, dest: File): File? {
        val urisToTry = buildList {
            if (Build.VERSION.SDK_INT >= 29) {
                add(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL))
                runCatching {
                    add(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY))
                }
            }
            add(MediaStore.Files.getContentUri("external"))
        }

        for (collection in urisToTry) {
            val found = queryMediaStoreCopy(context, collection, dest)
            if (found != null) return found
        }
        return null
    }

    private fun queryMediaStoreCopy(context: Context, collection: Uri, dest: File): File? {
        return runCatching {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
            )
            val selection =
                "(${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?)"
            val args = arrayOf("%DZS_PASPORT%", "%pasport%tpi%")
            context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val iSize = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                while (c.moveToNext()) {
                    val name = if (iName >= 0) c.getString(iName).orEmpty() else ""
                    if (!name.contains("DZS_PASPORT", ignoreCase = true) &&
                        !name.contains("PASPORT_TPI", ignoreCase = true)
                    ) continue
                    val size = if (iSize >= 0 && !c.isNull(iSize)) c.getLong(iSize) else -1L
                    if (dest.exists() && size > 0 && dest.length() == size) {
                        return@use dest
                    }
                    val id = c.getLong(iId)
                    val itemUri = ContentUris.withAppendedId(collection, id)
                    context.contentResolver.openInputStream(itemUri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (dest.exists() && dest.length() > 0L) {
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().remove(INDEXED_FLAG).apply()
                        rememberUri(context, itemUri)
                        return@use dest
                    }
                }
                null
            }
        }.getOrNull()
    }

    /** Indexy na lokální kopii — výrazně zrychlí LIKE 'UDU%'. */
    private fun ensureIndexes(context: Context, file: File) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(INDEXED_FLAG, false)) return
        runCatching {
            SQLiteDatabase.openDatabase(
                file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE,
            ).use { db ->
                val schema = resolveSchema(db)
                db.execSQL(
                    """CREATE INDEX IF NOT EXISTS idx_ro_tudu ON "$TABLE_RO"("${schema.cTudu}")"""
                )
                db.execSQL(
                    """CREATE INDEX IF NOT EXISTS idx_sl_repre ON "$TABLE_SL"("${schema.cRepre}")"""
                )
            }
            prefs.edit().putBoolean(INDEXED_FLAG, true).apply()
        }
    }

    fun load(context: Context): PasportLoadResult {
        val file = ensureLocalDatabase(context)
            ?: return PasportLoadResult(
                data = PasportData("", emptyList(), emptyList()),
                fromDeviceSqlite = false,
                sourceLabel = "nenalezeno",
                error = "Soubor $DB_FILE_NAME v Download nebyl nalezen. Klepni Vybrat a zvol ho ručně (jednou).",
            )

        return runCatching {
            openDb(file).use { db ->
                val schema = resolveSchema(db)
                val stations = loadStations(db, schema)
                PasportLoadResult(
                    data = PasportData("device", emptyList(), stations),
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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(INDEXED_FLAG).apply()
        copyUriToLocal(context, uri)
        // OneDrive URI nelze spolehlivě persistovat — neukládat pro pozdější Obnovit
        if (SafUris.isOneDrive(uri)) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_URI).apply()
        } else {
            rememberUri(context, uri)
        }
        return load(context).let {
            if (it.fromDeviceSqlite) {
                it.copy(sourceLabel = displayName(context, uri) ?: it.sourceLabel)
            } else it
        }
    }

    fun reload(context: Context): PasportLoadResult {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(INDEXED_FLAG).apply()
        File(context.filesDir, LOCAL_COPY).delete()
        savedUri(context)?.let { uri ->
            runCatching { return loadFromUri(context, uri) }
        }
        return load(context)
    }

    /**
     * Klávesy pro UDU — 3 cílené SQL (výhybky / spojky / koleje), ne celá tabulka.
     * Volat z Dispatchers.IO.
     */
    fun loadKeysForUdu(context: Context, udu: String): List<PasportKey> {
        val code = normalizeUdu(udu)
        if (code.isEmpty()) return emptyList()
        val file = ensureLocalDatabase(context) ?: return emptyList()
        return runCatching {
            openDb(file).use { db ->
                val schema = resolveSchema(db)
                val like = "$code%"
                val vyhybky = queryKind(db, schema, like, KindFilter.VYHYBKA)
                val spojky = queryKind(db, schema, like, KindFilter.SPOJKA)
                val kolejeRows = queryKind(db, schema, like, KindFilter.KOLEJ)
                PasportClassifier.buildKeys(spojky + kolejeRows + vyhybky)
            }
        }.getOrDefault(emptyList())
    }

    private enum class KindFilter { VYHYBKA, SPOJKA, KOLEJ }

    private fun queryKind(
        db: SQLiteDatabase,
        schema: DbSchema,
        tuduLike: String,
        kind: KindFilter,
    ): List<PasportClassifier.RawRow> {
        val poloha = schema.cPoloha?.let { "\"$it\"" }
        val tpi = schema.cTpi?.let { "\"$it\"" }
        val iob = schema.cIob?.let { "\"$it\"" }
        val tudu = "\"${schema.cTudu}\""
        val cobjekt = "\"${schema.cCobjekt}\""

        fun emptyExpr(col: String?) =
            if (col == null) "1=1"
            else """(trim(coalesce(CAST($col AS TEXT),'')) IN ('','-','—','.','null','NULL'))"""

        fun nonEmptyExpr(col: String?) =
            if (col == null) "0=1"
            else """(trim(coalesce(CAST($col AS TEXT),'')) NOT IN ('','-','—','.','null','NULL'))"""

        val kindWhere = when (kind) {
            KindFilter.VYHYBKA -> nonEmptyExpr(poloha)
            // Spojka: prázdná POLOHA + IOB X/S (zhl nerozhoduje — může mít i kolej)
            KindFilter.SPOJKA ->
                "${emptyExpr(poloha)} AND " +
                    "upper(trim(coalesce(CAST(${iob ?: "''"} AS TEXT),''))) IN ('X','S')"
            // Kolej: prázdná POLOHA + IOB prázdné nebo jiné než X/S
            // coalesce('',…) — NULL IOB musí projít (dřív NOT IN na NULL je vyřadilo)
            KindFilter.KOLEJ ->
                "${emptyExpr(poloha)} AND " +
                    "upper(trim(coalesce(CAST(${iob ?: "''"} AS TEXT),''))) NOT IN ('X','S')"
        }

        val cols = listOfNotNull(schema.cCobjekt, schema.cIob, schema.cPoloha, schema.cTpi, schema.cTudu)
        val select = cols.joinToString(", ") { "\"$it\"" }

        // LIKE 'UDU%' umí využít index na TUDU
        val sql = """
            SELECT $select FROM "$TABLE_RO"
            WHERE CAST($tudu AS TEXT) LIKE ?
              AND $cobjekt IS NOT NULL
              AND trim(CAST($cobjekt AS TEXT)) != ''
              AND ($kindWhere)
            LIMIT 2000
        """.trimIndent()

        val rows = ArrayList<PasportClassifier.RawRow>(256)
        db.rawQuery(sql, arrayOf(tuduLike)).use { c ->
            while (c.moveToNext()) {
                rows += PasportClassifier.RawRow(
                    cobjekt = cell(c, schema.cCobjekt),
                    iob = schema.cIob?.let { cell(c, it) },
                    poloha = schema.cPoloha?.let { cell(c, it) },
                    cobjektTpi = schema.cTpi?.let { cell(c, it) },
                    udu = tuduLike.removeSuffix("%"),
                )
            }
        }
        return rows
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
            cCobjekt = pick(ro, "COBJEKT") ?: error("RO bez COBJEKT"),
            cIob = pick(ro, "IOB"),
            cPoloha = pick(ro, "POLOHA"),
            cTpi = pick(ro, "COBJEKT_TPI"),
            cTudu = pick(ro, "TUDU", "UDU") ?: error("RO bez TUDU"),
            cRepre = pick(sl, "REPRE_TUDU", "TUDU", "UDU") ?: error("SL bez REPRE_TUDU"),
            cJmeno = pick(sl, "JMENO", "NAZEV", "NAME") ?: error("SL bez JMENO"),
        )
    }

    /** Jen MT_SL — bez DISTINCT přes celou RO (to zamrzalo).
     * Stejné UDU → jeden záznam s nejběžnějším názvem (Nymburk, Český Těšín…);
     * podnázvy jdou do aliases pro vyhledávání. */
    private fun loadStations(db: SQLiteDatabase, schema: DbSchema): List<Station> {
        val namesByUdu = linkedMapOf<String, MutableList<Pair<String, String>>>()
        db.rawQuery(
            """SELECT "${schema.cRepre}", "${schema.cJmeno}" FROM "$TABLE_SL"""",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val udu = normalizeUdu(cell(c, schema.cRepre))
                val raw = cell(c, schema.cJmeno) ?: continue
                if (udu.isEmpty()) continue
                val jmeno = StationNameCleaner.clean(raw)
                if (jmeno.isEmpty()) continue
                namesByUdu.getOrPut(udu) { mutableListOf() }.add(jmeno to raw)
            }
        }
        return namesByUdu.map { (udu, pairs) ->
            val names = pairs.map { it.first }
            val preferred = StationNameCleaner.preferName(names)
            val raw = pairs.firstOrNull { it.first == preferred }?.second ?: preferred
            val aliases = names.distinct().filter { it != preferred }
            Station(udu = udu, jmeno = preferred, jmenoRaw = raw, aliases = aliases)
        }.sortedBy { it.jmeno.lowercase() }
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
            else -> c.getString(idx)?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun copyUriToLocal(context: Context, uri: Uri): File {
        val dest = File(context.filesDir, LOCAL_COPY)
        // Přečíst hned (dočasný grant) — bez takePersistable (OneDrive deny)
        val bytes = SafUris.readAllBytes(context.contentResolver, uri)
        require(bytes.isNotEmpty()) { "Zkopírovaný soubor je prázdný" }
        dest.writeBytes(bytes)
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
