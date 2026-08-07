package cz.mereni.app.data

/**
 * Klasifikace řádků DZS_SUPER_RO_TPI → výhybka / spojka / kolej.
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

    /** Prázdné / pomlčka / null-text → "" */
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

        val tpiIsZhl = tpi.equals("zhl", ignoreCase = true) ||
            tpi.contains("zhl", ignoreCase = true)

        return when {
            // Výhybka — jakákoli neprázdná POLOHA (JAP…CH i další kódy v datech)
            poloha.isNotEmpty() -> PasportKind.VYHYBKA
            // Spojka — prázdná POLOHA + COBJEKT_TPI zhl (IOB typicky X/S)
            tpiIsZhl -> PasportKind.SPOJKA
            // Kolej — prázdná POLOHA i TPI, IOB není X/S
            tpi.isEmpty() && iob !in SPOJKA_IOB -> PasportKind.KOLEJ
            else -> null
        }
    }

    /**
     * Sestaví klávesy. Předpokládá řádky **jedné** stanice (stejné UDU),
     * jinak by se stejné COBJEKT z různých UDU slily.
     */
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
                if (main != null) listOf(main.copy(children = variants)) else variants
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
