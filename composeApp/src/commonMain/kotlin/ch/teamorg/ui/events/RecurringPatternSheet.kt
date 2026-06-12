package ch.teamorg.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.teamorg.ui.theme.PillShape
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val WEEKDAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringPatternSheet(
    initialPattern: RecurringPatternState,
    onDone: (RecurringPatternState) -> Unit,
    onDismiss: () -> Unit
) {
    // Derive initial frequencyUnit and intervalCount from the saved pattern
    val initFreqUnit = when (initialPattern.patternType) {
        "daily" -> "day"
        "weekly" -> "week"
        else -> {
            val d = initialPattern.intervalDays
            when {
                d > 0 && d % 30 == 0 -> "month"
                d > 0 && d % 7 == 0 -> "week"
                else -> "day"
            }
        }
    }
    val initCount = when (initFreqUnit) {
        "month" -> (initialPattern.intervalDays / 30).coerceAtLeast(1)
        "week" -> if (initialPattern.patternType == "weekly") 1 else (initialPattern.intervalDays / 7).coerceAtLeast(1)
        else -> if (initialPattern.patternType == "daily") 1 else initialPattern.intervalDays.coerceAtLeast(1)
    }

    var patternType by remember { mutableStateOf(initialPattern.patternType) }
    var weekdays by remember { mutableStateOf(initialPattern.weekdays) }
    var intervalCount by remember { mutableStateOf(initCount) }
    var frequencyUnit by remember { mutableStateOf(initFreqUnit) }
    var hasEndDate by remember { mutableStateOf(initialPattern.hasEndDate) }
    var endDate by remember { mutableStateOf(initialPattern.endDate) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Derive summary text from current UI state
    val summaryText = remember(patternType, weekdays, frequencyUnit, intervalCount) {
        when (frequencyUnit) {
            "day" -> if (intervalCount == 1) "Repeats daily" else "Repeats every $intervalCount days"
            "week" -> {
                if (weekdays.isEmpty()) {
                    if (intervalCount == 1) "Repeats weekly" else "Repeats every $intervalCount weeks"
                } else {
                    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                    val days = weekdays.sorted().joinToString(" and ") { dayNames[it] }
                    "Repeats every $days"
                }
            }
            "month" -> if (intervalCount == 1) "Repeats monthly" else "Repeats every $intervalCount months"
            else -> "Repeats every $intervalCount ${frequencyUnit}s"
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                "Repeats",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Frequency row: "Every" + number input + Week/Month/Day pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Every",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )

                // Number input
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(44.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = intervalCount.toString(),
                        onValueChange = { text ->
                            val v = text.toIntOrNull()
                            if (v != null && v > 0) intervalCount = v
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                }

                // Frequency unit pills
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Week" to "week", "Month" to "month", "Day" to "day").forEach { (label, value) ->
                        val selected = frequencyUnit == value
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .clickable {
                                    frequencyUnit = value
                                    patternType = when (value) {
                                        "day" -> "daily"
                                        "week" -> "weekly"
                                        else -> "custom"
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                label,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // "Repeat on" + 7 day circles (only for weekly)
            if (frequencyUnit == "week") {
                Text(
                    "Repeat on",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WEEKDAY_LABELS.forEachIndexed { index, label ->
                        val selected = index in weekdays
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .clickable {
                                    weekdays = if (index in weekdays) weekdays - index else weekdays + index
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Ends row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (hasEndDate) showEndDatePicker = true
                        }
                ) {
                    Text(
                        "Ends",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (hasEndDate && endDate != null) {
                            val d = endDate!!
                            "${d.dayOfMonth} ${d.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${d.year}"
                        } else "Never",
                        color = if (hasEndDate && endDate != null) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Switch(
                    checked = hasEndDate,
                    onCheckedChange = {
                        hasEndDate = it
                        if (it && endDate == null) showEndDatePicker = true
                    }
                )
            }

            // Summary
            Text(
                summaryText,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Done button
            Button(
                onClick = {
                    val computedInterval = when (frequencyUnit) {
                        "day" -> intervalCount
                        "week" -> intervalCount * 7
                        "month" -> intervalCount * 30
                        else -> intervalCount * 7
                    }
                    onDone(
                        RecurringPatternState(
                            patternType = patternType,
                            weekdays = weekdays,
                            intervalDays = computedInterval,
                            hasEndDate = hasEndDate,
                            endDate = endDate
                        )
                    )
                },
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

    if (showEndDatePicker) {
        RecurringEndDatePickerDialog(
            initialDate = endDate,
            onDateSelected = { date ->
                endDate = date
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringEndDatePickerDialog(
    initialDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val today = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val initMillis = initialDate?.let { d ->
        d.toEpochDays() * 24L * 60 * 60 * 1000
    } ?: (today.toEpochDays() * 24L * 60 * 60 * 1000)
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initMillis)
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
