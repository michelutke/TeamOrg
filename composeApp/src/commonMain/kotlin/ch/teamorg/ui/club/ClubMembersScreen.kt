package ch.teamorg.ui.club

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.teamorg.domain.ClubUser
import ch.teamorg.domain.Team
import ch.teamorg.domain.TeamRoleRef
import ch.teamorg.ui.theme.PillShape
import ch.teamorg.ui.theme.extendedColors

private val ROLES = listOf("player" to "Player", "coach" to "Coach")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubMembersScreen(
    clubId: String,
    viewModel: ClubMembersViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    // Initialize VM with the clubId passed from nav
    LaunchedEffect(clubId) { viewModel.init(clubId) }

    // Trigger loadMore when last item is near
    val layoutInfo = listState.layoutInfo
    LaunchedEffect(layoutInfo) {
        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        val total = layoutInfo.totalItemsCount
        if (total > 0 && lastVisible >= total - 3 && !state.loading && !state.endReached) {
            viewModel.loadMore()
        }
    }

    // Action sheet state
    var actionTarget by remember { mutableStateOf<ClubUser?>(null) }
    var showInviteSheet by remember { mutableStateOf(false) }

    // Snackbar for invite confirmation / error
    val snackbarHostState = remember { SnackbarHostState() }
    val inviteSentTo = state.inviteSentTo
    val error = state.error

    LaunchedEffect(inviteSentTo) {
        if (inviteSentTo != null) {
            snackbarHostState.showSnackbar("Invite sent to $inviteSentTo")
            viewModel.clearInviteSent()
        }
    }
    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Action bottom sheet for a specific member
    val target = actionTarget
    if (target != null) {
        MemberActionSheet(
            user = target,
            teams = state.teams,
            onDismiss = { actionTarget = null },
            onAddToTeam = { teamId, role ->
                viewModel.addToTeam(teamId, target.userId, role)
                actionTarget = null
            },
            onChangeRole = { teamId, role ->
                viewModel.changeRole(teamId, target.userId, role)
                actionTarget = null
            },
            onRemove = { teamId ->
                viewModel.remove(teamId, target.userId)
                actionTarget = null
            }
        )
    }

    if (showInviteSheet) {
        InviteByEmailSheet(
            teams = state.teams,
            onDismiss = { showInviteSheet = false },
            onInvite = { teamId, role, email ->
                viewModel.invite(teamId, role, email)
                showInviteSheet = false
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Club Members",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showInviteSheet = true }) {
                        Text("Invite")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Filter field (no minimum length)
            OutlinedTextField(
                value = state.filter,
                onValueChange = viewModel::setFilter,
                placeholder = { Text("Filter by name or email") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    if (state.filter.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setFilter("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear filter")
                        }
                    }
                }
            )

            val filtered = if (state.filter.isBlank()) {
                state.users
            } else {
                val q = state.filter.trim().lowercase()
                state.users.filter {
                    it.displayName.lowercase().contains(q) || it.email.lowercase().contains(q)
                }
            }

            when {
                state.loading && state.users.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
                filtered.isEmpty() && !state.loading -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = if (state.filter.isBlank()) "No members found" else "No matches for \"${state.filter}\"",
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { it.userId }) { user ->
                            MemberRow(
                                user = user,
                                onMenuClick = { actionTarget = user }
                            )
                        }
                        if (state.loading) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    user: ClubUser,
    onMenuClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar initials
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            val initials = user.displayName
                .split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercase() }
            Text(
                text = initials,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (user.teamRoles.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    user.teamRoles.take(3).forEach { ref ->
                        RoleChip(ref)
                    }
                    if (user.teamRoles.size > 3) {
                        Text(
                            text = "+${user.teamRoles.size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }

        IconButton(onClick = onMenuClick) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Member actions",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RoleChip(ref: TeamRoleRef) {
    val containerColor = when (ref.role) {
        "coach" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val labelColor = when (ref.role) {
        "coach" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                text = "${ref.teamName} · ${ref.role.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.labelSmall
            )
        },
        shape = PillShape,
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = containerColor,
            labelColor = labelColor
        ),
        border = null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberActionSheet(
    user: ClubUser,
    teams: List<Team>,
    onDismiss: () -> Unit,
    onAddToTeam: (teamId: String, role: String) -> Unit,
    onChangeRole: (teamId: String, role: String) -> Unit,
    onRemove: (teamId: String) -> Unit
) {
    var selectedTeamId by remember { mutableStateOf(teams.firstOrNull()?.id ?: "") }
    var selectedRole by remember { mutableStateOf("player") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // Add to team section
            if (teams.isNotEmpty()) {
                Text(
                    text = "Add to team",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Team selector — scrollable so all teams are reachable
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(teams) { team ->
                        FilterChip(
                            selected = selectedTeamId == team.id,
                            onClick = { selectedTeamId = team.id },
                            label = { Text(team.name, style = MaterialTheme.typography.labelSmall) },
                            shape = PillShape
                        )
                    }
                }

                // Role selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ROLES.forEach { (value, label) ->
                        FilterChip(
                            selected = selectedRole == value,
                            onClick = { selectedRole = value },
                            label = { Text(label) },
                            shape = PillShape
                        )
                    }
                }

                Button(
                    onClick = { if (selectedTeamId.isNotEmpty()) onAddToTeam(selectedTeamId, selectedRole) },
                    enabled = selectedTeamId.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = PillShape
                ) {
                    Text("Add to team", fontWeight = FontWeight.Bold)
                }
            }

            // Per-team actions (change role / remove)
            if (user.teamRoles.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Manage team membership",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                user.teamRoles.forEach { ref ->
                    TeamMembershipRow(
                        ref = ref,
                        onChangeRole = { role -> onChangeRole(ref.teamId, role) },
                        onRemove = { onRemove(ref.teamId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamMembershipRow(
    ref: TeamRoleRef,
    onChangeRole: (String) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = ref.teamName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        // Current role chip with dropdown to change
        Box {
            SuggestionChip(
                onClick = { expanded = true },
                label = { Text(ref.role.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) },
                shape = PillShape
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ROLES.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { expanded = false; onChangeRole(value) }
                    )
                }
            }
        }
        TextButton(
            onClick = onRemove,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Remove", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteByEmailSheet(
    teams: List<Team>,
    onDismiss: () -> Unit,
    onInvite: (teamId: String, role: String, email: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var selectedTeamId by remember { mutableStateOf(teams.firstOrNull()?.id ?: "") }
    var selectedRole by remember { mutableStateOf("player") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Invite by email",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            if (teams.isNotEmpty()) {
                Text(
                    text = "Team",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Scrollable so all teams are reachable regardless of count
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(teams) { team ->
                        FilterChip(
                            selected = selectedTeamId == team.id,
                            onClick = { selectedTeamId = team.id },
                            label = { Text(team.name, style = MaterialTheme.typography.labelSmall) },
                            shape = PillShape
                        )
                    }
                }
            }

            Text(
                text = "Role",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ROLES.forEach { (value, label) ->
                    FilterChip(
                        selected = selectedRole == value,
                        onClick = { selectedRole = value },
                        label = { Text(label) },
                        shape = PillShape
                    )
                }
            }

            Button(
                onClick = {
                    if (email.isNotBlank() && selectedTeamId.isNotEmpty()) {
                        onInvite(selectedTeamId, selectedRole, email.trim())
                    }
                },
                enabled = email.isNotBlank() && selectedTeamId.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = PillShape
            ) {
                Text("Send Invite", fontWeight = FontWeight.Bold)
            }
        }
    }
}
