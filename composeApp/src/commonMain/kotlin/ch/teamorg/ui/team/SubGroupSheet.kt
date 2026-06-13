package ch.teamorg.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.teamorg.domain.SubGroup
import ch.teamorg.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubGroupSheet(
    teamId: String,
    subGroups: List<SubGroup>,
    isCoachOrManager: Boolean,
    onDismiss: () -> Unit,
    onCreateSubGroup: (String) -> Unit,
    onDeleteSubGroup: (String) -> Unit
) {
    var showAddField by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Text(
                "Subgroups",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (subGroups.isEmpty() && !showAddField) {
                Text(
                    "No sub-groups yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(subGroups, key = { it.id }) { subGroup ->
                    SubGroupRow(
                        subGroup = subGroup,
                        isCoachOrManager = isCoachOrManager,
                        onDelete = { onDeleteSubGroup(subGroup.id) }
                    )
                }
            }

            if (showAddField) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    placeholder = { Text("Sub-group name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        Row {
                            TextButton(
                                onClick = {
                                    if (newGroupName.isNotBlank()) {
                                        onCreateSubGroup(newGroupName.trim())
                                        newGroupName = ""
                                        showAddField = false
                                    }
                                }
                            ) { Text("Add") }
                            TextButton(onClick = { showAddField = false; newGroupName = "" }) { Text("Cancel") }
                        }
                    }
                )
            }

            if (isCoachOrManager && !showAddField) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showAddField = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(57.dp),
                    shape = PillShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "New subgroup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SubGroupRow(
    subGroup: SubGroup,
    isCoachOrManager: Boolean,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                subGroup.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${subGroup.memberCount} member${if (subGroup.memberCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isCoachOrManager) {
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete sub-group",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Delete Sub-group", fontWeight = FontWeight.Bold) },
            text = { Text("Delete \"${subGroup.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
