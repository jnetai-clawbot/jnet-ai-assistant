package com.jnetai.assistant.ui.screens.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnetai.assistant.data.model.AuthType
import com.jnetai.assistant.data.model.ConnectionProfile
import com.jnetai.assistant.data.model.ProviderType
import com.jnetai.assistant.ui.components.GlowCard
import com.jnetai.assistant.ui.components.SectionHeader
import com.jnetai.assistant.ui.components.StatusBanner
import com.jnetai.assistant.ui.components.Tone
import com.jnetai.assistant.ui.screens.AppViewModel
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple
import com.jnetai.assistant.ui.theme.NeonPink

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onSecuritySettings: () -> Unit,
    onAbout: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onVoiceSettings: () -> Unit
) {
    val profiles by vm.profiles.collectAsState()
    val status by vm.statusMessage.collectAsState()
    val testResult by vm.testResult.collectAsState()

    var editing by remember { mutableStateOf<ConnectionProfile?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (status.isNotEmpty()) StatusBanner(status, vm.statusTone.collectAsState().value, Modifier.fillMaxWidth().padding(top = 6.dp))
        Spacer(Modifier.height(8.dp))

        SectionHeader("AI Connection Profiles")
        if (profiles.isEmpty()) {
            Text("No profiles yet — create one to chat.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        profiles.forEach { p ->
            ProfileRow(
                p,
                onEdit = { editing = p },
                onTest = { vm.testProfile(p) },
                onDelete = { vm.deleteProfile(p) }
            )
        }
        Text(
            "＋ New profile",
            Modifier.padding(top = 4.dp).clickable { editing = ConnectionProfile() },
            color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
        )

        testResult?.let { r ->
            GlowCard(Modifier.fillMaxWidth().padding(top = 8.dp), glow = if (r.ok) NeonCyan else NeonPink) {
                Text(
                    if (r.ok) "✓ Connected (${r.latencyMs} ms) — authentication ${if (r.authOk) "OK" else "n/a"}" else "✕ ${r.message}",
                    color = if (r.ok) NeonCyan else NeonPink, fontSize = 13.sp
                )
                if (r.ok && r.models.isNotEmpty()) {
                    Text("Models: ${r.models.take(8).joinToString(", ")}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionHeader("General")
        SettingsRow("Security", "App lock · biometric · encryption", onSecuritySettings)
        SettingsRow("Voice & Speech", "STT / TTS providers & settings", onVoiceSettings)
        SettingsRow("About", "Version, updates, share", onAbout)
        SettingsRow("Export backup", "Encrypted export of settings, profiles, chat", onExport)
        SettingsRow("Import backup", "Restore from an encrypted backup", onImport)

        Spacer(Modifier.height(24.dp))
    }

    editing?.let { profile ->
        ProfileEditor(
            vm = vm,
            profile = profile,
            onSubmit = { p, key ->
                vm.saveProfile(p, key)
                editing = null
            },
            onClose = { editing = null },
            testResult = testResult,
            onTest = { vm.testProfile(profile) }
        )
    }
}

@Composable
private fun ProfileRow(p: ConnectionProfile, onEdit: () -> Unit, onTest: () -> Unit, onDelete: () -> Unit) {
    GlowCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${p.providerType.display} · ${if (p.port > 0) "${p.endpoint}:${p.port}" else p.endpoint} · ${if (p.model.isNotBlank()) p.model else "no model"}",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (p.enabled) "enabled" else "disabled",
                    fontSize = 10.sp, color = if (p.enabled) NeonCyan else NeonPink
                )
            }
            IconButton(onClick = onTest) { Icon(Icons.Default.Build, "Test", tint = NeonCyan, modifier = Modifier.size(18.dp)) }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit", tint = NeonPurple, modifier = Modifier.size(18.dp)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = NeonPink, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.Edit, null, tint = NeonPurple, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun ProfileEditor(
    vm: AppViewModel,
    profile: ConnectionProfile,
    onSubmit: (ConnectionProfile, String?) -> Unit,
    onClose: () -> Unit,
    testResult: com.jnetai.assistant.ai.ConnectionTestResult?,
    onTest: () -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var type by remember { mutableStateOf(profile.providerType) }
    var endpoint by remember { mutableStateOf(profile.endpoint) }
    var port by remember { mutableStateOf(if (profile.port > 0) profile.port.toString() else "") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(profile.model) }
    var systemPrompt by remember { mutableStateOf(profile.systemPrompt) }
    var streaming by remember { mutableStateOf(profile.streaming) }
    var tls by remember { mutableStateOf(profile.tlsEnabled) }
    var maxTokens by remember { mutableStateOf(profile.maxTokens.toString()) }
    var temperature by remember { mutableStateOf(profile.temperature.toString()) }
    var dailyLimit by remember { mutableStateOf(if (profile.dailyTokenLimit > 0) profile.dailyTokenLimit.toString() else "") }
    var monthlyLimit by remember { mutableStateOf(if (profile.monthlyTokenLimit > 0) profile.monthlyTokenLimit.toString() else "") }

    var showKey by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }

    // Pre-fill example endpoint + model when the provider type is selected.
    // New profiles get the full example; existing profiles keep typed values
    // and only get blanks filled in. Everything remains fully editable.
    val isNew = profile.id == 0L
    LaunchedEffect(type) {
        val d = ProfileDefaults.defaultsFor(type)
        if (isNew) {
            endpoint = d.endpoint
            model = d.model
        } else {
            if (endpoint.isBlank()) endpoint = d.endpoint
            if (model.isBlank()) model = d.model
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (profile.id == 0L) "New Profile" else "Edit Profile", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Close", Modifier.clickable { onClose() }, color = NeonCyan, fontSize = 14.sp)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.surfaceVariant))
        Spacer(Modifier.height(12.dp))

        EditorField("Profile name", name, onChange = { name = it })
        Spacer(Modifier.height(8.dp))

        // Provider type selector
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProviderType.values().forEach { t ->
                val active = t == type
                Text(
                    t.display,
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) NeonPurple.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { type = t }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    color = if (active) NeonPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        EditorField("Endpoint (e.g. https://host or http://192.168.1.50)", endpoint, onChange = { endpoint = it })
        Spacer(Modifier.height(8.dp))
        EditorField("Port (optional, e.g. 11434)", port, numeric = true, onChange = { port = it })
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = tls, onCheckedChange = { tls = it })
            Text("Use HTTPS/TLS", fontSize = 13.sp)
            Spacer(Modifier.width(12.dp))
            Checkbox(checked = streaming, onCheckedChange = { streaming = it })
            Text("Streaming", fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            EditorFieldWeighted("API key (stored encrypted)", if (showKey) apiKey else "•••••••••••••••••", { if (!showKey) showKey = true else apiKey = it }, Modifier.weight(1f))
            Spacer(Modifier.width(4.dp))
            Text(if (showKey) "Hide" else "Show", Modifier.clickable { showKey = !showKey }, color = NeonCyan, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        EditorField("Model ID (e.g. deepseek-v4-flash, llama3.1)", model, onChange = { model = it })
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fetch models", Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(NeonCyan.copy(alpha = 0.2f))
                .clickable {
                    val p = profile.copy(name = name, providerType = type, endpoint = endpoint, port = port.toIntOrNull() ?: 0)
                    availableModels = emptyList()
                    vm.listModelsForProfile(p) { list -> availableModels = list }
                }
                .padding(horizontal = 10.dp, vertical = 6.dp), color = NeonCyan, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            availableModels.take(3).forEach { m ->
                Text(m, Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { model = m }
                    .padding(horizontal = 6.dp, vertical = 4.dp), fontSize = 10.sp, color = NeonCyan)
            }
        }
        Spacer(Modifier.height(8.dp))

        EditorField("System prompt (optional)", systemPrompt, multiLine = true, onChange = { systemPrompt = it })
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorFieldWeighted("Max tokens", maxTokens, { maxTokens = it }, Modifier.weight(1f), numeric = true)
            EditorFieldWeighted("Temperature", temperature, { temperature = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorFieldWeighted("Daily token limit (0=off)", dailyLimit, { dailyLimit = it }, Modifier.weight(1f), numeric = true)
            EditorFieldWeighted("Monthly token limit (0=off)", monthlyLimit, { monthlyLimit = it }, Modifier.weight(1f), numeric = true)
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Test Connection",
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NeonCyan.copy(alpha = 0.2f))
                    .clickable { onTest() }
                    .padding(vertical = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                "Save",
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NeonPurple)
                    .clickable {
                        val p = profile.copy(
                            name = name, providerType = type, endpoint = endpoint,
                            port = port.toIntOrNull() ?: 0, model = model,
                            systemPrompt = systemPrompt, streaming = streaming, tlsEnabled = tls,
                            maxTokens = maxTokens.toIntOrNull() ?: 2048,
                            temperature = temperature.toDoubleOrNull() ?: 0.7,
                            dailyTokenLimit = dailyLimit.toLongOrNull() ?: 0,
                            monthlyTokenLimit = monthlyLimit.toLongOrNull() ?: 0
                        )
                        onSubmit(p, apiKey.ifBlank { null })
                    }
                    .padding(vertical = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
        }

        testResult?.let { r ->
            Spacer(Modifier.height(8.dp))
            StatusBanner(
                if (r.ok) "✓ Connected · auth OK · ${r.latencyMs} ms" else "✕ ${r.message}",
                if (r.ok) Tone.SUCCESS else Tone.ERROR
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EditorField(label: String, value: String, onChange: (String) -> Unit, multiLine: Boolean = false, numeric: Boolean = false) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextField(
            value = value,
            onValueChange = { if (numeric && it.isNotEmpty() && it.toDoubleOrNull() == null && it != "-") {} else onChange(it) },
            singleLine = !multiLine,
            maxLines = if (multiLine) 3 else 1,
            shape = RoundedCornerShape(8.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
    }
}

@Composable
private fun EditorFieldWeighted(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, numeric: Boolean = false) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextField(
            value = value,
            onValueChange = { if (!numeric || it.isEmpty() || it.toDoubleOrNull() != null) onChange(it) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
    }
}