package ch.teamorg.ui.inbox

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ch.teamorg.ui.theme.PillShape

private data class Preset(val label: String, val minutes: Int)

private val PRESETS = listOf(
    Preset("30 min", 30),
    Preset("1 h", 60),
    Preset("2 h", 120),
    Preset("1 day", 1440),
    Preset("2 days", 2880)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderPickerSheet(
    currentLeadMinutes: Int?,
    onConfirm: (Int) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val initialHours = if (currentLeadMinutes != null) currentLeadMinutes / 60 else 0
    val initialMinutes = if (currentLeadMinutes != null) currentLeadMinutes % 60 else 0

    var hoursText by remember { mutableStateOf(if (initialHours > 0) initialHours.toString() else "") }
    var minutesText by remember { mutableStateOf(if (initialMinutes > 0) initialMinutes.toString() else "") }

    val totalMinutes = ((hoursText.toIntOrNull() ?: 0) * 60) + (minutesText.toIntOrNull() ?: 0)
    val isValid = totalMinutes > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Set Reminder",
                style = MaterialTheme.typography.titleLarge
            )

            // Preset chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(PRESETS) { preset ->
                    val selected = totalMinutes == preset.minutes
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val h = preset.minutes / 60
                            val m = preset.minutes % 60
                            hoursText = if (h > 0) h.toString() else ""
                            minutesText = if (m > 0) m.toString() else ""
                        },
                        label = { Text(preset.label) },
                        shape = PillShape
                    )
                }
            }

            // Hours and minutes input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Hours") },
                    suffix = { Text("h") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Minutes") },
                    suffix = { Text("min") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            // Set Reminder button
            Button(
                onClick = { if (isValid) onConfirm(totalMinutes) },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(57.dp),
                shape = PillShape
            ) {
                Text("Set Reminder", style = MaterialTheme.typography.titleMedium)
            }

            // No reminder (only for per-event override)
            if (onRemove != null) {
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("No reminder", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
