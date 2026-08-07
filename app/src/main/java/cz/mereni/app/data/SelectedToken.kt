package cz.mereni.app.data

/**
 * Vybraná položka v horním poli — nese i [kind], aby chip měl barvu typu.
 */
data class SelectedToken(
    val id: Long,
    val label: String,
    val kind: PasportKind,
)
