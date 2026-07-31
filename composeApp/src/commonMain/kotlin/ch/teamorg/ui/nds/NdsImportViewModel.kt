package ch.teamorg.ui.nds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.teamorg.domain.MemberSuggestionDto
import ch.teamorg.domain.NdsConflictGroup
import ch.teamorg.domain.NdsConflictOverride
import ch.teamorg.domain.NdsConflictResolution
import ch.teamorg.domain.NdsImportRequest
import ch.teamorg.domain.NdsImportResponse
import ch.teamorg.domain.NdsMapping
import ch.teamorg.domain.NdsMemberInput
import ch.teamorg.domain.NdsParseResponse
import ch.teamorg.domain.NdsSeries
import ch.teamorg.domain.NdsSeriesTime
import ch.teamorg.domain.ParsedMember
import ch.teamorg.domain.TeamMember
import ch.teamorg.repository.NdsFilePart
import ch.teamorg.repository.NdsImportRepository
import ch.teamorg.repository.TeamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val NDS_SLOT_TEILNEHMENDE = "teilnehmende"
const val NDS_SLOT_LEITER = "leiter"
const val NDS_SLOT_ANWESENHEITSLISTE = "anwesenheitsliste"

data class PickedFile(val fileName: String, val bytes: ByteArray)

data class MappingChoice(val action: String, val userId: String? = null)

data class SeriesTimeInput(val startTime: String, val endTime: String, val location: String? = null)

data class ResolutionChoice(val keep: String = "teamorg", val overrides: Map<String, String> = emptyMap())

data class NdsImportState(
    val step: Int = 1,
    val files: Map<String, PickedFile> = emptyMap(),
    val parse: NdsParseResponse? = null,
    val mappings: Map<String, MappingChoice> = emptyMap(),
    val seriesTimes: Map<String, SeriesTimeInput> = emptyMap(),
    val resolutions: Map<String, ResolutionChoice> = emptyMap(),
    val roster: List<TeamMember> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val result: NdsImportResponse? = null
)

class NdsImportViewModel(
    private val ndsImportRepository: NdsImportRepository,
    private val teamRepository: TeamRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NdsImportState())
    val state = _state.asStateFlow()

    fun pickFile(slot: String, fileName: String, bytes: ByteArray) {
        _state.value = _state.value.copy(files = _state.value.files + (slot to PickedFile(fileName, bytes)))
    }

    fun removeFile(slot: String) {
        _state.value = _state.value.copy(files = _state.value.files - slot)
    }

    fun canProceedFromFiles(): Boolean = _state.value.files.isNotEmpty()

    fun parseFiles(clubId: String, teamId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val parts = _state.value.files.map { (slot, file) -> NdsFilePart(slot, file.fileName, file.bytes) }
            ndsImportRepository.parse(clubId, teamId, parts).fold(
                onSuccess = { response ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        parse = response,
                        mappings = defaultMappings(response.memberSuggestions),
                        resolutions = response.conflicts.associate { it.seriesKey to ResolutionChoice() },
                        seriesTimes = emptyMap(),
                        step = 2
                    )
                    loadRoster(teamId)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Import fehlgeschlagen.")
                }
            )
        }
    }

    /** Full team roster for the mapping dropdown's "Teammitglieder" group. Loaded silently —
     * on failure the dropdown falls back to the union of suggestion candidates only. */
    private fun loadRoster(teamId: String) {
        viewModelScope.launch {
            teamRepository.getTeamRoster(teamId).onSuccess { roster ->
                _state.value = _state.value.copy(roster = roster)
            }
        }
    }

    /** Dropdown candidate pool: full team roster union suggestion candidates, deduped by userId. */
    fun allCandidates(): List<MemberSuggestionDto.CandidateDto> =
        teamCandidates(_state.value.roster, _state.value.parse?.memberSuggestions ?: emptyList())

    fun setMapping(rowKey: String, choice: MappingChoice) {
        _state.value = _state.value.copy(mappings = _state.value.mappings + (rowKey to choice))
    }

    fun setSeriesTime(seriesKey: String, time: SeriesTimeInput) {
        _state.value = _state.value.copy(seriesTimes = _state.value.seriesTimes + (seriesKey to time))
    }

    fun setResolution(seriesKey: String, resolution: ResolutionChoice) {
        _state.value = _state.value.copy(resolutions = _state.value.resolutions + (seriesKey to resolution))
    }

    fun goBack() {
        val current = _state.value.step
        val parse = _state.value.parse
        val previous = if (current == 4 && parse?.anwesenheitsliste == null) 2 else current - 1
        _state.value = _state.value.copy(step = previous.coerceAtLeast(1))
    }

    /** Step 2 "Weiter" — skips the events step entirely when there is no Anwesenheitsliste. */
    fun proceedFromMapping() {
        val parse = _state.value.parse ?: return
        _state.value = _state.value.copy(step = if (parse.anwesenheitsliste == null) 4 else 3)
    }

    fun canProceedFromEvents(): Boolean {
        val parse = _state.value.parse ?: return false
        return step3Gate(parse.series, parse.conflicts, _state.value.seriesTimes, _state.value.resolutions)
    }

    fun proceedFromEvents() {
        if (!canProceedFromEvents()) return
        _state.value = _state.value.copy(step = 4)
    }

    fun mergedRows(): List<NdsMemberInput> {
        val parse = _state.value.parse ?: return emptyList()
        return mergeRows(parse.anwesenheitsliste?.members, parse.persons)
    }

    fun mappingCounts(): Triple<Int, Int, Int> {
        var mapped = 0
        var created = 0
        var skipped = 0
        _state.value.mappings.values.forEach {
            when (it.action) {
                "map" -> mapped++
                "create" -> created++
                else -> skipped++
            }
        }
        return Triple(mapped, created, skipped)
    }

    fun eventCounts(): Triple<Int, Int, Int> {
        val parse = _state.value.parse ?: return Triple(0, 0, 0)
        return conflictCounts(parse.series, parse.conflicts, _state.value.resolutions)
    }

    fun submitImport(clubId: String, teamId: String, attendanceMode: String) {
        val parse = _state.value.parse ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val request = assemblePayload(teamId, parse, _state.value.mappings, _state.value.seriesTimes, _state.value.resolutions, attendanceMode)
            ndsImportRepository.import(clubId, request).fold(
                onSuccess = { response ->
                    _state.value = _state.value.copy(isLoading = false, result = response)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Import fehlgeschlagen.")
                }
            )
        }
    }

    companion object {
        // ── rowKey — mirrors server NdsMemberMatcher.rowKey/normalize + admin nds-import-wizard.ts ──

        fun normalizeName(s: String): String =
            s.trim()
                .lowercase()
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace(Regex("[éèê]"), "e")
                .replace(Regex("[àâ]"), "a")
                .replace("ß", "ss")
                .replace(Regex("\\s+"), " ")

        fun rowKey(funktion: String, lastName: String, firstName: String): String {
            val prefix = if (funktion.contains("leiter", ignoreCase = true)) "L:" else "T:"
            return "$prefix${normalizeName(lastName)}|${normalizeName(firstName)}"
        }

        /** Union of the Anwesenheitsliste roster and the dedicated person exports, deduped by rowKey. */
        fun mergeRows(awlMembers: List<ParsedMember>?, persons: List<NdsMemberInput>): List<NdsMemberInput> {
            val merged = LinkedHashMap<String, NdsMemberInput>()
            persons.forEach { p -> merged[rowKey(p.funktion, p.lastName, p.firstName)] = p }
            (awlMembers ?: emptyList()).forEach { m ->
                val input = NdsMemberInput(
                    lastName = m.lastName,
                    firstName = m.firstName,
                    birthDate = m.birthDate,
                    personNumber = null,
                    funktion = m.funktion
                )
                val key = rowKey(input.funktion, input.lastName, input.firstName)
                val existing = merged[key]
                merged[key] = if (existing != null) {
                    input.copy(
                        personNumber = existing.personNumber ?: input.personNumber,
                        birthDate = input.birthDate ?: existing.birthDate
                    )
                } else {
                    input
                }
            }
            return merged.values.toList()
        }

        /** Union of the full team roster and every suggestion's candidates, deduped by userId —
         * the roster wins on `distinctBy` order so a member without any suggestion overlap is
         * still a selectable manual map target. */
        fun teamCandidates(
            roster: List<TeamMember>,
            suggestions: List<MemberSuggestionDto>
        ): List<MemberSuggestionDto.CandidateDto> {
            val fromRoster = roster.map { MemberSuggestionDto.CandidateDto(it.userId, it.displayName, "", false) }
            val fromSuggestions = suggestions.flatMap { it.candidates }
            return (fromRoster + fromSuggestions).distinctBy { it.userId }
        }

        /** Default per-row mapping decision: a unique preselected candidate maps, otherwise create. */
        fun defaultMappings(suggestions: List<MemberSuggestionDto>): Map<String, MappingChoice> {
            val result = LinkedHashMap<String, MappingChoice>()
            suggestions.forEach { s ->
                if (s.alreadyLinkedUserId != null) return@forEach
                result[s.rowKey] = if (s.preselectedUserId != null) {
                    MappingChoice(action = "map", userId = s.preselectedUserId)
                } else {
                    MappingChoice(action = "create")
                }
            }
            return result
        }

        private val TIME_PATTERN = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

        fun isValidTimeRange(startTime: String, endTime: String): Boolean {
            if (!TIME_PATTERN.matches(startTime) || !TIME_PATTERN.matches(endTime)) return false
            return endTime > startTime
        }

        fun effectiveKeep(
            conflictGroup: NdsConflictGroup?,
            resolution: ResolutionChoice?,
            date: String
        ): String {
            val isConflictDate = conflictGroup?.dates?.any { it.date == date } ?: false
            if (!isConflictDate) return "nds"
            val groupKeep = resolution?.keep ?: "teamorg"
            return resolution?.overrides?.get(date) ?: groupKeep
        }

        fun seriesImportsAnyEvent(
            series: NdsSeries,
            conflicts: List<NdsConflictGroup>,
            resolutions: Map<String, ResolutionChoice>
        ): Boolean {
            val conflictGroup = conflicts.find { it.seriesKey == series.seriesKey }
            val resolution = resolutions[series.seriesKey]
            return series.dates.any { effectiveKeep(conflictGroup, resolution, it) != "teamorg" }
        }

        /** Gates the "Weiter" button: every series that imports ≥1 event needs a valid seriesTime. */
        fun step3Gate(
            series: List<NdsSeries>,
            conflicts: List<NdsConflictGroup>,
            times: Map<String, SeriesTimeInput>,
            resolutions: Map<String, ResolutionChoice>
        ): Boolean {
            series.forEach { s ->
                if (!seriesImportsAnyEvent(s, conflicts, resolutions)) return@forEach
                val t = times[s.seriesKey]
                if (t == null || !isValidTimeRange(t.startTime, t.endTime)) return false
            }
            return true
        }

        /** Bestätigen-step summary counts. */
        fun conflictCounts(
            series: List<NdsSeries>,
            conflicts: List<NdsConflictGroup>,
            resolutions: Map<String, ResolutionChoice>
        ): Triple<Int, Int, Int> {
            var eventsNew = 0
            var keepTeamorg = 0
            var keepNds = 0
            series.forEach { s ->
                val conflictGroup = conflicts.find { it.seriesKey == s.seriesKey }
                val resolution = resolutions[s.seriesKey]
                s.dates.forEach { d ->
                    val isConflict = conflictGroup?.dates?.any { it.date == d } ?: false
                    when (effectiveKeep(conflictGroup, resolution, d)) {
                        "teamorg" -> keepTeamorg++
                        else -> {
                            eventsNew++
                            if (isConflict) keepNds++
                        }
                    }
                }
            }
            return Triple(eventsNew, keepTeamorg, keepNds)
        }

        fun assemblePayload(
            teamId: String,
            parse: NdsParseResponse,
            mappings: Map<String, MappingChoice>,
            seriesTimes: Map<String, SeriesTimeInput>,
            resolutions: Map<String, ResolutionChoice>,
            attendanceMode: String
        ): NdsImportRequest {
            return NdsImportRequest(
                teamId = teamId,
                parsed = parse.anwesenheitsliste,
                persons = parse.persons,
                importEvents = parse.anwesenheitsliste != null,
                attendanceMode = attendanceMode,
                mappings = mappings.map { (rowKey, choice) -> NdsMapping(rowKey, choice.action, choice.userId) },
                seriesTimes = seriesTimes.map { (seriesKey, t) ->
                    NdsSeriesTime(seriesKey, t.startTime, t.endTime, t.location?.trim()?.ifEmpty { null })
                },
                conflictResolutions = resolutions.map { (seriesKey, r) ->
                    NdsConflictResolution(
                        seriesKey,
                        r.keep,
                        r.overrides.map { (date, keep) -> NdsConflictOverride(date, keep) }
                    )
                }
            )
        }
    }
}
