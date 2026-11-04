package ch.teamorg.ui.inbox

import ch.teamorg.domain.NotificationSettings
import ch.teamorg.domain.Team
import ch.teamorg.domain.TeamRoleEntry
import ch.teamorg.domain.ClubRoleEntry
import ch.teamorg.domain.UpdateNotificationSettingsRequest
import ch.teamorg.domain.UserRoles
import ch.teamorg.fake.FakeClubRepository
import ch.teamorg.fake.FakeNotificationRepository
import ch.teamorg.fake.FakeTeamRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val notificationRepo = FakeNotificationRepository()
    private val teamRepo = FakeTeamRepository()
    private val clubRepo = FakeClubRepository()

    private fun settings(teamId: String, eventsNew: Boolean = true) =
        NotificationSettings(userId = "u1", teamId = teamId, eventsNew = eventsNew)

    private fun givenRoles(vararg teamRoles: TeamRoleEntry, clubRoles: List<ClubRoleEntry> = emptyList()) {
        teamRepo.getMyRolesResult = Result.success(UserRoles(clubRoles = clubRoles, teamRoles = teamRoles.toList()))
        clubRepo.getClubTeamsResult = Result.success(
            teamRoles.map { Team(id = it.teamId, clubId = it.clubId, name = "Name ${it.teamId}") }
        )
        teamRoles.forEach { notificationRepo.settingsByTeam[it.teamId] = Result.success(settings(it.teamId)) }
    }

    private fun makeViewModel() = NotificationSettingsViewModel(notificationRepo, teamRepo, clubRepo)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        teamRepo.reset()
        clubRepo.reset()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region load

    @Test
    fun init_loadsTeamsWithNamesAndSelectsFirstTeamSettings() = runTest(testDispatcher) {
        givenRoles(
            TeamRoleEntry("t1", "c1", "player"),
            TeamRoleEntry("t2", "c1", "coach")
        )

        val vm = makeViewModel()

        val state = vm.state.value
        state.teams.map { it.teamName } shouldBe listOf("Name t1", "Name t2")
        state.teams.map { it.isCoach } shouldBe listOf(false, true)
        state.selectedTeamId shouldBe "t1"
        state.settings?.teamId shouldBe "t1"
        state.isCoach shouldBe true
        state.isLoading shouldBe false
        notificationRepo.getSettingsCalls shouldBe listOf("t1")
    }

    @Test
    fun init_playerOnly_isNotCoach() = runTest(testDispatcher) {
        givenRoles(TeamRoleEntry("t1", "c1", "player"))

        val vm = makeViewModel()

        vm.state.value.isCoach shouldBe false
    }

    @Test
    fun init_clubManagerWithoutTeamCoachRole_isCoach() = runTest(testDispatcher) {
        givenRoles(
            TeamRoleEntry("t1", "c1", "player"),
            clubRoles = listOf(ClubRoleEntry("c1", "club_manager"))
        )

        val vm = makeViewModel()

        vm.state.value.isCoach shouldBe true
    }

    @Test
    fun init_clubTeamsLookupFails_fallsBackToTeamIdAsName() = runTest(testDispatcher) {
        givenRoles(TeamRoleEntry("t1", "c1", "player"))
        clubRepo.getClubTeamsResult = Result.failure(RuntimeException("offline"))

        val vm = makeViewModel()

        vm.state.value.teams.single().teamName shouldBe "t1"
    }

    @Test
    fun init_noRoles_stopsLoadingWithoutSettings() = runTest(testDispatcher) {
        teamRepo.getMyRolesResult = Result.success(UserRoles())

        val vm = makeViewModel()

        vm.state.value.teams shouldHaveSize 0
        vm.state.value.selectedTeamId shouldBe null
        vm.state.value.settings shouldBe null
        vm.state.value.isLoading shouldBe false
        notificationRepo.getSettingsCalls shouldHaveSize 0
    }

    @Test
    fun init_rolesFailure_surfacesError() = runTest(testDispatcher) {
        teamRepo.getMyRolesResult = Result.failure(RuntimeException("401"))

        val vm = makeViewModel()

        vm.state.value.error shouldBe "401"
        vm.state.value.isLoading shouldBe false
    }

    @Test
    fun init_settingsFailure_surfacesError() = runTest(testDispatcher) {
        givenRoles(TeamRoleEntry("t1", "c1", "player"))
        notificationRepo.settingsByTeam["t1"] = Result.failure(RuntimeException("500"))

        val vm = makeViewModel()

        vm.state.value.error shouldBe "500"
        vm.state.value.settings shouldBe null
        vm.state.value.isLoading shouldBe false
    }

    // endregion

    // region per-team toggle

    @Test
    fun selectTeam_switchesTeamAndLoadsItsSettings() = runTest(testDispatcher) {
        givenRoles(
            TeamRoleEntry("t1", "c1", "player"),
            TeamRoleEntry("t2", "c1", "coach")
        )
        notificationRepo.settingsByTeam["t2"] = Result.success(settings("t2", eventsNew = false))
        val vm = makeViewModel()

        vm.selectTeam("t2")

        vm.state.value.selectedTeamId shouldBe "t2"
        vm.state.value.settings?.eventsNew shouldBe false
        vm.state.value.isCoach shouldBe true
        notificationRepo.getSettingsCalls shouldBe listOf("t1", "t2")
    }

    @Test
    fun selectTeam_unknownTeam_isIgnored() = runTest(testDispatcher) {
        givenRoles(TeamRoleEntry("t1", "c1", "player"))
        val vm = makeViewModel()

        vm.selectTeam("nope")

        vm.state.value.selectedTeamId shouldBe "t1"
        notificationRepo.getSettingsCalls shouldBe listOf("t1")
    }

    // endregion

    // region updateSetting

    @Test
    fun updateSetting_appliesOptimisticallyThenPersistsAfterDebounce() = runTest(testDispatcher) {
        givenRoles(TeamRoleEntry("t1", "c1", "player"))
        val vm = makeViewModel()

        vm.updateSetting("eventsNew", false)

        vm.state.value.settings?.eventsNew shouldBe false
        notificationRepo.updateSettingsCalls shouldHaveSize 0
        advanceTimeBy(299)
        notificationRepo.updateSettingsCalls shouldHaveSize 0
        advanceUntilIdle()
        notificationRepo.updateSettingsCalls shouldBe listOf(
            "t1" to UpdateNotificationSettingsRequest(eventsNew = false)
        )
    }

    @Test
    fun updateSetting_mapsEachFieldToItsRequestField() = runTest(testDispatcher) {
        givenRoles(TeamRoleEntry("t1", "c1", "coach"))
        val vm = makeViewModel()

        vm.updateSetting("eventsEdit", false); advanceUntilIdle()
        vm.updateSetting("eventsCancel", false); advanceUntilIdle()
        vm.updateSetting("remindersEnabled", false); advanceUntilIdle()
        vm.updateSetting("reminderLeadMinutes", 30); advanceUntilIdle()
        vm.updateSetting("coachResponseMode", "summary"); advanceUntilIdle()
        vm.updateSetting("absencesEnabled", false); advanceUntilIdle()

        notificationRepo.updateSettingsCalls.map { it.second } shouldBe listOf(
            UpdateNotificationSettingsRequest(eventsEdit = false),
            UpdateNotificationSettingsRequest(eventsCancel = false),
            UpdateNotificationSettingsRequest(remindersEnabled = false),
            UpdateNotificationSettingsRequest(reminderLeadMinutes = 30),
            UpdateNotificationSettingsRequest(coachResponseMode = "summary"),
            UpdateNotificationSettingsRequest(absencesEnabled = false)
        )
        val s = vm.state.value.settings!!
        s.eventsEdit shouldBe false
        s.eventsCancel shouldBe false
        s.remindersEnabled shouldBe false
        s.reminderLeadMinutes shouldBe 30
        s.coachResponseMode shouldBe "summary"
        s.absencesEnabled shouldBe false
    }

    @Test
    fun updateSetting_sameFieldTwiceWithinDebounce_sendsOnlyLastValue() = runTest(testDispatcher) {
        givenRoles(TeamRoleEntry("t1", "c1", "player"))
        val vm = makeViewModel()

        vm.updateSetting("eventsNew", false)
        advanceTimeBy(100)
        vm.updateSetting("eventsNew", true)
        advanceUntilIdle()

        notificationRepo.updateSettingsCalls.map { it.second } shouldBe listOf(
            UpdateNotificationSettingsRequest(eventsNew = true)
        )
    }

    @Test
    fun updateSetting_differentFieldsWithinDebounce_persistsBoth() = runTest(testDispatcher) {
        givenRoles(TeamRoleEntry("t1", "c1", "player"))
        val vm = makeViewModel()

        vm.updateSetting("eventsNew", false)
        advanceTimeBy(100)
        vm.updateSetting("eventsEdit", false)
        advanceUntilIdle()

        notificationRepo.updateSettingsCalls.map { it.second }.toSet() shouldBe setOf(
            UpdateNotificationSettingsRequest(eventsNew = false),
            UpdateNotificationSettingsRequest(eventsEdit = false)
        )
    }

    @Test
    fun updateSetting_failure_revertsAndSurfacesError() = runTest(testDispatcher) {
        givenRoles(TeamRoleEntry("t1", "c1", "player"))
        val vm = makeViewModel()
        notificationRepo.updateSettingsResult = Result.failure(RuntimeException("500"))

        vm.updateSetting("eventsNew", false)
        advanceUntilIdle()

        vm.state.value.settings?.eventsNew shouldBe true
        vm.state.value.error shouldBe "Settings not saved. Try again."
    }

    @Test
    fun updateSetting_beforeSettingsLoaded_doesNothing() = runTest(testDispatcher) {
        teamRepo.getMyRolesResult = Result.success(UserRoles())
        val vm = makeViewModel()

        vm.updateSetting("eventsNew", false)
        advanceUntilIdle()

        notificationRepo.updateSettingsCalls shouldHaveSize 0
    }

    // endregion
}
