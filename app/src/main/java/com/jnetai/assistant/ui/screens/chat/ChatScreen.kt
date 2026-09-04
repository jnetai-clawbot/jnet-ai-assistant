package com.jnetai.assistant.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.jnetai.assistant.data.model.ChatMode
import com.jnetai.assistant.ui.components.GlowCard
import com.jnetai.assistant.ui.components.StatusBanner
import com.jnetai.assistant.ui.components.Tone
import com.jnetai.assistant.ui.screens.AppViewModel
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple
import com.jnetai.assistant.ui.theme.surfaceDarkElevated

@Composable
fun ChatScreen(
    vm: AppViewModel,
    onViewConversations: () -> Unit,
    onPickAttachments: () -> Unit
) {
    val messages by vm.messages.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val selectedProfileId by vm.selectedProfileId.collectAsState()
    val streaming by vm.isStreaming.collectAsState()
    val streamingText by vm.streamingText.collectAsState()
    val status by vm.statusMessage.collectAsState()
    val tone by vm.statusTone.collectAsState()
    val input by vm.inputText.collectAsState()
    val mode by vm.chatMode.collectAsState()
    val busy by vm.chatBusy.collectAsState()

    var showHistory by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Chat", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            ProfileSelector(vm, profiles, selectedProfileId)
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { showHistory = !showHistory }) {
                Icon(Icons.Default.AttachFile, "History", tint = NeonCyan)
            }
        }

        ModeRow(vm, mode)

        AnimatedVisibility(visible = status.isNotEmpty()) {
            StatusBanner(status, tone, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }

        if (showHistory) {
            ConversationList(vm, onDone = { showHistory = false })
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { m ->
                MessageBubble(m.role, m.content, m.sources)
            }
            if (streaming && streamingText.isNotBlank()) {
                item { MessageBubble("assistant", streamingText, null, streaming = true) }
            }
            if (messages.isEmpty() && !streaming) {
                item { EmptyChat(vm) }
            }
        }

        LaunchedEffect(messages.size, streamingText.length) {
            if (messages.isNotEmpty() || (streaming && streamingText.isNotBlank())) {
                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
            }
        }

        InputBar(
            input, { vm.inputText.value = it },
            canSend = input.isNotBlank() && !busy,
            streaming = streaming,
            onSend = { vm.sendMessage(input) },
            onStop = vm::stopStreaming,
            onMic = { vm.onVoiceMicPress() },
            onPickAttachments = onPickAttachments
        )
    }
}

@Composable
private fun ProfileSelector(
    vm: AppViewModel, profiles: List<com.jnetai.assistant.data.model.ConnectionProfile>,
    selectedId: Long
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = profiles.find { it.id == selectedId } ?: profiles.firstOrNull()
    Box {
        Text(
            selected?.name ?: "No profile",
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(surfaceDarkElevated)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            color = NeonPurple, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
        )
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            profiles.forEach { p ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(p.name) },
                    onClick = { vm.selectProfile(p.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ModeRow(vm: AppViewModel, current: ChatMode) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ChatMode.values().forEach { m ->
            val active = m == current
            val dot = when (m) {
                ChatMode.NORMAL -> Color(0xFF4CAF50)
                ChatMode.RAG -> NeonCyan
                ChatMode.HYBRID -> Color(0xFFFFC107)
                ChatMode.AGENT -> NeonPurple
                ChatMode.VOICE -> Color(0xFFFF2D78)
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { vm.setMode(m) }
                    .background(if (active) dot.copy(alpha = 0.18f) else surfaceDarkElevated.copy(alpha = 0.6f))
                    .border(
                        1.dp,
                        if (active) dot.copy(alpha = 0.7f) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(7.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(dot))
                Text(
                    m.display,
                    Modifier.padding(start = 6.dp),
                    color = if (active) dot else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    role: String,
    content: String,
    sourcesJson: String?,
    streaming: Boolean = false
) {
    val isUser = role == "user"
    val sources = remember(sourcesJson) {
        sourcesJson?.let { com.google.gson.Gson().fromJson(it, Array<com.jnetai.assistant.data.model.ChatSource>::class.java)?.toList() }
    }
    if (content.isBlank()) return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        GlowCard(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 1f),
            glow = if (isUser) NeonCyan else NeonPurple
        ) {
            if (streaming) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(14.dp).height(14.dp), strokeWidth = 2.dp, color = NeonCyan)
                    Text(content, Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                Text(content, color = MaterialTheme.colorScheme.onSurface)
            }
            if (sources != null) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    sources.forEach { s ->
                        Text(
                            "• ${s.documentName}" + if (s.page > 0) " p.${s.page}" else "" + if (s.section.isNotBlank()) " — ${s.section}" else "",
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            color = NeonCyan, fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChat(vm: AppViewModel) {
    Column(
        Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("J~Net AI Assistant", color = NeonPurple, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Choose a profile above and start typing. Switch modes for RAG, agent or voice.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 40.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun InputBar(
    input: String,
    onInput: (String) -> Unit,
    canSend: Boolean,
    streaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMic: () -> Unit,
    onPickAttachments: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .imePadding(),
        verticalAlignment = Alignment.Bottom
    ) {
        TextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message…", fontSize = 14.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
            maxLines = 5,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = surfaceDarkElevated,
                unfocusedContainerColor = surfaceDarkElevated,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        if (streaming) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onStop)
                    .background(Color(0xFFC62828))
            ) {
                Icon(Icons.Default.Stop, "Stop", Modifier.align(Alignment.Center), tint = Color.White)
            }
        } else {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onMic)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Icon(Icons.Default.Mic, "Voice", Modifier.align(Alignment.Center), tint = Color(0xFFFF2D78))
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(50))
                .clickable(enabled = canSend, onClick = onSend)
                .background(if (canSend) NeonCyan.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                Icons.Default.Send, "Send",
                Modifier.align(Alignment.Center),
                tint = if (canSend) Color(0xFF00363F) else Color.Gray
            )
        }
    }
}

@Composable
private fun ConversationList(vm: AppViewModel, onDone: () -> Unit) {
    val conversations by vm.conversations.collectAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp)
    ) {
        Text("Conversations", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NeonCyan)
        LazyColumn(Modifier.fillMaxSize()) {
            items(conversations) { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { vm.openConversation(c.id); onDone() }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        Text(
                            "${c.model} · ${java.text.SimpleDateFormat("MMM d HH:mm").format(java.util.Date(c.updatedAt))}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                        )
                    }
                    Text("+", Modifier.clickable { vm.newConversation(); onDone() }, color = NeonCyan)
                }
            }
        }
    }
}