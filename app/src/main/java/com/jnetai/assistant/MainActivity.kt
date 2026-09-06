package com.jnetai.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.jnetai.assistant.data.security.BackupManager
import com.jnetai.assistant.ui.components.Tone
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
import com.jnetai.assistant.util.Err

class MainActivity : FragmentActivity() {

    private lateinit var graph: com.jnetai.assistant.data.AppGraph

    /**
     * Fallback VM access for onActivityResult (the document picker is launched
     * with a fixed request code, bypassing the ActivityResultRegistry whose
     * dynamic request-code counter overflowed the 16-bit limit — see E0505 in
     * the error log). Set from the Compose UI whenever the ViewModel is alive.
     */
    var pendingVm: AppViewModel? = null

    /** Collection the picked documents should be indexed into (0 = automatic). */
    var pendingUploadTarget: Long = 0L

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

    /** Resolves the live ViewModel, creating one via the factory if needed. */
    private fun vm(): AppViewModel =
        pendingVm ?: ViewModelProvider(this, AppViewModelFactory(graph))[AppViewModel::class.java]
            .also { pendingVm = it }

    /**
     * All file-picker style launches in this class use the classic
     * startActivityForResult path with FIXED 16-bit request codes instead of
     * the Compose ActivityResultRegistry. The registry assigns dynamically
     * counted codes that grow past the 16-bit limit over an app's lifetime
     * ("Can only use lower 16 bits for requestCode" → E0505), which made the
     * document picker silently refuse to open. Fixed low codes can never
     * overflow. Every launch is guarded so a failure is logged and surfaced
     * as a friendly status instead of ever closing the app.
     */
    private fun launchIntent(intent: Intent, requestCode: Int, what: String) {
        try {
            Err.i("Opening $what (requestCode=$requestCode)")
            startActivityForResult(intent, requestCode)
        } catch (t: Throwable) {
            Err.e(Err.DOC_PICKER_ERROR, "$what failed to open", t)
            pendingVm?.setStatus("Could not open $what — see Error logs", Tone.ERROR)
        }
    }

    /** System document picker (any document, multiple selection). */
    fun openDocsPicker() {
        launchIntent(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, PICK_MIME_TYPES)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            },
            REQ_PICK_DOCS,
            "document picker"
        )
    }

    /** Local model file picker. */
    fun openModelPicker() {
        launchIntent(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/gguf", "*/*"))
            },
            REQ_PICK_MODEL,
            "model picker"
        )
    }

    /** Create-document target for the encrypted backup export. */
    fun exportBackupFile() {
        launchIntent(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, "JNet-AI-Assistant-backup.enc")
            },
            REQ_EXPORT_BACKUP,
            "backup export"
        )
    }

    /** Open-document picker for restoring an encrypted backup. */
    fun importBackupFile() {
        launchIntent(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "*/*"))
            },
            REQ_IMPORT_BACKUP,
            "backup import"
        )
    }

    /** Create-document target for exporting conversation history. */
    fun exportHistoryFile() {
        launchIntent(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "JNet-AI-History.txt")
            },
            REQ_EXPORT_HISTORY,
            "history export"
        )
    }

    private fun resultUri(resultCode: Int, data: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) data?.data else null

    /**
     * Handles every fixed-code result and imports/logs whatever came back.
     * Never throws — failures are logged (E0505) and surfaced as statuses.
     */
    @Deprecated("Legacy result path kept for the fixed-code file pickers")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQ_PICK_DOCS -> {
                val target = pendingUploadTarget
                pendingUploadTarget = 0L
                if (resultCode != Activity.RESULT_OK || data == null) {
                    Err.i("Document picker returned without a selection (resultCode=$resultCode)")
                    return
                }
                val uris = mutableListOf<Uri>()
                data.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) {
                        clip.getItemAt(i).uri?.let { uris.add(it) }
                    }
                }
                data.data?.let { uris.add(it) }
                if (uris.isEmpty()) {
                    Err.w("Document picker returned OK but contained no URIs")
                    vm().setStatus("No documents were selected", Tone.INFO)
                    return
                }
                uris.forEach { uri ->
                    // Persist read access so documents stay re-indexable later.
                    runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        .onFailure { t -> Err.e(Err.DOC_PICKER_ERROR, "takePersistableUriPermission failed for $uri", t) }
                    if (target != 0L) vm().importDocumentToCollection(uri, target) else vm().importDocument(uri)
                }
            }
            REQ_PICK_MODEL -> {
                val uri = resultUri(resultCode, data) ?: return
                val name = uri.lastPathSegment ?: "model"
                val size = runCatching {
                    contentResolver.openInputStream(uri)?.use { stream -> stream.available().toLong() } ?: 0L
                }.getOrDefault(0L)
                vm().importLocalModel(name, uri.toString(), size)
            }
            REQ_EXPORT_BACKUP -> resultUri(resultCode, data)?.let { vm().exportBackup(it) }
            REQ_IMPORT_BACKUP -> resultUri(resultCode, data)?.let { vm().importBackup(it) }
            REQ_EXPORT_HISTORY -> resultUri(resultCode, data)?.let { vm().exportHistoryToUri(it) }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    companion object {
        /**
         * Fixed 16-bit request codes — well below the 0xFFFF limit, so these
         * launches can never throw "Can only use lower 16 bits for requestCode".
         */
        private const val REQ_PICK_DOCS = 7001
        private const val REQ_PICK_MODEL = 7002
        private const val REQ_EXPORT_BACKUP = 7003
        private const val REQ_IMPORT_BACKUP = 7004
        private const val REQ_EXPORT_HISTORY = 7005
    }
}

@Composable
private fun AppRoot(
    graph: com.jnetai.assistant.data.AppGraph,
    activity: MainActivity
) {
    val vm: AppViewModel = viewModel(factory = AppViewModelFactory(graph))
    val needsOnboarding by vm.needsOnboarding.collectAsState()
    val appLocked by vm.appLocked.collectAsState()

    var currentDest by remember { mutableStateOf(Dest.Chat.route) }

    // Keep the Activity's fallback VM reference in sync with the live one so
    // onActivityResult (fixed-code file pickers) can always reach the VM.
    SideEffect { activity.pendingVm = vm }

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
                        onPickAttachments = { activity.openDocsPicker() }
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
                        onExportHistory = { activity.exportHistoryFile() }
                    )
                }
                composable(Dest.Documents.route) {
                    DocumentsScreen(
                        vm = vm,
                        onUpload = { cid -> activity.pendingUploadTarget = cid; activity.openDocsPicker() },
                        onFastPick = { activity.pendingUploadTarget = 0L; activity.openDocsPicker() }
                    )
                }
                composable(Dest.Models.route) { ModelsScreen(vm, onPickModel = { activity.openModelPicker() }) }
                composable(Dest.Agents.route) { AgentsScreen(vm) }
                composable(Dest.Voice.route) { VoiceScreen(vm) }
                composable(Dest.Activity.route) { ActivityScreen(vm) }
                composable(Dest.Settings.route) {
                    SettingsScreen(
                        vm,
                        onSecuritySettings = { nav.navigate("security") },
                        onAbout = { nav.navigate("about") },
                        onExport = { activity.exportBackupFile() },
                        onImport = { activity.importBackupFile() },
                        onVoiceSettings = { nav.navigate("voice_settings") },
                        onErrorLogs = { nav.navigate("diagnostics") },
                        onOpenHistory = { nav.navigate("history") },
                        onExportHistory = { activity.exportHistoryFile() }
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
    "text/markdown", "application/json", "text/csv", "text/html", "text/xml",
    "audio/*", "application/octet-stream", "application/zip", "application/gzip",
    "video/*", "image/*"
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