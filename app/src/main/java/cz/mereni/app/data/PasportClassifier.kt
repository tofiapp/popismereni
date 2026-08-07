package cz.mereni.app.data

/**
 * Klasifikace DZS_SUPER_RO_TPI:
 * - Výhybka: neprázdná POLOHA
 * - Spojka: prázdná POLOHA + COBJEKT_TPI = zhl + IOB ∈ {X,S}
 * - Kolej: prázdná POLOHA + prázdné COBJEKT_TPI + IOB ∉ {X,S}
 */
object PasportClassifier {

    val VYHYBKA_POLOHY: Set<String> = setOf(
        "JAP", "JBP", "JAL", "JBL", "JCP", "JDP", "JCL", "JDL",
        "CA", "CB", "CC", "CD", "CE", "CF", "CG", "CH",
    )

    private val SPOJKA_IOB: Set<String> = setOf("X", "S")

    data class RawRow(
        val cobjekt: String?,
        val iob: String?,
        val poloha: String?,
        val cobjektTpi: String?,
        val udu: String? = null,
    )

    fun norm(value: String?): String {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) return ""
        if (v == "-" || v == "—" || v.equals("null", true) || v == ".") return ""
        return v
    }

    fun classify(row: RawRow): PasportKind? {
        val poloha = norm(row.poloha).uppercase()
        val tpi = norm(row.cobjektTpi)
        val iob = norm(row.iob).uppercase()
        val cobjekt = norm(row.cobjekt)
        if (cobjekt.isEmpty()) return null

        val tpiIsZhl = tpi.equals("zhl", ignoreCase = true)

        return when {
            poloha.isNotEmpty() -> PasportKind.VYHYBKA
            // Spojka: musí mít X nebo S (2A/2B sem nepatří)
            tpiIsZhl && iob in SPOJKA_IOB -> PasportKind.SPOJKA
            // Kolej: bez zhl, IOB není X/S (prázdné, A, B, …)
            !tpiIsZhl && iob !in SPOJKA_IOB -> PasportKind.KOLEJ
            else -> null
        }
    }

    fun buildKeys(rows: List<RawRow>): List<PasportKey> {
        val classified = rows.mapNotNull { row ->
            val kind = classify(row) ?: return@mapNotNull null
            PasportKey(
                kind = kind,
                cobjekt = norm(row.cobjekt),
                iob = norm(row.iob).takeIf { it.isNotEmpty() },
                poloha = norm(row.poloha).takeIf { it.isNotEmpty() },
                cobjektTpi = norm(row.cobjektTpi).takeIf { it.isNotEmpty() },
                udu = norm(row.udu).takeIf { it.isNotEmpty() },
            )
        }

        val vyhybky = classified
            .filter { it.kind == PasportKind.VYHYBKA }
            .distinctBy { it.label.uppercase() }
            .sortedWith(compareBy({ naturalKey(it.cobjekt) }, { it.iob.orEmpty() }))

        val spojky = classified
            .filter { it.kind == PasportKind.SPOJKA }
            .distinctBy { it.label.uppercase() }
            .sortedWith(compareBy({ naturalKey(it.cobjekt) }, { it.iob.orEmpty() }))

        val koleje = classified
            .filter { it.kind == PasportKind.KOLEJ }
            .groupBy { it.cobjekt }
            .flatMap { (_, group) ->
                val unique = group.distinctBy { it.iob.orEmpty().uppercase() }
                val main = unique.firstOrNull { it.iob.isNullOrEmpty() }
                val variants = unique
                    .filter { !it.iob.isNullOrEmpty() }
                    .sortedBy { it.iob }
                if (main != null) {
                    listOf(main.copy(children = variants))
                } else {
                    // jen varianty A/B… — syntetický hlavní bez IOB + children
                    val synthetic = unique.first().copy(
                        iob = null,
                        children = variants,
                    )
                    listOf(synthetic)
                }
            }
            .sortedBy { naturalKey(it.cobjekt) }

        return spojky + koleje + vyhybky
    }

    private fun naturalKey(value: String): String {
        val match = Regex("""^(\d+)(.*)$""").matchEntire(value.trim())
        return if (match != null) {
            match.groupValues[1].padStart(8, '0') + match.groupValues[2]
        } else value
    }
}
