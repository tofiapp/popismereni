package cz.mereni.app.data

import java.text.Normalizer

/**
 * Stanice z DZS_SUPER_MT_SL (JMENO), napojená přes REPRE_TUDU
 * na prvních 5 znaků TUDU z DZS_SUPER_RO_TPI.
 *
 * UDU = 5 míst (xxxxx), TUDU = 6 míst (xxxxxx).
 * Hlavní název UDU = JMENO u TUDU končícího na 1 (xxxxx1).
 */
data class Station(
    /** UDU kód — prvních 5 znaků TUDU / REPRE_TUDU */
    val udu: String,
    /** Zobrazovaný název — z TUDU xxxxx1 (jinak fallback). */
    val jmeno: String,
    /** Původní JMENO z pasportu (pro ladění). */
    val jmenoRaw: String = jmeno,
    /** Podnázvy se stejným UDU — pro vyhledávání. */
    val aliases: List<String> = emptyList(),
)

object StationNameCleaner {
    private val PREFIXES = listOf("žst.", "odb.", "z.")

    /** Záznam JMENO + plné TUDU/REPRE_TUDU pro výběr hlavního názvu. */
    data class NameCandidate(
        val jmeno: String,
        val raw: String,
        /** Plné TUDU / REPRE_TUDU (ideálně 6 znaků). */
        val tudu: String = "",
    )

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

    /** Pro vyhledávání — malá písmena bez diakritiky. */
    fun foldForSearch(value: String): String {
        val nfd = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{M}+"), "")
    }

    /**
     * Hlavní název pro UDU: vždy z TUDU končícího na „1“ (xxxxx1).
     * Když takový záznam chybí, fallback na [preferName].
     */
    fun pickPrimary(candidates: List<NameCandidate>): NameCandidate {
        require(candidates.isNotEmpty()) { "prázdný seznam názvů stanice" }
        val fromTudu1 = candidates.filter { tuduEndsWithOne(it.tudu) }
        if (fromTudu1.isNotEmpty()) {
            // více řádků s …1 → frekvenční preferName mezi nimi
            val preferred = preferName(fromTudu1.map { it.jmeno })
            return fromTudu1.firstOrNull { it.jmeno == preferred } ?: fromTudu1.first()
        }
        val preferred = preferName(candidates.map { it.jmeno })
        return candidates.firstOrNull { it.jmeno == preferred } ?: candidates.first()
    }

    /** TUDU xxxxx1 — alespoň 6 znaků a končí na „1“. */
    fun tuduEndsWithOne(tudu: String): Boolean {
        val t = tudu.trim()
        // UDU má 5 míst; TUDU 6 — 5místný kód končící 1 není xxxxx1
        return t.length >= 6 && t.last() == '1'
    }

    /**
     * Fallback: nejběžnější (hlavní) název — např. „Nymburk“, „Český Těšín“.
     * Preferuje vyšší frekvenci, pak „čistší“ kratší název bez podnázvových znaků.
     */
    fun preferName(names: Collection<String>): String {
        val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }
        require(cleaned.isNotEmpty()) { "prázdný seznam názvů stanice" }
        val counts = cleaned.groupingBy { it }.eachCount()
        return counts.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { subNamePenalty(it.key) }
                    .thenBy { it.key.length }
                    .thenBy { it.key.lowercase() },
            )
            .first()
            .key
    }

    /** Vyšší = méně vhodné jako hlavní název. */
    private fun subNamePenalty(name: String): Int {
        var p = 0
        val l = name.lowercase()
        if ('(' in name || ')' in name) p += 4
        if ('–' in name || '—' in name || name.contains(" - ")) p += 3
        if (l.contains("zast")) p += 3
        if (l.contains("nákl") || l.contains("nakl")) p += 3
        if (l.contains("odboč")) p += 2
        if (l.contains("vjezd") || l.contains("výhyb")) p += 2
        if (l.contains("hl.n") || l.contains("hlavní")) p += 1
        // více slov → spíš podnázev (ale „Český Těšín“ má 2 slova — penalizace mírná)
        val words = name.trim().split(Regex("\\s+")).size
        if (words >= 3) p += words - 2
        return p
    }
}
