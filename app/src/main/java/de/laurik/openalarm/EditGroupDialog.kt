package de.laurik.openalarm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun EditGroupDialog(
    group: AlarmGroup,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
    onDelete: ((Boolean) -> Unit)? = null
) {
    var name by remember { mutableStateOf(group.name) }
    var selectedColor by remember { mutableIntStateOf(group.colorArgb) }
    val isNewGroup = group.id.isEmpty() // Assuming empty ID means new group
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val groupColors = listOf(0xFFE3F2FD, 0xFFF3E5F5, 0xFFE8F5E9, 0xFFFFF3E0, 0xFFFFEBEE, 0xFFE0F7FA)

    // Delete confirmation
    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Group '${group.name}'?") },
            text = { Text("Do you want to delete the alarms inside this group too?") },
            confirmButton = {
                TextButton(onClick = { onDelete(false); showDeleteConfirm = false; onDismiss() }) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onDelete(true); showDeleteConfirm = false; onDismiss() }) {
                    Text("Keep Alarms")
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(24.dp)) {
                Text(if (isNewGroup) "New Group" else "Edit Group", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
                Text("Group Color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    groupColors.forEach { colorLong ->
                        val colorInt = colorLong.toInt()
                        val isSelected = selectedColor == colorInt
                        Box(
                            modifier = Modifier
                                .size(48.dp) // Larger touch target
                                .clip(CircleShape)
                                .background(Color(colorInt))
                                .border(
                                    if (isSelected) 3.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                                .clickable { selectedColor = colorInt },
                            contentAlignment = Alignment.Center
                        ) {
                             if (isSelected) {
                                 Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black.copy(alpha=0.7f))
                             }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                
                // Delete button
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Group")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onSave(name, selectedColor) }) { Text("Save") }
                }
            }
        }
    }
}