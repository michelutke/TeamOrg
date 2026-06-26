package ch.teamorg.infra.nds

import java.time.ZoneId

/**
 * J+S / NDS domain rules: activity-type symbol mapping and the allowed DAUER value sets.
 * See docs/nds-import-export-design.md §0. Symbol legend assumed standard (open question);
 * verified for `T` (Training) against a real export.
 */
object NdsRules {
    /** NDS courses are Swiss; all dates/times are local. */
    val ZONE: ZoneId = ZoneId.of("Europe/Zurich")

    // NDS AKTIVITAETSTYP strings (exact spelling required by the import).
    const val TYP_TRAINING = "Training"
    const val TYP_WETTKAMPF = "Wettkampf"
    const val TYP_TRAININGSTAG = "Trainingstag"
    const val TYP_LAGERTAG = "Lagertag"

    /** Anwesenheitsliste 'Symbol der Tagesaktivität' → NDS AKTIVITAETSTYP. */
    fun symbolToAktivitaetstyp(symbol: String): String = when (symbol.trim().uppercase()) {
        "T" -> TYP_TRAINING
        "W" -> TYP_WETTKAMPF
        "TT" -> TYP_TRAININGSTAG
        "L", "LT" -> TYP_LAGERTAG
        else -> TYP_TRAINING // unknown symbols default to Training (most common); flagged on import
    }

    /** NDS AKTIVITAETSTYP → TeamOrg EventType name (events.type). */
    fun aktivitaetstypToEventType(typ: String): String = when (typ) {
        TYP_TRAINING -> "training"
        TYP_WETTKAMPF -> "match"
        else -> "other" // Trainingstag / Lagertag have no dedicated EventType
    }

    /**
     * TeamOrg EventType → NDS AKTIVITAETSTYP, used on export when an event has no stored
     * nds_symbol. 'other' has no clean target → Trainingstag (closest J+S concept).
     */
    fun eventTypeToAktivitaetstyp(eventType: String): String = when (eventType) {
        "training" -> TYP_TRAINING
        "match" -> TYP_WETTKAMPF
        else -> TYP_TRAININGSTAG
    }

    /** ZEIT and ORT are required for Training and forbidden for all other types. */
    fun requiresTimeAndLocation(typ: String): Boolean = typ == TYP_TRAINING

    /**
     * Allowed DAUER (minutes) for a given Nutzergruppe + activity type. Returns null when the NG
     * is unknown/unset → caller skips duration validation (and warns). Uses the broader (Abs. 2)
     * set where the J+S article split would otherwise narrow it, to avoid false rejections.
     */
    fun allowedDurations(nutzergruppe: String?, typ: String): Set<Int>? {
        val ng = nutzergruppe?.trim()?.removePrefix("NG")?.trim()?.toIntOrNull() ?: return null
        val broad = setOf(45, 60, 90, 120, 150, 180, 210, 240, 270, 300)
        val ng1Training = setOf(60, 75, 90)
        return when (typ) {
            TYP_TRAINING -> when (ng) {
                1 -> ng1Training
                2, 4 -> broad - 45 // 60..300
                5 -> broad
                else -> null
            }
            TYP_TRAININGSTAG -> setOf(240, 300)
            TYP_WETTKAMPF -> when (ng) {
                2 -> broad - 45
                else -> emptySet() // NG1/4/5: no Wettkämpfe / no duration
            }
            TYP_LAGERTAG -> emptySet() // no duration allowed in a Lager
            else -> null
        }
    }

    /** Snap an arbitrary duration to the nearest allowed value, or null if no allowed set. */
    fun snapDuration(nutzergruppe: String?, typ: String, minutes: Int): Int? {
        val allowed = allowedDurations(nutzergruppe, typ) ?: return minutes
        if (allowed.isEmpty()) return null
        return allowed.minByOrNull { kotlin.math.abs(it - minutes) }
    }
}
