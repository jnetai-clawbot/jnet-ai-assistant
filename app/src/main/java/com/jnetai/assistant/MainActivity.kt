package com.jnetai.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.jnetai.assistant.data.security.BackupManager
import com.jnetai.assistant.ui.navigation.Dest
import com.jnetai.assistant.ui.navigation.allDestinations
import com.jnetai.assistant.ui.screens.AppLockScreen
import com.jnetai.assistant.ui.screens.AppViewModel
import com.jnetai.assistant.ui.screens.AppViewModelFactory
import com.jnetai.assistant.ui.screens.about.AboutScreen
import com.jnetai.assistant.ui.screens.activity.ActivityScreen
import com.jnetai.assistant.ui.screens.agents.AgentsScreen
import com.jnetai.assistant.ui.screens.chat.ChatScreen
import com.jnetai.assistant.ui.screens.docs.DocumentsScreen
import com.jnetai.assistant.ui.screens.history.HistoryScreen
import com.jnetai.assistant.ui.screens.models.ModelsScreen
import com.jnetai.assistant.ui.screens.onboarding.OnboardingScreen
import com.jnetai.assistant.ui.screens.settings.SecurityScreen
import com.jnetai.assistant.ui.screens.settings.SettingsScreen
import com.jnetai.assistant.ui.screens.settings.VoiceSettingsScreen
import com.jnetai.assistant.ui.screens.voice.VoiceScreen
import com.jnetai.assistant.ui.theme.JNetAssistantTheme
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple
import com.jnetai.assistant.ui.theme.surfaceDarkElevated

class MainActivity : FragmentActivity() {

    private lateinit var graph: com.jnetai.assistant.data.AppGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        graph = (application as JNetAssistantApp).graph
        setContent {
            JNetAssistantTheme(darkTheme = true) {
                AppRoot(graph, this)
            }
        }

        // request microphone permission proactively (needed for voice features)
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        }
    }
}

@Composable
private fun AppRoot(
    graph: com.jnetai.assistant.data.AppGraph,
    activity: FragmentActivity
) {
    val vm: AppViewModel = viewModel(factory = AppViewModelFactory(graph))
    val context = LocalContext.current
    val needsOnboarding by vm.needsOnboarding.collectAsState()
    val appLocked by vm.appLocked.collectAsState()

    var currentDest by remember { mutableStateOf(Dest.Chat.route) }

    val pickDocs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            vm.importDocument(uri)
        }
    }
    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val name = it.lastPathSegment ?: "model"
            val size = context.contentResolver.openInputStream(it)?.use { stream -> stream.available().toLong() } ?: 0
            vm.importLocalModel(name, it.toString(), size)
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { u ->
            vm.exportBackup(u)
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            vm.importBackup(u)
        }
    }
    val exportHistory = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { u ->
            vm.exportHistoryToUri(u)
        }
    }

    if (needsOnboarding) {
        OnboardingScreen(vm = vm, onFinished = {})
        return
    }
    if (appLocked) {
        AppLockScreen(vm)
        return
    }

    val nav = rememberNavController()
    Scaffold(
        bottomBar = { BottomBar(nav, currentDest, { currentDest = it }) }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            NavHost(nav, startDestination = Dest.Chat.route) {
                composable(Dest.Chat.route) {
                    ChatScreen(
                        vm,
                        onViewConversations = { nav.navigate("history") },
                        onPickAttachments = { pickDocs.launch(PICK_MIME_TYPES) }
                    )
                }
                composable("history") {
                    HistoryScreen(
                        vm,
                        onBack = { nav.popBackStack() },
                        onOpenConversation = { id ->
                            vm.openConversation(id)
                            nav.navigate(Dest.Chat.route) {
                                popUpTo(Dest.Chat.route) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onExportHistory = { exportHistory.launch("JNet-AI-History.txt") }
                    )
                }
                composable(Dest.Documents.route) {
                    DocumentsScreen(vm, onPickFiles = { pickDocs.launch(PICK_MIME_TYPES) }, onPickFolder = {})
                }
                composable(Dest.Models.route) { ModelsScreen(vm, onPickModel = { pickModel.launch(arrayOf("application/octet-stream", "application/gguf", "*/*")) }) }
                composable(Dest.Agents.route) { AgentsScreen(vm) }
                composable(Dest.Voice.route) { VoiceScreen(vm) }
                composable(Dest.Activity.route) { ActivityScreen(vm) }
                composable(Dest.Settings.route) {
                    SettingsScreen(
                        vm,
                        onSecuritySettings = { nav.navigate("security") },
                        onAbout = { nav.navigate("about") },
                        onExport = { export.launch("JNet-AI-Assistant-backup.enc") },
                        onImport = { import.launch(arrayOf("application/octet-stream", "*/*")) },
                        onVoiceSettings = { nav.navigate("voice_settings") },
                        onErrorLogs = { nav.navigate("diagnostics") },
                        onOpenHistory = { nav.navigate("history") },
                        onExportHistory = { exportHistory.launch("JNet-AI-History.txt") }
                    )
                }
                composable("security") { SecurityScreen(vm, onBack = { nav.popBackStack() }, onDiagnostics = { nav.navigate("diagnostics") }) }
                composable("voice_settings") { VoiceSettingsScreen(vm, onBack = { nav.popBackStack() }) }
                composable("about") { AboutScreen(vm, onBack = { nav.popBackStack() }) }
                composable("diagnostics") { com.jnetai.assistant.ui.screens.settings.DiagnosticsScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
}

private val PICK_MIME_TYPES = arrayOf(
    "application/pdf", "text/*", "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "text/markdown", "application/json", "text/csv", "text/html", "text/xml", "audio/*"
)

@Composable
private fun BottomBar(nav: NavHostController, currentDest: String, onSelect: (String) -> Unit) {
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: currentDest
    if (current in listOf("security", "voice_settings", "about", "diagnostics", "history")) return
    NavigationBar(
        containerColor = surfaceDarkElevated,
        modifier = Modifier.navigationBarsPadding()
    ) {
        allDestinations.forEach { d ->
            NavigationBarItem(
                selected = current == d.route,
                onClick = {
                    onSelect(d.route)
                    nav.navigate(d.route) {
                        popUpTo(Dest.Chat.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(iconFor(d), contentDescription = d.label, tint = if (current == d.route) NeonCyan else androidx.compose.ui.graphics.Color.Gray) },
                label = {
                    Text(
                        d.label,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

private fun iconFor(d: Dest): ImageVector = when (d) {
    Dest.Chat -> Icons.Default.Chat
    Dest.Documents -> Icons.Default.Description
    Dest.Models -> Icons.Default.Folder
    Dest.Agents -> Icons.Default.SmartToy
    Dest.Voice -> Icons.Default.Mic
    Dest.Activity -> Icons.Default.Scale
    Dest.Settings -> Icons.Default.Settings
    else -> Icons.Default.Settings
}