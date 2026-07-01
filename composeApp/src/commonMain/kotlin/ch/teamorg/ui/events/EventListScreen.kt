package ch.teamorg.ui.events

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.teamorg.domain.EventWithTeams
import ch.teamorg.domain.MatchedTeam
import ch.teamorg.ui.attendance.BegrundungSheet
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
private fun calendarEventColor(type: String, isCancelled: Boolean): Color = when {
    isCancelled -> MaterialTheme.colorScheme.outline
    type == "training" -> MaterialTheme.colorScheme.primary
    type == "match" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    viewModel: EventListViewModel,
    onEventClick: (String) -> Unit,
    onCreateClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showBegrundung by remember { mutableStateOf(false) }
    var begrundungEventId by remember { mutableStateOf("") }
    var begrundungStatus by remember { mutableStateOf("unsure") }

    BegrundungSheet(
        visible = showBegrundung,
        mode = begrundungStatus,
        onDismiss = { showBegrundung = false },
        onConfirm = { reason ->
            showBegrundung = false
            viewModel.submitResponse(begrundungEventId, begrundungStatus, reason.ifBlank { null })
        }
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Events",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        SegmentedButton(
                            selected = state.viewMode == EventViewMode.LIST,
                            onClick = { viewModel.setViewMode(EventViewMode.LIST) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) { Text("List") }
                        SegmentedButton(
                            selected = state.viewMode == EventViewMode.CALENDAR,
                            onClick = { viewModel.setViewMode(EventViewMode.CALENDAR) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) { Text("Calendar") }
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.isCoach) {
                ExtendedFloatingActionButton(
                    onClick = onCreateClick,
                    modifier = Modifier.padding(bottom = 96.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = ch.teamorg.ui.theme.PillShape,
                    icon = { Icon(Icons.Default.Add, contentDescription = "Create event") },
                    text = { Text("New event") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Filters: two rows
            FilterRows(
                teams = state.teams,
                selectedTeamIds = state.selectedTeamIds,
                selectedTypes = state.selectedTypes,
                showAwaitingOnly = state.showAwaitingOnly,
                isCoach = state.isCoach,
                onTeamToggle = viewModel::toggleTeamFilter,
                onTeamsClear = viewModel::clearTeamFilters,
                onTypeToggle = viewModel::toggleTypeFilter,
                onTypesClear = viewModel::clearTypeFilters,
                onAwaitingToggle = viewModel::toggleAwaitingFilter
            )

            when (state.viewMode) {
                EventViewMode.LIST -> EventListContent(
                    state = state,
                    onEventClick = onEventClick,
                    onCreateClick = onCreateClick,
                    onRsvpSelect = { eventId, status ->
                        if (status == "unsure" || status == "declined") {
                            begrundungEventId = eventId
                            begrundungStatus = status
                            showBegrundung = true
                        } else {
                            viewModel.submitResponse(eventId, status, null)
                        }
                    }
                )
                EventViewMode.CALENDAR -> CalendarContent(
                    state = state,
                    viewModel = viewModel,
                    onEventClick = onEventClick,
                    onRsvpSelect = { eventId, status ->
                        if (status == "unsure" || status == "declined") {
                            begrundungEventId = eventId
                            begrundungStatus = status
                            showBegrundung = true
                        } else {
                            viewModel.submitResponse(eventId, status, null)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FilterRows(
    teams: List<MatchedTeam>,
    selectedTeamIds: Set<String>,
    selectedTypes: Set<String>,
    showAwaitingOnly: Boolean,
    isCoach: Boolean,
    onTeamToggle: (String) -> Unit,
    onTeamsClear: () -> Unit,
    onTypeToggle: (String) -> Unit,
    onTypesClear: () -> Unit,
    onAwaitingToggle: () -> Unit
) {
    Column {
        // Row 1: Team filters
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                M3eFilterChip(
                    selected = selectedTeamIds.isEmpty(),
                    onClick = onTeamsClear,
                    label = "All teams"
                )
            }
            items(teams) { team ->
                M3eFilterChip(
                    selected = team.id in selectedTeamIds,
                    onClick = { onTeamToggle(team.id) },
                    label = team.name
                )
            }
        }
        // Row 2: Type filters + coach-only awaiting filter
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                M3eFilterChip(
                    selected = selectedTypes.isEmpty(),
                    onClick = onTypesClear,
                    label = "All"
                )
            }
            val types = listOf("training" to "Training", "match" to "Match", "other" to "Other")
            items(types) { (value, label) ->
                M3eFilterChip(
                    selected = value in selectedTypes,
                    onClick = { onTypeToggle(value) },
                    label = label
                )
            }
            if (isCoach) {
                item {
                    M3eFilterChip(
                        selected = showAwaitingOnly,
                        onClick = onAwaitingToggle,
                        label = "Check-in offen"
                    )
                }
            }
        }
    }
}

@Composable
private fun M3eFilterChip(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = ch.teamorg.ui.theme.PillShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        leadingIcon = if (selected) {
            { Text("✓", style = MaterialTheme.typography.labelMedium) }
        } else null,
        label = { Text(label) }
    )
}

@Composable
private fun EventListContent(
    state: EventListState,
    onEventClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    onRsvpSelect: (String, String) -> Unit  // (eventId, status)
) {
    when {
        state.isLoading -> {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
        state.error != null -> {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = state.error ?: "Unknown error",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        state.events.isEmpty() -> {
            EmptyEventsList(isCoach = state.isCoach, onCreateClick = onCreateClick)
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(state.events, key = { it.event.id }) { ewt ->
                    val counts = state.attendanceCounts[ewt.event.id]
                    EventCard(
                        ewt = ewt,
                        confirmedCount = counts?.confirmedCount ?: 0,
                        maybeCount = counts?.maybeCount ?: 0,
                        declinedCount = counts?.declinedCount ?: 0,
                        myResponse = counts?.myResponse,
                        onClick = { onEventClick(ewt.event.id) },
                        onRsvpSelect = { status -> onRsvpSelect(ewt.event.id, status) },
                        isCoach = state.isCoach
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyEventsList(
    isCoach: Boolean,
    onCreateClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isCoach) "No events yet" else "No upcoming events",
                style = MaterialTheme.typography.titleMedium
            )
            if (isCoach) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onCreateClick, shape = ch.teamorg.ui.theme.PillShape) {
                    Text("Create your first event")
                }
            }
        }
    }
}

// ── Calendar view ──────────────────────────────────────────

@Composable
private fun CalendarContent(
    state: EventListState,
    viewModel: EventListViewModel,
    onEventClick: (String) -> Unit,
    onRsvpSelect: (String, String) -> Unit
) {
    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    MonthView(state = state, viewModel = viewModel, onEventClick = onEventClick, onRsvpSelect = onRsvpSelect)
}

@Composable
private fun MonthView(
    state: EventListState,
    viewModel: EventListViewModel,
    onEventClick: (String) -> Unit,
    onRsvpSelect: (String, String) -> Unit
) {
    val currentDate = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    var displayYear by remember { mutableIntStateOf(currentDate.year) }
    var displayMonth by remember { mutableIntStateOf(currentDate.monthNumber) }
    // Track direction for animation
    var navDirection by remember { mutableIntStateOf(0) }

    // Swipe helpers
    fun goNext() {
        navDirection = 1
        if (displayMonth == 12) { displayMonth = 1; displayYear++ } else displayMonth++
    }
    fun goPrev() {
        navDirection = -1
        if (displayMonth == 1) { displayMonth = 12; displayYear-- } else displayMonth--
    }
    fun goToday() {
        navDirection = 0
        displayYear = currentDate.year
        displayMonth = currentDate.monthNumber
        viewModel.selectDate(currentDate)
    }

    // Default select today when no date selected
    LaunchedEffect(Unit) {
        if (state.selectedDate == null) {
            viewModel.selectDate(currentDate)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Month navigation: ‹ June 2026 › + Today button
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "${Month(displayMonth).name.lowercase().replaceFirstChar { it.uppercase() }} $displayYear",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
                val isCurrentMonth = displayYear == currentDate.year && displayMonth == currentDate.monthNumber
                if (!isCurrentMonth) {
                    Text(
                        "Today",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { goToday() }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { goPrev() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { goNext() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Calendar grid card
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            // Day headers
            DaysOfWeekHeader(daysOfWeek = listOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
            ))

            // Calendar grid with swipe + animation
            val monthKey = displayYear * 100 + displayMonth
            AnimatedContent(
                targetState = monthKey,
                transitionSpec = {
                    val dir = navDirection
                    (slideInHorizontally { w -> if (dir >= 0) w else -w } + fadeIn()) togetherWith
                        (slideOutHorizontally { w -> if (dir >= 0) -w else w } + fadeOut())
                },
                modifier = Modifier.pointerInput(Unit) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            if (totalDrag > 100f) goPrev()
                            else if (totalDrag < -100f) goNext()
                        },
                        onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount }
                    )
                }
            ) { key ->
                val year = key / 100
                val month = key % 100
                val weeks = buildMonthGrid(year, month)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    weeks.forEach { week ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                        ) {
                            week.forEach { date ->
                                Box(modifier = Modifier.weight(1f)) {
                                    if (date != null && date.monthNumber == month) {
                                        DayCell(
                                            date = date,
                                            events = state.eventsByDate[date] ?: emptyList(),
                                            isToday = date == currentDate,
                                            isSelected = date == state.selectedDate,
                                            onClick = { viewModel.selectDate(date) }
                                        )
                                    } else if (date != null) {
                                        DayCell(
                                            date = date,
                                            events = emptyList(),
                                            isToday = false,
                                            isSelected = false,
                                            isOutside = true,
                                            onClick = {}
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.fillMaxSize())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Padding before selected-day section
        Spacer(modifier = Modifier.height(12.dp))

        // Selected day events
        Column(modifier = Modifier.animateContentSize()) {
            if (state.selectedDate != null && state.selectedDayEvents.isNotEmpty()) {
                val sel = state.selectedDate!!
                val dayName = sel.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
                val monthName = sel.month.name.lowercase().replaceFirstChar { it.uppercase() }
                Text(
                    "$dayName, ${sel.dayOfMonth} $monthName",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                DayEventsList(
                    events = state.selectedDayEvents,
                    attendanceCounts = state.attendanceCounts,
                    isCoach = state.isCoach,
                    onEventClick = onEventClick,
                    onRsvpSelect = onRsvpSelect
                )
            }
        }
    }
}

private fun buildMonthGrid(year: Int, month: Int): List<List<LocalDate?>> {
    val firstDay = LocalDate(year, month, 1)
    // Monday=1 .. Sunday=7
    val startDow = firstDay.dayOfWeek.ordinal + 1 // Monday=1 .. Sunday=7
    val daysInMonth = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        else -> 30
    }

    val cells = mutableListOf<LocalDate?>()
    // Pad start
    for (i in 1 until startDow) cells.add(null)
    for (d in 1..daysInMonth) cells.add(LocalDate(year, month, d))
    // Pad end to fill last week
    while (cells.size % 7 != 0) cells.add(null)

    return cells.chunked(7)
}

@Composable
private fun DaysOfWeekHeader(daysOfWeek: List<DayOfWeek>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        for (dayOfWeek in daysOfWeek) {
            Text(
                modifier = Modifier.weight(1f),
                text = dayOfWeek.name.take(1),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    events: List<EventWithTeams>,
    isToday: Boolean,
    isSelected: Boolean,
    isOutside: Boolean = false,
    onClick: () -> Unit
) {
    val dayNumber = date.dayOfMonth.toString()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = !isOutside,
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .alpha(if (isOutside) 0.3f else 1f)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Day number: selected = filled primary, today (not selected) = outlined, default = plain
        when {
            isSelected -> {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        dayNumber,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            isToday -> {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        dayNumber,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            else -> {
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Text(
                        dayNumber,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Event indicator dots
        if (events.isNotEmpty() && !isOutside) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                events.take(3).forEach { ewt ->
                    val isCancelled = ewt.event.status == "cancelled"
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(calendarEventColor(ewt.event.type, isCancelled))
                    )
                }
                if (events.size > 3) {
                    Text(
                        "+${events.size - 3}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DayEventsList(
    events: List<EventWithTeams>,
    attendanceCounts: Map<String, EventAttendanceCounts>,
    isCoach: Boolean,
    onEventClick: (String) -> Unit,
    onRsvpSelect: (String, String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        events.forEach { ewt ->
            val counts = attendanceCounts[ewt.event.id]
            EventCard(
                ewt = ewt,
                confirmedCount = counts?.confirmedCount ?: 0,
                maybeCount = counts?.maybeCount ?: 0,
                declinedCount = counts?.declinedCount ?: 0,
                myResponse = counts?.myResponse,
                onClick = { onEventClick(ewt.event.id) },
                onRsvpSelect = { status -> onRsvpSelect(ewt.event.id, status) },
                isCoach = isCoach
            )
        }
    }
}
