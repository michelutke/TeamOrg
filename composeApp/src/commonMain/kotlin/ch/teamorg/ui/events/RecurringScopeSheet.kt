package ch.teamorg.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.teamorg.ui.theme.PillShape

/**
 * Scope selection sheet for recurring events — edit/cancel/uncancel scope.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScopeSheet(
    mode: String = "edit",
    onContinue: (scope: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedScope by remember { mutableStateOf("this_only") }

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
            // Title
            Text(
                when (mode) {
                    "cancel" -> "Cancel which events?"
                    "uncancel" -> "Restore which events?"
                    else -> "Apply changes to"
                },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Subtitle
            Text(
                when (mode) {
                    "cancel" -> "This is a recurring event. Choose which events to cancel."
                    "uncancel" -> "This is a recurring event. Choose which events to restore."
                    else -> "This event is part of a recurring series."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(4.dp))

            // Option cards
            ScopeOptionCard(
                label = "This event only",
                selected = selectedScope == "this_only",
                onClick = { selectedScope = "this_only" }
            )
            ScopeOptionCard(
                label = "This and future events",
                selected = selectedScope == "this_and_future",
                onClick = { selectedScope = "this_and_future" }
            )
            ScopeOptionCard(
                label = "All events in the series",
                selected = selectedScope == "all",
                onClick = { selectedScope = "all" }
            )

            Spacer(Modifier.height(4.dp))

            // Action buttons: Cancel + primary CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(57.dp),
                    shape = PillShape
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { onContinue(selectedScope) },
                    modifier = Modifier.weight(1f).height(57.dp),
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        when (mode) {
                            "cancel" -> "Cancel events"
                            "uncancel" -> "Restore events"
                            else -> "Save changes"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ScopeOptionCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(PillShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
    }
}
