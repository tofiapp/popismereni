package cz.mereni.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Načte klávesy z assets.
 *
 * Očekávaný soubor (s verzí v názvu): `pasport_tpi_v{VERSION}.json`
 * Fallback: `pasport_tpi.json`
 *
 * Formát:
 * ```json
 * {
 *   "version": "0.1.0",
 *   "source": "DZS_PASPORT_TPI.sqlite / DZS_SUPER_RO_TPI",
 *   "rows": [
 *     {"cobjekt":"12","iob":"A","poloha":"JAP","cobjekt_tpi":""}
 *   ]
 * }
 * ```
 *
 * Až bude k dispozici DZS_PASPORT_TPI.sqlite, vygeneruj JSON skriptem
 * `tools/export_pasport_v0.1.0.py` (název vždy s verzí).
 */
object PasportRepository {

    fun load(context: Context, appVersion: String): List<PasportKey> {
        val candidates = listOf(
            "pasport_tpi_v$appVersion.json",
            "pasport_tpi.json",
        )
        val jsonText = candidates.firstNotNullOfOrNull { name ->
            runCatching {
                context.assets.open(name).bufferedReader().use { it.readText() }
            }.getOrNull()
        } ?: return emptyList()

        return parse(jsonText)
    }

    fun parse(jsonText: String): List<PasportKey> {
        val root = JSONObject(jsonText)
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
                    )
                )
            }
        }
        return PasportClassifier.buildKeys(rows)
    }

    /** Uloží exportovaný JSON do Documents (název vždy s verzí). */
    fun exportToDocuments(context: Context, appVersion: String, json: String): File {
        val dir = File(context.getExternalFilesDir(null), "Documents").apply { mkdirs() }
        val file = File(dir, "pasport_tpi_v$appVersion.json")
        file.writeText(json)
        return file
    }
}
