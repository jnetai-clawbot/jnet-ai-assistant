package com.jnetai.assistant.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnetai.assistant.data.model.Conversation
import com.jnetai.assistant.ui.components.GlowCard
import com.jnetai.assistant.ui.components.SectionHeader
import com.jnetai.assistant.ui.screens.AppViewModel
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPink
import com.jnetai.assistant.ui.theme.NeonPurple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * History screen — Issue #5. Shows every past conversation across all modes
 * (Normal / RAG / Hybrid / Agent / Voice) with open, rename, duplicate and
 * delete, plus Export history and Clear history (with confirmation).
 */
@Composable
fun HistoryScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onExportHistory: () -> Unit
) {
    val conversations by vm.conversations.collectAsState()
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("← back", Modifier.clickable { onBack() }, color = NeonCyan, fontSize = 14.sp)
            Text("History", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
        }
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryAction("Export history", Modifier.weight(1f)) { onExportHistory() }
            HistoryAction("Clear history", Modifier.weight(1f)) { showClearConfirm = true }
        }
        Spacer(Modifier.height(8.dp))

        if (conversations.isEmpty()) {
            Text(
                "No conversations yet — every mode (Chat, RAG, Hybrid, Agent, Voice) is shown here as you use it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(conversations) { c ->
                HistoryRow(
                    c,
                    onOpen = { onOpenConversation(c.id) },
                    onRename = { renameTarget = c },
                    onDuplicate = { vm.duplicateConversation(c.id) },
                    onDelete = { vm.deleteConversation(c.id) }
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            title = target.title,
            onDismiss = { renameTarget = null },
            onSave = { newTitle ->
                vm.renameConversation(target.id, newTitle)
                renameTarget = null
            }
        )
    }

    if (showClearConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all history?") },
            text = { Text("This deletes every conversation and response across all modes (Chat, RAG, Hybrid, Agent, Voice). This cannot be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showClearConfirm = false
                    vm.clearAllHistory()
                }) { Text("Clear all", color = NeonPink) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HistoryRow(
    c: Conversation,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    GlowCard(Modifier.fillMaxWidth(), glow = NeonPurple.copy(alpha = 0.4f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(
                    c.title,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    "${c.mode.display} · ${c.model.ifBlank { "no model" }} · " +
                        SimpleDateFormat("MMM d HH:mm", Locale.ROOT).format(Date(c.updatedAt)),
                    fontSize = 11.sp, color = NeonCyan, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onOpen) { Icon(Icons.Default.KeyboardArrowRight, "Open", tint = NeonCyan, modifier = Modifier.height(18.dp)) }
            IconButton(onClick = onRename) { Icon(Icons.Default.Edit, "Rename", tint = NeonPurple, modifier = Modifier.height(18.dp)) }
            IconButton(onClick = onDuplicate) { Icon(Icons.Default.ContentCopy, "Duplicate", tint = NeonCyan, modifier = Modifier.height(18.dp)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = NeonPink, modifier = Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun HistoryAction(label: String, modifier: Modifier = Modifier, onclick: () -> Unit) {
    Text(
        label,
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(NeonCyan.copy(alpha = 0.18f))
            .clickable(onClick = onclick)
            .padding(vertical = 10.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun RenameDialog(title: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(title) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename conversation") },
        text = {
            Column {
                SectionHeader("New title")
                TextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onSave(value.trim().ifBlank { title }) }) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}