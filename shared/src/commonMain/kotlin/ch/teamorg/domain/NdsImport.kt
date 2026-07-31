package ch.teamorg.domain

import kotlinx.serialization.Serializable

@Serializable
data class ParsedActivity(
    val date: String,
    val weekday: String? = null,
    val kw: Int? = null,
    val symbol: String,
    val durationMin: Int? = null,
    val fokus: String? = null,
    val columnIndex: Int
)

@Serializable
data class ParsedMember(
    val funktion: String,
    val nummer: Int? = null,
    val lastName: String,
    val firstName: String,
    val birthDate: String? = null,
    val attendedDates: List<String> = emptyList()
)

@Serializable
data class ParsedAnwesenheitsliste(
    val angebotId: String,
    val kursName: String? = null,
    val hauptsportart: String? = null,
    val gruppengroesse: String? = null,
    val kursstatus: String? = null,
    val activities: List<ParsedActivity> = emptyList(),
    val members: List<ParsedMember> = emptyList(),
    val linkedTeamId: String? = null,
    val linkedTeamName: String? = null
)

@Serializable
data class NdsMemberInput(
    val lastName: String,
    val firstName: String,
    val birthDate: String? = null,
    val personNumber: String? = null,
    val funktion: String
)

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

@Serializable
data class NdsSeries(
    val seriesKey: String,
    val weekday: Int?,
    val symbol: String,
    val durationMin: Int?,
    val dates: List<String>,
    val count: Int
)

@Serializable
data class NdsConflictDate(
    val date: String,
    val existingEventId: String,
    val existingEventTitle: String,
    val existingEventStart: String,
    val rsvpCount: Int = 0
)

@Serializable
data class NdsConflictGroup(val seriesKey: String, val dates: List<NdsConflictDate>)

@Serializable
data class NdsMapping(val rowKey: String, val action: String, val userId: String? = null)

@Serializable
data class NdsSeriesTime(val seriesKey: String, val startTime: String, val endTime: String, val location: String? = null)

@Serializable
data class NdsConflictOverride(val date: String, val keep: String)

@Serializable
data class NdsConflictResolution(
    val seriesKey: String,
    val keep: String,
    val overrides: List<NdsConflictOverride> = emptyList()
)

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

@Serializable
data class NdsImportRequest(
    val teamId: String? = null,
    val createTeamName: String? = null,
    val nutzergruppe: String? = null,
    val parsed: ParsedAnwesenheitsliste? = null,
    val persons: List<NdsMemberInput> = emptyList(),
    val importEvents: Boolean = false,
    val attendanceMode: String = "discard",
    val mappings: List<NdsMapping> = emptyList(),
    val seriesTimes: List<NdsSeriesTime> = emptyList(),
    val conflictResolutions: List<NdsConflictResolution> = emptyList()
)

@Serializable
data class NdsImportResponse(
    val teamId: String,
    val membersImported: Int,
    val eventsCreated: Int,
    val attendanceImported: Int = 0
)
