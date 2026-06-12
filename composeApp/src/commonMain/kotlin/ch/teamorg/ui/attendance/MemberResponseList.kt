package ch.teamorg.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.teamorg.domain.CheckInEntry
import ch.teamorg.ui.theme.extendedColors

@Composable
fun MemberResponseList(
    entries: List<CheckInEntry>,
    isCoach: Boolean,
    onOverrideTap: (CheckInEntry, String) -> Unit  // entry + status button tapped
) {
    if (entries.isEmpty()) {
        EmptyAttendanceState()
        return
    }

    // Coach record status overrides player response status
    fun effectiveStatus(entry: CheckInEntry): String? {
        val recordStatus = entry.record?.status
        if (recordStatus != null) return when (recordStatus) {
            "present" -> "confirmed"
            "absent" -> "declined"
            "excused" -> "unsure"
            else -> recordStatus
        }
        return entry.response?.status
    }

    val confirmed = entries.filter { effectiveStatus(it) == "confirmed" }
    val maybe = entries.filter { effectiveStatus(it) == "unsure" }
    val declined = entries.filter { entry ->
        val s = effectiveStatus(entry)
        s == "declined" || s == "declined-auto"
    }
    val noResponse = entries.filter { entry ->
        val s = effectiveStatus(entry)
        s == null || s == "no-response"
    }

    val ext = MaterialTheme.extendedColors

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (confirmed.isNotEmpty()) {
            ResponseSectionCard(label = "GOING", count = confirmed.size, color = ext.going) {
                confirmed.forEach { entry ->
                    MemberResponseRow(
                        entry = entry,
                        isCoach = isCoach,
                        onStatusTap = { status -> onOverrideTap(entry, status) }
                    )
                }
            }
        }

        if (maybe.isNotEmpty()) {
            ResponseSectionCard(label = "UNSURE", count = maybe.size, color = ext.unsure) {
                maybe.forEach { entry ->
                    MemberResponseRow(
                        entry = entry,
                        isCoach = isCoach,
                        onStatusTap = { status -> onOverrideTap(entry, status) }
                    )
                }
            }
        }

        if (declined.isNotEmpty()) {
            ResponseSectionCard(label = "DECLINED", count = declined.size, color = ext.declined) {
                declined.forEach { entry ->
                    MemberResponseRow(
                        entry = entry,
                        isCoach = isCoach,
                        onStatusTap = { status -> onOverrideTap(entry, status) }
                    )
                }
            }
        }

        if (noResponse.isNotEmpty()) {
            ResponseSectionCard(
                label = "NO RESPONSE",
                count = noResponse.size,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                noResponse.forEach { entry ->
                    MemberResponseRow(
                        entry = entry,
                        isCoach = isCoach,
                        onStatusTap = { status -> onOverrideTap(entry, status) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ResponseSectionCard(
    label: String,
    count: Int,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label · $count",
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        content()
    }
}

@Composable
private fun EmptyAttendanceState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No responses yet",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Team members haven't responded to this event.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
