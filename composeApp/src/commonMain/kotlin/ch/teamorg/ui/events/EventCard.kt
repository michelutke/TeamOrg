package ch.teamorg.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.teamorg.domain.EventWithTeams
import ch.teamorg.ui.attendance.AttendanceRsvpButtons
import ch.teamorg.ui.theme.PillShape
import ch.teamorg.ui.theme.extendedColors
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun formatTwoDigits(n: Int): String = n.toString().padStart(2, '0')

private fun dayAbbrev(instant: kotlinx.datetime.Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return local.dayOfWeek.name.take(3).uppercase()
}

private fun formatHHmm(instant: kotlinx.datetime.Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${formatTwoDigits(local.hour)}:${formatTwoDigits(local.minute)}"
}

private fun buildTimeLine(ewt: EventWithTeams): String {
    val event = ewt.event
    val start = formatHHmm(event.startAt)
    val end = formatHHmm(event.endAt)
    val meetupAt = event.meetupAt
    return if (meetupAt != null) {
        "$start – $end · Meet ${formatHHmm(meetupAt)}"
    } else {
        "$start – $end"
    }
}

@Composable
private fun TypeChip(type: String) {
    val (label, container, content) = when (type) {
        "training" -> Triple(
            "Training",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        "match" -> Triple(
            "Match",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        else -> Triple(
            "Other",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
    Box(
        modifier = Modifier.clip(PillShape).background(container)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
private fun MyStatusChip(myResponse: String) {
    val ext = MaterialTheme.extendedColors
    val (label, container, content) = when (myResponse) {
        "confirmed" -> Triple("Going", ext.goingContainer, ext.going)
        "unsure" -> Triple("Unsure", ext.unsureContainer, ext.unsure)
        else -> Triple("Declined", ext.declinedContainer, ext.declined)
    }
    Box(
        modifier = Modifier.clip(PillShape).background(container)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
fun EventCard(
    ewt: EventWithTeams,
    confirmedCount: Int,
    maybeCount: Int,
    declinedCount: Int,
    myResponse: String?,
    onClick: () -> Unit,
    onRsvpSelect: (String) -> Unit
) {
    val event = ewt.event
    val isCancelled = event.status == "cancelled"
    val local = event.startAt.toLocalDateTime(TimeZone.currentSystemDefault())
    val contentColor =
        if (isCancelled) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Date block
                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (isCancelled) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                        .size(56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = dayAbbrev(event.startAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCancelled) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = local.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isCancelled) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildTimeLine(ewt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val locationOrTeam = event.location ?: ewt.matchedTeams.firstOrNull()?.name
                    if (locationOrTeam != null) {
                        Text(
                            text = locationOrTeam,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Chip row: type + my status / cancelled badge
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isCancelled) {
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Cancelled",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                } else {
                    TypeChip(event.type)
                    if (myResponse != null) {
                        MyStatusChip(myResponse)
                    }
                    if (event.externalSource == "nds" && event.presentCount > 0) {
                        val ext = MaterialTheme.extendedColors
                        Text(
                            text = "${event.presentCount} anwesend",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.going,
                            modifier = Modifier
                                .clip(PillShape)
                                .background(ext.goingContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Quick RSVP row
            if (!isCancelled) {
                AttendanceRsvpButtons(
                    currentResponse = myResponse,
                    confirmedCount = confirmedCount,
                    maybeCount = maybeCount,
                    declinedCount = declinedCount,
                    deadlinePassed = false,
                    compact = true,
                    onSelect = onRsvpSelect
                )
            }
        }
    }
}
