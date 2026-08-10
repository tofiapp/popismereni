package cz.mereni.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PasportData(
    val version: String,
    val keys: List<PasportKey>,
    val stations: List<Station>,
)

object PasportRepository {

    fun load(context: Context, appVersion: String): PasportLoadResult {
        val device = PasportSqliteLoader.load(context)
        if (device.fromDeviceSqlite) return device

        val assets = loadAssetsFallback(context, appVersion)
        if (assets != null) {
            return PasportLoadResult(
                data = assets,
                fromDeviceSqlite = false,
                sourceLabel = "assets (demo)",
                error = device.error,
                stats = "${assets.stations.size} stanic (demo)",
            )
        }
        return device
    }

    fun loadFromUri(context: Context, uri: android.net.Uri): PasportLoadResult =
        PasportSqliteLoader.loadFromUri(context, uri)

    fun loadFromBytes(
        context: Context,
        bytes: ByteArray,
        sourceUri: android.net.Uri? = null,
    ): PasportLoadResult =
        PasportSqliteLoader.loadFromBytes(context, bytes, sourceUri)

    fun reload(context: Context, appVersion: String): PasportLoadResult {
        val device = PasportSqliteLoader.reload(context)
        if (device.fromDeviceSqlite) return device
        return load(context, appVersion)
    }

    /** Klávesy pro vybranou stanici — ze SQLite, nebo filtrované z demo assets. */
    fun keysForStation(context: Context, station: Station?, fallbackKeys: List<PasportKey>): List<PasportKey> {
        if (station == null) return emptyList()
        val fromDb = PasportSqliteLoader.loadKeysForUdu(context, station.udu)
        if (fromDb.isNotEmpty()) return fromDb
        // demo assets: filtr podle udu
        return fallbackKeys.filter { it.udu == null || it.udu == station.udu }
    }

    private fun loadAssetsFallback(context: Context, appVersion: String): PasportData? {
        val candidates = listOf(
            "pasport_tpi_v$appVersion.json",
            "pasport_tpi_v0.42.0.json",
            "pasport_tpi_v0.41.0.json",
            "pasport_tpi_v0.40.0.json",
            "pasport_tpi_v0.39.0.json",
            "pasport_tpi_v0.38.0.json",
            "pasport_tpi_v0.37.0.json",
            "pasport_tpi_v0.36.0.json",
            "pasport_tpi_v0.35.0.json",
            "pasport_tpi_v0.34.0.json",
            "pasport_tpi_v0.33.0.json",
            "pasport_tpi_v0.32.0.json",
            "pasport_tpi_v0.31.0.json",
            "pasport_tpi_v0.30.0.json",
            "pasport_tpi_v0.29.0.json",
            "pasport_tpi_v0.28.0.json",
            "pasport_tpi_v0.27.0.json",
            "pasport_tpi_v0.26.0.json",
            "pasport_tpi_v0.25.0.json",
            "pasport_tpi_v0.24.0.json",
            "pasport_tpi_v0.23.0.json",
            "pasport_tpi_v0.22.0.json",
            "pasport_tpi_v0.21.0.json",
            "pasport_tpi_v0.20.0.json",
            "pasport_tpi_v0.19.0.json",
            "pasport_tpi_v0.18.0.json",
            "pasport_tpi_v0.17.0.json",
            "pasport_tpi_v0.16.0.json",
            "pasport_tpi_v0.15.0.json",
            "pasport_tpi_v0.14.0.json",
            "pasport_tpi_v0.13.0.json",
            "pasport_tpi_v0.12.0.json",
            "pasport_tpi_v0.11.0.json",
            "pasport_tpi_v0.10.0.json",
            "pasport_tpi_v0.8.0.json",
            "pasport_tpi_v0.4.0.json",
            "pasport_tpi.json",
        )
        val jsonText = candidates.firstNotNullOfOrNull { name ->
            runCatching {
                context.assets.open(name).bufferedReader().use { it.readText() }
            }.getOrNull()
        } ?: return null
        return parse(jsonText, appVersion)
    }

    fun parse(jsonText: String, fallbackVersion: String = ""): PasportData {
        val root = JSONObject(jsonText)
        val version = root.optString("version").ifBlank { fallbackVersion }

        val stations = buildList {
            val arr = root.optJSONArray("stations") ?: return@buildList
            val byUdu = linkedMapOf<String, MutableList<StationNameCleaner.NameCandidate>>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val tuduFull = o.optString("tudu").ifBlank {
                    o.optString("repre_tudu").ifBlank { o.optString("udu") }
                }.trim()
                val udu = PasportSqliteLoader.normalizeUdu(
                    o.optString("udu").ifBlank { tuduFull },
                )
                val raw = o.optString("jmeno_raw").ifBlank { o.optString("jmeno") }.trim()
                val jmeno = StationNameCleaner.clean(
                    o.optString("jmeno").ifBlank { raw },
                )
                if (udu.isEmpty() || jmeno.isEmpty()) continue
                byUdu.getOrPut(udu) { mutableListOf() }.add(
                    StationNameCleaner.NameCandidate(
                        jmeno = jmeno,
                        raw = raw,
                        tudu = tuduFull,
                    ),
                )
            }
            for ((udu, candidates) in byUdu) {
                val primary = StationNameCleaner.pickPrimary(candidates)
                val aliases = candidates.map { it.jmeno }.distinct().filter { it != primary.jmeno }
                add(
                    Station(
                        udu = udu,
                        jmeno = primary.jmeno,
                        jmenoRaw = primary.raw,
                        aliases = aliases,
                    ),
                )
            }
        }.sortedBy { it.jmeno.lowercase() }

        val rowsJson = root.optJSONArray("rows") ?: JSONArray()
        val rows = buildList {
            for (i in 0 until rowsJson.length()) {
                val o = rowsJson.getJSONObject(i)
                val tudu = o.optString("tudu").ifBlank { o.optString("udu") }
                add(
                    PasportClassifier.RawRow(
                        cobjekt = o.optString("cobjekt").ifBlank { null },
                        iob = o.optString("iob").ifBlank { null },
                        poloha = o.optString("poloha").ifBlank { null },
                        cobjektTpi = o.optString("cobjekt_tpi").ifBlank {
                            o.optString("cobjektTpi").ifBlank { null }
                        },
                        udu = PasportSqliteLoader.normalizeUdu(tudu).ifBlank { null },
                    )
                )
            }
        }

        // Pro demo: sestav klávesy per UDU (neslévat stejné COBJEKT napříč stanicemi)
        val keys = rows.groupBy { it.udu.orEmpty() }
            .filterKeys { it.isNotEmpty() }
            .values
            .flatMap { PasportClassifier.buildKeys(it) }

        return PasportData(
            version = version,
            keys = keys,
            stations = stations.ifEmpty {
                rows.mapNotNull { it.udu }.distinct().sorted()
                    .map { Station(udu = it, jmeno = it) }
            },
        )
    }
}
