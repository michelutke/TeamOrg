package ch.teamorg.ui.attendance

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun ResponseDeadlineLabel(
    deadline: Instant?,
    modifier: Modifier = Modifier
) {
    if (deadline == null) return

    val now = Clock.System.now()
    val isPast = deadline <= now

    if (isPast) {
        Text(
            text = "Response closed",
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = modifier
        )
    } else {
        val local = deadline.toLocalDateTime(TimeZone.currentSystemDefault())
        val month = local.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val hour = local.hour.toString().padStart(2, '0')
        val min = local.minute.toString().padStart(2, '0')
        val formatted = "${local.dayOfMonth} $month at $hour:$min"
        Text(
            text = "Respond by $formatted",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = modifier
        )
    }
}
