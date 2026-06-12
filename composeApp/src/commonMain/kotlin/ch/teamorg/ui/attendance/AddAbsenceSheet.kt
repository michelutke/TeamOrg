package ch.teamorg.ui.attendance

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.teamorg.domain.AbwesenheitRule
import ch.teamorg.domain.CreateAbwesenheitRequest
import ch.teamorg.ui.theme.PillShape
import kotlinx.datetime.LocalDate

private data class ReasonPreset(
    val key: String,
    val label: String,
    val icon: ImageVector
)

private val REASON_PRESETS = listOf(
    ReasonPreset("holidays", "Holidays", Icons.Outlined.WbSunny),
    ReasonPreset("injury", "Injury", Icons.Outlined.FlashOn),
    ReasonPreset("work", "Work", Icons.Outlined.Work),
    ReasonPreset("school", "School", Icons.Outlined.MenuBook),
    ReasonPreset("travel", "Travel", Icons.Outlined.Flight),
    ReasonPreset("other", "Other", Icons.Outlined.MoreHoriz)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAbsenceSheet(
    visible: Boolean,
    editingRule: AbwesenheitRule? = null,
    onDismiss: () -> Unit,
    onSave: (CreateAbwesenheitRequest) -> Unit
) {
    if (!visible) return

    // Pre-fill from editingRule if provided
    var selectedReason by remember(editingRule) {
        mutableStateOf(editingRule?.presetType ?: "")
    }
    var selectedBodyParts by remember(editingRule) {
        mutableStateOf(
            editingRule?.bodyPart?.let { setOf(it) } ?: emptySet<String>()
        )
    }
    var ruleType by remember(editingRule) {
        mutableStateOf(editingRule?.ruleType ?: "recurring")
    }
    var selectedWeekdays by remember(editingRule) {
        mutableStateOf(editingRule?.weekdays?.toSet() ?: emptySet<Int>())
    }
    var startDate by remember(editingRule) { mutableStateOf(editingRule?.startDate ?: "") }
    var endDate by remember(editingRule) { mutableStateOf(editingRule?.endDate ?: "") }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val title = if (editingRule != null) "Edit Absence" else "Add Absence"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = Modifier.fillMaxHeight(),
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
                .fillMaxHeight()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics { contentDescription = "Close" }
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Reason section
                SectionLabel("Reason")
                Spacer(Modifier.height(2.dp))

                // 2x3 grid of reason tiles
                val rows = REASON_PRESETS.chunked(3)
                rows.forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPresets.forEach { preset ->
                            AbsenceReasonTile(
                                label = preset.label,
                                icon = preset.icon,
                                selected = selectedReason == preset.key,
                                onClick = { selectedReason = preset.key },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Body part grid — only for Injury
                Column(modifier = Modifier.animateContentSize()) {
                    if (selectedReason == "injury") {
                        Spacer(Modifier.height(4.dp))
                        SectionLabel("Affected area")
                        Spacer(Modifier.height(8.dp))
                        BodyPartGrid(
                            selectedParts = selectedBodyParts,
                            onToggle = { part ->
                                selectedBodyParts = if (part in selectedBodyParts) {
                                    selectedBodyParts - part
                                } else {
                                    selectedBodyParts + part
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Type section
                SectionLabel("Rule type")
                Spacer(Modifier.height(2.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = ruleType == "recurring",
                        onClick = { ruleType = "recurring" },
                        label = { Text("Recurring weekly") },
                        shape = PillShape
                    )
                    FilterChip(
                        selected = ruleType == "period",
                        onClick = { ruleType = "period" },
                        label = { Text("Period") },
                        shape = PillShape
                    )
                }

                if (ruleType == "recurring") {
                    SectionLabel("Days")
                    Spacer(Modifier.height(2.dp))
                    WeekdaySelector(
                        selectedDays = selectedWeekdays,
                        onToggle = { day ->
                            selectedWeekdays = if (day in selectedWeekdays) {
                                selectedWeekdays - day
                            } else {
                                selectedWeekdays + day
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("End date (optional)")
                    Spacer(Modifier.height(2.dp))
                    DatePickerField(
                        value = endDate,
                        placeholder = "Select end date",
                        onClick = { showEndDatePicker = true }
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DatePickerField(
                            value = startDate,
                            placeholder = "From",
                            onClick = { showStartDatePicker = true },
                            modifier = Modifier.weight(1f)
                        )
                        DatePickerField(
                            value = endDate,
                            placeholder = "Until",
                            onClick = { showEndDatePicker = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        val label = REASON_PRESETS
                            .firstOrNull { it.key == selectedReason }?.label
                            ?: selectedReason
                        val bodyPart = selectedBodyParts.firstOrNull()
                        onSave(
                            CreateAbwesenheitRequest(
                                presetType = selectedReason,
                                label = label,
                                bodyPart = bodyPart,
                                ruleType = ruleType,
                                weekdays = if (ruleType == "recurring") selectedWeekdays.sorted() else null,
                                startDate = if (ruleType == "period") startDate.ifBlank { null } else null,
                                endDate = endDate.ifBlank { null }
                            )
                        )
                    },
                    enabled = selectedReason.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(57.dp),
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Save Rule",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showStartDatePicker) {
        AbsenceDatePickerDialog(
            onDateSelected = { date ->
                startDate = date
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }

    if (showEndDatePicker) {
        AbsenceDatePickerDialog(
            onDateSelected = { date ->
                endDate = date
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun DatePickerField(
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = placeholder,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value.ifBlank { "Select date" },
            color = if (value.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontSize = 16.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AbsenceDatePickerDialog(
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = datePickerState.selectedDateMillis
                if (millis != null) {
                    val epochDays = (millis / (24L * 60 * 60 * 1000)).toInt()
                    val date = LocalDate.fromEpochDays(epochDays)
                    onDateSelected("${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}")
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
