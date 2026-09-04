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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple
import com.jnetai.assistant.ui.theme.NeonPink

/** Full-screen gate shown before the app opens when Protect App is enabled. */
@Composable
fun AppLockScreen(vm: AppViewModel) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("J~Net AI Assistant", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
        Spacer(Modifier.height(30.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it; error = "" },
            label = { Text("Enter PIN") },
            singleLine = true,
            modifier = Modifier.width(240.dp)
        )
        if (error.isNotEmpty()) {
            Text(error, color = NeonPink, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Unlock",
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(NeonPurple)
                .clickable {
                    vm.unlockWithPin(pin) { ok ->
                        if (!ok) error = "Incorrect PIN"
                    }
                }
                .padding(horizontal = 60.dp, vertical = 12.dp),
            color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
        )
        if (vm.lock.canUseBiometric()) {
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