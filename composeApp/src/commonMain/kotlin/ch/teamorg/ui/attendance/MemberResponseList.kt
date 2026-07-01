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
import ch.teamorg.domain.AttendanceResponse
import ch.teamorg.domain.TeamMember
import ch.teamorg.ui.theme.extendedColors

@Composable
fun MemberResponseList(
    responses: List<AttendanceResponse>,
    isCoach: Boolean,
    checkInStatus: String,
    rosterMap: Map<String, TeamMember>,
    onEditTap: (AttendanceResponse) -> Unit
) {
    if (responses.isEmpty()) {
        EmptyAttendanceState()
        return
    }

    val confirmed = responses.filter { it.status == "confirmed" }
    val maybe = responses.filter { it.status == "unsure" }
    val declined = responses.filter { it.status == "declined" || it.status == "declined-auto" }
    val noResponse = responses.filter { it.status == "no-response" }

    val ext = MaterialTheme.extendedColors
    val coachEditable = isCoach && checkInStatus != "done"

    @Composable
    fun RowFor(response: AttendanceResponse) {
        val member = rosterMap[response.userId]
        MemberResponseRow(
            response = response,
            displayName = member?.displayName ?: response.userId,
            avatarUrl = member?.avatarUrl,
            isCoach = isCoach,
            coachEditable = coachEditable,
            onEditTap = { onEditTap(response) }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (confirmed.isNotEmpty()) {
            ResponseSectionCard(label = "GOING", count = confirmed.size, color = ext.going) {
                confirmed.forEach { RowFor(it) }
            }
        }

        if (maybe.isNotEmpty()) {
            ResponseSectionCard(label = "UNSURE", count = maybe.size, color = ext.unsure) {
                maybe.forEach { RowFor(it) }
            }
        }

        if (declined.isNotEmpty()) {
            ResponseSectionCard(label = "DECLINED", count = declined.size, color = ext.declined) {
                declined.forEach { RowFor(it) }
            }
        }

        if (noResponse.isNotEmpty()) {
            ResponseSectionCard(
                label = "NO RESPONSE",
                count = noResponse.size,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                noResponse.forEach { RowFor(it) }
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
