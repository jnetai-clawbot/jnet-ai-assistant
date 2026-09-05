package com.jnetai.assistant.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnetai.assistant.data.security.AppLockManager
import com.jnetai.assistant.ui.components.GlowCard
import com.jnetai.assistant.ui.components.SectionHeader
import com.jnetai.assistant.ui.components.StatusBanner
import com.jnetai.assistant.ui.screens.AppViewModel
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple

@Composable
fun SecurityScreen(vm: AppViewModel, onBack: () -> Unit, onDiagnostics: () -> Unit = {}) {
    val lock = vm.lock
    val status by vm.statusMessage.collectAsState()
    val busy by vm.unlockBusy.collectAsState()

    var enabled by remember { mutableStateOf(lock.isEnabled) }
    var defaultPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf("") }
    var biometric by remember { mutableStateOf(lock.biometricEnabled) }
    var timeout by remember { mutableStateOf((lock.autoLockTimeoutMs / 60000).toString()) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("← back", Modifier.clickable { onBack() }, color = NeonCyan, fontSize = 14.sp)
            Text("Security", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
        }
        if (status.isNotEmpty()) StatusBanner(status, com.jnetai.assistant.ui.components.Tone.INFO, Modifier.fillMaxWidth().padding(top = 6.dp))
        Spacer(Modifier.height(8.dp))

        // ---- Secure mode toggle + PIN enable/change form ----
        GlowCard(Modifier.fillMaxWidth(), glow = NeonPurple) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Secure mode (app lock)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (enabled) "ON — app requires your PIN" else "OFF (default) — app opens without a PIN",
                        fontSize = 12.sp, color = NeonCyan
                    )
                }
                Switch(checked = enabled, onCheckedChange = { v ->
                    enabled = v
                    formError = ""
                    if (!v) {
                        vm.disablePinSecurity { enabled = false }
                    }
                })
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Use the default PIN ${AppLockManager.DEFAULT_PIN} to authorise, then choose a personal PIN. " +
                    "Only a salted PBKDF2 hash of the PIN is stored — never the PIN itself.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            Txt("1. Default PIN (to authorise)", defaultPin, password = true, onChange = { defaultPin = it; formError = "" })
            Txt("2. New personal PIN", newPin, password = true, onChange = { newPin = it; formError = "" })
            Txt("3. Confirm new PIN", confirm, password = true, onChange = { confirm = it; formError = "" })
            if (formError.isNotEmpty()) {
                Text(formError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (busy) "Working…" else if (enabled) "Change PIN" else "Enable Secure mode",
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonCyan.copy(alpha = 0.25f))
                    .clickable(enabled = !busy) {
                        vm.enablePinSecurity(defaultPin, newPin, confirm) { err ->
                            if (err == null) {
                                enabled = true
                                formError = ""
                                defaultPin = ""; newPin = ""; confirm = ""
                            } else {
                                formError = err
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Lock screen always accepts the default PIN as a recovery path.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(10.dp))
        GlowCard(Modifier.fillMaxWidth(), glow = NeonCyan) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Allow biometric unlock", modifier = Modifier.weight(1f), fontSize = 14.sp)
                Switch(checked = biometric, onCheckedChange = { v ->
                    biometric = v
                    lock.biometricEnabled = v
                })
            }
            Text(
                if (lock.canUseBiometric()) "Biometric (fingerprint/face) is available on this device."
                else "Biometric not available or not enrolled on this device.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Txt("Auto-lock after (minutes, 0 = only on app close)", timeout) {
                timeout = it
                lock.autoLockTimeoutMs = (it.toLongOrNull() ?: 0L) * 60_000L
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("Data protection")
        GlowCard(Modifier.fillMaxWidth(), glow = NeonPurple) {
            Text("Encryption at rest", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "API keys and connection secrets are always encrypted with an AES-256 key held in the Android Keystore (never exported). " +
                    "Chat history, documents, RAG index and settings live in the app's private sandbox; backups are encrypted too. " +
                    "Android sandbox storage alone is not claimed to be equivalent to full-disk encryption.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("Diagnostics")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onDiagnostics)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Diagnostics & crash log", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NeonCyan)
                Text(
                    "View error codes, copy the log to clipboard, or share it — helps diagnose crashes/hangs.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("Open →", fontSize = 13.sp, color = NeonCyan)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Txt(label: String, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Text
            ),
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