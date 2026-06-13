package ch.teamorg.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.teamorg.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    viewModel: NotificationSettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showReminderPicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Team picker
            if (state.teams.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.teams) { team ->
                        FilterChip(
                            selected = state.selectedTeamId == team.teamId,
                            onClick = { viewModel.selectTeam(team.teamId) },
                            label = { Text(team.teamName) },
                            shape = PillShape
                        )
                    }
                }
            }

            val settings = state.settings
            if (settings != null) {
                // Events section
                SettingsCard {
                    ToggleRow(
                        label = "New events",
                        checked = settings.eventsNew,
                        onCheckedChange = { viewModel.updateSetting("eventsNew", it) }
                    )
                    ToggleRow(
                        label = "Event changes",
                        checked = settings.eventsEdit,
                        onCheckedChange = { viewModel.updateSetting("eventsEdit", it) }
                    )
                    ToggleRow(
                        label = "Event cancellations",
                        checked = settings.eventsCancel,
                        onCheckedChange = { viewModel.updateSetting("eventsCancel", it) }
                    )
                }

                // Reminders section
                SectionHeader("Reminders")
                SettingsCard {
                    ToggleRow(
                        label = "Event reminders",
                        checked = settings.remindersEnabled,
                        onCheckedChange = { viewModel.updateSetting("remindersEnabled", it) }
                    )
                    if (settings.remindersEnabled) {
                        LeadTimeRow(
                            leadMinutes = settings.reminderLeadMinutes,
                            onClick = { showReminderPicker = true }
                        )
                    }
                }

                // Coach mode
                if (state.isCoach) {
                    SectionHeader("Coach mode")
                    SettingsCard {
                        RadioRow(
                            title = "Every response",
                            subtitle = "Get notified on each confirm / decline / unsure",
                            selected = settings.coachResponseMode == "per_response",
                            onClick = { viewModel.updateSetting("coachResponseMode", "per_response") }
                        )
                        RadioRow(
                            title = "Summary before event",
                            subtitle = "One list of missing responses before each event",
                            selected = settings.coachResponseMode == "summary",
                            onClick = { viewModel.updateSetting("coachResponseMode", "summary") }
                        )
                        ToggleRow(
                            label = "Player absences",
                            subtitle = "Notify me when an absence affects an event",
                            checked = settings.absencesEnabled,
                            onCheckedChange = { viewModel.updateSetting("absencesEnabled", it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (showReminderPicker) {
            val settings2 = state.settings
            ReminderPickerSheet(
                currentLeadMinutes = settings2?.reminderLeadMinutes,
                onConfirm = { minutes ->
                    viewModel.updateSetting("reminderLeadMinutes", minutes)
                    showReminderPicker = false
                },
                onRemove = null,
                onDismiss = { showReminderPicker = false }
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large
            )
            .padding(horizontal = 18.dp, vertical = 6.dp),
        content = content
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun RadioRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LeadTimeRow(leadMinutes: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Lead time",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatLeadTime(leadMinutes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.secondaryContainer, PillShape)
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

fun formatLeadTime(minutes: Int): String = when {
    minutes >= 1440 -> "${minutes / 1440} day(s) before"
    minutes >= 60 -> "${minutes / 60} hours before"
    else -> "$minutes min before"
}
