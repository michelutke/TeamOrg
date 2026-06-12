package ch.teamorg.ui.invite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.teamorg.ui.testTagsAsResourceId
import ch.teamorg.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteScreen(
    token: String,
    viewModel: InviteViewModel,
    isLoggedIn: Boolean,
    onNavigateToLogin: (String) -> Unit,
    onNavigateToRegister: (String) -> Unit,
    onJoinSuccess: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(token) {
        viewModel.loadInvite(token)
    }

    LaunchedEffect(state.isRedeemed) {
        if (state.isRedeemed) {
            onJoinSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(24.dp)
            .testTagsAsResourceId(),
        contentAlignment = Alignment.Center
    ) {
        if (state.isLoading) {
            CircularProgressIndicator()
        } else if (state.error != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = { viewModel.loadInvite(token) },
                    modifier = Modifier.height(57.dp).testTag("btn_retry"),
                    shape = PillShape
                ) {
                    Text("Retry", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else if (state.inviteDetails != null) {
            val invite = state.inviteDetails!!

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Invite card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Club avatar with initials
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier.size(88.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = invite.clubName
                                        .split(" ")
                                        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                        .take(2)
                                        .joinToString(""),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Text(
                            "You've been invited to join",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            invite.teamName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            "at ${invite.clubName}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        // Invited-by chip
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = PillShape
                        ) {
                            Text(
                                "Invited by ${invite.invitedBy} as ${invite.role.replaceFirstChar { it.uppercase() }}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (isLoggedIn) {
                    Button(
                        onClick = { viewModel.redeemInvite(token) },
                        modifier = Modifier.fillMaxWidth().height(57.dp).testTag("btn_join_team"),
                        enabled = !state.isRedeeming,
                        shape = PillShape
                    ) {
                        if (state.isRedeeming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Join ${invite.teamName}", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onNavigateToRegister(token) },
                            modifier = Modifier.fillMaxWidth().height(57.dp).testTag("btn_create_account_to_join"),
                            shape = PillShape
                        ) {
                            Text("Create Account to Join", style = MaterialTheme.typography.titleMedium)
                        }

                        TextButton(
                            onClick = { onNavigateToLogin(token) },
                            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("btn_login_to_join")
                        ) {
                            Text(
                                "Login to Join",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
