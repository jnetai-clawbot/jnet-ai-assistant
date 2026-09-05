package com.jnetai.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnetai.assistant.data.security.AppLockManager
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple
import com.jnetai.assistant.ui.theme.NeonPink
import com.jnetai.assistant.util.Err

/**
 * Full-screen gate shown before the app opens when Protect App is enabled.
 * PIN fields are always masked as ••••••. If unlocked with the default/reset
 * PIN (12345678) the user is forced to set a personal PIN before continuing.
 */
@Composable
fun AppLockScreen(vm: AppViewModel) {
    var phase by remember { mutableStateOf(if (vm.lock.mustChangePin()) "change" else "enter") }
    var pin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val busy by vm.unlockBusy.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        Err.i("Lock screen shown (phase=$phase, defaultPinInUse=${vm.lock.defaultPinInUse()})")
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("J~Net AI Assistant", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
        Spacer(Modifier.height(12.dp))

        if (phase == "change") {
            Text(
                "Set a new personal PIN to continue",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it; error = "" },
                label = { Text("New PIN") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.width(240.dp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { confirmPin = it; error = "" },
                label = { Text("Confirm PIN") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.width(240.dp)
            )
            if (error.isNotEmpty()) {
                Text(error, color = NeonPink, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (busy) "Saving…" else "Save",
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(NeonPurple)
                    .clickable(enabled = !busy) {
                        try {
                            when {
                                newPin.length < 4 -> error = "PIN must be at least 4 characters"
                                newPin != confirmPin -> error = "PINs do not match"
                                else -> vm.changePin(newPin) { ok ->
                                    if (!ok) error = "Could not save PIN — please try again"
                                }
                            }
                        } catch (t: Throwable) {
                            com.jnetai.assistant.util.Err.e(Err.LOCK_PIN_ERROR, "Save PIN UI failed", t)
                            error = "Something went wrong saving the PIN — try again"
                        }
                    }
                    .padding(horizontal = 60.dp, vertical = 12.dp),
                color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
            return@Column
        }

        Text(
            if (vm.lock.defaultPinInUse())
                "Using the default PIN: ${AppLockManager.DEFAULT_PIN} — you will be asked to change it."
            else
                "Enter your PIN to continue",
            fontSize = 14.sp,
            color = if (vm.lock.defaultPinInUse()) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it; error = "" },
            label = { Text("Enter PIN") },
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.width(240.dp)
        )
        if (error.isNotEmpty()) {
            Text(error, color = NeonPink, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (busy) "Unlocking…" else "Unlock",
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(NeonPurple)
                .clickable(enabled = !busy) {
                    try {
                        vm.unlockWithPin(pin) { decision ->
                            if (!decision.ok) {
                                error = "Incorrect PIN"
                            } else if (decision.mustChangePin) {
                                phase = "change"
                                pin = ""
                            }
                        }
                    } catch (t: Throwable) {
                        com.jnetai.assistant.util.Err.e(Err.LOCK_PIN_ERROR, "Unlock UI failed", t)
                        error = "Could not check PIN — please try again"
                    }
                }
                .padding(horizontal = 60.dp, vertical = 12.dp),
            color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Forgot your PIN? Use the default PIN ${AppLockManager.DEFAULT_PIN} — " +
                "you'll be asked to set a new personal PIN afterwards.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(280.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Reset app protection (type default PIN first, then tap here)",
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (pin == AppLockManager.DEFAULT_PIN) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = !busy && pin == AppLockManager.DEFAULT_PIN) {
                    if (vm.resetLockUsingDefaultPin()) {
                        vm.markUnlocked()  // unlocks and turns Secure mode off
                    } else {
                        error = "Reset failed — try again"
                    }
                }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            color = NeonPurple, fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Copy diagnostic log (error codes to share)",
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    val clip = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val log = com.jnetai.assistant.util.Err.readLog()
                    clip.setPrimaryClip(android.content.ClipData.newPlainText("JNetAI-diagnostics", log.ifBlank { "(no diagnostics yet)" }))
                    error = "Diagnostics copied to clipboard"
                }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            color = NeonCyan, fontSize = 12.sp
        )
        if (!busy && vm.lock.canUseBiometric()) {
            Spacer(Modifier.height(20.dp))
            Text(
                "Unlock with biometric",
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        val activity = context as? androidx.fragment.app.FragmentActivity
                        if (activity != null) {
                            vm.lock.promptBiometric(activity) { ok ->
                                if (ok) vm.markUnlocked()
                                else error = "Biometric cancelled"
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = NeonCyan, fontSize = 13.sp
            )
        }
    }
}