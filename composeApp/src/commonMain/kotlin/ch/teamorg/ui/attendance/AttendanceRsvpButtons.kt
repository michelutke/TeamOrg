package ch.teamorg.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.teamorg.ui.theme.PillShape
import ch.teamorg.ui.theme.extendedColors

@Composable
fun AttendanceRsvpButtons(
    currentResponse: String?,          // "confirmed"|"declined"|"unsure"|null
    confirmedCount: Int,
    maybeCount: Int,
    declinedCount: Int,
    deadlinePassed: Boolean,
    compact: Boolean = false,          // true = 36dp for list cards, false = 52dp for detail
    onSelect: (String) -> Unit         // "confirmed"|"unsure"|"declined"
) {
    val ext = MaterialTheme.extendedColors
    val height = if (compact) 36.dp else 52.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Going (selected = filled green)
        RsvpPill(
            modifier = Modifier.weight(1f),
            symbol = "✓",
            label = "Going",
            count = confirmedCount,
            isSelected = currentResponse == "confirmed",
            selectedBg = ext.going,
            selectedText = Color.White,
            deadlinePassed = deadlinePassed,
            compact = compact,
            height = height,
            onClick = { if (!deadlinePassed) onSelect("confirmed") },
            contentDesc = "Going"
        )

        // Decline (selected = tonal declined container)
        RsvpPill(
            modifier = Modifier.weight(1f),
            symbol = "✗",
            label = "Decline",
            count = declinedCount,
            isSelected = currentResponse == "declined" || currentResponse == "declined-auto",
            selectedBg = ext.declinedContainer,
            selectedText = ext.declined,
            deadlinePassed = deadlinePassed,
            compact = compact,
            height = height,
            onClick = { if (!deadlinePassed) onSelect("declined") },
            contentDesc = "Can't Go"
        )

        // Unsure (selected = tonal unsure container)
        RsvpPill(
            modifier = Modifier.weight(1f),
            symbol = "?",
            label = "Unsure",
            count = maybeCount,
            isSelected = currentResponse == "unsure",
            selectedBg = ext.unsureContainer,
            selectedText = ext.unsure,
            deadlinePassed = deadlinePassed,
            compact = compact,
            height = height,
            onClick = { if (!deadlinePassed) onSelect("unsure") },
            contentDesc = "Maybe"
        )
    }
}

@Composable
private fun RsvpPill(
    modifier: Modifier,
    symbol: String,
    label: String,
    count: Int,
    isSelected: Boolean,
    selectedBg: Color,
    selectedText: Color,
    deadlinePassed: Boolean,
    compact: Boolean,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    contentDesc: String
) {
    val bg = if (isSelected) selectedBg else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (isSelected) selectedText else MaterialTheme.colorScheme.onSurfaceVariant
    val alpha = if (deadlinePassed) 0.5f else 1f

    Box(
        modifier = modifier
            .height(height)
            .clip(PillShape)
            .background(bg)
            .alpha(alpha)
            .then(if (!deadlinePassed) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center
    ) {
        if (compact) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    symbol,
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    count.toString(),
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    symbol,
                    color = textColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    label,
                    color = textColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
