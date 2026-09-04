package com.jnetai.assistant.ui.screens.models

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnetai.assistant.data.model.LocalModel
import com.jnetai.assistant.ml.HardwareAccel
import com.jnetai.assistant.ml.detectHardware
import com.jnetai.assistant.ui.components.GlowCard
import com.jnetai.assistant.ui.components.SectionHeader
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple

@Composable
fun ModelsScreen(vm: com.jnetai.assistant.ui.screens.AppViewModel, onPickModel: () -> Unit) {
    val models by androidx.compose.runtime.produceState(initialValue = emptyList<LocalModel>()) {
        value = com.jnetai.assistant.data.db.AppDatabase
            .get(androidx.compose.ui.platform.LocalContext.current)
            .modelDao().getAllOnce()
    }.value
    val hw = androidx.compose.runtime.remember { detectHardware() }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Local Models", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "Import",
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonCyan.copy(alpha = 0.25f))
                    .clickable(onClick = onPickModel)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
        }

        HardwareCard(hw)

        SectionHeader("Installed models")
        if (models.isEmpty()) {
            Text(
                "No local models imported. Use the storage picker to import a GGUF/llama.cpp compatible model.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(models) { m -> ModelCard(m, vm) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HardwareCard(hw: HardwareAccel) {
    GlowCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PhoneAndroid, null, tint = NeonPurple, modifier = Modifier.size(26.dp))
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("Device capabilities", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "CPU: ${if (hw.cpu) "available" else "unavailable"}   " +
                        "GPU: ${if (hw.gpu) "available" else "unavailable"}   " +
                        "NNA: ${if (hw.nna) "available" else "unavailable"}",
                    fontSize = 13.sp, color = NeonCyan
                )
                Text(
                    "Local inference runs on-device when an engine is present. Larger models warn before loading.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModelCard(m: LocalModel, vm: com.jnetai.assistant.ui.screens.AppViewModel) {
    GlowCard(Modifier.fillMaxWidth(), glow = if (m.active) NeonCyan else NeonPurple) {
        Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoChip("${prettyMb(m.sizeBytes)} MB")
            InfoChip("ctx ${m.contextLength}")
            InfoChip(m.quantisation)
            InfoChip("${m.threads} threads")
            InfoChip("GPU ${m.gpuLayers} layers")
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (m.loaded) Icons.Default.Verified else Icons.Default.Memory,
                null, tint = if (m.loaded) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                if (m.loaded) "Loaded" else if (m.active) "Active · not loaded" else "Inactive",
                Modifier.padding(start = 4.dp), fontSize = 12.sp,
                color = if (m.loaded) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "· ~${m.memoryEstimateMb} MB RAM",
                Modifier.padding(start = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (m.active) "Active" else "Activate",
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (m.active) NeonCyan.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .clickable { vm.selectActiveModel(m.id) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = if (m.active) NeonCyan else NeonPurple, fontSize = 12.sp
            )
            Text(
                "Remove",
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                    .clickable { vm.removeModel(m.id) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.error, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun InfoChip(label: String) {
    Text(
        label,
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun prettyMb(bytes: Long): String =
    String.format(java.util.Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0))