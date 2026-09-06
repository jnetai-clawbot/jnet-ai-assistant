package com.jnetai.assistant.ui.screens.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnetai.assistant.data.model.DocCollection
import com.jnetai.assistant.data.model.IndexStatus
import com.jnetai.assistant.data.model.IndexedDocument
import com.jnetai.assistant.ui.components.GlowCard
import com.jnetai.assistant.ui.components.SectionHeader
import com.jnetai.assistant.ui.components.StatusBanner
import com.jnetai.assistant.ui.screens.AppViewModel
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Documents tab.
 *
 * Each collection is a card with its OWN Upload button (pick any document
 * format into that specific collection), a Save button (rename) and a Remove
 * button (deletes the collection AND its docs from Docs / RAG / search only —
 * the files on the device are never touched). Every document inside a
 * collection can also be removed individually.
 */
@Composable
fun DocumentsScreen(vm: AppViewModel, onUpload: (Long) -> Unit, onFastPick: () -> Unit) {
    val documents by vm.documents.collectAsState()
    val collections by vm.collections.collectAsState()
    val status by vm.statusMessage.collectAsState()
    val tone by vm.statusTone.collectAsState()
    val selectedCollections by vm.selectedCollections.collectAsState()

    var newCollectionName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    var renamingId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    var confirmDeleteCollection by remember { mutableStateOf<Long?>(null) }

    val filtered = remember(documents, searchQuery) {
        if (searchQuery.isBlank()) documents else documents.filter { it.name.contains(searchQuery, true) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Documents", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Upload any document into a collection. Removing a doc or collection only clears it from RAG — the file stays on your device.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onFastPick) {
                Icon(Icons.Default.Add, "Add document", tint = NeonCyan)
            }
        }

        if (status.isNotEmpty()) StatusBanner(status, tone, Modifier.fillMaxWidth().padding(bottom = 6.dp))

        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = newCollectionName,
                onValueChange = { newCollectionName = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("New collection name") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(NeonPurple.copy(alpha = 0.25f))
                    .clickable { if (newCollectionName.isNotBlank()) { vm.createCollection(newCollectionName); newCollectionName = "" } }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("Create", color = NeonPurple, fontSize = 13.sp) }
        }

        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            placeholder = { Text("Search documents…") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        )

        SectionHeader("RAG scope — tap a collection to include it in RAG chat")

        if (collections.isEmpty()) {
            Text(
                "No collections yet — create one above, then tap Upload to add documents (any format).",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val existingIds = collections.map { it.id }
            val orphans = filtered.filter { d -> d.collectionId !in existingIds }

            if (collections.isEmpty() && filtered.isEmpty()) {
                item {
                    Text("Nothing here yet", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                }
            }

            items(collections, key = { c -> "col-${c.id}" }) { c ->
                val docs = filtered.filter { it.collectionId == c.id }
                CollectionCard(
                    collection = c,
                    docs = docs,
                    selected = selectedCollections.contains(c.id),
                    onToggleScope = { vm.toggleCollection(c.id) },
                    onUpload = { onUpload(c.id) },
                    onRenameStart = { renamingId = c.id; renameText = c.name },
                    onSave = { newName -> vm.saveCollectionName(c.id, newName); renamingId = null },
                    onRenameCancel = { renamingId = null },
                    onRemove = { confirmDeleteCollection = c.id },
                    onDeleteDoc = { docId -> vm.deleteDocument(docId) },
                    renaming = renamingId == c.id,
                    renameText = renameText,
                    onRenameTextChange = { renameText = it }
                )
            }

            if (orphans.isNotEmpty()) {
                item(key = "orphans") {
                    GlowCard(Modifier.fillMaxWidth(), glow = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Unassigned documents", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text("Not in a collection — searchable individually", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        orphans.forEach { doc ->
                            DocRow(doc) { vm.deleteDocument(doc.id) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    confirmDeleteCollection?.let { cid ->
        val col = collections.firstOrNull { it.id == cid }
        AlertDialog(
            onDismissRequest = { confirmDeleteCollection = null },
            title = { Text("Remove collection?") },
            text = {
                Text(
                    "Remove '${col?.name ?: "collection"}' and its documents from Docs / RAG / search? " +
                        "The files themselves stay on your device — only the local index is cleared."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteCollection(cid); confirmDeleteCollection = null }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteCollection = null }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun CollectionCard(
    collection: DocCollection,
    docs: List<IndexedDocument>,
    selected: Boolean,
    onToggleScope: () -> Unit,
    onUpload: () -> Unit,
    onRenameStart: () -> Unit,
    onSave: (String) -> Unit,
    onRenameCancel: () -> Unit,
    onRemove: () -> Unit,
    onDeleteDoc: (Long) -> Unit,
    renaming: Boolean,
    renameText: String,
    onRenameTextChange: (String) -> Unit
) {
    GlowCard(Modifier.fillMaxWidth(), glow = if (selected) NeonCyan else NeonPurple) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, null, tint = NeonPurple, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable(onClick = onToggleScope)) {
                Text(
                    collection.name, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    "${docs.size} doc(s) · ${SimpleDateFormat("MMM d", Locale.ROOT).format(Date(collection.createdAt))}" +
                        (if (selected) " · RAG ON" else " · tap to scope RAG"),
                    color = if (selected) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallChip("Upload", NeonCyan, onUpload, Icons.Default.Upload)
            if (!renaming) {
                SmallChip("Save", NeonPurple, onRenameStart, Icons.Default.Edit)
            }
            SmallChip("Remove", MaterialTheme.colorScheme.error, onRemove, Icons.Default.Delete)
        }
        if (renaming) {
            Spacer(Modifier.height(8.dp))
            TextField(
                value = renameText, onValueChange = onRenameTextChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(10.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallChip("Save name", NeonCyan, { onSave(renameText) }, Icons.Default.Check)
                SmallChip("Cancel", MaterialTheme.colorScheme.onSurfaceVariant, onRenameCancel, Icons.Default.Close)
            }
        }
        if (docs.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Documents", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            docs.take(100).forEach { doc -> DocRow(doc) { onDeleteDoc(doc.id) } }
            if (docs.size > 100) {
                Text("… and ${docs.size - 100} more (use search above)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Spacer(Modifier.height(4.dp))
            Text("No documents yet — tap Upload to add some (any format).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DocRow(doc: IndexedDocument, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Description, null, tint = NeonPurple, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                doc.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Row {
                Text(prettySize(doc.sizeBytes), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1)
                Text(" · ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                Text(doc.status.display, color = statusColor(doc.status), fontSize = 10.sp, maxLines = 1)
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SmallChip(
    label: String,
    color: Color,
    onClick: () -> Unit,
    icon: ImageVector? = null
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(it, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun statusColor(s: IndexStatus) = when (s) {
    IndexStatus.READY -> NeonCyan
    IndexStatus.FAILED -> MaterialTheme.colorScheme.error
    else -> NeonPurple
}

private fun prettySize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb)
    return String.format(Locale.ROOT, "%.1f MB", kb / 1024)
}