package ch.teamorg.ui.events

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.teamorg.ui.theme.PillShape
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditEventScreen(
    viewModel: CreateEditEventViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showRecurringSheet by remember { mutableStateOf(false) }
    var showScopeSheet by remember { mutableStateOf(false) }
    var showSubgroupSheet by remember { mutableStateOf(false) }

    // Date/time picker dialog states
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showMeetupTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is FormEvent.SaveSuccess -> onSaved()
                is FormEvent.CancelSuccess -> onSaved()
            }
        }
    }

    // Auto-open recurring sheet when recurring is enabled without a pattern
    LaunchedEffect(state.recurringEnabled, state.recurringPattern) {
        if (state.recurringEnabled && state.recurringPattern == null) {
            showRecurringSheet = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // -- HEADER --
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = if (state.isEditMode) "Edit event" else "New event",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        // -- ERROR BANNER --
        if (state.saveError != null) {
            Text(
                text = state.saveError!!,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        // -- SCROLLABLE BODY --
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ===== TITLE =====
            FilledField(label = "Title", onClick = null) {
                FieldTextInput(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    placeholder = "Event title"
                )
            }
            if (state.titleError != null) {
                FieldError(state.titleError!!)
            }

            // ===== TYPE =====
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Type")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypePill("Training", state.type == "training") { viewModel.setType("training") }
                    TypePill("Match", state.type == "match") { viewModel.setType("match") }
                    TypePill("Other", state.type == "other") { viewModel.setType("other") }
                }
            }

            // ===== DATE & TIME =====
            val isMultiDay = state.startDate != state.endDate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledField(
                    label = "Starts",
                    onClick = { showStartDatePicker = true },
                    modifier = Modifier.weight(1.4f)
                ) {
                    Text(
                        "${formatShortDate(state.startDate)} · ${formatTime(state.startTime)}",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.clickable { showStartTimePicker = true }
                    )
                }
                FilledField(
                    label = "Ends",
                    onClick = { showEndTimePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (isMultiDay) "${formatShortDate(state.endDate)} · ${formatTime(state.endTime)}"
                        else formatTime(state.endTime),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.clickable { showEndDatePicker = true }
                    )
                }
            }
            if (state.endTimeError != null) {
                FieldError(state.endTimeError!!)
            }

            // Meetup time
            FilledField(
                label = "Meetup time (optional)",
                onClick = {
                    if (!state.meetupEnabled) viewModel.toggleMeetup(true)
                    showMeetupTimePicker = true
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (state.meetupEnabled) formatTime(state.meetupTime) else "None",
                        color = if (state.meetupEnabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.meetupEnabled) {
                        Text(
                            "✕",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { viewModel.toggleMeetup(false) }
                        )
                    }
                }
            }

            // Location
            FilledField(label = "Location", onClick = null) {
                FieldTextInput(
                    value = state.location,
                    onValueChange = viewModel::setLocation,
                    placeholder = "Venue or address"
                )
            }

            // Description
            FilledField(label = "Description", onClick = null) {
                FieldTextInput(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    placeholder = "Add details for your team...",
                    singleLine = false,
                    minLines = 2
                )
            }

            // ===== TEAMS & SUBGROUPS =====
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Teams & subgroups")
                if (state.availableTeams.isEmpty()) {
                    Text(
                        "No teams",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.availableTeams.forEach { team ->
                            TypePill(
                                label = team.name,
                                selected = team.id in state.selectedTeamIds,
                                onClick = { viewModel.toggleTeam(team.id) }
                            )
                        }
                    }
                }
                if (state.teamError != null) {
                    FieldError(state.teamError!!)
                }

                // Sub-groups selector
                FilledField(
                    label = "Sub-groups",
                    onClick = { showSubgroupSheet = true }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val subgroupLabel = if (state.selectedSubgroupIds.isEmpty()) {
                            "All players"
                        } else {
                            val names = state.availableSubgroups
                                .filter { it.id in state.selectedSubgroupIds }
                                .joinToString(", ") { it.name }
                            names.ifEmpty { "All players" }
                        }
                        Text(
                            subgroupLabel,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ===== REPEATS =====
            val isSeriesLocked = state.isEditMode && state.isSeriesEvent
            val recurringPattern = state.recurringPattern
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .animateContentSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (!isSeriesLocked && state.recurringEnabled)
                                Modifier.clickable { showRecurringSheet = true }
                            else Modifier
                        )
                ) {
                    Text(
                        "Repeats",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            isSeriesLocked -> "Part of a series"
                            state.recurringEnabled && recurringPattern != null ->
                                buildRecurringSummary(recurringPattern)
                            else -> "Does not repeat"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (!isSeriesLocked) {
                    Switch(
                        checked = state.recurringEnabled,
                        onCheckedChange = viewModel::setRecurringEnabled
                    )
                }
            }

            // ===== MINIMUM ATTENDEES =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .animateContentSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Minimum attendees (optional)",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.minAttendeesEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                "−",
                                color = if (state.minAttendees > 1) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    if (state.minAttendees > 1) viewModel.setMinAttendees(state.minAttendees - 1)
                                }
                            )
                            Text(
                                state.minAttendees.toString(),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "+",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    viewModel.setMinAttendees(state.minAttendees + 1)
                                }
                            )
                        }
                    }
                }
                Switch(
                    checked = state.minAttendeesEnabled,
                    onCheckedChange = viewModel::toggleMinAttendees
                )
            }

            // Auto-scroll to bottom when min attendees expands
            LaunchedEffect(state.minAttendeesEnabled) {
                if (state.minAttendeesEnabled) {
                    kotlinx.coroutines.delay(150) // wait for animateContentSize to start
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }
        }

        // -- CTA BUTTON --
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp)
        ) {
            Button(
                onClick = {
                    if (state.isEditMode && state.isSeriesEvent) {
                        showScopeSheet = true
                    } else {
                        viewModel.save()
                    }
                },
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(57.dp),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        if (state.isEditMode) "Save changes" else "Create event",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Date pickers
    if (showStartDatePicker) {
        EventDatePickerDialog(
            initialDate = state.startDate,
            onDateSelected = { date ->
                viewModel.setStartDate(date)
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }

    if (showEndDatePicker) {
        EventDatePickerDialog(
            initialDate = state.endDate,
            onDateSelected = { date ->
                viewModel.setEndDate(date)
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false }
        )
    }

    // Time pickers
    if (showStartTimePicker) {
        EventTimePickerDialog(
            initialTime = state.startTime,
            onTimeSelected = { time ->
                viewModel.setStartTime(time)
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }
    if (showEndTimePicker) {
        EventTimePickerDialog(
            initialTime = state.endTime,
            onTimeSelected = { time ->
                viewModel.setEndTime(time)
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }
    if (showMeetupTimePicker) {
        EventTimePickerDialog(
            initialTime = state.meetupTime,
            onTimeSelected = { time ->
                viewModel.setMeetupTime(time)
                showMeetupTimePicker = false
            },
            onDismiss = { showMeetupTimePicker = false }
        )
    }

    // Recurring pattern bottom sheet
    if (showRecurringSheet) {
        RecurringPatternSheet(
            initialPattern = state.recurringPattern ?: RecurringPatternState(),
            onDone = { pattern ->
                viewModel.setRecurringPattern(pattern)
                showRecurringSheet = false
            },
            onDismiss = {
                if (state.recurringPattern == null) {
                    viewModel.setRecurringEnabled(false)
                }
                showRecurringSheet = false
            }
        )
    }

    // Scope sheet for edit of recurring events
    if (showScopeSheet) {
        RecurringScopeSheet(
            mode = "edit",
            onContinue = { scope ->
                showScopeSheet = false
                viewModel.save(scope)
            },
            onDismiss = { showScopeSheet = false }
        )
    }

    // Sub-groups bottom sheet
    if (showSubgroupSheet) {
        SubgroupsSheet(
            subgroups = state.availableSubgroups,
            selectedIds = state.selectedSubgroupIds,
            onToggle = viewModel::toggleSubgroup,
            onDismiss = { showSubgroupSheet = false }
        )
    }
}

// ─── Reusable M3E components ───

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun FieldError(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
    )
}

/** Filled tonal field with small primary label above content (Figma "filled field" pattern). */
@Composable
private fun FilledField(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@Composable
private fun FieldTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.bodyLarge.fontSize
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                innerTextField()
            }
        }
    )
}

/** Selectable pill ("✓ Training" style). */
@Composable
private fun TypePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (selected) {
            Text(
                "✓",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

private fun formatShortDate(date: LocalDate): String {
    val dayOfWeek = date.dayOfWeek.name.take(3).lowercase()
        .replaceFirstChar { it.uppercase() }
    val month = date.month.name.take(3).lowercase()
        .replaceFirstChar { it.uppercase() }
    return "$dayOfWeek ${date.dayOfMonth} $month"
}

private fun formatDate(date: LocalDate): String {
    val dayOfWeek = date.dayOfWeek.name.take(3).lowercase()
        .replaceFirstChar { it.uppercase() }
    val month = date.month.name.take(3).lowercase()
        .replaceFirstChar { it.uppercase() }
    return "$dayOfWeek, ${date.dayOfMonth} $month ${date.year}"
}

private fun formatTime(time: LocalTime): String {
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

private fun buildRecurringSummary(pattern: RecurringPatternState): String {
    val typePart = when (pattern.patternType) {
        "daily" -> "Daily"
        "weekly" -> {
            if (pattern.weekdays.isEmpty()) {
                "Weekly"
            } else {
                val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                val days = pattern.weekdays.sorted().joinToString(", ") { dayNames[it] }
                "Weekly · $days"
            }
        }
        else -> "Every ${pattern.intervalDays} days"
    }
    val endPart = if (pattern.hasEndDate && pattern.endDate != null) {
        " · until ${formatDate(pattern.endDate)}"
    } else ""
    return "$typePart$endPart"
}

// ─── Date/Time Picker Dialogs (kept from existing code) ───

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDatePickerDialog(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = run {
            val epochDays = initialDate.toEpochDays()
            epochDays * 24L * 60 * 60 * 1000
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = datePickerState.selectedDateMillis
                if (millis != null) {
                    val epochDays = (millis / (24L * 60 * 60 * 1000)).toInt()
                    onDateSelected(LocalDate.fromEpochDays(epochDays))
                } else {
                    onDismiss()
                }
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventTimePickerDialog(
    initialTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select time") },
        text = {
            TimePicker(state = timePickerState)
        },
        confirmButton = {
            TextButton(onClick = {
                onTimeSelected(LocalTime(timePickerState.hour, timePickerState.minute))
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─── Sub-groups bottom sheet ───

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubgroupsSheet(
    subgroups: List<ch.teamorg.domain.SubGroup>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Text(
                "Select sub-groups",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Select who this event is for",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )

            // "All Players" option
            val allSelected = selectedIds.isEmpty()
            SubgroupListItem(
                label = "All players",
                count = subgroups.sumOf { it.memberCount },
                selected = allSelected,
                onClick = {
                    // Deselect all sub-groups to mean "all players"
                    selectedIds.forEach { id -> onToggle(id) }
                }
            )

            // Individual sub-groups
            subgroups.forEach { subgroup ->
                SubgroupListItem(
                    label = subgroup.name,
                    count = subgroup.memberCount,
                    selected = subgroup.id in selectedIds,
                    onClick = { onToggle(subgroup.id) }
                )
            }

            // Apply button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(57.dp),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    "Done",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SubgroupListItem(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                count.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            if (selected) {
                Text(
                    "✓",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
