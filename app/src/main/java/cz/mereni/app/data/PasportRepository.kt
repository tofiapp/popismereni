package cz.mereni.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pasport TPI: klávesy + stanice.
 * Primární zdroj je SQLite na zařízení ([PasportSqliteLoader]).
 * JSON v assets je jen nouzový fallback (bez zařízení / bez DB).
 */
data class PasportData(
    val version: String,
    val keys: List<PasportKey>,
    val stations: List<Station>,
)

object PasportRepository {

    /**
     * 1) DZS_PASPORT_TPI.sqlite na zařízení
     * 2) fallback assets JSON (demo)
     */
    fun load(context: Context, appVersion: String): PasportLoadResult {
        val device = PasportSqliteLoader.load(context)
        if (device.fromDeviceSqlite && device.data.stations.isNotEmpty()) {
            return device
        }
        if (device.fromDeviceSqlite && device.error == null) {
            return device
        }

        val assets = loadAssetsFallback(context, appVersion)
        if (assets != null) {
            return PasportLoadResult(
                data = assets,
                fromDeviceSqlite = false,
                sourceLabel = "assets (demo) — na zařízení chybí ${PasportSqliteLoader.DB_FILE_NAME}",
                error = device.error,
            )
        }

        return device
    }

    fun loadFromUri(context: Context, uri: android.net.Uri): PasportLoadResult =
        PasportSqliteLoader.loadFromUri(context, uri)

    fun reload(context: Context, appVersion: String): PasportLoadResult {
        val device = PasportSqliteLoader.reload(context)
        if (device.fromDeviceSqlite) return device
        return load(context, appVersion)
    }

    private fun loadAssetsFallback(context: Context, appVersion: String): PasportData? {
        val candidates = listOf(
            "pasport_tpi_v$appVersion.json",
            "pasport_tpi_v0.4.0.json",
            "pasport_tpi_v0.3.0.json",
            "pasport_tpi.json",
        )
        val jsonText = candidates.firstNotNullOfOrNull { name ->
            runCatching {
                context.assets.open(name).bufferedReader().use { reader -> reader.readText() }
            }.getOrNull()
        } ?: return null
        return parse(jsonText, appVersion)
    }

    fun parse(jsonText: String, fallbackVersion: String = ""): PasportData {
        val root = JSONObject(jsonText)
        val version = root.optString("version").ifBlank { fallbackVersion }

        val stations = buildList {
            val arr = root.optJSONArray("stations")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val udu = o.optString("udu").trim()
                    val raw = o.optString("jmeno_raw").ifBlank { o.optString("jmeno") }.trim()
                    val jmeno = o.optString("jmeno").ifBlank {
                        StationNameCleaner.clean(raw)
                    }.trim()
                    if (udu.isNotEmpty() && jmeno.isNotEmpty()) {
                        add(Station(udu = udu, jmeno = StationNameCleaner.clean(jmeno), jmenoRaw = raw))
                    }
                }
            }
        }.distinctBy { it.udu }.sortedBy { it.jmeno.lowercase() }

        val rowsJson = root.optJSONArray("rows") ?: JSONArray()
        val rows = buildList {
            for (i in 0 until rowsJson.length()) {
                val o = rowsJson.getJSONObject(i)
                val tudu = o.optString("tudu").ifBlank { o.optString("udu") }
                val udu = tudu.trim().take(5).ifBlank { null }
                add(
                    PasportClassifier.RawRow(
                        cobjekt = o.optString("cobjekt").ifBlank { null },
                        iob = o.optString("iob").ifBlank { null },
                        poloha = o.optString("poloha").ifBlank { null },
                        cobjektTpi = o.optString("cobjekt_tpi").ifBlank {
                            o.optString("cobjektTpi").ifBlank { null }
                        },
                        udu = udu,
                    )
                )
            }
        }

        val finalStations = stations.ifEmpty {
            rows.mapNotNull { it.udu?.trim()?.takeIf { u -> u.isNotEmpty() } }
                .distinct()
                .sorted()
                .map { Station(udu = it, jmeno = it) }
        }

        return PasportData(
            version = version,
            keys = PasportClassifier.buildKeys(rows),
            stations = finalStations,
        )
    }
}
