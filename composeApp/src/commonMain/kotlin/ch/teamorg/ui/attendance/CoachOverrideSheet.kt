package ch.teamorg.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.teamorg.ui.theme.PillShape
import ch.teamorg.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachOverrideSheet(
    visible: Boolean,
    playerName: String,
    currentStatus: String?,
    onDismiss: () -> Unit,
    onSave: (status: String, note: String?) -> Unit
) {
    if (!visible) return

    var selectedStatus by remember(currentStatus) { mutableStateOf(currentStatus) }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Header: avatar + name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = playerName
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .joinToString("") { it.first().uppercase() }
                    Text(
                        text = initials,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = playerName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics { contentDescription = "Close" }
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status label
            Text(
                text = "Set status",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            // Status buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusButton(
                    label = "Present",
                    description = "Mark as Present",
                    selected = selectedStatus == "present",
                    accentColor = MaterialTheme.extendedColors.going,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedStatus = "present" }
                )
                StatusButton(
                    label = "Absent",
                    description = "Mark as Absent",
                    selected = selectedStatus == "absent",
                    accentColor = MaterialTheme.extendedColors.declined,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedStatus = "absent" }
                )
                StatusButton(
                    label = "Excused",
                    description = "Mark as Excused",
                    selected = selectedStatus == "excused",
                    accentColor = MaterialTheme.extendedColors.unsure,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedStatus = "excused" }
                )
            }

            Spacer(Modifier.height(16.dp))

            TextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(Modifier.height(16.dp))

            // Save CTA
            Button(
                onClick = { onSave(selectedStatus!!, note.ifBlank { null }) },
                enabled = selectedStatus != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(57.dp),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Save Override",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatusButton(
    label: String,
    description: String,
    selected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = if (selected) PillShape else RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (selected) accentColor else MaterialTheme.colorScheme.surface)
            .then(
                if (!selected) Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = shape
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else accentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
