package ch.teamorg.ui.attendance

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.teamorg.domain.AbwesenheitRule
import ch.teamorg.ui.theme.PillShape
import ch.teamorg.ui.theme.extendedColors

private fun iconForPresetType(presetType: String): ImageVector = when (presetType.lowercase()) {
    "holidays" -> Icons.Outlined.WbSunny
    "injury" -> Icons.Outlined.FlashOn
    "work" -> Icons.Outlined.Work
    "school" -> Icons.Outlined.MenuBook
    "travel" -> Icons.Outlined.Flight
    else -> Icons.Outlined.MoreHoriz
}

private fun formatDateRange(rule: AbwesenheitRule): String {
    return when (rule.ruleType) {
        "period" -> {
            val from = rule.startDate ?: ""
            val to = rule.endDate ?: ""
            if (from.isNotEmpty() && to.isNotEmpty()) "Period · $from – $to"
            else if (from.isNotEmpty()) "Period · From $from"
            else "No date set"
        }
        "recurring" -> {
            val days = rule.weekdays
            if (!days.isNullOrEmpty()) {
                val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                "Recurring · Every " + days.sorted().joinToString(", ") { names[it] }
            } else "Recurring"
        }
        else -> rule.startDate ?: ""
    }
}

private fun isActive(rule: AbwesenheitRule): Boolean {
    val endDate = rule.endDate ?: return true
    // Simple string comparison works for ISO dates
    val today = kotlinx.datetime.Clock.System.now()
        .toString().take(10) // "YYYY-MM-DD"
    return endDate >= today
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AbsenceCard(
    rule: AbwesenheitRule,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = isActive(rule)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .alpha(if (active) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconForPresetType(rule.presetType),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = rule.label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatDateRange(rule),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        // Status badge
        Surface(
            color = if (active) {
                MaterialTheme.extendedColors.goingContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            shape = PillShape
        ) {
            Text(
                text = if (active) "Active" else "Ended",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = if (active) {
                    MaterialTheme.extendedColors.going
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
