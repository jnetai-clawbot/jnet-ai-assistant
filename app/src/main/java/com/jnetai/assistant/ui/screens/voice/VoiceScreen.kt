package com.jnetai.assistant.ui.screens.voice

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jnetai.assistant.ui.components.GlowCard
import com.jnetai.assistant.ui.components.SectionHeader
import com.jnetai.assistant.ui.components.StatusBanner
import com.jnetai.assistant.ui.components.Tone
import com.jnetai.assistant.ui.screens.AppViewModel
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple
import com.jnetai.assistant.ui.theme.NeonPink
import com.jnetai.assistant.voice.VoiceState

@Composable
fun VoiceScreen(vm: AppViewModel) {
    val state by vm.voice.state.collectAsState()
    val partial by vm.voice.partialTranscript.collectAsState()
    val streamingResponse by vm.voice.streamingResponse.collectAsState()
    val currentTurn by vm.voice.currentTurn.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val context = LocalContext.current

    val profile = vm.selectedProfile()
    var saveStatus by remember { mutableStateOf("") }
    var saveTone by remember { mutableStateOf(Tone.INFO) }

    // Legacy devices (API < 29) need a runtime storage permission to write to Downloads.
    val storagePerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            vm.saveVoiceResponseAsAudio(responseText(streamingResponse, currentTurn)) { ok, msg ->
                saveStatus = if (ok) "Voice clip saved — $msg" else "Save failed: $msg"
                saveTone = if (ok) Tone.SUCCESS else Tone.ERROR
            }
        } else {
            saveStatus = "Storage permission needed to save clips (pre-Android 10)"
            saveTone = Tone.ERROR
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Voice Assistant", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            profile?.let { "Profile: ${it.name} — ${it.model}" } ?: "No profile selected",
            fontSize = 12.sp, color = NeonCyan
        )
        Spacer(Modifier.height(20.dp))

        // State indicator
        Text(
            state.displayName(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = when (state) {
                VoiceState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                VoiceState.LISTENING, VoiceState.SPEAKING, VoiceState.TRANSCRIBING, VoiceState.THINKING -> NeonCyan
                VoiceState.INTERRUPTED -> NeonPurple
                VoiceState.ERROR -> NeonPink
            }
        )
        Spacer(Modifier.height(16.dp))

        // Large mic
        val listening = state == VoiceState.LISTENING
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(colorForState(state).copy(alpha = 0.18f))
                .border(2.dp, colorForState(state), CircleShape)
                .clickable {
                    if (listening) vm.onVoiceInterrupt()
                    else vm.onVoiceMicPress()
                },
            contentAlignment = Alignment.Center
        ) {
            if (listening) {
                Text("●", color = NeonPink, fontSize = 44.sp)
            } else {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(colorForState(state))
                )
            }
        }
        Text(
            if (listening) "Listening — speak now (tap to stop)"
            else if (state == VoiceState.SPEAKING) "Speaking…"
            else if (state == VoiceState.THINKING) "Thinking…"
            else if (state == VoiceState.TRANSCRIBING) "Transcribing…"
            else "Tap the microphone to start a voice conversation",
            Modifier.padding(top = 12.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        SectionHeader("Transcript")
        GlowCard(Modifier.fillMaxWidth(), glow = NeonPurple) {
            Text(
                partial.ifBlank { currentTurn?.transcript ?: "( nothing yet )" },
                fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(8.dp))
        SectionHeader("Response")
        GlowCard(Modifier.fillMaxWidth(), glow = NeonCyan) {
            Text(
                streamingResponse.ifBlank { currentTurn?.response ?: "( waiting for a response )" },
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface
            )
            if (!currentTurn?.sources.isNullOrEmpty()) {
                Text(
                    currentTurn!!.sources.joinToString(", ") { "${it.documentName} p.${it.page}" },
                    fontSize = 11.sp, color = NeonCyan, modifier = Modifier.padding(top = 6.dp)
                )
            }

            val response = responseText(streamingResponse, currentTurn)
            if (response.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoiceAction("Copy response", Modifier.weight(1f), icon = {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = NeonCyan, modifier = Modifier.size(16.dp))
                    }) {
                        copyResponse(context, vm, response)
                    }
                    VoiceAction("Save clip", Modifier.weight(1f), icon = {
                        Icon(Icons.Default.FileDownload, "Save audio", tint = NeonPurple, modifier = Modifier.size(16.dp))
                    }) {
                        if (Build.VERSION.SDK_INT >= 29) {
                            vm.saveVoiceResponseAsAudio(response) { ok, msg ->
                                saveStatus = if (ok) "Voice clip saved — $msg" else "Save failed: $msg"
                                saveTone = if (ok) Tone.SUCCESS else Tone.ERROR
                            }
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                            vm.saveVoiceResponseAsAudio(response) { ok, msg ->
                                saveStatus = if (ok) "Voice clip saved — $msg" else "Save failed: $msg"
                                saveTone = if (ok) Tone.SUCCESS else Tone.ERROR
                            }
                        } else {
                            storagePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                    VoiceAction("Share", Modifier.weight(1f), icon = {
                        Icon(Icons.Default.Share, "Share response", tint = NeonPink, modifier = Modifier.size(16.dp))
                    }) {
                        shareResponse(context, vm, response)
                    }
                }
            }
        }
        if (saveStatus.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            StatusBanner(saveStatus, saveTone, Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(28.dp))
    }
}

private fun responseText(streaming: String, turn: com.jnetai.assistant.voice.VoiceTurn?): String =
    streaming.ifBlank { turn?.response ?: "" }

/** Issue #4 — copies the AI response text to the clipboard. */
private fun copyResponse(context: Context, vm: AppViewModel, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("JNetAI-response", text))
        vm.setStatus("Response copied to clipboard", Tone.SUCCESS)
    } catch (t: Throwable) {
        com.jnetai.assistant.util.Err.e(com.jnetai.assistant.util.Err.BACKUP_ERROR, "Voice copy failed", t)
        vm.setStatus("Could not copy response", Tone.ERROR)
    }
}

private fun shareResponse(context: Context, vm: AppViewModel, text: String) {
    try {
        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "J~Net AI response")
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        context.startActivity(android.content.Intent.createChooser(share, "Share response"))
    } catch (t: Throwable) {
        com.jnetai.assistant.util.Err.e(com.jnetai.assistant.util.Err.BACKUP_ERROR, "Voice share failed", t)
    }
}

@Composable
private fun VoiceAction(label: String, modifier: Modifier = Modifier, icon: @Composable () -> Unit = {}, onclick: () -> Unit) {
    Row(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(NeonCyan.copy(alpha = 0.15f))
            .clickable(onClick = onclick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text(
            label,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

private fun VoiceState.displayName() = when (this) {
    VoiceState.IDLE -> "IDLE"
    VoiceState.LISTENING -> "LISTENING"
    VoiceState.TRANSCRIBING -> "TRANSCRIBING"
    VoiceState.THINKING -> "THINKING"
    VoiceState.SPEAKING -> "SPEAKING"
    VoiceState.INTERRUPTED -> "INTERRUPTED"
    VoiceState.ERROR -> "ERROR"
}

private fun colorForState(s: VoiceState): Color = when (s) {
    VoiceState.IDLE -> NeonPurple
    VoiceState.LISTENING -> NeonPink
    VoiceState.TRANSCRIBING, VoiceState.THINKING -> NeonCyan
    VoiceState.SPEAKING -> NeonCyan
    VoiceState.INTERRUPTED -> NeonPurple
    VoiceState.ERROR -> NeonPink
}