package ch.teamorg.ui.nds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.teamorg.domain.NdsConflictGroup
import ch.teamorg.domain.NdsSeries
import ch.teamorg.ui.components.TeamorgLoader
import ch.teamorg.ui.theme.PillShape
import ch.teamorg.ui.util.rememberDocumentPickerLauncher
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NdsImportScreen(
    teamId: String,
    clubId: String,
    viewModel: NdsImportViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("NDS-Import", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { if (state.step == 1) onBack() else viewModel.goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading) {
                TeamorgLoader(modifier = Modifier.align(Alignment.Center))
            } else if (state.result != null) {
                NdsImportResultStep(viewModel = viewModel, onDone = onDone)
            } else {
                when (state.step) {
                    1 -> NdsFilesStep(viewModel = viewModel, clubId = clubId, teamId = teamId)
                    2 -> NdsMappingStep(viewModel = viewModel)
                    3 -> NdsEventsStep(viewModel = viewModel)
                    else -> NdsConfirmStep(viewModel = viewModel, clubId = clubId, teamId = teamId)
                }
            }

            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { }
                ) {
                    Text(error)
                }
            }
        }
    }
}

// ─── Step 1 — Dateien ───

private data class FileSlotSpec(val slot: String, val label: String, val extensions: List<String>, val recommended: Boolean)

private val FILE_SLOTS = listOf(
    FileSlotSpec(NDS_SLOT_TEILNEHMENDE, "Teilnehmende (.csv)", listOf("csv"), recommended = false),
    FileSlotSpec(NDS_SLOT_LEITER, "Leiter/innen (.xlsx)", listOf("xlsx"), recommended = false),
    FileSlotSpec(NDS_SLOT_ANWESENHEITSLISTE, "Anwesenheitsliste (.xlsx)", listOf("xlsx"), recommended = true)
)

@Composable
private fun NdsFilesStep(viewModel: NdsImportViewModel, clubId: String, teamId: String) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Wähle mindestens eine NDS-Datei aus, um den Import zu starten.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FILE_SLOTS.forEach { spec ->
            val picked = state.files[spec.slot]
            val launchPicker = rememberDocumentPickerLauncher(spec.extensions) { bytes, fileName ->
                viewModel.pickFile(spec.slot, fileName, bytes)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(spec.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (spec.recommended) {
                                AssistChip(onClick = {}, label = { Text("empfohlen", style = MaterialTheme.typography.labelSmall) })
                            }
                        }
                        Text(
                            picked?.fileName ?: "Keine Datei ausgewählt",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (picked != null) {
                        TextButton(onClick = { viewModel.removeFile(spec.slot) }) { Text("Entfernen") }
                    } else {
                        Button(onClick = launchPicker, shape = PillShape) { Text("Wählen") }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.parseFiles(clubId, teamId) },
            enabled = viewModel.canProceedFromFiles(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = PillShape
        ) {
            Text("Weiter", fontWeight = FontWeight.Bold)
        }
    }
}

// ─── Step 2 — Mitglieder-Zuordnung ───

@Composable
private fun NdsMappingStep(viewModel: NdsImportViewModel) {
    val state by viewModel.state.collectAsState()
    val rows = remember(state.parse) { viewModel.mergedRows() }
    val suggestionsByKey = remember(state.parse) {
        (state.parse?.memberSuggestions ?: emptyList()).associateBy { it.rowKey }
    }
    val allCandidates = remember(state.parse, state.roster) { viewModel.allCandidates() }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Ordne jede Person einem bestehenden Teammitglied zu, erstelle einen neuen Nutzer, oder überspringe die Zeile.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(rows, key = { NdsImportViewModel.rowKey(it.funktion, it.lastName, it.firstName) }) { row ->
                val key = NdsImportViewModel.rowKey(row.funktion, row.lastName, row.firstName)
                val suggestion = suggestionsByKey[key]
                MappingRow(
                    funktion = row.funktion,
                    name = "${row.firstName} ${row.lastName}",
                    locked = suggestion?.alreadyLinkedUserId != null,
                    suggestionCandidates = suggestion?.candidates ?: emptyList(),
                    otherCandidates = allCandidates.filter { c -> suggestion?.candidates?.none { it.userId == c.userId } ?: true },
                    choice = state.mappings[key],
                    onChoice = { viewModel.setMapping(key, it) }
                )
            }
        }
        Button(
            onClick = { viewModel.proceedFromMapping() },
            modifier = Modifier.fillMaxWidth().padding(20.dp).height(52.dp),
            shape = PillShape
        ) {
            Text("Weiter", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappingRow(
    funktion: String,
    name: String,
    locked: Boolean,
    suggestionCandidates: List<ch.teamorg.domain.MemberSuggestionDto.CandidateDto>,
    otherCandidates: List<ch.teamorg.domain.MemberSuggestionDto.CandidateDto>,
    choice: MappingChoice?,
    onChoice: (MappingChoice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(funktion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (locked) {
                Text(
                    "bereits verknüpft",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = labelFor(choice, suggestionCandidates, otherCandidates),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { Icon(Icons.Default.ExpandMore, contentDescription = null) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        if (suggestionCandidates.isNotEmpty()) {
                            suggestionCandidates.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.displayName) },
                                    onClick = { onChoice(MappingChoice(action = "map", userId = c.userId)); expanded = false }
                                )
                            }
                            HorizontalDivider()
                        }
                        otherCandidates.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.displayName) },
                                onClick = { onChoice(MappingChoice(action = "map", userId = c.userId)); expanded = false }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Neuen Nutzer erstellen") },
                            onClick = { onChoice(MappingChoice(action = "create")); expanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Überspringen") },
                            onClick = { onChoice(MappingChoice(action = "skip")); expanded = false }
                        )
                    }
                }
            }
        }
    }
}

private fun labelFor(
    choice: MappingChoice?,
    suggestionCandidates: List<ch.teamorg.domain.MemberSuggestionDto.CandidateDto>,
    otherCandidates: List<ch.teamorg.domain.MemberSuggestionDto.CandidateDto>
): String {
    return when (choice?.action) {
        "map" -> (suggestionCandidates + otherCandidates).find { it.userId == choice.userId }?.displayName ?: "Zuordnen"
        "skip" -> "Überspringen"
        else -> "Neuen Nutzer erstellen"
    }
}

// ─── Step 3 — Events & Konflikte ───

@Composable
private fun NdsEventsStep(viewModel: NdsImportViewModel) {
    val state by viewModel.state.collectAsState()
    val parse = state.parse
    val series = parse?.series ?: emptyList()
    val conflicts = parse?.conflicts ?: emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(series, key = { it.seriesKey }) { s ->
                val conflictGroup = conflicts.find { it.seriesKey == s.seriesKey }
                SeriesCard(
                    series = s,
                    conflictGroup = conflictGroup,
                    time = state.seriesTimes[s.seriesKey],
                    onTimeChange = { viewModel.setSeriesTime(s.seriesKey, it) },
                    resolution = state.resolutions[s.seriesKey] ?: ResolutionChoice(),
                    onResolutionChange = { viewModel.setResolution(s.seriesKey, it) }
                )
            }
        }
        Button(
            onClick = { viewModel.proceedFromEvents() },
            enabled = viewModel.canProceedFromEvents(),
            modifier = Modifier.fillMaxWidth().padding(20.dp).height(52.dp),
            shape = PillShape
        ) {
            Text("Weiter", fontWeight = FontWeight.Bold)
        }
    }
}

private fun symbolLabel(symbol: String): String = when (symbol) {
    "T" -> "Training"
    "W" -> "Spiel"
    else -> symbol
}

private fun weekdayLabel(weekday: Int?): String = when (weekday) {
    0 -> "MO"; 1 -> "DI"; 2 -> "MI"; 3 -> "DO"; 4 -> "FR"; 5 -> "SA"; 6 -> "SO"; else -> "–"
}

@Composable
private fun SeriesCard(
    series: NdsSeries,
    conflictGroup: NdsConflictGroup?,
    time: SeriesTimeInput?,
    onTimeChange: (SeriesTimeInput) -> Unit,
    resolution: ResolutionChoice,
    onResolutionChange: (ResolutionChoice) -> Unit
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var location by remember(series.seriesKey) { mutableStateOf(time?.location ?: "") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "${weekdayLabel(series.weekday)} · ${symbolLabel(series.symbol)} · ${series.durationMin ?: 0} min · ${series.count} Termine",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                    Text(time?.startTime ?: "Startzeit")
                }
                OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                    Text(time?.endTime ?: "Endzeit")
                }
            }
            OutlinedTextField(
                value = location,
                onValueChange = {
                    location = it
                    onTimeChange((time ?: SeriesTimeInput("", "")).copy(location = it))
                },
                label = { Text("Ort (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (conflictGroup != null) {
                HorizontalDivider()
                Text("Konflikte", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = resolution.keep == "teamorg",
                        onClick = { onResolutionChange(resolution.copy(keep = "teamorg")) }
                    )
                    Text("TeamOrg behalten", modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = resolution.keep == "nds",
                        onClick = { onResolutionChange(resolution.copy(keep = "nds")) }
                    )
                    Text("NDS übernehmen", modifier = Modifier.weight(1f))
                }
                val rsvpLoss = conflictGroup.dates
                    .filter { (resolution.overrides[it.date] ?: resolution.keep) == "nds" }
                    .sumOf { it.rsvpCount }
                if (rsvpLoss > 0) {
                    Text(
                        "$rsvpLoss Rückmeldungen gehen verloren",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Termine ausblenden" else "Einzelne Termine anzeigen (${conflictGroup.dates.size})")
                }
                if (expanded) {
                    conflictGroup.dates.forEach { d ->
                        val effective = resolution.overrides[d.date] ?: resolution.keep
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                "${d.date} · ${d.existingEventTitle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (effective == "nds" && d.rsvpCount > 0) {
                                Text(
                                    "${d.rsvpCount} Rückmeldungen gehen verloren",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = effective == "teamorg",
                                    onClick = {
                                        onResolutionChange(resolution.copy(overrides = resolution.overrides + (d.date to "teamorg")))
                                    }
                                )
                                Text("TeamOrg behalten", style = MaterialTheme.typography.bodySmall)
                                RadioButton(
                                    selected = effective == "nds",
                                    onClick = {
                                        onResolutionChange(resolution.copy(overrides = resolution.overrides + (d.date to "nds")))
                                    }
                                )
                                Text("NDS übernehmen", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        NdsTimePickerDialog(
            initial = time?.startTime,
            onTimeSelected = { onTimeChange((time ?: SeriesTimeInput("", "", location.ifBlank { null })).copy(startTime = it)); showStartPicker = false },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        NdsTimePickerDialog(
            initial = time?.endTime,
            onTimeSelected = { onTimeChange((time ?: SeriesTimeInput("", "", location.ifBlank { null })).copy(endTime = it)); showEndPicker = false },
            onDismiss = { showEndPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NdsTimePickerDialog(initial: String?, onTimeSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val initialTime = initial?.let {
        val parts = it.split(":")
        LocalTime(parts.getOrNull(0)?.toIntOrNull() ?: 18, parts.getOrNull(1)?.toIntOrNull() ?: 0)
    } ?: LocalTime(18, 0)
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zeit wählen") },
        text = { TimePicker(state = timePickerState) },
        confirmButton = {
            TextButton(onClick = {
                val hh = timePickerState.hour.toString().padStart(2, '0')
                val mm = timePickerState.minute.toString().padStart(2, '0')
                onTimeSelected("$hh:$mm")
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

// ─── Step 4 — Bestätigen ───

@Composable
private fun NdsConfirmStep(viewModel: NdsImportViewModel, clubId: String, teamId: String) {
    val state by viewModel.state.collectAsState()
    val (mapped, created, skipped) = viewModel.mappingCounts()
    val (eventsNew, keepTeamorg, keepNds) = viewModel.eventCounts()
    var attendanceOn by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Zusammenfassung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("$mapped zugeordnet · $created neu · $skipped übersprungen")
                if (state.parse?.anwesenheitsliste != null) {
                    Text("$eventsNew Events neu")
                    Text("$keepTeamorg Konflikte TeamOrg · $keepNds Konflikte NDS")
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = attendanceOn, onCheckedChange = { attendanceOn = it })
            Spacer(modifier = Modifier.width(8.dp))
            Text("Anwesenheiten übernehmen")
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.submitImport(clubId, teamId, if (attendanceOn) "keep" else "discard") },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = PillShape
        ) {
            Text("Importieren", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NdsImportResultStep(viewModel: NdsImportViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val result = state.result ?: return

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text("Import abgeschlossen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${result.membersImported} Mitglieder · ${result.eventsCreated} Events · ${result.attendanceImported} Anwesenheiten")
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp), shape = PillShape) {
            Text("Fertig", fontWeight = FontWeight.Bold)
        }
    }
}
