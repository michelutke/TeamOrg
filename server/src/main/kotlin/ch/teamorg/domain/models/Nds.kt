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
    val members: List<ParsedMember> = emptyList(),
    // Populated on the PARSE RESPONSE when the Angebot is already linked to a team of the
    // same club, so the import dialog re-imports into that team instead of creating a new
    // one. Ignored on import (the request carries an explicit teamId instead).
    val linkedTeamId: String? = null,
    val linkedTeamName: String? = null
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

/** String-UUID mirror of [ch.teamorg.infra.nds.MemberSuggestion], for the wire. */
@Serializable
data class MemberSuggestionDto(
    val rowKey: String,
    val candidates: List<CandidateDto>,
    val preselectedUserId: String?,
    val alreadyLinkedUserId: String?
) {
    @Serializable
    data class CandidateDto(val userId: String, val displayName: String, val score: String, val birthdateMatch: Boolean)
}

/** A detected weekly-recurrence group (or single one-off) among the Anwesenheitsliste activities. */
@Serializable
data class NdsSeries(
    val seriesKey: String,
    val weekday: Int?,
    val symbol: String,
    val durationMin: Int?,
    val dates: List<@Serializable(with = LocalDateSerializer::class) LocalDate>,
    val count: Int
)

/** One date where a series collides with an existing non-cancelled TeamOrg event of the team. */
@Serializable
data class NdsConflictDate(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val existingEventId: String,
    val existingEventTitle: String,
    val existingEventStart: String,
    val rsvpCount: Int = 0
)

@Serializable
data class NdsConflictGroup(val seriesKey: String, val dates: List<NdsConflictDate>)

/** One member-row decision from the wizard's Mitglieder-Zuordnung step. */
@Serializable
data class NdsMapping(val rowKey: String, val action: String /* map|create|skip */, val userId: String? = null)

/** Wizard-set start/end/location for one series (or one-off) importing events. */
@Serializable
data class NdsSeriesTime(val seriesKey: String, val startTime: String /* HH:mm */, val endTime: String, val location: String? = null)

/** Per-date override of a conflict group's default keep decision. */
@Serializable
data class NdsConflictOverride(@Serializable(with = LocalDateSerializer::class) val date: LocalDate, val keep: String /* teamorg|nds */)

/** The wizard's resolution for one conflict group, with optional per-date overrides. */
@Serializable
data class NdsConflictResolution(
    val seriesKey: String,
    val keep: String /* teamorg|nds */,
    val overrides: List<NdsConflictOverride> = emptyList()
)

/** Response of `POST /clubs/{clubId}/nds/parse` — file subsets, match suggestions, series + conflicts. */
@Serializable
data class NdsParseResponse(
    val anwesenheitsliste: ParsedAnwesenheitsliste? = null,
    val persons: List<NdsMemberInput> = emptyList(),
    val memberSuggestions: List<MemberSuggestionDto> = emptyList(),
    val series: List<NdsSeries> = emptyList(),
    val conflicts: List<NdsConflictGroup> = emptyList(),
    val linkedTeamId: String? = null,
    val linkedTeamName: String? = null
)
