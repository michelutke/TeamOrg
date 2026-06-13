package ch.teamorg.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.teamorg.domain.Notification

private val RowShape = RoundedCornerShape(22.dp)

@Composable
fun NotificationRow(
    notification: Notification,
    onClick: () -> Unit
) {
    val typeIcon = notificationIcon(notification.type)
    val (iconContainer, iconTint) = notificationIconColors(notification.type)

    val containerModifier = if (!notification.isRead) {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow, RowShape)
    } else {
        Modifier
            .background(MaterialTheme.colorScheme.surface, RowShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RowShape)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .then(containerModifier)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(iconContainer, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = typeIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (notification.body.isNotBlank()) {
                Text(
                    text = notification.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = formatRelativeTime(notification.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

private fun notificationIcon(type: String): ImageVector = when (type) {
    "event_new" -> Icons.Outlined.Event
    "event_edit" -> Icons.Outlined.Edit
    "event_cancel" -> Icons.Outlined.Block
    "reminder" -> Icons.Outlined.Alarm
    "response" -> Icons.AutoMirrored.Outlined.Reply
    "absence" -> Icons.Outlined.EventBusy
    else -> Icons.Outlined.Notifications
}

@Composable
private fun notificationIconColors(type: String): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (type) {
        "event_new" -> scheme.primaryContainer to scheme.onPrimaryContainer
        "event_edit", "response" -> scheme.secondaryContainer to scheme.onSecondaryContainer
        "reminder" -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        "event_cancel", "absence" -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
}

fun formatRelativeTime(isoTimestamp: String): String {
    return try {
        val epochMillis = parseIsoToMillis(isoTimestamp)
        val nowMillis = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val diffMs = nowMillis - epochMillis
        val diffMin = diffMs / 60_000
        val diffHours = diffMin / 60
        val diffDays = diffHours / 24
        when {
            diffMin < 1 -> "Just now"
            diffMin < 60 -> "${diffMin}m ago"
            diffHours < 24 -> "${diffHours}h ago"
            diffDays == 1L -> "1d ago"
            else -> "${diffDays}d ago"
        }
    } catch (_: Exception) {
        ""
    }
}

private fun parseIsoToMillis(iso: String): Long {
    // Parse ISO-8601 like "2026-03-26T08:18:14Z" or "2026-03-26T08:18:14.000Z"
    val cleaned = iso.trimEnd('Z').substringBefore('+').substringBefore('.').let {
        if (it.length == 19) it else iso.take(19)
    }
    // "2026-03-26T08:18:14"
    val parts = cleaned.split("T")
    val dateParts = parts[0].split("-").map { it.toInt() }
    val timeParts = parts[1].split(":").map { it.toInt() }

    val year = dateParts[0]
    val month = dateParts[1]
    val day = dateParts[2]
    val hour = timeParts[0]
    val minute = timeParts[1]
    val second = timeParts[2]

    // Days since Unix epoch (1970-01-01) via Zeller-style calculation
    var y = year.toLong()
    var m = month.toLong()
    if (m <= 2) { y--; m += 12 }
    val a = y / 100
    val b = 2 - a + a / 4
    val jd = (365.25 * (y + 4716)).toLong() + (30.6001 * (m + 1)).toLong() + day + b - 1524
    val epochDays = jd - 2440588L
    return epochDays * 86_400_000L + hour * 3_600_000L + minute * 60_000L + second * 1_000L
}
