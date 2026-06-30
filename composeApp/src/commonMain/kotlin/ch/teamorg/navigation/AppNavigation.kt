package ch.teamorg.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import ch.teamorg.ui.club.ClubMembersScreen
import ch.teamorg.ui.club.ClubMembersViewModel
import ch.teamorg.ui.club.ClubSetupScreen
import ch.teamorg.ui.club.ClubSetupViewModel
import ch.teamorg.ui.emptystate.EmptyStateScreen
import ch.teamorg.ui.emptystate.EmptyStateViewModel
import ch.teamorg.ui.events.CreateEditEventScreen
import ch.teamorg.ui.events.CreateEditEventViewModel
import ch.teamorg.ui.events.EventDetailScreen
import ch.teamorg.ui.events.EventDetailViewModel
import ch.teamorg.ui.events.EventListScreen
import ch.teamorg.ui.events.EventListViewModel
import ch.teamorg.ui.events.EventViewMode
import ch.teamorg.ui.invite.InviteScreen
import ch.teamorg.ui.invite.InviteViewModel
import ch.teamorg.ui.login.LoginScreen
import ch.teamorg.ui.login.LoginViewModel
import ch.teamorg.ui.inbox.InboxScreen
import ch.teamorg.ui.inbox.InboxViewModel
import ch.teamorg.ui.inbox.NotificationSettingsScreen
import ch.teamorg.ui.inbox.NotificationSettingsViewModel
import ch.teamorg.ui.placeholder.PlaceholderScreen
import ch.teamorg.ui.register.RegisterScreen
import ch.teamorg.ui.register.RegisterViewModel
import ch.teamorg.ui.team.TeamRosterScreen
import ch.teamorg.ui.team.TeamRosterViewModel
import ch.teamorg.ui.team.PlayerProfileScreen
import ch.teamorg.ui.team.PlayerProfileViewModel
import ch.teamorg.ui.team.TeamsListScreen
import ch.teamorg.ui.team.TeamsListViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.teamorg.DeepLinkHandler
import org.koin.mp.KoinPlatform

@Composable
fun AppNavigation(
    backStack: MutableList<Screen>,
    isLoggedIn: Boolean,
    onAuthSuccess: () -> Unit,
    onLogout: () -> Unit
) {
    var detailRefreshTrigger by remember { mutableIntStateOf(0) }
    var previousStackSize by remember { mutableIntStateOf(backStack.size) }
    val currentScreen = backStack.lastOrNull() ?: Screen.Loading
    val isGoingBack = backStack.size < previousStackSize

    LaunchedEffect(backStack.size) {
        previousStackSize = backStack.size
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            val duration = 300
            if (isGoingBack) {
                // Back: slide in from left + fade, slide out to right + fade
                (slideInHorizontally(tween(duration)) { -it / 4 } + fadeIn(tween(duration)))
                    .togetherWith(slideOutHorizontally(tween(duration)) { it / 3 } + fadeOut(tween(duration / 2)))
            } else {
                // Forward: slide in from right + fade, slide out to left + fade
                (slideInHorizontally(tween(duration)) { it / 4 } + fadeIn(tween(duration)))
                    .togetherWith(slideOutHorizontally(tween(duration)) { -it / 3 } + fadeOut(tween(duration / 2)))
            }
        }
    ) { screen ->
        when (screen) {
            Screen.Loading -> PlaceholderScreen("Loading...")
            Screen.Login -> {
                val viewModel: LoginViewModel = viewModel { KoinPlatform.getKoin().get() }
                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = { onAuthSuccess() },
                    onNavigateToRegister = { backStack.add(Screen.Register) }
                )
            }
            Screen.Register -> {
                val viewModel: RegisterViewModel = viewModel { KoinPlatform.getKoin().get() }
                RegisterScreen(
                    viewModel = viewModel,
                    onRegisterSuccess = { onAuthSuccess() },
                    onNavigateToLogin = { backStack.add(Screen.Login) }
                )
            }
            Screen.EmptyState -> {
                val viewModel: EmptyStateViewModel = viewModel { KoinPlatform.getKoin().get() }
                EmptyStateScreen(
                    viewModel = viewModel,
                    onNavigateToClubSetup = { backStack.add(Screen.ClubSetup) },
                    onNavigateToInvite = { token -> backStack.add(Screen.Invite(token)) }
                )
            }
            Screen.ClubSetup -> {
                val viewModel: ClubSetupViewModel = viewModel { KoinPlatform.getKoin().get() }
                ClubSetupScreen(
                    viewModel = viewModel,
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                    onClubCreated = { _ -> backStack.add(Screen.Teams) }
                )
            }
            is Screen.TeamRoster -> {
                val viewModel: TeamRosterViewModel = viewModel { KoinPlatform.getKoin().get() }
                TeamRosterScreen(
                    teamId = screen.teamId,
                    viewModel = viewModel,
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                    onShareInvite = { },
                    onMemberClick = { userId -> backStack.add(Screen.PlayerProfile(screen.teamId, userId)) }
                )
            }
            is Screen.Invite -> {
                val viewModel: InviteViewModel = viewModel { KoinPlatform.getKoin().get() }
                InviteScreen(
                    token = screen.token,
                    viewModel = viewModel,
                    isLoggedIn = isLoggedIn,
                    onNavigateToLogin = { token ->
                        DeepLinkHandler.pendingToken.value = token
                        backStack.add(Screen.Login)
                    },
                    onNavigateToRegister = { invite ->
                        DeepLinkHandler.pendingToken.value = invite.token
                        // Personal invite (not reusable) → prefill + lock the email
                        DeepLinkHandler.pendingInviteEmail.value =
                            if (!invite.reusable) invite.invitedEmail else null
                        backStack.add(Screen.Register)
                    },
                    onJoinSuccess = {
                        // Remove Invite screen from backStack BEFORE triggering
                        // checkAuthState so the LaunchedEffect guard in TeamorgApp
                        // allows navigation to proceed.
                        backStack.removeAll { it is Screen.Invite }
                        onAuthSuccess()
                    },
                    onLogout = onLogout
                )
            }
            Screen.Events -> {
                val viewModel: EventListViewModel = viewModel { KoinPlatform.getKoin().get() }
                LaunchedEffect(backStack.size) { viewModel.loadEvents() }
                EventListScreen(
                    viewModel = viewModel,
                    onEventClick = { eventId -> backStack.add(Screen.EventDetail(eventId)) },
                    onCreateClick = { backStack.add(Screen.CreateEvent) }
                )
            }
            is Screen.EventDetail -> {
                val viewModel: EventDetailViewModel = viewModel { KoinPlatform.getKoin().get() }
                LaunchedEffect(screen.eventId, detailRefreshTrigger) {
                    viewModel.loadEvent(screen.eventId)
                }
                EventDetailScreen(
                    viewModel = viewModel,
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                    onEdit = { backStack.add(Screen.EditEvent(screen.eventId)) },
                    onDuplicate = { backStack.add(Screen.DuplicateEvent(screen.eventId)) },
                    onCancel = {
                        detailRefreshTrigger++
                        backStack.removeAt(backStack.lastIndex)
                    }
                )
            }
            Screen.CreateEvent -> {
                val viewModel: CreateEditEventViewModel = viewModel { KoinPlatform.getKoin().get() }
                LaunchedEffect(Unit) { viewModel.resetForm() }
                CreateEditEventScreen(
                    viewModel = viewModel,
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                    onSaved = { backStack.removeAt(backStack.lastIndex) }
                )
            }
            is Screen.EditEvent -> {
                val viewModel: CreateEditEventViewModel = viewModel { KoinPlatform.getKoin().get() }
                LaunchedEffect(screen.eventId) { viewModel.loadForEdit(screen.eventId) }
                CreateEditEventScreen(
                    viewModel = viewModel,
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                    onSaved = {
                        detailRefreshTrigger++
                        backStack.removeAt(backStack.lastIndex)
                    }
                )
            }
            is Screen.DuplicateEvent -> {
                val viewModel: CreateEditEventViewModel = viewModel { KoinPlatform.getKoin().get() }
                LaunchedEffect(screen.eventId) { viewModel.loadForDuplicate(screen.eventId) }
                CreateEditEventScreen(
                    viewModel = viewModel,
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                    onSaved = { backStack.removeAt(backStack.lastIndex) }
                )
            }
            Screen.Calendar -> {
                // Calendar is now integrated into Events screen
                val viewModel: EventListViewModel = viewModel { KoinPlatform.getKoin().get() }
                LaunchedEffect(Unit) {
                    viewModel.setViewMode(EventViewMode.CALENDAR)
                }
                LaunchedEffect(backStack.size) { viewModel.loadEvents() }
                EventListScreen(
                    viewModel = viewModel,
                    onEventClick = { eventId -> backStack.add(Screen.EventDetail(eventId)) },
                    onCreateClick = { backStack.add(Screen.CreateEvent) }
                )
            }
            Screen.Teams -> {
                val viewModel: TeamsListViewModel = viewModel { KoinPlatform.getKoin().get() }
                LaunchedEffect(backStack.size) { viewModel.loadTeams() }
                TeamsListScreen(
                    viewModel = viewModel,
                    onTeamClick = { teamId -> backStack.add(Screen.TeamRoster(teamId)) },
                    onMembersClick = { clubId -> backStack.add(Screen.ClubMembers(clubId)) }
                )
            }
            Screen.Inbox -> {
                val viewModel: InboxViewModel = viewModel { KoinPlatform.getKoin().get() }
                InboxScreen(
                    viewModel = viewModel,
                    onNavigate = { screen -> backStack.add(screen) }
                )
            }
            Screen.NotificationSettings -> {
                val viewModel: NotificationSettingsViewModel = viewModel { KoinPlatform.getKoin().get() }
                NotificationSettingsScreen(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            Screen.Profile -> {
                val viewModel: PlayerProfileViewModel = viewModel { KoinPlatform.getKoin().get() }
                val userPreferences = remember { KoinPlatform.getKoin().get<ch.teamorg.preferences.UserPreferences>() }
                val userId = remember { userPreferences.getUserId() ?: "" }
                val teamRepository = remember { KoinPlatform.getKoin().get<ch.teamorg.repository.TeamRepository>() }
                var teamId by remember { mutableStateOf("") }

                LaunchedEffect(userId) {
                    if (userId.isNotEmpty()) {
                        teamRepository.getMyRoles().onSuccess { roles ->
                            teamId = roles.teamRoles.firstOrNull()?.teamId ?: ""
                            if (teamId.isNotEmpty()) {
                                viewModel.loadProfile(teamId, userId)
                            }
                        }
                    }
                }

                PlayerProfileScreen(
                    teamId = teamId,
                    userId = userId,
                    viewModel = viewModel,
                    onBack = { },
                    onLeftTeam = {
                        backStack.removeAll { it == Screen.Profile }
                        backStack.add(Screen.Teams)
                    },
                    isNavProfile = true
                )
            }
            is Screen.PlayerProfile -> {
                val viewModel: PlayerProfileViewModel = viewModel { KoinPlatform.getKoin().get() }
                LaunchedEffect(screen.teamId, screen.userId) { viewModel.loadProfile(screen.teamId, screen.userId) }
                PlayerProfileScreen(
                    teamId = screen.teamId,
                    userId = screen.userId,
                    viewModel = viewModel,
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                    onLeftTeam = { backStack.removeAt(backStack.lastIndex) }
                )
            }
            is Screen.ClubMembers -> {
                val viewModel: ClubMembersViewModel = viewModel { KoinPlatform.getKoin().get() }
                ClubMembersScreen(
                    clubId = screen.clubId,
                    viewModel = viewModel,
                    onBack = { backStack.removeAt(backStack.lastIndex) }
                )
            }
        }
    }
}
