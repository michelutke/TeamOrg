package ch.teamorg.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ch.teamorg.domain.DeleteAccountResult

fun deleteAccountErrorMessage(result: DeleteAccountResult): String? = when (result) {
    DeleteAccountResult.Success -> null
    DeleteAccountResult.InvalidPassword -> "That password is incorrect."
    is DeleteAccountResult.OwnsClubs -> {
        val subject = if (result.clubNames.isEmpty()) {
            "You still own a club"
        } else {
            "You own ${result.clubNames.joinToString(", ")}"
        }
        "$subject. Contact info@teamorg.ch so we can transfer or close the club " +
            "before you delete your account."
    }
    is DeleteAccountResult.Error -> "Couldn't delete your account. Please try again."
}

@Composable
fun DeleteAccountDialog(
    deleteInProgress: Boolean,
    deleteError: String?,
    showCoachWarning: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var disclosureExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!deleteInProgress) onDismiss() },
        shape = RoundedCornerShape(28.dp),
        title = { Text("Delete account", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "This permanently deletes your personal data.",
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { disclosureExpanded = !disclosureExpanded }) {
                        Icon(Icons.Outlined.Info, contentDescription = "What gets deleted")
                    }
                }
                if (disclosureExpanded) {
                    Spacer(Modifier.height(8.dp))
                    Text("Deleted:", fontWeight = FontWeight.Bold)
                    Text("Your email address and name, profile picture, attendance replies, absences, notifications and their settings, and your team and club memberships.")
                    Spacer(Modifier.height(8.dp))
                    Text("Kept for your team:", fontWeight = FontWeight.Bold)
                    Text("Events you created and attendance you recorded stay with your team. Your name is replaced there.")
                    if (showCoachWarning) {
                        Spacer(Modifier.height(8.dp))
                        Text("Teams you coach will have no coach until a club manager assigns one.")
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Your password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !deleteInProgress,
                    modifier = Modifier.fillMaxWidth()
                )
                deleteError?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                Text("This cannot be undone.", fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank() && !deleteInProgress
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !deleteInProgress
            ) { Text("Cancel") }
        }
    )
}
