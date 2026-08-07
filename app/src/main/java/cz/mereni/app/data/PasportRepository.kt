package cz.mereni.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Načtený pasport TPI: klávesy + seznam UDU.
 */
data class PasportData(
    val version: String,
    val keys: List<PasportKey>,
    val uduList: List<String>,
)

/**
 * Načte klávesy z assets.
 *
 * Očekávaný soubor (s verzí v názvu): `pasport_tpi_v{VERSION}.json`
 * Fallback: `pasport_tpi.json`
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
        val rowsJson = root.optJSONArray("rows") ?: JSONArray()
        val rows = buildList {
            for (i in 0 until rowsJson.length()) {
                val o = rowsJson.getJSONObject(i)
                add(
                    PasportClassifier.RawRow(
                        cobjekt = o.optString("cobjekt").ifBlank { null },
                        iob = o.optString("iob").ifBlank { null },
                        poloha = o.optString("poloha").ifBlank { null },
                        cobjektTpi = o.optString("cobjekt_tpi").ifBlank {
                            o.optString("cobjektTpi").ifBlank { null }
                        },
                        udu = o.optString("udu").ifBlank { null },
                    )
                )
            }
        }

        val uduFromArray = buildList {
            val arr = root.optJSONArray("udu") ?: return@buildList
            for (i in 0 until arr.length()) {
                val v = arr.optString(i).trim()
                if (v.isNotEmpty()) add(v)
            }
        }
        val uduFromRows = rows.mapNotNull { row -> row.udu?.trim()?.takeIf { u -> u.isNotEmpty() } }
        val uduList = (uduFromArray + uduFromRows).distinct().sorted()

        return PasportData(
            version = version,
            keys = PasportClassifier.buildKeys(rows),
            uduList = uduList,
        )
    }

    fun exportToDocuments(context: Context, appVersion: String, json: String): File {
        val dir = File(context.getExternalFilesDir(null), "Documents").apply { mkdirs() }
        val file = File(dir, "pasport_tpi_v$appVersion.json")
        file.writeText(json)
        return file
    }
}
