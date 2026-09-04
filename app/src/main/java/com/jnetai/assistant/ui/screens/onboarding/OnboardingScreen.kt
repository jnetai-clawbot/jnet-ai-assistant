package com.jnetai.assistant.ui.screens.onboarding

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnetai.assistant.ui.components.GlowCard
import com.jnetai.assistant.ui.screens.AppViewModel
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple
import com.jnetai.assistant.ui.theme.NeonPink

private const val STEPS = 4

/**
 * Minimal first-launch wizard:
 * 1) Welcome  2) Security choice  3) AI connection  4) Complete.
 * Cloud AI is never forced — the user may choose "Use local AI" and skip.
 */
@Composable
fun OnboardingScreen(vm: AppViewModel, onFinished: () -> Unit) {
    var step by remember { mutableStateOf(1) }
    var protect by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var profileName by remember { mutableStateOf("OpenCode") }
    var providerType by remember { mutableStateOf(com.jnetai.assistant.data.model.ProviderType.OPENCODE) }
    var endpoint by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var useLocal by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("J~Net AI Assistant", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
        Text("Step $step of $STEPS", fontSize = 12.sp, color = NeonPurple)
        Spacer(Modifier.height(20.dp))

        when (step) {
            1 -> {
                GlowCard(Modifier.fillMaxWidth(), glow = NeonPurple) {
                    Text("Welcome", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "A private AI workstation for Android: chat with cloud AI, local models, your own documents (RAG), voice assistant and authorised automation. Everything is configurable.",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            2 -> {
                GlowCard(Modifier.fillMaxWidth(), glow = NeonPurple) {
                    Text("Security choice", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Protect the app with a PIN", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        androidx.compose.material3.Switch(checked = protect, onCheckedChange = { protect = it })
                    }
                    if (protect) {
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it },
                            label = { Text("Choose a PIN (4+ digits)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        "API keys are encrypted with Android Keystore regardless of this choice.",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            3 -> {
                GlowCard(Modifier.fillMaxWidth(), glow = NeonCyan) {
                    Text("AI connection", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Use local AI only (skip cloud)", modifier = Modifier.weight(1f), fontSize = 13.sp)
                        androidx.compose.material3.Switch(checked = useLocal, onCheckedChange = { useLocal = it })
                    }
                    if (!useLocal) {
                        OutlinedTextField(value = profileName, onValueChange = { profileName = it }, label = { Text("Profile name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("Endpoint (e.g. https://api.provider.com/v1)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API key (optional, stored encrypted)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model ID") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        Text(
                            "You can change everything later in Settings → Connections.",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        Column(Modifier.padding(top = 8.dp)) {
                            providerOptions().forEach { t ->
                                Text(
                                    t.display,
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (t == com.jnetai.assistant.data.model.ProviderType.LOCAL) NeonPurple.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { providerType = t }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    color = if (t == com.jnetai.assistant.data.model.ProviderType.LOCAL) NeonPurple else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            4 -> {
                GlowCard(Modifier.fillMaxWidth(), glow = NeonCyan) {
                    Text("Complete", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (useLocal) "You chose local AI. Import a mobile model from the Models tab when ready."
                        else "Your profile '$profileName' will be created. Head to Chat to start.",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 1) {
                Text(
                    "Back",
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { step-- }
                        .padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp
                )
            }
            Text(
                if (step == STEPS) "Finish" else "Next",
                Modifier
                    .weight(if (step > 1) 2f else 1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NeonCyan)
                    .clickable {
                        if (step == STEPS) {
                            if (protect && pin.length >= 4) { vm.lock.setPin(pin); vm.lock.isEnabled = true }
                            if (!useLocal && endpoint.isNotBlank() && model.isNotBlank()) {
                                vm.saveProfile(
                                    com.jnetai.assistant.data.model.ConnectionProfile(
                                        name = profileName, providerType = providerType, endpoint = endpoint, model = model
                                    ),
                                    apiKey.ifBlank { null }
                                )
                            } else if (useLocal) {
                                vm.saveProfile(
                                    com.jnetai.assistant.data.model.ConnectionProfile(
                                        name = "Local Device", providerType = com.jnetai.assistant.data.model.ProviderType.LOCAL, model = ""
                                    ),
                                    null
                                )
                            }
                            vm.completeOnboarding()
                            vm.markUnlocked()
                            onFinished()
                        } else step++
                    }
                    .padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = androidx.compose.ui.graphics.Color(0xFF00363F), fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(30.dp))
    }
}

private fun providerOptions(): List<com.jnetai.assistant.data.model.ProviderType> =
    listOf(com.jnetai.assistant.data.model.ProviderType.LOCAL, com.jnetai.assistant.data.model.ProviderType.OLLAMA, com.jnetai.assistant.data.model.ProviderType.OPENCODE)