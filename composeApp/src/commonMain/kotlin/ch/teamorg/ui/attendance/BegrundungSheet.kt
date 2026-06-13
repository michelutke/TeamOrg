package ch.teamorg.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.teamorg.ui.theme.PillShape
import ch.teamorg.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BegrundungSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit,
    mode: String = "unsure"  // "unsure" | "declined"
) {
    if (!visible) return

    val isDecline = mode == "declined"
    val title = if (isDecline) "Why can't you go?" else "Tell your coach why"
    val subtitle = if (isDecline) {
        "An optional note helps your coach plan ahead."
    } else {
        "A short explanation is required when you're unsure about attending."
    }
    val buttonLabel = if (isDecline) "Confirm Decline" else "Confirm Maybe"
    val statusChipLabel = if (isDecline) "✗ Declined" else "? Unsure"
    val statusColor = if (isDecline) {
        MaterialTheme.extendedColors.declined
    } else {
        MaterialTheme.extendedColors.unsure
    }
    val statusContainer = if (isDecline) {
        MaterialTheme.extendedColors.declinedContainer
    } else {
        MaterialTheme.extendedColors.unsureContainer
    }
    val reasonRequired = !isDecline  // unsure requires reason, decline is optional

    var text by remember { mutableStateOf("") }

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
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status pill
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(statusContainer)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = statusChipLabel,
                    color = statusColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtitle,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Begründung") },
                minLines = 3,
                maxLines = 6,
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

            Text(
                text = "${text.length} / 200",
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )

            val isEnabled = !reasonRequired || text.isNotBlank()
            Button(
                onClick = { if (isEnabled) onConfirm(text.trim()) },
                modifier = Modifier.fillMaxWidth().height(57.dp),
                enabled = isEnabled,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    buttonLabel,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
