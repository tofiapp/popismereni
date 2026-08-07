package cz.mereni.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Načtený pasport TPI: klávesy + stanice (UDU ↔ JMENO).
 */
data class PasportData(
    val version: String,
    val keys: List<PasportKey>,
    val stations: List<Station>,
) {
    @Deprecated("Použij stations")
    val uduList: List<String> get() = stations.map { it.udu }
}

/**
 * Načte klávesy z assets.
 *
 * Očekávaný soubor (s verzí v názvu): `pasport_tpi_v{VERSION}.json`
 */
object PasportRepository {

    fun load(context: Context, appVersion: String): PasportData {
        val candidates = listOf(
            "pasport_tpi_v$appVersion.json",
            "pasport_tpi.json",
        )
        val jsonText = candidates.firstNotNullOfOrNull { name ->
            runCatching {
                context.assets.open(name).bufferedReader().use { reader -> reader.readText() }
            }.getOrNull()
        } ?: return PasportData(appVersion, emptyList(), emptyList())

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

        // Fallback: pokud stations chybí, sestav z unique UDU v řádcích
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

    fun exportToDocuments(context: Context, appVersion: String, json: String): File {
        val dir = File(context.getExternalFilesDir(null), "Documents").apply { mkdirs() }
        val file = File(dir, "pasport_tpi_v$appVersion.json")
        file.writeText(json)
        return file
    }
}
