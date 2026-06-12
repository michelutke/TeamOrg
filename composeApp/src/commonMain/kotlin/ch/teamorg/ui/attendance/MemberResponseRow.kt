package ch.teamorg.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.teamorg.domain.CheckInEntry
import ch.teamorg.ui.theme.extendedColors

@Composable
fun MemberResponseRow(
    entry: CheckInEntry,
    isCoach: Boolean,
    onStatusTap: (String) -> Unit    // "present"|"absent"|"excused"
) {
    val ext = MaterialTheme.extendedColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar (40dp circle with initials)
        val initials = entry.userName
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials.ifEmpty { "?" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name + reason column
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.userName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Coach override indicator
                val entryRecord = entry.record
                if (entryRecord != null && entryRecord.setBy != entry.userId) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "✎",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.semantics { contentDescription = "Coach override" }
                    )
                }
            }
            // Auto-decline annotation + reason
            val isAutoDeclined = entry.response?.abwesenheitRuleId != null
            val reason = entry.record?.note ?: entry.response?.reason
            val annotation = when {
                isAutoDeclined && !reason.isNullOrBlank() -> "Auto-declined · $reason"
                isAutoDeclined -> "Auto-declined"
                !reason.isNullOrBlank() -> reason
                else -> null
            }
            if (annotation != null) {
                Text(
                    text = annotation,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Coach override mini controls
        if (isCoach) {
            val currentStatus = entry.record?.status
                ?: when (entry.response?.status) {
                    "confirmed" -> "present"
                    "declined", "declined-auto" -> "absent"
                    "unsure" -> "excused"
                    else -> null
                }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CoachStatusButton(
                    symbol = "✓",
                    color = ext.going,
                    isSelected = currentStatus == "present",
                    contentDesc = "Set present",
                    onClick = { onStatusTap("present") }
                )
                CoachStatusButton(
                    symbol = "✗",
                    color = ext.declined,
                    isSelected = currentStatus == "absent",
                    contentDesc = "Set absent",
                    onClick = { onStatusTap("absent") }
                )
                CoachStatusButton(
                    symbol = "?",
                    color = ext.unsure,
                    isSelected = currentStatus == "excused",
                    contentDesc = "Set excused",
                    onClick = { onStatusTap("excused") }
                )
            }
        }
    }
}

@Composable
private fun CoachStatusButton(
    symbol: String,
    color: Color,
    isSelected: Boolean = false,
    contentDesc: String,
    onClick: () -> Unit
) {
    val bg = if (isSelected) color else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
