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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnetai.assistant.ui.components.GlowCard
import com.jnetai.assistant.ui.components.SectionHeader
import com.jnetai.assistant.ui.components.StatusBanner
import com.jnetai.assistant.ui.screens.AppViewModel
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple
import com.jnetai.assistant.ui.theme.NeonPink

@Composable
fun SecurityScreen(vm: AppViewModel, onBack: () -> Unit, onDiagnostics: () -> Unit = {}) {
    val lock = vm.lock
    val status by vm.statusMessage.collectAsState()

    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(lock.isEnabled) }
    var biometric by remember { mutableStateOf(lock.biometricEnabled) }
    var timeout by remember { mutableStateOf((lock.autoLockTimeoutMs / 60000).toString()) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("← back", Modifier.clickable { onBack() }, color = NeonCyan, fontSize = 14.sp)
            Text("Security", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
        }
        if (status.isNotEmpty()) StatusBanner(status, com.jnetai.assistant.ui.components.Tone.INFO, Modifier.fillMaxWidth().padding(top = 6.dp))
        Spacer(Modifier.height(8.dp))

        GlowCard(Modifier.fillMaxWidth(), glow = NeonPurple) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Protect App", modifier = Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Checkbox(checked = enabled, onCheckedChange = { v ->
                    enabled = v
                    if (v && !lock.hasPin()) {
                        // require PIN setup below before enforcing
                        lock.isEnabled = false
                    } else {
                        lock.isEnabled = v
                    }
                })
            }
            Text(
                "Require authentication before the app opens. PINs are stored only as salted PBKDF2 hashes — never in the clear.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(10.dp))
        GlowCard(Modifier.fillMaxWidth(), glow = NeonCyan) {
            SectionHeader("Application PIN / password")
            Txt("New PIN", pin, password = true, onChange = { pin = it })
            Txt("Confirm PIN", confirm, password = true, onChange = { confirm = it })
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    "Set PIN",
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonCyan.copy(alpha = 0.25f))
                        .clickable(enabled = pin.length >= 4 && pin == confirm) {
                            lock.setPin(pin)
                            lock.isEnabled = enabled
                            lock.markUnlocked()
                            vm.setStatus("PIN set", com.jnetai.assistant.ui.components.Tone.SUCCESS)
                            pin = ""; confirm = ""
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = NeonCyan, fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            if (lock.defaultPinInUse()) {
                Text(
                    "Default PIN in use: ${com.jnetai.assistant.data.security.AppLockManager.DEFAULT_PIN}. " +
                        "Set a new personal PIN above to replace it.",
                    fontSize = 12.sp, color = NeonCyan
                )
            } else {
                Text(
                    "If you forget your PIN you can always reset with the documented default PIN.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        GlowCard(Modifier.fillMaxWidth(), glow = NeonCyan) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Allow biometric unlock", modifier = Modifier.weight(1f), fontSize = 14.sp)
                Checkbox(checked = biometric, onCheckedChange = { v ->
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
            Txt("Auto-lock after (minutes, 0 = only on app close)", timeout) { timeout = it }
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