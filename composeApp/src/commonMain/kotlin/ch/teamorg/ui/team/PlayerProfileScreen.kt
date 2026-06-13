package ch.teamorg.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ch.teamorg.domain.AbwesenheitRule
import ch.teamorg.ui.attendance.AbsenceCard
import ch.teamorg.ui.attendance.AddAbsenceSheet
import ch.teamorg.ui.attendance.AttendanceStatsBar
import ch.teamorg.ui.theme.PillShape
import ch.teamorg.ui.util.rememberImagePickerLauncher
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProfileScreen(
    teamId: String,
    userId: String,
    viewModel: PlayerProfileViewModel,
    onBack: () -> Unit,
    onLeftTeam: () -> Unit,
    isNavProfile: Boolean = false  // true = bottom nav profile tab, false = member detail
) {
    val state by viewModel.state.collectAsState()
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showJerseyDialog by remember { mutableStateOf(false) }
    var showPositionDialog by remember { mutableStateOf(false) }
    var showAddAbsenceSheet by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<AbwesenheitRule?>(null) }
    var deleteTargetRule by remember { mutableStateOf<AbwesenheitRule?>(null) }

    LaunchedEffect(state.leftTeam) {
        if (state.leftTeam) onLeftTeam()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Header bar (back only on member detail variant)
            if (!isNavProfile) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }

            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.member == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Player not found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    val member = state.member!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                    ) {
                        // Hero: centered avatar + name + jersey/position
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val pickImage = rememberImagePickerLauncher { bytes, ext ->
                                viewModel.uploadAvatar(teamId, userId, bytes, ext)
                            }
                            val avatarContainer = if (isNavProfile) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                            val avatarContent = if (isNavProfile) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                            Box(
                                modifier = Modifier.size(96.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .background(avatarContainer)
                                        .then(
                                            if (state.isOwnProfile) Modifier.clickable { pickImage() } else Modifier
                                        ),
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
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = avatarContent
                                        )
                                    }
                                }
                                if (state.isOwnProfile) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.CameraAlt,
                                            contentDescription = "Upload avatar",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }

                            Text(
                                text = member.displayName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            val info = listOfNotNull(
                                member.jerseyNumber?.let { "#$it" },
                                member.position
                            ).joinToString(" · ")
                            if (info.isNotEmpty()) {
                                Text(
                                    text = info,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Role pill
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = PillShape
                            ) {
                                Text(
                                    text = member.role.replaceFirstChar { it.uppercase() },
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        AttendanceStatsBar(presencePct = state.presencePct)

                        Spacer(Modifier.height(24.dp))

                        // My Absences section header
                        Text(
                            text = "My absences",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(12.dp))

                        // Absence list or empty state
                        val displayedRules = state.absenceRules
                        if (displayedRules.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "No absences",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Add an absence rule to automatically decline events.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                displayedRules.forEach { rule ->
                                    AbsenceCard(
                                        rule = rule,
                                        onClick = {
                                            editingRule = rule
                                            showAddAbsenceSheet = true
                                        },
                                        onLongPress = { deleteTargetRule = rule }
                                    )
                                }
                            }
                        }

                        // Backfill hint
                        if (state.backfillStatus == "pending") {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "Applying absence rule to matching events...",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        state.error?.let { error ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Coach-editable fields
                        if (state.isCoachOrManager) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(horizontal = 18.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            "Jersey Number",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = member.jerseyNumber?.let { "#$it" } ?: "Not set",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    TextButton(onClick = { showJerseyDialog = true }) {
                                        Text("Edit")
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            "Position",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = member.position ?: "Not set",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    TextButton(onClick = { showPositionDialog = true }) {
                                        Text("Edit")
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        if (state.isOwnProfile && member.role == "player") {
                            TextButton(
                                onClick = { showLeaveDialog = true },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = PillShape,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(
                                    "Leave team",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        // Bottom padding for FAB
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }

        // FAB — Add absence (own profile or coach viewing member)
        val canManageAbsences = state.isOwnProfile || state.isCoachOrManager
        if (canManageAbsences) {
            FloatingActionButton(
                onClick = {
                    editingRule = null
                    showAddAbsenceSheet = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = if (isNavProfile) 112.dp else 24.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add absence")
            }
        }
    }

    // Add/Edit absence sheet
    AddAbsenceSheet(
        visible = showAddAbsenceSheet,
        editingRule = editingRule,
        onDismiss = {
            showAddAbsenceSheet = false
            editingRule = null
        },
        onSave = { request ->
            showAddAbsenceSheet = false
            val editing = editingRule
            if (editing != null) {
                viewModel.updateAbsence(
                    editing.id,
                    ch.teamorg.domain.UpdateAbwesenheitRequest(
                        presetType = request.presetType,
                        label = request.label,
                        bodyPart = request.bodyPart,
                        ruleType = request.ruleType,
                        weekdays = request.weekdays,
                        startDate = request.startDate,
                        endDate = request.endDate
                    )
                )
            } else {
                viewModel.createAbsence(request)
            }
            editingRule = null
        }
    )

    // Delete confirm dialog
    deleteTargetRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleteTargetRule = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Delete absence rule?", fontWeight = FontWeight.Bold) },
            text = { Text("This rule will be removed and will no longer auto-decline matching events.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAbsence(rule.id)
                        deleteTargetRule = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Rule")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetRule = null }) {
                    Text("Keep Rule")
                }
            }
        )
    }

    // Leave confirmation dialog
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Leave Team", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to leave this team? You will need a new invitation to rejoin.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveDialog = false
                        viewModel.leaveTeam(teamId)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Edit jersey dialog
    if (showJerseyDialog) {
        TextFieldDialog(
            title = "Edit Jersey Number",
            initialValue = state.member?.jerseyNumber?.toString() ?: "",
            placeholder = "Jersey number",
            keyboardType = KeyboardType.Number,
            onConfirm = { text ->
                showJerseyDialog = false
                viewModel.updateJerseyNumber(teamId, userId, text.toIntOrNull())
            },
            onDismiss = { showJerseyDialog = false }
        )
    }

    // Edit position dialog
    if (showPositionDialog) {
        TextFieldDialog(
            title = "Edit Position",
            initialValue = state.member?.position ?: "",
            placeholder = "Position (e.g. Forward)",
            onConfirm = { text ->
                showPositionDialog = false
                viewModel.updatePosition(teamId, userId, text.ifBlank { null })
            },
            onDismiss = { showPositionDialog = false }
        )
    }
}

@Composable
private fun TextFieldDialog(
    title: String,
    initialValue: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
