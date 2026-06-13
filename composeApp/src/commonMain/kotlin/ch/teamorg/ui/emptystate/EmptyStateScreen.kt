package ch.teamorg.ui.emptystate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.teamorg.ui.components.TeamorgTextField
import ch.teamorg.ui.testTagsAsResourceId
import ch.teamorg.ui.theme.PillShape

@Composable
fun EmptyStateScreen(
    viewModel: EmptyStateViewModel,
    onNavigateToClubSetup: () -> Unit,
    onNavigateToInvite: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EmptyStateEvent.NavigateToClubSetup -> onNavigateToClubSetup()
                is EmptyStateEvent.NavigateToInvite -> onNavigateToInvite(event.token)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .testTagsAsResourceId()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Illustration stand-in
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(120.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Text(
                text = "Welcome to Teamorg",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "You're not part of a team yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Create a club — primary CTA
            Button(
                onClick = { viewModel.onCreateClubClick() },
                modifier = Modifier.fillMaxWidth().height(57.dp).testTag("btn_setup_club"),
                shape = PillShape
            ) {
                Text("Set up your club", style = MaterialTheme.typography.titleMedium)
            }

            // Join a team — invite redemption card
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Join a team",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Got an invite link from your coach?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TeamorgTextField(
                        value = state.inviteLink,
                        onValueChange = { viewModel.onInviteLinkChange(it) },
                        label = "Paste invite link",
                        modifier = Modifier.fillMaxWidth().testTag("tf_invite_link")
                    )
                    FilledTonalButton(
                        onClick = { viewModel.onJoinTeamClick() },
                        modifier = Modifier.fillMaxWidth().height(57.dp).testTag("btn_join_team"),
                        shape = PillShape
                    ) {
                        Text("Join Team", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // Share your profile
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Let a coach add you",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Share your profile link so a coach can find you:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.profileLink,
                            modifier = Modifier.weight(1f).padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        IconButton(
                            onClick = { viewModel.onProfileLinkCopied() },
                            modifier = Modifier.testTag("btn_copy_profile_link")
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Messages
        state.error?.let { error ->
            Snackbar(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.error,
                action = { TextButton(onClick = { viewModel.dismissMessages() }) { Text("Dismiss") } }
            ) { Text(error) }
        }

        state.infoMessage?.let { info ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                shape = MaterialTheme.shapes.medium,
                action = { TextButton(onClick = { viewModel.dismissMessages() }) { Text("OK") } }
            ) { Text(info) }
        }
    }
}
