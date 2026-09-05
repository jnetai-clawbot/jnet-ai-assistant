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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnetai.assistant.data.model.IndexStatus
import com.jnetai.assistant.ui.components.GlowCard
import com.jnetai.assistant.ui.components.SectionHeader
import com.jnetai.assistant.ui.components.StatusBanner
import com.jnetai.assistant.ui.screens.AppViewModel
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DocumentsScreen(vm: AppViewModel, onPickFiles: () -> Unit, onPickFolder: () -> Unit) {
    val documents by vm.documents.collectAsState()
    val collections by vm.collections.collectAsState()
    val status by vm.statusMessage.collectAsState()
    val tone by vm.statusTone.collectAsState()
    val selectedCollections by vm.selectedCollections.collectAsState()

    var newCollectionName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Documents", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onPickFiles) {
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

        SectionHeader("Collections — tap to filter RAG scope")
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            collections.forEach { c ->
                val active = selectedCollections.contains(c.id)
                Text(
                    c.name,
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) NeonCyan.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable { vm.toggleCollection(c.id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = if (active) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            if (collections.isEmpty()) Text("No collections yet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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

        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val filtered = documents.filter {
                searchQuery.isBlank() || it.name.contains(searchQuery, true)
            }
            if (filtered.isEmpty()) {
                item { Text("No documents indexed yet", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 30.dp)) }
            }
            items(filtered, key = { it.id }) { doc ->
                DocumentCard(doc, onDelete = { vm.deleteDocument(doc.id) })
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DocumentCard(doc: com.jnetai.assistant.data.model.IndexedDocument, onDelete: () -> Unit) {
    GlowCard(Modifier.fillMaxWidth(), glow = when (doc.status) {
        IndexStatus.READY -> NeonCyan
        IndexStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> NeonPurple
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Description,
                null, tint = NeonPurple, modifier = Modifier.size(28.dp)
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    doc.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Row {
                    Text(prettySize(doc.sizeBytes), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
                    Text(" · ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    Text(
                        if (doc.pageCount > 0) "${doc.pageCount} pages" else "1 page",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1
                    )
                }
                Text(
                    "Status: ${doc.status.display} · ${SimpleDateFormat("MMM d HH:mm", Locale.ROOT).format(Date(doc.updatedAt))}",
                    color = statusColor(doc.status), fontSize = 11.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
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