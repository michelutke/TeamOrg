package ch.teamorg.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.teamorg.domain.AttendanceResponse
import ch.teamorg.domain.EventWithTeams
import ch.teamorg.domain.TeamMember
import ch.teamorg.ui.attendance.AttendanceRsvpButtons
import ch.teamorg.ui.attendance.BegrundungSheet
import ch.teamorg.ui.attendance.MemberResponseList
import ch.teamorg.ui.attendance.ResponseDeadlineLabel
import ch.teamorg.ui.inbox.ReminderPickerSheet
import ch.teamorg.ui.inbox.formatLeadTime
import ch.teamorg.ui.theme.PillShape
import ch.teamorg.ui.theme.extendedColors
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun formatDay(instant: Instant): String {
    val l = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val d = l.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val m = l.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$d, ${l.dayOfMonth} $m"
}

private fun formatTime(instant: Instant): String {
    val l = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${l.hour.toString().padStart(2, '0')}:${l.minute.toString().padStart(2, '0')}"
}

// Default status when opening coach-edit sheet
private fun coachEditDefault(currentStatus: String?): String = when (currentStatus) {
    "declined", "declined-auto" -> "declined"
    else -> "confirmed"    // unsure / no-response / confirmed → confirmed
}

@Composable
fun EventDetailScreen(
    viewModel: EventDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onCancel: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showCancelScopeSheet by remember { mutableStateOf(false) }
    var showUncancelScopeSheet by remember { mutableStateOf(false) }
    var showBegrundung by remember { mutableStateOf(false) }
    var begrundungStatus by remember { mutableStateOf("unsure") }
    var showReminderSheet by remember { mutableStateOf(false) }

    // Coach edit sheet state
    var coachEditTarget by remember { mutableStateOf<AttendanceResponse?>(null) }
    var coachEditStatus by remember { mutableStateOf("confirmed") }
    var coachEditUnexcused by remember { mutableStateOf(false) }

    val isCancelled = state.event?.event?.status == "cancelled"
    val isSeries = state.event?.event?.seriesId != null
    val checkInStatus = state.event?.event?.checkInStatus ?: "open"
    val rsvpLocked = checkInStatus != "open"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (state.isCoach) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (!isCancelled) {
                            DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEdit() })
                            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { showMenu = false; onDuplicate() })
                            DropdownMenuItem(
                                text = { Text("Cancel event", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    if (isSeries) showCancelScopeSheet = true
                                    else viewModel.cancelEvent("this_only")
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Restore event", color = MaterialTheme.extendedColors.going) },
                                onClick = {
                                    showMenu = false
                                    if (isSeries) showUncancelScopeSheet = true
                                    else viewModel.uncancelEvent("this_only")
                                }
                            )
                            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { showMenu = false; onDuplicate() })
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.size(20.dp))
            }
        }

        if (showCancelScopeSheet) {
            RecurringScopeSheet(
                mode = "cancel",
                onContinue = { scope ->
                    viewModel.cancelEvent(scope)
                    showCancelScopeSheet = false
                },
                onDismiss = { showCancelScopeSheet = false }
            )
        }

        if (showUncancelScopeSheet) {
            RecurringScopeSheet(
                mode = "uncancel",
                onContinue = { scope ->
                    viewModel.uncancelEvent(scope)
                    showUncancelScopeSheet = false
                },
                onDismiss = { showUncancelScopeSheet = false }
            )
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
            }
            state.event != null -> EventDetailBody(
                ewt = state.event!!,
                myResponse = state.myResponse,
                confirmedCount = state.confirmedCount,
                maybeCount = state.maybeCount,
                declinedCount = state.declinedCount,
                responseDeadline = state.responseDeadline,
                deadlinePassed = state.deadlinePassed,
                attendanceResponses = state.attendanceResponses,
                rosterMap = state.rosterMap,
                isCoach = state.isCoach,
                checkInStatus = checkInStatus,
                rsvpLocked = rsvpLocked,
                reminderLeadMinutes = state.reminderLeadMinutes,
                isFinalizingOrReopening = state.isFinalizingOrReopening,
                onReminderTap = { showReminderSheet = true },
                onRsvpSelect = { status ->
                    if (status == "unsure" || status == "declined") {
                        begrundungStatus = status
                        showBegrundung = true
                    } else {
                        viewModel.submitResponse(status, null)
                    }
                },
                onEditResponseTap = { response ->
                    coachEditTarget = response
                    coachEditStatus = coachEditDefault(response.status)
                    coachEditUnexcused = false
                },
                onFinalize = { viewModel.finalize() },
                onReopen = { viewModel.reopen() }
            )
        }

        BegrundungSheet(
            visible = showBegrundung,
            mode = begrundungStatus,
            onDismiss = { showBegrundung = false },
            onConfirm = { reason ->
                showBegrundung = false
                viewModel.submitResponse(begrundungStatus, reason.ifBlank { null })
            }
        )

        // Coach edit bottom sheet
        val editTarget = coachEditTarget
        if (editTarget != null) {
            CoachEditSheet(
                status = coachEditStatus,
                unexcused = coachEditUnexcused,
                onStatusChange = { coachEditStatus = it; if (it != "declined") coachEditUnexcused = false },
                onUnexcusedChange = { coachEditUnexcused = it },
                onConfirm = {
                    viewModel.setMemberResponse(editTarget.userId, coachEditStatus, coachEditUnexcused)
                    coachEditTarget = null
                },
                onDismiss = { coachEditTarget = null }
            )
        }

        // Finalize blocked dialog
        val blocked = state.finalizeBlocked
        if (blocked != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissFinalizeBlocked() },
                title = { Text("CheckIn konnte nicht abgeschlossen werden") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(blocked.reason)
                        if (blocked.memberNames.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            blocked.memberNames.forEach { name ->
                                Text(
                                    text = "• $name",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissFinalizeBlocked() }) {
                        Text("OK")
                    }
                }
            )
        }

        val eventId = state.event?.event?.id
        if (showReminderSheet && eventId != null) {
            ReminderPickerSheet(
                currentLeadMinutes = state.reminderLeadMinutes,
                onConfirm = { minutes ->
                    viewModel.setReminderOverride(eventId, minutes)
                    showReminderSheet = false
                },
                onRemove = {
                    viewModel.setReminderOverride(eventId, null)
                    showReminderSheet = false
                },
                onDismiss = { showReminderSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoachEditSheet(
    status: String,
    unexcused: Boolean,
    onStatusChange: (String) -> Unit,
    onUnexcusedChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Anwesenheit bearbeiten",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = status == "confirmed",
                    onClick = { onStatusChange("confirmed") },
                    label = { Text("Anwesend") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = status == "declined",
                    onClick = { onStatusChange("declined") },
                    label = { Text("Abgemeldet") },
                    modifier = Modifier.weight(1f)
                )
            }

            if (status == "declined") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUnexcusedChange(!unexcused) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Nicht entschuldigt",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = unexcused,
                        onCheckedChange = onUnexcusedChange
                    )
                }
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Speichern")
            }
        }
    }
}

@Composable
private fun EventDetailBody(
    ewt: EventWithTeams,
    myResponse: String?,
    confirmedCount: Int,
    maybeCount: Int,
    declinedCount: Int,
    responseDeadline: Instant?,
    deadlinePassed: Boolean,
    attendanceResponses: List<AttendanceResponse>,
    rosterMap: Map<String, TeamMember>,
    isCoach: Boolean,
    checkInStatus: String,
    rsvpLocked: Boolean,
    reminderLeadMinutes: Int?,
    isFinalizingOrReopening: Boolean,
    onReminderTap: () -> Unit,
    onRsvpSelect: (String) -> Unit,
    onEditResponseTap: (AttendanceResponse) -> Unit,
    onFinalize: () -> Unit,
    onReopen: () -> Unit
) {
    val event = ewt.event
    val isCancelled = event.status == "cancelled"
    val startLocal = event.startAt.toLocalDateTime(TimeZone.currentSystemDefault())
    val endLocal = event.endAt.toLocalDateTime(TimeZone.currentSystemDefault())
    val isMultiDay = startLocal.date != endLocal.date

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isCancelled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    "This event has been cancelled",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        HeroCard(
            ewt = ewt,
            isCoach = isCoach,
            isMultiDay = isMultiDay,
            responseDeadline = responseDeadline
        )

        // RSVP section
        Column(modifier = Modifier.fillMaxWidth()) {
            AttendanceRsvpButtons(
                currentResponse = myResponse,
                confirmedCount = confirmedCount,
                maybeCount = maybeCount,
                declinedCount = declinedCount,
                deadlinePassed = deadlinePassed || rsvpLocked,
                compact = false,
                onSelect = onRsvpSelect
            )
            if (rsvpLocked) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Zeit zum An-/Abmelden abgelaufen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (responseDeadline != null && !rsvpLocked) {
                Spacer(modifier = Modifier.height(8.dp))
                ResponseDeadlineLabel(deadline = responseDeadline)
            }
        }

        // Coach finalize / reopen buttons
        if (isCoach) {
            when (checkInStatus) {
                "awaiting_checkin" -> {
                    Button(
                        onClick = onFinalize,
                        enabled = !isFinalizingOrReopening,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isFinalizingOrReopening) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("CheckIn abschliessen")
                        }
                    }
                }
                "done" -> {
                    OutlinedButton(
                        onClick = onReopen,
                        enabled = !isFinalizingOrReopening,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isFinalizingOrReopening) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("CheckIn wieder öffnen")
                        }
                    }
                }
                else -> {}
            }
        }

        // Description card
        val description = event.description
        if (!description.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Description",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Reminder row
        val reminderText = when {
            reminderLeadMinutes == null -> "Global default (2 h)"
            reminderLeadMinutes == -1 -> "No reminder"
            else -> formatLeadTime(reminderLeadMinutes)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable { onReminderTap() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Reminder",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            Text(
                reminderText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }

        // Member response list
        MemberResponseList(
            responses = attendanceResponses,
            isCoach = isCoach,
            checkInStatus = checkInStatus,
            rosterMap = rosterMap,
            onEditTap = onEditResponseTap
        )
    }
}

@Composable
private fun HeroCard(
    ewt: EventWithTeams,
    isCoach: Boolean,
    isMultiDay: Boolean,
    responseDeadline: Instant?
) {
    val event = ewt.event
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    when (event.type) {
                        "training" -> "Training"
                        "match" -> "Match"
                        else -> "Other"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            if (event.externalSource == "nds" && event.presentCount > 0) {
                val ext = MaterialTheme.extendedColors
                Text(
                    text = "${event.presentCount} anwesend",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.going,
                    modifier = Modifier
                        .clip(PillShape)
                        .background(ext.goingContainer)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }

        Text(
            text = event.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (isCoach) {
            CoachInfoChips(ewt = ewt, isMultiDay = isMultiDay)
        } else {
            PlayerInfoTiles(ewt = ewt, isMultiDay = isMultiDay)
        }

        if (responseDeadline != null) {
            val l = responseDeadline.toLocalDateTime(TimeZone.currentSystemDefault())
            val day = l.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val month = l.month.name.take(4).lowercase().replaceFirstChar { it.uppercase() }
            val hm = "${l.hour.toString().padStart(2, '0')}:${l.minute.toString().padStart(2, '0')}"
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Outlined.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "Respond by $day ${l.dayOfMonth} $month, $hm",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerInfoTiles(ewt: EventWithTeams, isMultiDay: Boolean) {
    val event = ewt.event
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoTile(
                icon = Icons.Outlined.CalendarToday,
                label = "Date",
                value = if (isMultiDay) "${formatDay(event.startAt)} – ${formatDay(event.endAt)}"
                else formatDay(event.startAt),
                modifier = Modifier.weight(1f)
            )
            InfoTile(
                icon = Icons.Outlined.Schedule,
                label = "Time",
                value = "${formatTime(event.startAt)} – ${formatTime(event.endAt)}",
                modifier = Modifier.weight(1f)
            )
        }
        val meetupAt = event.meetupAt
        if (meetupAt != null) {
            InfoTile(
                icon = Icons.Outlined.Flag,
                label = "Meetup",
                value = formatTime(meetupAt)
            )
        }
        val loc = event.location
        if (loc != null) {
            InfoTile(
                icon = Icons.Outlined.Place,
                label = "Location",
                value = loc
            )
        }
        if (ewt.matchedTeams.isNotEmpty()) {
            InfoTile(
                icon = Icons.Outlined.Groups,
                label = "Team",
                value = ewt.matchedTeams.joinToString(", ") { it.name }
            )
        }
    }
}

@Composable
private fun InfoTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CoachInfoChips(ewt: EventWithTeams, isMultiDay: Boolean) {
    val event = ewt.event
    @Composable
    fun InfoChip(icon: ImageVector, text: String) {
        Row(
            modifier = Modifier
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(
                Icons.Outlined.CalendarToday,
                if (isMultiDay) "${formatDay(event.startAt)} – ${formatDay(event.endAt)}"
                else formatDay(event.startAt)
            )
            InfoChip(Icons.Outlined.Schedule, "${formatTime(event.startAt)} – ${formatTime(event.endAt)}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val meetupAt = event.meetupAt
            if (meetupAt != null) {
                InfoChip(Icons.Outlined.Flag, "Meet ${formatTime(meetupAt)}")
            }
            val loc = event.location
            if (loc != null) {
                InfoChip(Icons.Outlined.Place, loc)
            }
        }
    }
}
