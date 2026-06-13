package ch.teamorg.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import ch.teamorg.ui.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import ch.teamorg.domain.TeamMember
import ch.teamorg.ui.theme.PillShape

internal fun memberInitials(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamRosterScreen(
    teamId: String,
    viewModel: TeamRosterViewModel,
    onBack: () -> Unit,
    onShareInvite: (String) -> Unit,
    onMemberClick: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var memberToRemove by remember { mutableStateOf<TeamMember?>(null) }
    var memberToPromote by remember { mutableStateOf<TeamMember?>(null) }
    var memberAction by remember { mutableStateOf<TeamMember?>(null) }
    var showInviteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(teamId) {
        viewModel.loadRoster(teamId)
    }

    Scaffold(
        modifier = Modifier.testTagsAsResourceId(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.teamName.ifBlank { "Team Roster" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        val coachCount = state.members.count { it.role != "player" }
                        if (state.members.isNotEmpty()) {
                            Text(
                                text = "${state.members.size} member${if (state.members.size != 1) "s" else ""} · $coachCount coach${if (coachCount != 1) "es" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isClubManager) {
                        IconButton(
                            onClick = {
                                viewModel.loadSubGroups(teamId)
                                viewModel.toggleSubGroupSheet()
                            },
                            modifier = Modifier.testTag("btn_sub_groups")
                        ) {
                            Icon(Icons.Default.Groups, contentDescription = "Sub-groups")
                        }
                        IconButton(
                            onClick = { viewModel.showEditTeamSheet() },
                            modifier = Modifier.testTag("btn_edit_team")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Team")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading && !state.isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                val coaches = state.members.filter { it.role != "player" }
                val players = state.members.filter { it.role == "player" }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        // Full-width pill CTA replaces the old FAB visual
                        Button(
                            onClick = { showInviteDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("fab_invite_player"),
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Invite members",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (state.members.isEmpty() && !state.isLoading) {
                        item {
                            Text(
                                "No members in this team yet.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (coaches.isNotEmpty()) {
                        item { RosterSectionHeader("Coaches") }
                        items(coaches, key = { "c_${it.userId}" }) { member ->
                            MemberItem(
                                member = member,
                                onClick = { onMemberClick(member.userId) },
                                onLongClick = {
                                    if (state.isClubManager) {
                                        memberAction = member
                                    } else {
                                        memberToRemove = member
                                    }
                                }
                            )
                        }
                    }

                    if (players.isNotEmpty()) {
                        item { RosterSectionHeader("Players") }
                        items(players, key = { "p_${it.userId}" }) { member ->
                            MemberItem(
                                member = member,
                                onClick = { onMemberClick(member.userId) },
                                onLongClick = {
                                    if (state.isClubManager) {
                                        memberAction = member
                                    } else {
                                        memberToRemove = member
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ClubManager member action dialog (promote / remove)
    memberAction?.let { member ->
        AlertDialog(
            onDismissRequest = { memberAction = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text(member.displayName, fontWeight = FontWeight.Bold) },
            text = { Text("Choose an action for this member.") },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (member.role == "player") {
                        Button(
                            onClick = {
                                memberToPromote = member
                                memberAction = null
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_promote_to_coach"),
                            shape = PillShape
                        ) {
                            Text("Promote to Coach")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            memberToRemove = member
                            memberAction = null
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_remove_member"),
                        shape = PillShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Remove from Team")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { memberAction = null }) { Text("Cancel") }
            }
        )
    }

    // Promote confirmation dialog
    memberToPromote?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToPromote = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Promote to Coach", fontWeight = FontWeight.Bold) },
            text = { Text("Promote ${member.displayName} to coach?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.promoteMember(teamId, member.userId)
                        memberToPromote = null
                    },
                    modifier = Modifier.testTag("btn_promote_confirm")
                ) {
                    Text("Promote")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToPromote = null }) { Text("Cancel") }
            }
        )
    }

    // Remove confirmation dialog
    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Remove Member", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove ${member.displayName} from the team?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeMember(teamId, member.userId)
                        memberToRemove = null
                    },
                    modifier = Modifier.testTag("btn_remove_confirm"),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { memberToRemove = null },
                    modifier = Modifier.testTag("btn_remove_cancel")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Invite role chooser sheet
    if (showInviteDialog) {
        ModalBottomSheet(
            onDismissRequest = { showInviteDialog = false },
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Invite to ${state.teamName.ifBlank { "Team" }}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "What role would you like to invite?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        viewModel.createInvite(teamId, "player")
                        showInviteDialog = false
                    },
                    modifier = Modifier.fillMaxWidth().height(57.dp).testTag("btn_invite_as_player"),
                    shape = PillShape
                ) {
                    Text("Invite as Player", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        viewModel.createCoachInvite(teamId)
                        showInviteDialog = false
                    },
                    modifier = Modifier.fillMaxWidth().height(57.dp).testTag("btn_invite_as_coach"),
                    shape = PillShape
                ) {
                    Text("Invite as Coach", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Invite URL sheet (share link)
    state.inviteUrl?.let { url ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.resetInvite() },
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Invite Link Created",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Share this link to invite someone to the team:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = url,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(url))
                            viewModel.resetInvite()
                        },
                        modifier = Modifier.testTag("btn_copy_invite_link"),
                        shape = PillShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Copy", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Edit Team Sheet
    if (state.showEditTeamSheet) {
        TeamEditSheet(
            initialName = state.teamName,
            initialDescription = state.teamDescription ?: "",
            isCreate = false,
            onSave = { name, description -> viewModel.editTeam(teamId, name, description) },
            onDismiss = { viewModel.hideEditTeamSheet() }
        )
    }

    // Sub-group Sheet
    if (state.showSubGroupSheet) {
        SubGroupSheet(
            teamId = teamId,
            subGroups = state.subGroups,
            isCoachOrManager = state.isClubManager,
            onDismiss = { viewModel.toggleSubGroupSheet() },
            onCreateSubGroup = { name -> viewModel.createSubGroup(teamId, name) },
            onDeleteSubGroup = { subGroupId -> viewModel.deleteSubGroup(teamId, subGroupId) }
        )
    }
}

@Composable
private fun RosterSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
fun MemberItem(
    member: TeamMember,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .testTag("member_item_${member.userId}")
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (member.avatarUrl != null) {
                AsyncImage(
                    model = member.avatarUrl,
                    contentDescription = member.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = memberInitials(member.displayName),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = member.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            val subtitle = if (member.role == "player") {
                listOfNotNull(
                    member.jerseyNumber?.let { "#$it" },
                    member.position
                ).joinToString(" · ").ifEmpty { "Player" }
            } else {
                member.role.replaceFirstChar { it.uppercase() }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
