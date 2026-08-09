package cz.mereni.app.data

import java.text.Normalizer

/**
 * Stanice z DZS_SUPER_MT_SL (JMENO), napojená přes REPRE_TUDU
 * na prvních 5 znaků TUDU z DZS_SUPER_RO_TPI.
 */
data class Station(
    /** UDU kód — prvních 5 znaků TUDU / REPRE_TUDU */
    val udu: String,
    /** Zobrazovaný název — hlavní/nejběžnější pro dané UDU. */
    val jmeno: String,
    /** Původní JMENO z pasportu (pro ladění). */
    val jmenoRaw: String = jmeno,
    /** Podnázvy se stejným UDU — pro vyhledávání. */
    val aliases: List<String> = emptyList(),
)

object StationNameCleaner {
    /** Úvodní označení typu (žst., zast., …) — opakovaně. */
    private val PREFIX_REGEX = Regex(
        """^(?:žst\.?|zast\.?|nádr\.?|nádraží|stanice|výh\.?|výhybna|odbočka|odb\.?|hl\.?\s*n\.?|z\.)\s*""",
        RegexOption.IGNORE_CASE,
    )

    /** Odstraní úvodní žst./odb./zast./… (s tečkou i bez, s mezerou i bez). */
    fun clean(raw: String): String {
        var s = raw.trim().replace(Regex("""\s+"""), " ")
        if (s.isEmpty()) return s
        var prev: String
        do {
            prev = s
            s = s.replaceFirst(PREFIX_REGEX, "").trim(' ', '.', ',', '-')
        } while (s != prev && s.isNotEmpty())
        return s.ifEmpty { raw.trim() }
    }

    /** Pro vyhledávání — malá písmena bez diakritiky. */
    fun foldForSearch(value: String): String {
        val nfd = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{M}+"), "")
    }

    /**
     * Hlavní název pro UDU (Nymburk, Český Těšín).
     * Preferuje: být prefixem jiných variant → vyšší frekvence → méně „podnázvové“ → kratší.
     * Také bere v úvahu „základ“ odvozený z delších názvů (Nymburk z „Nymburk hl.n.“).
     */
    fun preferName(names: Collection<String>): String {
        val cleaned = names
            .map { clean(it.trim().replace(Regex("""\s+"""), " ")) }
            .filter { it.isNotEmpty() }
        require(cleaned.isNotEmpty()) { "prázdný seznam názvů stanice" }

        data class Agg(var display: String, var count: Int)
        val byLower = linkedMapOf<String, Agg>()
        for (n in cleaned) {
            val key = n.lowercase()
            val agg = byLower[key]
            if (agg == null) {
                byLower[key] = Agg(n, 1)
            } else {
                agg.count++
                if (n.length < agg.display.length) agg.display = n
            }
        }

        // Základy z delších názvů — „Nymburk“ z „Nymburk hl.n.“, i když samotný Nymburk v datech chybí
        for (n in cleaned) {
            for (base in derivedBases(n)) {
                val key = base.lowercase()
                if (key !in byLower) {
                    byLower[key] = Agg(base, 0)
                }
            }
        }

        val unique = byLower.values.toList()

        fun prefixHits(name: String): Int {
            val fold = foldForSearch(name)
            if (fold.length < 2) return 0
            return cleaned.count { other ->
                val of = foldForSearch(other)
                of != fold && of.startsWith(fold)
            }
        }

        return unique.maxWith(
            compareBy<Agg> { prefixHits(it.display) }
                .thenBy { it.count }
                .thenBy { -subNamePenalty(it.display) }
                .thenBy { -it.display.length }
                .thenBy { it.display.lowercase() },
        ).display
    }

    /** Kandidáti základního názvu z delšího (oddělovače, závorky, hl.n.). */
    private fun derivedBases(name: String): List<String> {
        val out = linkedSetOf<String>()
        val cutters = listOf(
            Regex("""\s*[\(\[\{].*"""),
            Regex("""\s+[–—\-].*"""),
            Regex("""\s+hl\.?\s*n\.?.*""", RegexOption.IGNORE_CASE),
            Regex("""\s+hlavn[ií].*""", RegexOption.IGNORE_CASE),
            Regex("""\s+zast\.?.*""", RegexOption.IGNORE_CASE),
            Regex("""\s+město\b.*""", RegexOption.IGNORE_CASE),
            Regex("""\s+mesto\b.*""", RegexOption.IGNORE_CASE),
            Regex("""\s+doln[ií].*""", RegexOption.IGNORE_CASE),
            Regex("""\s+horn[ií].*""", RegexOption.IGNORE_CASE),
            Regex("""\s+předm.*""", RegexOption.IGNORE_CASE),
            Regex("""\s+predm.*""", RegexOption.IGNORE_CASE),
        )
        for (r in cutters) {
            val t = name.replace(r, "").trim(' ', '.', ',', '-')
            if (t.length >= 2 && !t.equals(name, ignoreCase = true)) out.add(t)
        }
        return out.toList()
    }

    /** Vyšší = méně vhodné jako hlavní název. */
    private fun subNamePenalty(name: String): Int {
        var p = 0
        val l = name.lowercase()
        if ('(' in name || ')' in name) p += 5
        if ('–' in name || '—' in name || name.contains(" - ") || name.contains("-")) p += 4
        if (l.contains("zast")) p += 4
        if (l.contains("nákl") || l.contains("nakl")) p += 4
        if (l.contains("odboč") || l.contains("odb ")) p += 3
        if (l.contains("vjezd") || l.contains("výhyb")) p += 3
        if (l.contains("město") || l.contains("mesto")) p += 2
        if (l.contains("hl.n") || l.contains("hlavní") || l.contains("hlavni")) p += 2
        if (l.contains("dolní") || l.contains("horní") || l.contains("dolni") || l.contains("horni")) p += 1
        val words = name.trim().split(Regex("\\s+")).size
        if (words >= 3) p += (words - 2) * 2
        return p
    }
}
