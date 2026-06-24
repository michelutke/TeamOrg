package ch.teamorg

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import ch.teamorg.auth.AuthState
import ch.teamorg.auth.AuthViewModel
import ch.teamorg.navigation.AppNavigation
import ch.teamorg.navigation.Screen
import ch.teamorg.ui.components.TeamorgBottomBar
import ch.teamorg.ui.theme.TeamorgTheme
import ch.teamorg.ui.components.PlatformBackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.teamorg.data.MutationQueueManager
import ch.teamorg.repository.NotificationRepository
import kotlinx.coroutines.delay
import org.koin.mp.KoinPlatform

@Composable
fun TeamorgApp(
    viewModel: AuthViewModel = viewModel { KoinPlatform.getKoin().get() }
) {
    val authState by viewModel.state.collectAsState()
    val backStack = remember { mutableStateListOf<Screen>(Screen.Loading) }
    val pendingToken by DeepLinkHandler.pendingToken
    var unreadCount by remember { mutableLongStateOf(0L) }

    // Poll unread count while authenticated
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            // Flush offline mutations on foreground resume
            try {
                val mutationQueue = KoinPlatform.getKoin().get<MutationQueueManager>()
                if (mutationQueue.hasPendingMutations()) {
                    mutationQueue.flushQueue()
                }
            } catch (_: Exception) {
                // Silently ignore flush errors — will retry next foreground
            }
            val notifRepo = KoinPlatform.getKoin().get<NotificationRepository>()
            while (true) {
                notifRepo.getUnreadCount().onSuccess { unreadCount = it }
                delay(15_000)
            }
        } else {
            unreadCount = 0
        }
    }

    TeamorgTheme {
        LaunchedEffect(authState, pendingToken) {
            val token = pendingToken
            when (val state = authState) {
                is AuthState.Loading -> Unit
                is AuthState.Unauthenticated -> {
                    backStack.clear()
                    backStack.add(Screen.Login)
                    // token stays in DeepLinkHandler until after login/register
                }
                is AuthState.Authenticated -> {
                    if (token != null) {
                        DeepLinkHandler.pendingToken.value = null
                        backStack.clear()
                        backStack.add(if (!state.hasTeam) Screen.EmptyState else Screen.Events)
                        backStack.add(Screen.Invite(token))
                    } else if (backStack.none { it is Screen.Invite }) {
                        backStack.clear()
                        backStack.add(if (!state.hasTeam) Screen.EmptyState else Screen.Events)
                    }
                }
            }
        }

        val currentScreen = backStack.lastOrNull() ?: Screen.Loading
        val showBottomBar = currentScreen !in listOf(
            Screen.Login, Screen.Register, Screen.EmptyState, Screen.Loading,
            Screen.CreateEvent
        ) && currentScreen !is Screen.Invite
          && currentScreen !is Screen.EventDetail
          && currentScreen !is Screen.EditEvent
          && currentScreen !is Screen.DuplicateEvent
          && currentScreen !is Screen.PlayerProfile

        PlatformBackHandler(enabled = backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }

        Box(
            modifier = Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
        ) {
            // Content takes full screen — no layout shift from bottom bar
            AppNavigation(
                backStack = backStack,
                isLoggedIn = authState is AuthState.Authenticated,
                onAuthSuccess = { viewModel.checkAuthState() },
                onLogout = {
                    DeepLinkHandler.pendingToken.value = null
                    DeepLinkHandler.pendingInviteEmail.value = null
                    viewModel.logout()
                }
            )

            // Bottom bar overlaid on top — animates without affecting content layout
            AnimatedVisibility(
                visible = showBottomBar,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TeamorgBottomBar(
                        currentRoute = currentScreen.route,
                        onNavigate = { screen ->
                            backStack.add(screen)
                        },
                        unreadCount = unreadCount
                    )
                }
            }
        }
    }
}
