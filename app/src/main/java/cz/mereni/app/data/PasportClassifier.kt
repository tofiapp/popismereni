package cz.mereni.app.data

/**
 * Klasifikace a sestavení kláves z řádků tabulky DZS_SUPER_RO_TPI.
 *
 * Pravidla:
 * - Výhybka: POLOHA ∈ [VYHYBKA_POLOHY]; číslo v COBJEKT, někdy písmeno v IOB;
 *   záznamy s IOB se zobrazují samostatně.
 * - Spojka: prázdná POLOHA, COBJEKT_TPI = "zhl"; číslo v COBJEKT, IOB vždy X nebo S.
 * - Kolej: prázdná POLOHA i COBJEKT_TPI; číslo v COBJEKT, IOB nikdy X/S;
 *   prázdné IOB = hlavní volba, varianty s IOB až po rozkliknutí.
 */
object PasportClassifier {

    /** Hodnoty POLOHA = výhybka (dle DZS_SUPER_RO_TPI). */
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
    )

    fun classify(row: RawRow): PasportKind? {
        val poloha = row.poloha?.trim().orEmpty()
        val tpi = row.cobjektTpi?.trim().orEmpty()
        val iob = row.iob?.trim().orEmpty()
        val cobjekt = row.cobjekt?.trim().orEmpty()
        if (cobjekt.isEmpty()) return null

        return when {
            poloha.isNotEmpty() && poloha.uppercase() in VYHYBKA_POLOHY -> PasportKind.VYHYBKA
            poloha.isEmpty() &&
                tpi.equals("zhl", ignoreCase = true) &&
                iob.uppercase() in SPOJKA_IOB -> PasportKind.SPOJKA
            poloha.isEmpty() &&
                tpi.isEmpty() &&
                iob.uppercase() !in SPOJKA_IOB -> PasportKind.KOLEJ
            else -> null
        }
    }

    /**
     * Sestaví klávesy pro klávesnici.
     *
     * - výhybky: každá kombinace COBJEKT+IOB zvlášť
     * - spojky: každá zvlášť (vždy s X/S)
     * - koleje: seskupené podle COBJEKT; prázdné IOB = rodič, ostatní = children
     */
    fun buildKeys(rows: List<RawRow>): List<PasportKey> {
        val classified = rows.mapNotNull { row ->
            val kind = classify(row) ?: return@mapNotNull null
            PasportKey(
                kind = kind,
                cobjekt = row.cobjekt!!.trim(),
                iob = row.iob?.trim()?.takeIf { it.isNotEmpty() },
                poloha = row.poloha?.trim()?.takeIf { it.isNotEmpty() },
                cobjektTpi = row.cobjektTpi?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        val vyhybky = classified
            .filter { it.kind == PasportKind.VYHYBKA }
            .distinctBy { it.label }
            .sortedWith(compareBy({ naturalKey(it.cobjekt) }, { it.iob.orEmpty() }))

        val spojky = classified
            .filter { it.kind == PasportKind.SPOJKA }
            .distinctBy { it.label }
            .sortedWith(compareBy({ naturalKey(it.cobjekt) }, { it.iob.orEmpty() }))

        val koleje = classified
            .filter { it.kind == PasportKind.KOLEJ }
            .groupBy { it.cobjekt }
            .flatMap { (_, group) ->
                val unique = group.distinctBy { it.iob.orEmpty() }
                val main = unique.firstOrNull { it.iob.isNullOrEmpty() }
                val variants = unique
                    .filter { !it.iob.isNullOrEmpty() }
                    .sortedBy { it.iob }
                if (main != null) {
                    listOf(main.copy(children = variants))
                } else {
                    variants
                }
            }
            .sortedBy { naturalKey(it.cobjekt) }

        return spojky + koleje + vyhybky
    }

    /** Klávesnice pro obdélník 1 (ODKUD): spojky + koleje vedle sebe, jiné barvy. */
    fun keysForObdelnikJedna(all: List<PasportKey>): List<PasportKey> =
        all.filter { it.kind == PasportKind.SPOJKA || it.kind == PasportKind.KOLEJ }

    /** Klávesnice pro obdélník 2 (KAM): výhybky (jiná barva než spojky/koleje). */
    fun keysForObdelnikDva(all: List<PasportKey>): List<PasportKey> =
        all.filter { it.kind == PasportKind.VYHYBKA }

    private fun naturalKey(value: String): String {
        val match = Regex("""^(\d+)(.*)$""").matchEntire(value.trim())
        return if (match != null) {
            match.groupValues[1].padStart(8, '0') + match.groupValues[2]
        } else {
            value
        }
    }
}
