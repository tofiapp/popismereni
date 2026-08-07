package cz.mereni.app.data

/**
 * Vybraná položka v horním poli — nese i [kind], aby chip měl barvu typu.
 * [custom] = ručně zadaný obdélníček (+), vlastní barva.
 * [fromSlot] = 0 stanice A, 1 stanice B (dual) — chip z B má okraj barvy 2. měření;
 *   zašednutí/zámek kláves platí jen v rámci stejného slotu.
 */
data class SelectedToken(
    val id: Long,
    val label: String,
    val kind: PasportKind,
    val custom: Boolean = false,
    val fromSlot: Int = 0,
)
