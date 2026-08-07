package cz.mereni.app.data

/**
 * Stanice z DZS_SUPER_MT_SL (JMENO), napojená přes REPRE_TUDU
 * na prvních 5 znaků TUDU z DZS_SUPER_RO_TPI.
 */
data class Station(
    /** UDU kód — prvních 5 znaků TUDU / REPRE_TUDU */
    val udu: String,
    /** Zobrazovaný název bez prefixů žst./odb./z. */
    val jmeno: String,
    /** Původní JMENO z pasportu (pro ladění). */
    val jmenoRaw: String = jmeno,
)

object StationNameCleaner {
    private val PREFIXES = listOf("žst.", "odb.", "z.")

    /** Odstraní úvodní „žst.“ / „odb.“ / „z.“ (case-insensitive). */
    fun clean(raw: String): String {
        var s = raw.trim()
        var changed = true
        while (changed) {
            changed = false
            val lower = s.lowercase()
            for (prefix in PREFIXES) {
                if (lower.startsWith(prefix)) {
                    s = s.substring(prefix.length).trimStart()
                    changed = true
                    break
                }
            }
        }
        return s
    }
}
