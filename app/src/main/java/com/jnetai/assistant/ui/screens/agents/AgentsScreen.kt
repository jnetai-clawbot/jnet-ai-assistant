package com.jnetai.assistant.ui.screens.agents

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnetai.assistant.agent.PermissionKind
import com.jnetai.assistant.ui.components.GlowCard
import com.jnetai.assistant.ui.components.SectionHeader
import com.jnetai.assistant.ui.components.StatusBanner
import com.jnetai.assistant.ui.screens.AppViewModel
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple
import com.jnetai.assistant.ui.theme.NeonPink

@Composable
fun AgentsScreen(vm: AppViewModel) {
    val status by vm.statusMessage.collectAsState()
    val tone by vm.statusTone.collectAsState()
    var prompt by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }

    val trust = vm.permissions.trustLevel()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Agent", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "The agent can use authorised tools. Every action is permission-controlled and logged.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        if (status.isNotEmpty()) StatusBanner(status, tone, Modifier.fillMaxWidth())

        Spacer(Modifier.height(8.dp))
        SectionHeader("Trust level: $trust")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AgentTrustChip("ask", trust) { vm.permissions.setTrustLevel("ask") }
            AgentTrustChip("destructive", trust) { vm.permissions.setTrustLevel("destructive") }
            AgentTrustChip("trusted", trust) { vm.permissions.setTrustLevel("trusted") }
            AgentTrustChip("disabled", trust) { vm.permissions.setTrustLevel("disabled") }
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("Permissions")
        PermissionToggle(vm, PermissionKind.DOCUMENTS)
        PermissionToggle(vm, PermissionKind.MICROPHONE)
        PermissionToggle(vm, PermissionKind.NOTIFICATIONS)
        PermissionToggle(vm, PermissionKind.CLIPBOARD)
        PermissionToggle(vm, PermissionKind.ACCESSIBILITY)
        PermissionToggle(vm, PermissionKind.NETWORK)
        PermissionToggle(vm, PermissionKind.FILES)
        PermissionToggle(vm, PermissionKind.DEVICE_ACTIONS)
        PermissionToggle(vm, PermissionKind.CALCULATION)
        PermissionToggle(vm, PermissionKind.DATA_QUERY)
        PermissionToggle(vm, PermissionKind.AI_ENDPOINT)

        Spacer(Modifier.height(12.dp))
        SectionHeader("Run agent")
        TextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. Find the PDF about my Raspberry Pi and summarise it.") },
            maxLines = 4,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp)
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = prompt.isNotBlank() && !running) {
                        running = true
                        vm.runAgent(prompt) { out -> result = out; running = false }
                    }
                    .background(if (prompt.isNotBlank() && !running) NeonPurple else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (running) {
                    Text("Running…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                } else {
                    Icon(Icons.Default.Send, null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                    Text("Run", Modifier.padding(start = 6.dp), color = NeonCyan, fontSize = 13.sp)
                }
            }
        }

        if (result.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            GlowCard(Modifier.fillMaxWidth(), glow = NeonCyan) {
                Text(result, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AgentTrustChip(value: String, current: String, onClick: () -> Unit) {
    val active = value == current
    Text(
        value,
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) NeonCyan.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = if (active) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp
    )
}

@Composable
private fun PermissionToggle(vm: AppViewModel, kind: PermissionKind) {
    var enabled by remember(kind) { mutableStateOf(vm.permissions.permitKind(kind)) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { enabled = !enabled; vm.permissions.setPermit(kind, enabled) }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(kind.name.replace("_", " "), modifier = Modifier.weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(
            if (enabled) "ON" else "OFF",
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (enabled) NeonCyan.copy(alpha = 0.25f) else NeonPink.copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = if (enabled) NeonCyan else NeonPink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
        )
    }
}