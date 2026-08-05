package ch.teamorg.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ch.teamorg.ui.components.TeamorgLoader
import coil3.compose.AsyncImage
import ch.teamorg.domain.DuplicateSuggestion
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
    onMemberClick: (String) -> Unit = {},
    onNdsImportClick: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var memberToRemove by remember { mutableStateOf<TeamMember?>(null) }
    var memberToPromote by remember { mutableStateOf<TeamMember?>(null) }
    var memberAction by remember { mutableStateOf<TeamMember?>(null) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteEmail by remember { mutableStateOf("") }

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
                TeamorgLoader(modifier = Modifier.align(Alignment.Center))
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

                    if (state.isCoachOrManager) {
                        item {
                            OutlinedButton(
                                onClick = { onNdsImportClick(state.clubId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("btn_nds_import"),
                                shape = PillShape
                            ) {
                                Text(
                                    "NDS-Import",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (state.duplicates.isNotEmpty()) {
                        item {
                            val n = state.duplicates.size
                            OutlinedButton(
                                onClick = { viewModel.openDuplicatesSheet() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("btn_review_duplicates"),
                                shape = PillShape
                            ) {
                                Text(
                                    if (n == 1) "1 possible duplicate — review"
                                    else "$n possible duplicates — review",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
            onDismissRequest = { showInviteDialog = false; inviteEmail = "" },
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            val hasEmail = inviteEmail.isNotBlank()
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
                    text = "Enter an email to invite one person privately — only that address can join " +
                        "(best for coaches). Leave it empty for a shareable link anyone can use.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = inviteEmail,
                    onValueChange = { inviteEmail = it },
                    label = { Text("Email (optional)") },
                    placeholder = { Text("person@example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().testTag("input_invite_email"),
                    shape = RoundedCornerShape(18.dp)
                )
                Button(
                    onClick = {
                        viewModel.createInvite(teamId, "player", inviteEmail)
                        showInviteDialog = false
                        inviteEmail = ""
                    },
                    modifier = Modifier.fillMaxWidth().height(57.dp).testTag("btn_invite_as_player"),
                    shape = PillShape
                ) {
                    Text(
                        if (hasEmail) "Email invite as Player" else "Invite as Player",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = {
                        viewModel.createInvite(teamId, "coach", inviteEmail)
                        showInviteDialog = false
                        inviteEmail = ""
                    },
                    modifier = Modifier.fillMaxWidth().height(57.dp).testTag("btn_invite_as_coach"),
                    shape = PillShape
                ) {
                    Text(
                        if (hasEmail) "Email invite as Coach" else "Invite as Coach",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Email-invite confirmation sheet
    state.inviteSentTo?.let { email ->
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
                    text = "Invite Sent",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "We emailed an invite to $email. Only that address can use it to join.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { viewModel.resetInvite() },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_invite_sent_done"),
                    shape = PillShape
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
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

    // Duplicate review sheet
    if (state.showDuplicatesSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeDuplicatesSheet() },
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Possible duplicates",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "These imported members may already have an account. Merging moves their " +
                        "imported history to that account and deletes the provisional one. This can't be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (state.mergedCount > 0) {
                    Text(
                        if (state.mergedCount == 1) "1 member merged."
                        else "${state.mergedCount} members merged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("txt_merge_success")
                    )
                }

                if (state.mergeError != null) {
                    Text(
                        state.mergeError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("txt_merge_error")
                    )
                }

                if (state.duplicates.isEmpty()) {
                    Text(
                        "Nothing left to review.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                state.duplicates.forEach { suggestion ->
                    DuplicateSuggestionCard(
                        suggestion = suggestion,
                        mergeInProgress = state.mergeInProgress,
                        onMerge = { userId -> viewModel.mergeDuplicate(teamId, suggestion.memberId, userId) }
                    )
                }
            }
        }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (member.provisional) {
                    // Import placeholder holding attendance for someone without an account yet.
                    // Only coaches and club managers ever receive these rows from the server.
                    Text(
                        text = "Provisional",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("chip_provisional_${member.userId}")
                    )
                }
            }
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

@Composable
private fun DuplicateSuggestionCard(
    suggestion: DuplicateSuggestion,
    mergeInProgress: Boolean,
    onMerge: (String) -> Unit
) {
    var selectedUserId by remember(suggestion.memberId) { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp)
            .testTag("card_duplicate_${suggestion.memberId}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "${suggestion.firstName} ${suggestion.lastName}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            listOfNotNull(
                suggestion.birthDate,
                suggestion.personNumber?.let { "No. $it" },
                suggestion.funktion
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Moves ${pluralize(suggestion.willMove.attendance, "attendance", "attendances")}, " +
                "${pluralize(suggestion.willMove.subgroups, "group", "groups")}, " +
                "${pluralize(suggestion.willMove.rules, "absence rule", "absence rules")}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        suggestion.candidates.forEach { candidate ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { selectedUserId = candidate.userId }
                    .padding(vertical = 6.dp)
                    .testTag("candidate_${suggestion.memberId}_${candidate.userId}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioButton(
                    selected = selectedUserId == candidate.userId,
                    onClick = { selectedUserId = candidate.userId }
                )
                Text(
                    if (candidate.score == "HIGH") "${candidate.displayName} (very likely)"
                    else candidate.displayName,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Button(
            onClick = { selectedUserId?.let(onMerge) },
            enabled = selectedUserId != null && !mergeInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_merge_${suggestion.memberId}"),
            shape = PillShape
        ) {
            Text("Merge", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** English uses the plural for 0 ("0 groups"), so key on == 1, never on > 1. */
private fun pluralize(count: Int, singular: String, pluralForm: String): String =
    "$count ${if (count == 1) singular else pluralForm}"
