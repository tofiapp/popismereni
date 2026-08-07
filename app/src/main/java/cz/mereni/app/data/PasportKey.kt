package cz.mereni.app.data

/**
 * Typ objektu z pasportu TPI (DZS_SUPER_RO_TPI).
 * Barvy na klávesnici jsou různé podle typu.
 */
enum class PasportKind {
    /** Výhybka — POLOHA ∈ JAP,JBP,…,CH */
    VYHYBKA,

    /** Spojka — prázdná POLOHA, COBJEKT_TPI = zhl, IOB ∈ {X,S} */
    SPOJKA,

    /** Kolej — prázdná POLOHA i COBJEKT_TPI, IOB ∉ {X,S} */
    KOLEJ,
}

/**
 * Jedna klávesa / obdélníček na klávesnici.
 *
 * Popisek = COBJEKT + případné IOB (např. "12", "12A", "3X").
 * U kolejí může mít [children] varianty s vyplněným IOB;
 * záznam s prázdným IOB je hlavní volba (zobrazí se rovnou).
 */
data class PasportKey(
    val kind: PasportKind,
    val cobjekt: String,
    val iob: String? = null,
    val poloha: String? = null,
    val cobjektTpi: String? = null,
    val children: List<PasportKey> = emptyList(),
) {
    val label: String
        get() = buildString {
            append(cobjekt.trim())
            val letter = iob?.trim().orEmpty()
            if (letter.isNotEmpty()) append(letter)
        }

    val hasExpandableChildren: Boolean
        get() = children.isNotEmpty()
}
