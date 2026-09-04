package com.jnetai.assistant.ui.screens.voice

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnetai.assistant.ui.components.GlowCard
import com.jnetai.assistant.ui.components.SectionHeader
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

    val profile = vm.selectedProfile()

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
            color = stateColor(state)
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
        }

        Spacer(Modifier.height(28.dp))
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

@Composable
private fun VoiceState.stateColor() = when (this) {
    VoiceState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    VoiceState.LISTENING, VoiceState.SPEAKING, VoiceState.TRANSCRIBING, VoiceState.THINKING -> NeonCyan
    VoiceState.INTERRUPTED -> NeonPurple
    VoiceState.ERROR -> NeonPink
}

private fun colorForState(s: VoiceState): Color = when (s) {
    VoiceState.IDLE -> NeonPurple
    VoiceState.LISTENING -> NeonPink
    VoiceState.TRANSCRIBING, VoiceState.THINKING -> NeonCyan
    VoiceState.SPEAKING -> NeonCyan
    VoiceState.INTERRUPTED -> NeonPurple
    VoiceState.ERROR -> NeonPink
}