package ch.teamorg.ui.nds

import ch.teamorg.domain.MemberSuggestionDto
import ch.teamorg.domain.NdsConflictDate
import ch.teamorg.domain.NdsConflictGroup
import ch.teamorg.domain.NdsMemberInput
import ch.teamorg.domain.NdsParseResponse
import ch.teamorg.domain.NdsSeries
import ch.teamorg.domain.ParsedAnwesenheitsliste
import ch.teamorg.domain.ParsedMember
import ch.teamorg.domain.TeamMember
import ch.teamorg.fake.FakeNdsImportRepository
import ch.teamorg.fake.FakeTeamRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NdsImportViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeRepo = FakeNdsImportRepository()
    private val fakeTeamRepo = FakeTeamRepository()
    private lateinit var viewModel: NdsImportViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo.reset()
        fakeTeamRepo.reset()
        viewModel = NdsImportViewModel(fakeRepo, fakeTeamRepo)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region — default mapping derivation

    @Test
    fun defaultMappings_preselected_mapsToUser() {
        val suggestions = listOf(
            MemberSuggestionDto(
                rowKey = "T:muster|hans",
                candidates = listOf(MemberSuggestionDto.CandidateDto("u1", "Hans Muster", "HIGH", false)),
                preselectedUserId = "u1",
                alreadyLinkedUserId = null
            )
        )
        val result = NdsImportViewModel.defaultMappings(suggestions)
        result["T:muster|hans"] shouldBe MappingChoice(action = "map", userId = "u1")
    }

    @Test
    fun defaultMappings_noPreselect_createsNewUser() {
        val suggestions = listOf(
            MemberSuggestionDto(rowKey = "T:x|y", candidates = emptyList(), preselectedUserId = null, alreadyLinkedUserId = null)
        )
        val result = NdsImportViewModel.defaultMappings(suggestions)
        result["T:x|y"] shouldBe MappingChoice(action = "create")
    }

    @Test
    fun defaultMappings_alreadyLinked_excludedFromMap() {
        val suggestions = listOf(
            MemberSuggestionDto(rowKey = "T:locked|row", candidates = emptyList(), preselectedUserId = null, alreadyLinkedUserId = "u9")
        )
        val result = NdsImportViewModel.defaultMappings(suggestions)
        result.containsKey("T:locked|row") shouldBe false
    }

    @Test
    fun parseFiles_success_derivesDefaultMappingsIntoState() = runTest(testDispatcher) {
        fakeRepo.parseResult = Result.success(
            NdsParseResponse(
                memberSuggestions = listOf(
                    MemberSuggestionDto(rowKey = "T:a|b", candidates = emptyList(), preselectedUserId = "u1", alreadyLinkedUserId = null)
                )
            )
        )
        viewModel.pickFile(NDS_SLOT_TEILNEHMENDE, "teilnehmende.csv", byteArrayOf(1))

        viewModel.parseFiles("club1", "team1")

        viewModel.state.value.mappings["T:a|b"] shouldBe MappingChoice(action = "map", userId = "u1")
        viewModel.state.value.step shouldBe 2
    }

    @Test
    fun teamCandidates_rosterMemberAbsentFromSuggestions_isSelectableAndProducesMapMapping() {
        val roster = listOf(
            TeamMember(userId = "u-suggested", displayName = "Suggested Player", avatarUrl = null, role = "player", jerseyNumber = null, position = null),
            TeamMember(userId = "u-roster-only", displayName = "Roster Only Player", avatarUrl = null, role = "player", jerseyNumber = null, position = null)
        )
        val suggestions = listOf(
            MemberSuggestionDto(
                rowKey = "T:a|b",
                candidates = listOf(MemberSuggestionDto.CandidateDto("u-suggested", "Suggested Player", "MEDIUM", false)),
                preselectedUserId = null,
                alreadyLinkedUserId = null
            )
        )

        val candidates = NdsImportViewModel.teamCandidates(roster, suggestions)

        candidates.map { it.userId } shouldBe listOf("u-suggested", "u-roster-only")

        // Selecting the roster-only candidate (never suggested for any row) still produces a valid mapping.
        viewModel.setMapping("T:a|b", MappingChoice(action = "map", userId = "u-roster-only"))
        viewModel.state.value.mappings["T:a|b"] shouldBe MappingChoice(action = "map", userId = "u-roster-only")
    }

    @Test
    fun parseFiles_success_loadsRosterForDropdown() = runTest(testDispatcher) {
        val roster = listOf(
            TeamMember(userId = "u1", displayName = "Hans Muster", avatarUrl = null, role = "player", jerseyNumber = null, position = null)
        )
        fakeTeamRepo.getRosterResult = Result.success(roster)
        fakeRepo.parseResult = Result.success(NdsParseResponse())
        viewModel.pickFile(NDS_SLOT_TEILNEHMENDE, "teilnehmende.csv", byteArrayOf(1))

        viewModel.parseFiles("club1", "team1")

        viewModel.state.value.roster shouldBe roster
        viewModel.allCandidates().map { it.userId } shouldBe listOf("u1")
    }

    // region — step-3 gate

    private fun series(key: String, dates: List<String>) = NdsSeries(
        seriesKey = key, weekday = 0, symbol = "T", durationMin = 90, dates = dates, count = dates.size
    )

    @Test
    fun step3Gate_missingTimeForImportingSeries_blocks() {
        val gate = NdsImportViewModel.step3Gate(
            series = listOf(series("s1", listOf("2026-08-03"))),
            conflicts = emptyList(),
            times = emptyMap(),
            resolutions = emptyMap()
        )
        gate shouldBe false
    }

    @Test
    fun step3Gate_fullyKeepTeamorgSeries_exemptFromTimeRequirement() {
        val conflictGroup = NdsConflictGroup(
            seriesKey = "s1",
            dates = listOf(NdsConflictDate("2026-08-03", "e1", "Existing", "2026-08-03T18:00:00Z"))
        )
        val gate = NdsImportViewModel.step3Gate(
            series = listOf(series("s1", listOf("2026-08-03"))),
            conflicts = listOf(conflictGroup),
            times = emptyMap(),
            resolutions = mapOf("s1" to ResolutionChoice(keep = "teamorg"))
        )
        gate shouldBe true
    }

    @Test
    fun step3Gate_unresolvedConflict_defaultsToTeamorgAndIsResolved() {
        val conflictGroup = NdsConflictGroup(
            seriesKey = "s1",
            dates = listOf(NdsConflictDate("2026-08-03", "e1", "Existing", "2026-08-03T18:00:00Z"))
        )
        // No resolution entry at all for s1 — defaults to "teamorg" per effectiveKeep, so no time required.
        val gate = NdsImportViewModel.step3Gate(
            series = listOf(series("s1", listOf("2026-08-03"))),
            conflicts = listOf(conflictGroup),
            times = emptyMap(),
            resolutions = emptyMap()
        )
        gate shouldBe true
    }

    @Test
    fun step3Gate_validTimeForImportingSeries_passes() {
        val gate = NdsImportViewModel.step3Gate(
            series = listOf(series("s1", listOf("2026-08-03"))),
            conflicts = emptyList(),
            times = mapOf("s1" to SeriesTimeInput("18:00", "19:30")),
            resolutions = emptyMap()
        )
        gate shouldBe true
    }

    @Test
    fun partialConflictSeries_groupKeepAppliesOnlyToConflictDates() {
        val s1 = series("s1", listOf("2026-08-03", "2026-08-10", "2026-08-17", "2026-08-24"))
        val conflictGroup = NdsConflictGroup(
            seriesKey = "s1",
            dates = listOf(
                NdsConflictDate("2026-08-03", "e1", "Existing", "2026-08-03T18:00:00Z"),
                NdsConflictDate("2026-08-10", "e2", "Existing", "2026-08-10T18:00:00Z")
            )
        )
        val resolutions = mapOf("s1" to ResolutionChoice(keep = "teamorg"))

        NdsImportViewModel.seriesImportsAnyEvent(s1, listOf(conflictGroup), resolutions) shouldBe true
        NdsImportViewModel.step3Gate(listOf(s1), listOf(conflictGroup), emptyMap(), resolutions) shouldBe false
        NdsImportViewModel.step3Gate(
            listOf(s1),
            listOf(conflictGroup),
            mapOf("s1" to SeriesTimeInput("18:00", "19:30")),
            resolutions
        ) shouldBe true

        val counts = NdsImportViewModel.conflictCounts(listOf(s1), listOf(conflictGroup), resolutions)
        counts.first shouldBe 2 // eventsNew
        counts.second shouldBe 2 // keepTeamorg
        counts.third shouldBe 0 // keepNds
    }

    @Test
    fun canProceedFromEvents_usesCurrentState() = runTest(testDispatcher) {
        fakeRepo.parseResult = Result.success(
            NdsParseResponse(
                anwesenheitsliste = ParsedAnwesenheitsliste(angebotId = "a1"),
                series = listOf(series("s1", listOf("2026-08-03")))
            )
        )
        viewModel.pickFile(NDS_SLOT_ANWESENHEITSLISTE, "liste.xlsx", byteArrayOf(1))
        viewModel.parseFiles("club1", "team1")

        viewModel.canProceedFromEvents() shouldBe false

        viewModel.setSeriesTime("s1", SeriesTimeInput("18:00", "19:30"))
        viewModel.canProceedFromEvents() shouldBe true
    }

    // region — payload assembly

    @Test
    fun assemblePayload_mapsActionsAndOverridesAndRoundTripsParsedAndPersons() {
        val parsed = ParsedAnwesenheitsliste(angebotId = "a1")
        val persons = listOf(NdsMemberInput(lastName = "Muster", firstName = "Hans", funktion = "Teilnehmer"))
        val parse = NdsParseResponse(anwesenheitsliste = parsed, persons = persons)
        val mappings = mapOf(
            "T:a|b" to MappingChoice(action = "map", userId = "u1"),
            "T:c|d" to MappingChoice(action = "create"),
            "T:e|f" to MappingChoice(action = "skip")
        )
        val seriesTimes = mapOf("s1" to SeriesTimeInput("18:00", "19:30", "Halle 1"))
        val resolutions = mapOf(
            "s1" to ResolutionChoice(keep = "nds", overrides = mapOf("2026-08-10" to "teamorg"))
        )

        val request = NdsImportViewModel.assemblePayload("team1", parse, mappings, seriesTimes, resolutions, "keep")

        request.teamId shouldBe "team1"
        request.parsed shouldBe parsed
        request.persons shouldBe persons
        request.importEvents shouldBe true
        request.attendanceMode shouldBe "keep"
        request.mappings.size shouldBe 3
        request.mappings.find { it.rowKey == "T:a|b" }!!.action shouldBe "map"
        request.mappings.find { it.rowKey == "T:a|b" }!!.userId shouldBe "u1"
        request.mappings.find { it.rowKey == "T:e|f" }!!.action shouldBe "skip"
        request.seriesTimes.single().let {
            it.seriesKey shouldBe "s1"
            it.startTime shouldBe "18:00"
            it.endTime shouldBe "19:30"
            it.location shouldBe "Halle 1"
        }
        request.conflictResolutions.single().let {
            it.seriesKey shouldBe "s1"
            it.keep shouldBe "nds"
            it.overrides.single().date shouldBe "2026-08-10"
            it.overrides.single().keep shouldBe "teamorg"
        }
    }

    @Test
    fun assemblePayload_noAnwesenheitsliste_importEventsFalse() {
        val parse = NdsParseResponse(anwesenheitsliste = null, persons = emptyList())
        val request = NdsImportViewModel.assemblePayload("team1", parse, emptyMap(), emptyMap(), emptyMap(), "discard")
        request.importEvents shouldBe false
        request.parsed shouldBe null
    }

    @Test
    fun submitImport_assemblesFromCurrentStateAndCallsRepository() = runTest(testDispatcher) {
        fakeRepo.parseResult = Result.success(NdsParseResponse(anwesenheitsliste = ParsedAnwesenheitsliste(angebotId = "a1")))
        viewModel.pickFile(NDS_SLOT_ANWESENHEITSLISTE, "liste.xlsx", byteArrayOf(1))
        viewModel.parseFiles("club1", "team1")

        viewModel.submitImport("club1", "team1", "keep")

        fakeRepo.lastImportClubId shouldBe "club1"
        fakeRepo.lastImportRequest!!.teamId shouldBe "team1"
        fakeRepo.lastImportRequest!!.attendanceMode shouldBe "keep"
        viewModel.state.value.result shouldBe fakeRepo.importResult.getOrNull()
    }

    // region — merge rows (mapping table)

    @Test
    fun mergeRows_unionsAwlAndPersonRowsByRowKey() {
        val awl = listOf(ParsedMember(funktion = "Teilnehmer", lastName = "Muster", firstName = "Hans", birthDate = "2000-01-01"))
        val persons = listOf(NdsMemberInput(lastName = "Muster", firstName = "Hans", funktion = "Teilnehmer", personNumber = "123"))
        val merged = NdsImportViewModel.mergeRows(awl, persons)
        merged.size shouldBe 1
        merged.single().personNumber shouldBe "123"
        merged.single().birthDate shouldBe "2000-01-01"
    }

    // region — parse error handling

    @Test
    fun parseFiles_failure_setsErrorAndKeepsFiles() = runTest(testDispatcher) {
        fakeRepo.parseResult = Result.failure(Exception("Netzwerkfehler"))
        viewModel.pickFile(NDS_SLOT_TEILNEHMENDE, "teilnehmende.csv", byteArrayOf(1, 2, 3))

        viewModel.parseFiles("club1", "team1")

        viewModel.state.value.error shouldBe "Netzwerkfehler"
        viewModel.state.value.step shouldBe 1
        viewModel.state.value.files.containsKey(NDS_SLOT_TEILNEHMENDE) shouldBe true

        // Retry after fixing the server keeps the picked files and succeeds.
        fakeRepo.parseResult = Result.success(NdsParseResponse())
        viewModel.parseFiles("club1", "team1")

        viewModel.state.value.error shouldBe null
        viewModel.state.value.step shouldBe 2
    }
}
