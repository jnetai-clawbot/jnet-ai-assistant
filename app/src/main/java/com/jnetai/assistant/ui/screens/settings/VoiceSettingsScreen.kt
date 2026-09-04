package com.jnetai.assistant.ui.screens.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnetai.assistant.ui.components.GlowCard
import com.jnetai.assistant.ui.components.SectionHeader
import com.jnetai.assistant.ui.screens.AppViewModel
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple

@Composable
fun VoiceSettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    var sttProvider by remember { mutableStateOf("android") }
    var ttsProvider by remember { mutableStateOf("android") }
    var rate by remember { mutableStateOf(1.0f) }
    var pitch by remember { mutableStateOf(1.0f) }
    var autoSpeak by remember { mutableStateOf(true) }
    var liveMode by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        sttProvider = vm.settings.getSttProvider()
        ttsProvider = vm.settings.getTtsProvider()
        rate = vm.settings.getTtsRate()
        pitch = vm.settings.getTtsPitch()
        autoSpeak = vm.settings.getAutoSpeak()
        liveMode = vm.settings.getBool("voice.live_mode", true)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("← back", Modifier.clickable { onBack() }, color = NeonCyan, fontSize = 14.sp)
            Text("Voice & Speech", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
        }
        Spacer(Modifier.height(8.dp))

        SectionHeader("Speech-to-Text provider")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProviderChip("android", sttProvider) { sttProvider = it }
            ProviderChip("whisper", sttProvider) { sttProvider = it }
            ProviderChip("custom", sttProvider) { sttProvider = it }
        }
        Text(
            "Android STT uses the device recogniser. Whisper/custom endpoints plug into the same provider interface (configure them on a connection profile).",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))
        SectionHeader("Text-to-Speech")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProviderChip("android", ttsProvider) { ttsProvider = it }
            ProviderChip("custom", ttsProvider) { ttsProvider = it }
        }

        Spacer(Modifier.height(8.dp))
        GlowCard(Modifier.fillMaxWidth(), glow = NeonPurple) {
            Text("Speech speed: ${"%.1f".format(rate)}x", fontSize = 13.sp)
            Slider(value = rate, onValueChange = { rate = it }, valueRange = 0.5f..2.0f)
            Spacer(Modifier.height(6.dp))
            Text("Pitch: %.1f".format(pitch), fontSize = 13.sp)
            Slider(value = pitch, onValueChange = { pitch = it }, valueRange = 0.5f..2.0f)
        }

        Spacer(Modifier.height(8.dp))
        GlowCard(Modifier.fillMaxWidth(), glow = NeonCyan) {
            Row {
                Text("Speak AI responses automatically in voice mode", modifier = Modifier.weight(1f), fontSize = 14.sp)
                Checkbox(checked = autoSpeak, onCheckedChange = { autoSpeak = it })
            }
            Row {
                Text("Live assistant mode (continuous listen)", modifier = Modifier.weight(1f), fontSize = 14.sp)
                Checkbox(checked = liveMode, onCheckedChange = { liveMode = it })
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                vm.saveVoiceSettings(sttProvider, ttsProvider, rate, pitch, autoSpeak, liveMode)
                onBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProviderChip(label: String, current: String, onSelect: (String) -> Unit) {
    val active = current == label
    Text(
        label,
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) NeonCyan.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { onSelect(label) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (active) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp
    )
}