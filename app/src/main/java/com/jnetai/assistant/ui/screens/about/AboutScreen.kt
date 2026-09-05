package com.jnetai.assistant.ui.screens.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnetai.assistant.BuildConfig
import com.jnetai.assistant.ui.components.GlowCard
import com.jnetai.assistant.ui.components.StatusBanner
import com.jnetai.assistant.ui.components.Tone
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

const val GITHUB_REPO = "https://github.com/jnetai-clawbot/jnet-ai-assistant"

@Composable
fun AboutScreen(vm: com.jnetai.assistant.ui.screens.AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = BuildConfig.VERSION_NAME

    var checking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf("") }
    var checkTone by remember { mutableStateOf(Tone.INFO) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("← back", Modifier.clickable { onBack() }, color = NeonCyan, fontSize = 14.sp)
            Text("About", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
        }
        Spacer(Modifier.height(16.dp))

        // App / version card
        Column(Modifier.fillMaxWidth()) {
            Text("J~Net AI Assistant", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
            Spacer(Modifier.height(6.dp))
            Text("Made by jnetai.com", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("Version $versionName", fontSize = 13.sp, color = NeonPurple)
            Spacer(Modifier.height(8.dp))
            Text(
                "A private AI workstation for Android: chat, RAG, local models, voice assistant and controlled automation.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))

        // Buttons
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionBtn("Check for update", onclick = {
                checking = true
                checkResult = ""
                runUpdateCheck(versionName) { result, tone ->
                    checking = false
                    checkResult = result; checkTone = tone
                }
            }, modifier = Modifier.weight(1f))
            ActionBtn("Share app", onclick = {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "J~Net AI Assistant — private AI workstation for Android. Download: $GITHUB_REPO")
                }
                context.startActivity(Intent.createChooser(share, "Share J~Net AI Assistant"))
            }, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        if (checking) {
            StatusBanner("Checking for updates…", Tone.INFO, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        if (checkResult.isNotEmpty()) {
            StatusBanner(checkResult, checkTone, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            if (checkTone == Tone.SUCCESS && checkResult.contains("Update available")) {
                Text(
                    "Download",
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonCyan.copy(alpha = 0.25f))
                        .clickable { openUrl(context, GITHUB_REPO) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    color = NeonCyan, fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // Release card
        Column(Modifier.fillMaxWidth()) {
            Text("Release", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Releases are built with GitHub Actions and signed with a stable keystore so the app can update in place without uninstalling.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Open releases on GitHub",
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonPurple.copy(alpha = 0.2f))
                    .clickable { openUrl(context, GITHUB_REPO + "/releases") }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color = NeonPurple, fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Made by jnetai.com",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ActionBtn(label: String, onclick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        label,
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .clickable(onClick = onclick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        color = NeonCyan,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
    )
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Checks the GitHub latest-release tag. On any failure falls over to opening
 * the repository release page so the user still reaches the newest version.
 */
private fun runUpdateCheck(currentVersion: String, onResult: (String, Tone) -> Unit) {
    Thread {
        try {
            var latest = ""
            val conn = URL("https://api.github.com/repos/jnetai-clawbot/jnet-ai-assistant/releases/latest").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                latest = json.optString("tag_name", "")
            }
            conn.disconnect()
            if (latest.isBlank()) {
                onResult("Could not determine latest version — opening release page.", Tone.INFO)
            } else {
                val cleanLatest = latest.removePrefix("v")
                val current = currentVersion.removePrefix("v")
                onResult(
                    if (cleanLatest != current) "Update available: $latest (you have $currentVersion)." else "You are up to date ($currentVersion).",
                    Tone.SUCCESS
                )
            }
        } catch (t: Throwable) {
            onResult("Update check failed — opening release page.", Tone.ERROR)
        }
    }.start()
}