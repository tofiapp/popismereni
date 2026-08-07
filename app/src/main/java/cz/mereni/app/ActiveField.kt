package cz.mereni.app

/**
 * Aktivní vstupní pole na hlavní obrazovce.
 */
enum class ActiveField {
    CO_SE_MERI,
    ODKUD_KAM,
    CAS_MERENI,
}

/**
 * Která strana ODKUD–KAM se právě vyplňuje (obdélník 1 / 2).
 * Obdélník 1 → spojky + koleje; obdélník 2 → výhybky.
 */
enum class OdkudKamSide {
    ODKUD, // obdélník 1
    KAM,   // obdélník 2
}
