package ch.teamorg.domain.models

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** One activity column from the NDS Anwesenheitsliste. */
@Serializable
data class ParsedActivity(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val weekday: String? = null,        // 'MO','MI',…
    val kw: Int? = null,                // Kalenderwoche
    val symbol: String,                 // 'T','W','TT','L'
    val durationMin: Int? = null,       // derived from 'Dauer der Tagesaktivität' (hours)
    val fokus: String? = null,
    val columnIndex: Int                // sheet column — used to read attendance cells
)

/** One person (leader or participant) with the dates they attended. */
@Serializable
data class ParsedMember(
    val funktion: String,               // 'Teilnehmer/in' | 'Leiter/in'
    val nummer: Int? = null,            // list position (NOT a national id)
    val lastName: String,
    val firstName: String,
    @Serializable(with = LocalDateSerializer::class) val birthDate: LocalDate? = null,
    val attendedDates: List<@Serializable(with = LocalDateSerializer::class) LocalDate> = emptyList()
)

/** Full parse of an Anwesenheitsliste xlsx. Returned to the web as an import preview. */
@Serializable
data class ParsedAnwesenheitsliste(
    val angebotId: String,
    val kursName: String? = null,
    val hauptsportart: String? = null,
    val gruppengroesse: String? = null,
    val kursstatus: String? = null,
    val activities: List<ParsedActivity> = emptyList(),
    val members: List<ParsedMember> = emptyList()
)

/**
 * A person parsed from a dedicated NDS person export (Teilnehmende CSV / Leiterinnen xlsx).
 * Carries the PERSONENNUMMER the Anwesenheitsliste lacks. Birthdate may be absent.
 */
@Serializable
data class NdsMemberInput(
    val lastName: String,
    val firstName: String,
    @Serializable(with = LocalDateSerializer::class) val birthDate: LocalDate? = null,
    val personNumber: String? = null,
    val funktion: String // 'Teilnehmer/in' | 'Leiter/in'
)

/** A roster member as stored, exposed to the web (claim status + NDS data). */
@Serializable
data class NdsMember(
    @Serializable(with = UUIDSerializer::class) val id: java.util.UUID,
    @Serializable(with = UUIDSerializer::class) val teamId: java.util.UUID,
    @Serializable(with = UUIDSerializer::class) val userId: java.util.UUID?,
    val lastName: String,
    val firstName: String,
    @Serializable(with = LocalDateSerializer::class) val birthDate: LocalDate?,
    val personNumber: String?,
    val funktion: String,
    val source: String,
    val claimed: Boolean
)
