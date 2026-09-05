package com.jnetai.assistant.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
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
    val micListening by vm.chatMicListening.collectAsState()

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
            IconButton(onClick = onViewConversations) {
                Icon(Icons.Default.History, "History", tint = NeonCyan)
            }
            IconButton(onClick = { shareConversation(vm, context) }) {
                Icon(Icons.Default.Share, "Share conversation", tint = NeonPurple)
            }
        }

        ModeRow(vm, mode)

        AnimatedVisibility(visible = status.isNotEmpty()) {
            StatusBanner(status, tone, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
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
            micListening = micListening && !streaming,
            onSend = { vm.sendMessage(input) },
            onStop = vm::stopStreaming,
            onMic = { vm.onChatMicPress() },
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
    micListening: Boolean,
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
                    .background(if (micListening) Color(0xFFFF2D78).copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                if (micListening) {
                    Text("●", Modifier.align(Alignment.Center), color = Color.White, fontSize = 18.sp)
                } else {
                    Icon(Icons.Default.Mic, "Voice", Modifier.align(Alignment.Center), tint = Color(0xFFFF2D78))
                }
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

/**
 * Issue #6 (share conversation): copies the current conversation text to the
 * clipboard and offers the system share sheet.
 */
private fun shareConversation(vm: AppViewModel, context: Context) {
    val text = vm.currentConversationText()
    if (text.isBlank()) {
        vm.setStatus("Nothing to share yet", com.jnetai.assistant.ui.components.Tone.INFO)
        return
    }
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("JNetAI-conversation", text))
    } catch (t: Throwable) {
        com.jnetai.assistant.util.Err.e(com.jnetai.assistant.util.Err.BACKUP_ERROR, "Clipboard write failed", t)
    }
    try {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "J~Net AI conversation")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(share, "Share conversation"))
        vm.setStatus("Conversation copied to clipboard", com.jnetai.assistant.ui.components.Tone.SUCCESS)
    } catch (t: Throwable) {
        vm.setStatus("Copied to clipboard (no share app available)", com.jnetai.assistant.ui.components.Tone.SUCCESS)
    }
}