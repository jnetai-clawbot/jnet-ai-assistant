package com.jnetai.assistant.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnetai.assistant.BuildConfig
import com.jnetai.assistant.ui.components.GlowCard
import com.jnetai.assistant.ui.components.StatusBanner
import com.jnetai.assistant.ui.components.Tone
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple
import com.jnetai.assistant.util.Err

/**
 * Diagnostics screen: shows the persistent log (with J~Net error codes),
 * one-tap Copy to clipboard and Share, so any crash/hang can be diagnosed.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var logText by remember { mutableStateOf(Err.readLog()) }
    var copied by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("← back", Modifier.clickable { onBack() }, color = NeonCyan, fontSize = 14.sp)
            Text("Diagnostics & crash log", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
        }
        Text(
            "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action("Copy log", modifier = Modifier.weight(1f)) {
                copyToClipboard(context, logText)
                copied = true
            }
            Action("Refresh", modifier = Modifier.weight(1f)) {
                logText = Err.readLog()
                copied = false
            }
            Action("Share", modifier = Modifier.weight(1f)) {
                shareLog(context, logText)
            }
        }
        if (copied) {
            Spacer(Modifier.height(8.dp))
            StatusBanner("Log copied to clipboard — paste it to jnetai.com support", Tone.SUCCESS, Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(10.dp))
        GlowCard(Modifier.fillMaxWidth(), glow = NeonPurple) {
            Text("What it contains", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeonCyan)
            Text(
                "Stable error codes (E0001–E1001, FATAL), timestamps, app lifecycle events and full stack traces " +
                    "of any crash. No API keys, secrets or PINs are ever written to this log.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(10.dp)
        ) {
            Text(
                logText.ifBlank { "(no diagnostics recorded yet)" },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Clear log",
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                .clickable { Err.clearLog(); logText = "" }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.error, fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun Action(label: String, modifier: Modifier = Modifier, onclick: () -> Unit) {
    Text(
        label,
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(NeonCyan.copy(alpha = 0.2f))
            .clickable(onClick = onclick)
            .padding(vertical = 10.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("JNetAI-diagnostics", text.ifBlank { "(no diagnostics yet)" }))
}

private fun shareLog(context: Context, text: String) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "J~Net AI Assistant diagnostics (${BuildConfig.VERSION_NAME})")
        putExtra(Intent.EXTRA_TEXT, text.ifBlank { "(no diagnostics yet — version ${BuildConfig.VERSION_NAME})" })
    }
    context.startActivity(Intent.createChooser(share, "Share diagnostics"))
}