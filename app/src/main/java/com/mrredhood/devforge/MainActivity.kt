package com.mrredhood.devforge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrredhood.devforge.core.ai.AIChatScreen
import com.mrredhood.devforge.core.ai.AIChatViewModel
import com.mrredhood.devforge.core.build.BuildCenterScreen
import com.mrredhood.devforge.core.build.BuildViewModel
import com.mrredhood.devforge.core.editor.ChainedVisualTransformation
import com.mrredhood.devforge.core.editor.CodeSyntaxVisualTransformation
import com.mrredhood.devforge.core.editor.EditorFolding
import com.mrredhood.devforge.core.editor.EditorLanguage
import com.mrredhood.devforge.core.editor.EditorFoldRange
import com.mrredhood.devforge.core.editor.EditorTab
import com.mrredhood.devforge.core.editor.EditorViewModel
import com.mrredhood.devforge.core.editor.FoldingVisualTransformation
import com.mrredhood.devforge.core.editor.VisibleWhitespaceVisualTransformation
import com.mrredhood.devforge.core.git.GitDashboardScreen
import com.mrredhood.devforge.core.git.GitDiffScreen
import com.mrredhood.devforge.core.git.GitDiffViewModel
import com.mrredhood.devforge.core.git.GitRemoteOverviewScreen
import com.mrredhood.devforge.core.git.GitCommitHistoryScreen
import com.mrredhood.devforge.core.git.GitDiffDocument
import com.mrredhood.devforge.core.git.GitDiffSection
import com.mrredhood.devforge.core.git.GitFileStatus
import com.mrredhood.devforge.core.git.GitViewModel
import com.mrredhood.devforge.core.git.CapabilityAvailability
import com.mrredhood.devforge.core.editor.DiffEngine
import com.mrredhood.devforge.core.editor.DiffKind
import com.mrredhood.devforge.core.github.GitHubPendingChanges
import com.mrredhood.devforge.core.github.GitHubRepositoryCreationScreen
import com.mrredhood.devforge.core.github.LiveActionsScreen
import com.mrredhood.devforge.core.github.GitHubPendingChangeBatch
import com.mrredhood.devforge.core.ide.IdeTool
import com.mrredhood.devforge.core.ide.IdeToolScreen
import com.mrredhood.devforge.core.guide.FeatureGuideScreen
import com.mrredhood.devforge.core.github.GitHubRepositoryViewModel
import com.mrredhood.devforge.core.github.GitHubRepository
import com.mrredhood.devforge.core.model.DevForgeDestination
import com.mrredhood.devforge.core.policy.ApprovalCenterScreen
import com.mrredhood.devforge.core.preview.PreviewMode
import com.mrredhood.devforge.core.preview.PreviewScreen
import com.mrredhood.devforge.core.policy.ApprovalCenterViewModel
import com.mrredhood.devforge.core.storage.ApprovalEntity
import com.mrredhood.devforge.core.picker.PickerBridge
import com.mrredhood.devforge.core.picker.PickerResult
import com.mrredhood.devforge.core.picker.SystemPickerActivity
import com.mrredhood.devforge.core.settings.AiGitHubHubScreen
import com.mrredhood.devforge.core.settings.DevForgeSettingsScreen
import com.mrredhood.devforge.core.settings.DevForgeSettingsViewModel
import com.mrredhood.devforge.core.settings.DensityMode
import com.mrredhood.devforge.core.settings.ThemeMode
import com.mrredhood.devforge.core.terminal.TerminalScreen
import com.mrredhood.devforge.core.security.CredentialSecurityScreen
import com.mrredhood.devforge.core.workspace.WorkspaceEntry
import com.mrredhood.devforge.core.workspace.WorkspaceLanguageIcon
import com.mrredhood.devforge.core.workspace.WorkspaceFolderIcon
import com.mrredhood.devforge.core.workspace.WorkspaceViewModel
import com.mrredhood.devforge.core.workspace.GitHubWorkspaceImportScreen
import com.mrredhood.devforge.core.workspace.BuildWithAiDialog
import com.mrredhood.devforge.core.workspace.LocalProjectPublishDialog
import com.mrredhood.devforge.core.workspace.GitHubWorkspaceUris
import com.mrredhood.devforge.ui.theme.DevForgeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    companion object {
        const val NOTIFICATION_PERMISSION_REQUEST = 4810
        const val EXTRA_OPEN_APPROVAL_ID = "open_approval_id"
    }

    private var pendingOpenApprovalId by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenApprovalId = intent.getStringExtra(EXTRA_OPEN_APPROVAL_ID)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SystemPickerActivity.PICKER_REQUEST_CODE) {
            val kind = data?.getStringExtra(SystemPickerActivity.EXTRA_KIND)
                ?: SystemPickerActivity.KIND_ATTACHMENTS
            val uris = buildList {
                data?.data?.let(::add)
                data?.clipData?.let { clip ->
                    for (index in 0 until clip.itemCount) {
                        clip.getItemAt(index)?.uri?.let(::add)
                    }
                }
            }.distinct()
            PickerBridge.emit(
                PickerResult(
                    kind = kind,
                    uris = uris,
                    cancelled = resultCode != RESULT_OK || uris.isEmpty(),
                ),
            )
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOpenApprovalId = intent.getStringExtra(EXTRA_OPEN_APPROVAL_ID)
        lifecycleScope.launch {
            val database = DevForgeDatabase.get(this@MainActivity)
            val approvals = ApprovalRepository(database.approvalDao())
            approvals.expireDue()
            approvals.recoverStaleExecuting()
            com.mrredhood.devforge.core.storage.DurableStateRepository(database)
                .reconcileWaitingAgentApprovals()
        }
        setContent {
            val settings: DevForgeSettingsViewModel = viewModel()
            DevForgeTheme(
                themeMode = settings.settings.themeMode,
                densityMode = settings.settings.densityMode,
            ) {
                DevForgeApp(
                    settings = settings,
                    openApprovalId = pendingOpenApprovalId,
                    onApprovalOpenConsumed = { pendingOpenApprovalId = null },
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            window.decorView.post {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DevForgeApp(
    settings: DevForgeSettingsViewModel,
    openApprovalId: String?,
    onApprovalOpenConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val expanded = activity?.let {
        calculateWindowSizeClass(it).widthSizeClass != WindowWidthSizeClass.Compact
    } ?: false
    var destinationName by rememberSaveable { mutableStateOf(DevForgeDestination.Build.name) }
    var showEditor by rememberSaveable { mutableStateOf(true) }
    var showEditorMenu by rememberSaveable { mutableStateOf(false) }
    var showChat by rememberSaveable { mutableStateOf(false) }
    var ideTool by rememberSaveable { mutableStateOf<IdeTool?>(null) }
    var showFeatureGuide by rememberSaveable { mutableStateOf(false) }
    var destinationHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    var showGlobalSearch by rememberSaveable { mutableStateOf(false) }
    var globalSearchQuery by rememberSaveable { mutableStateOf("") }
    var unsavedEditorUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showProjectActivity by rememberSaveable { mutableStateOf(false) }
    var commitDialogOpen by rememberSaveable { mutableStateOf(false) }
    var lastPendingSignature by rememberSaveable { mutableStateOf("") }
    var gitCommitHistoryOpen by rememberSaveable { mutableStateOf(false) }
    var selectedGitCommitSha by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsSection by rememberSaveable { mutableStateOf("home") }
    var appSettingsSection by rememberSaveable { mutableStateOf("home") }
    var buildRepositoryPickerOpen by rememberSaveable { mutableStateOf(false) }
    var showBuildWithAi by rememberSaveable { mutableStateOf(false) }
    var buildWithAiBusy by rememberSaveable { mutableStateOf(false) }
    var buildWithAiError by rememberSaveable { mutableStateOf<String?>(null) }
    var showPublishLocalProject by rememberSaveable { mutableStateOf(false) }
    var previewMode by rememberSaveable { mutableStateOf<PreviewMode?>(null) }

    LaunchedEffect(openApprovalId) {
        if (!openApprovalId.isNullOrBlank()) {
            destinationHistory = emptyList()
            destinationName = DevForgeDestination.Approvals.name
            showEditor = false
            showEditorMenu = false
            showChat = false
            ideTool = null
            showFeatureGuide = false
            showProjectActivity = false
            commitDialogOpen = false
            gitCommitHistoryOpen = false
            selectedGitCommitSha = null
            onApprovalOpenConsumed()
        }
    }

    val pendingBatches by GitHubPendingChanges.batches.collectAsState()
    val appScope = rememberCoroutineScope()
    val workspace: WorkspaceViewModel = viewModel()
    val editor: EditorViewModel = viewModel()
    val build: BuildViewModel = viewModel()
    val approvalCenter: ApprovalCenterViewModel = viewModel()
    val githubRepositories: GitHubRepositoryViewModel = viewModel()
    val aiChat: AIChatViewModel = viewModel()
    val activeWorkspaceId = workspace.workspace?.id
    val pendingBatch = activeWorkspaceId?.let { pendingBatches[it] }
    val destination = DevForgeDestination.entries.firstOrNull { it.name == destinationName } ?: DevForgeDestination.Files
    val editing = showEditor && (editor.activeTab != null || editor.isLoading || editor.error != null)
    val screenTitle = when {
        editing -> "Editor"
        gitCommitHistoryOpen -> "Commit history"
        destination == DevForgeDestination.Build && buildRepositoryPickerOpen -> "GitHub repository"
        destination != DevForgeDestination.Settings -> destination.label
        settingsSection == "security" -> "Security"
        settingsSection != "app" -> "Settings"
        appSettingsSection == "routing" -> "AI routing"
        appSettingsSection == "build" -> "Build & automation"
        appSettingsSection == "terminal" -> "Terminal"
        appSettingsSection == "privacy" -> "Privacy & retention"
        appSettingsSection == "appearance" -> "Appearance"
        appSettingsSection == "editor" -> "Editor"
        else -> "App settings"
    }

    fun openEditor() {
        showEditor = true
        showEditorMenu = false
        ideTool = null
        showProjectActivity = false
    }

    fun navigateTo(next: DevForgeDestination) {
        showChat = false
        if (next.name == destinationName && !showEditor) return
        if (!showEditor) destinationHistory = (destinationHistory + destinationName).takeLast(MAX_DESTINATION_HISTORY)
        destinationName = next.name
        showEditor = false
        if (next != DevForgeDestination.Git) {
            gitCommitHistoryOpen = false
            selectedGitCommitSha = null
        }
        if (next != DevForgeDestination.Build) {
            buildRepositoryPickerOpen = false
        }
    }

    fun handleBackNavigation() {
        val activeEditorTab = editor.activeTab
        when {
            destination == DevForgeDestination.More && destinationHistory.isEmpty() -> {
                // More is a secondary surface. From the app root, return to the
                // Editor main menu before the next Back press offers app exit.
                destinationHistory = emptyList()
                destinationName = DevForgeDestination.Build.name
                openEditor()
            }
            activeEditorTab != null -> {
                if (activeEditorTab.isDirty) unsavedEditorUri = activeEditorTab.uri
                else editor.close(activeEditorTab.uri)
            }
            gitCommitHistoryOpen -> {
                gitCommitHistoryOpen = false
                selectedGitCommitSha = null
            }
            destination == DevForgeDestination.Settings && settingsSection == "app" && appSettingsSection != "home" -> {
                appSettingsSection = "home"
            }
            destination == DevForgeDestination.Build && buildRepositoryPickerOpen -> {
                buildRepositoryPickerOpen = false
            }
            destination == DevForgeDestination.Settings && settingsSection != "home" -> {
                settingsSection = "home"
            }
            showEditorMenu -> {
                showEditorMenu = false
            }
            showProjectActivity -> {
                showProjectActivity = false
            }
            destinationHistory.isNotEmpty() -> {
                val previous = destinationHistory.last()
                destinationHistory = destinationHistory.dropLast(1)
                destinationName = previous
            }
            else -> showExitDialog = true
        }
    }

    BackHandler(enabled = true) { handleBackNavigation() }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (showEditor) {
                EditorWorkspaceTopBar(
                    workspace = workspace,
                    fileName = editor.activeTab?.name,
                    dirty = editor.activeTab?.isDirty == true,
                    onMenu = { showEditorMenu = true },
                    onSave = editor::saveActive,
                    onPreview = { previewMode = it },
                )
            } else if (destination !in setOf(DevForgeDestination.Terminal, DevForgeDestination.Connections) && !gitCommitHistoryOpen) {
                DevForgeTopBar(
                    workspace = workspace,
                    screenTitle = screenTitle,
                    onBack = ::handleBackNavigation,
                    onSearch = { showGlobalSearch = true },
                    onTerminal = { navigateTo(DevForgeDestination.Terminal) },
                )
            }
        },
        bottomBar = {
            if (
                destination !in setOf(DevForgeDestination.Terminal, DevForgeDestination.Settings, DevForgeDestination.More, DevForgeDestination.Connections) &&
                !expanded &&
                !gitCommitHistoryOpen
            ) {
                NavigationBottom(
                    showEditor = showEditor,
                    current = destination,
                    onOpenEditor = ::openEditor,
                    onSelect = ::navigateTo,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxSize()) {
                if (
                    expanded &&
                    destination !in setOf(DevForgeDestination.Terminal, DevForgeDestination.Settings, DevForgeDestination.More) &&
                    !gitCommitHistoryOpen
                ) {
                    NavigationSide(
                        showEditor = showEditor,
                        current = destination,
                        onOpenEditor = ::openEditor,
                        onSelect = ::navigateTo,
                    )
                }
                if (showEditor && ideTool == null) {
                    if (editing) {
                        Column(Modifier.fillMaxSize()) {
                            ProjectPulseStrip(
                                workspace = workspace,
                                editor = editor,
                                build = build,
                                pendingBatch = pendingBatch,
                                onClick = { showProjectActivity = true },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                EditorScreen(
                                    editor,
                                    settings,
                                    workspace,
                                    ::navigateTo,
                                    { ideTool = it },
                                )
                                ActivityRail(
                                    onActivity = { showProjectActivity = true },
                                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 5.dp),
                                )
                            }
                        }
                    } else EditorHomeScreen(
                        workspace = workspace,
                        editor = editor,
                        onMenu = { showEditorMenu = true },
                        onBuildWithAi = {
                            buildWithAiError = null
                            showBuildWithAi = true
                        },
                    )
                } else if (ideTool == null && destination == DevForgeDestination.Git && gitCommitHistoryOpen) {
                    GitCommitHistoryScreen(
                        initialCommitSha = selectedGitCommitSha,
                        onBack = {
                            gitCommitHistoryOpen = false
                            selectedGitCommitSha = null
                        },
                    )
                } else {
                    DestinationScreen(
                        build = build,
                        destination = destination,
                        workspace = workspace,
                        editor = editor,
                        settings = settings,
                        onCommitPending = { commitDialogOpen = true },
                        onOpenCommitHistory = {
                            selectedGitCommitSha = null
                            gitCommitHistoryOpen = true
                        },
                        onOpenDiffs = { navigateTo(DevForgeDestination.Diffs) },
                        settingsSection = settingsSection,
                        onSettingsSectionChange = { settingsSection = it },
                        appSettingsSection = appSettingsSection,
                        onAppSettingsSectionChange = { appSettingsSection = it },
                        buildRepositoryPickerOpen = buildRepositoryPickerOpen,
                        onBuildRepositoryPickerChange = { buildRepositoryPickerOpen = it },
                        onBack = ::handleBackNavigation,
                        onMoreDestination = ::navigateTo,
                        onOpenFeatureGuide = { showFeatureGuide = true },
                    )
                }
            }
            if (
                approvalCenter.pending.isEmpty() &&
                !showChat &&
                !showEditorMenu &&
                !showFeatureGuide &&
                !showProjectActivity
            ) {
                FloatingActionButton(
                    onClick = { showChat = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ChatBubble, contentDescription = "AI Chat")
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.align(Alignment.TopEnd).size(12.dp),
                        )
                    }
                }
            }
        }
    }

    if (showEditorMenu) {
        EditorWorkspaceMenu(
            workspace = workspace,
            editor = editor,
            github = githubRepositories,
            onClose = { showEditorMenu = false },
            onPublishLocalProject = {
                showEditorMenu = false
                showPublishLocalProject = true
            },
        )
    }

    if (showBuildWithAi) {
        BuildWithAiDialog(
            busy = buildWithAiBusy,
            error = buildWithAiError,
            onDismiss = { if (!buildWithAiBusy) showBuildWithAi = false },
            onCreateProject = { parentUri, projectName, goal ->
                buildWithAiBusy = true
                buildWithAiError = null
                appScope.launch {
                    workspace.createLocalProject(parentUri, projectName).onSuccess {
                        aiChat.prepareBuildWithAiPrompt(goal)
                        buildWithAiBusy = false
                        showBuildWithAi = false
                        showEditor = true
                        showChat = true
                    }.onFailure {
                        buildWithAiBusy = false
                        buildWithAiError = it.message ?: "Unable to create the local project."
                    }
                }
            },
        )
    }

    if (showPublishLocalProject) {
        LocalProjectPublishDialog(
            workspace = workspace,
            github = githubRepositories,
            onDismiss = { showPublishLocalProject = false },
        )
    }

    if (showFeatureGuide) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            FeatureGuideScreen(onClose = { showFeatureGuide = false })
        }
    }

    if (showChat) {
        BackHandler(enabled = true) { showChat = false }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                androidx.compose.material3.TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("AI Chat", fontWeight = FontWeight.Bold)
                            Text(
                                "Single main AI · plans, searches, edits and executes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { showChat = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Close AI Chat")
                        }
                    },
                )
                AIChatScreen()
            }
        }
    }

    if (previewMode != null) {
        BackHandler(enabled = true) { previewMode = null }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            PreviewScreen(
                mode = previewMode!!,
                editor = editor,
                workspace = workspace,
                build = build,
                onClose = { previewMode = null },
            )
        }
    }

    if (showProjectActivity) {
        ProjectActivityScreen(
            workspaceId = activeWorkspaceId,
            onDismiss = { showProjectActivity = false },
        )
    }

    LaunchedEffect(pendingBatch?.changes, pendingBatch?.branch) {
        val signature = pendingBatch?.let {
            it.changes.joinToString("|") { change ->
                change.path + ":" + change.delete + ":" + (change.content?.hashCode() ?: 0)
            } + ":" + it.branch
        }.orEmpty()
        if (signature.isBlank()) {
            lastPendingSignature = ""
            commitDialogOpen = false
        } else if (signature != lastPendingSignature) {
            lastPendingSignature = signature
            commitDialogOpen = true
        }
    }

    unsavedEditorUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { unsavedEditorUri = null },
            title = { Text("Unsaved changes") },
            text = { Text("This file has changes that have not been saved. What should happen before going back?") },
            confirmButton = {
                TextButton(onClick = {
                    unsavedEditorUri = null
                    editor.saveAndCloseActive()
                }) { Text("Save & go back") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        unsavedEditorUri = null
                        editor.close(uri, discard = true)
                    }) { Text("Discard") }
                    TextButton(onClick = { unsavedEditorUri = null }) { Text("Cancel") }
                }
            },
        )
    }

    if (showGlobalSearch) {
        AlertDialog(
            onDismissRequest = { showGlobalSearch = false },
            title = { Text("Search workspace") },
            text = {
                OutlinedTextField(
                    value = globalSearchQuery,
                    onValueChange = { globalSearchQuery = it.take(240) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("File or folder name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showGlobalSearch = false
                    if (globalSearchQuery.isNotBlank()) {
                        workspace.search(globalSearchQuery)
                        navigateTo(DevForgeDestination.Files)
                    }
                }) { Text("Search") }
            },
            dismissButton = { TextButton(onClick = { showGlobalSearch = false }) { Text("Cancel") } },
        )
    }

    if (commitDialogOpen && pendingBatch != null) {
        RemoteCommitDialog(
            batch = pendingBatch,
            onDismiss = { commitDialogOpen = false },
            onCommit = { branch, message ->
                appScope.launch {
                    val result = workspace.commitPendingRemote(branch, message)
                    result.onSuccess {
                        commitDialogOpen = false
                    }.onFailure {
                        workspace.showWorkspaceMessage(it.message ?: "GitHub commit failed.")
                    }
                }
            },
            onDiscard = {
                workspace.discardPendingRemote()
                commitDialogOpen = false
            },
        )
    }

    if (approvalCenter.pending.isNotEmpty()) {
        Box(Modifier.fillMaxSize()) {
            InAppApprovalOverlay(
                approval = approvalCenter.pending.first(),
                onApprove = approvalCenter::approve,
                onReject = approvalCenter::reject,
                onExpired = approvalCenter::expireDue,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 12.dp),
            )
        }
    }
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit DevForge?") },
            text = { Text("You are already at the first screen. Do you want to exit the app?") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    (context as? FragmentActivity)?.finish()
                }) { Text("Exit") }
            },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("Cancel") } },
        )
    }
    }
}

@Composable
private fun InAppApprovalOverlay(
    approval: ApprovalEntity,
    onApprove: (ApprovalEntity) -> Unit,
    onReject: (ApprovalEntity) -> Unit,
    onExpired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(approval.approvalId, approval.expiresAtEpochMs) {
        while (isActive) {
            now = System.currentTimeMillis()
            if (approval.expiresAtEpochMs <= now) {
                onExpired()
                break
            }
            delay(250)
        }
    }

    val remainingMs = (approval.expiresAtEpochMs - now).coerceAtLeast(0L)
    val remainingSeconds = ((remainingMs + 999L) / 1000L).toInt().coerceIn(0, 15)
    val expired = remainingMs <= 0L

    Surface(
        modifier = modifier.widthIn(max = 360.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Approval required", fontWeight = FontWeight.Bold)
                    Text(
                        if (expired) "Expired" else "Expires in " + remainingSeconds + "s",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (expired) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                    )
                }
                Text(
                    approval.risk,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                approval.summary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = { onReject(approval) },
                    enabled = !expired,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Reject")
                }
                Button(
                    onClick = { onApprove(approval) },
                    enabled = !expired,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Approve")
                }
            }
        }
    }
}

@Composable
private fun ProjectPulseStrip(
    workspace: WorkspaceViewModel,
    editor: EditorViewModel,
    build: BuildViewModel,
    pendingBatch: GitHubPendingChangeBatch?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dirtyCount = editor.tabs.count { it.isDirty }
    val changed = pendingBatch?.changes?.size ?: dirtyCount
    val buildLabel = when (build.state) {
        is com.mrredhood.devforge.core.build.BuildState.Running,
        is com.mrredhood.devforge.core.build.BuildState.Dispatching,
        is com.mrredhood.devforge.core.build.BuildState.Cancelling -> "Build …"
        is com.mrredhood.devforge.core.build.BuildState.Succeeded -> "Build ✓"
        is com.mrredhood.devforge.core.build.BuildState.Failed -> "Build !"
        is com.mrredhood.devforge.core.build.BuildState.Cancelled -> "Build —"
        else -> "Build ready"
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
        tonalElevation = 2.dp,
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    workspace.workspace?.name ?: "No workspace",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    pendingBatch?.branch ?: build.configuration.branch.ifBlank { "main" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    changed.toString() + " changes",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (changed > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    buildLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        buildLabel.contains("!") -> MaterialTheme.colorScheme.error
                        buildLabel.contains("✓") -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun ActivityRail(
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
    ) {
        Column(
            Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(onClick = onActivity, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.List, contentDescription = "Activity")
            }
        }
    }
}

@Composable
private fun ProjectActivityScreen(
    workspaceId: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = remember { DevForgeDatabase.get(context) }
    val auditFlow = remember(workspaceId) {
        workspaceId?.let { database.auditEventDao().observeForWorkspace(it, 40) }
    }
    val auditEvents = auditFlow?.collectAsState(initial = emptyList())?.value.orEmpty()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Project activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Change Story · AI tool actions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) { Text("×", style = MaterialTheme.typography.titleLarge) }
            }
            Divider()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text("Change Story", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (auditEvents.isEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Text(
                                "No recorded project activity yet. AI tool calls, approvals, and builds will appear here.",
                                Modifier.fillMaxWidth().padding(14.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(auditEvents.take(18), key = { it.eventId }) { event ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        event.eventType.replace('_', ' '),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                                            .format(java.util.Date(event.createdAtEpochMs)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(event.summary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorWorkspaceTopBar(
    workspace: WorkspaceViewModel,
    fileName: String?,
    dirty: Boolean,
    onMenu: () -> Unit,
    onSave: () -> Unit,
    onPreview: (PreviewMode) -> Unit,
) {
    androidx.compose.material3.TopAppBar(
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Editor", maxLines = 1, fontWeight = FontWeight.Bold)
                Text(
                    fileName ?: (workspace.workspace?.name ?: "No workspace"),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.Menu, contentDescription = "Workspace menu")
            }
        },
        actions = {
            var previewMenuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { previewMenuExpanded = true }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Preview")
                }
                DropdownMenu(
                    expanded = previewMenuExpanded,
                    onDismissRequest = { previewMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Show live preview") },
                        onClick = {
                            previewMenuExpanded = false
                            onPreview(PreviewMode.LIVE)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Show Preview (Build APK)") },
                        onClick = {
                            previewMenuExpanded = false
                            onPreview(PreviewMode.APK)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Web view preview") },
                        onClick = {
                            previewMenuExpanded = false
                            onPreview(PreviewMode.WEB)
                        },
                    )
                }
            }
            if (fileName != null && dirty) {
                IconButton(onClick = onSave) { Icon(Icons.Default.Save, contentDescription = "Save") }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}
@Composable
private fun EditorHomeScreen(
    workspace: WorkspaceViewModel,
    editor: EditorViewModel,
    onMenu: () -> Unit,
    onBuildWithAi: () -> Unit,
) {
    var createKind by rememberSaveable { mutableStateOf<String?>(null) }
    var createName by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize()) {
        if (workspace.workspace == null) {
            Column(
                Modifier.align(Alignment.Center).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(52.dp))
                Text("Choose a Workspace", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Open the workspace menu and select the project you want to edit.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onMenu) { Text("Open workspace menu") }
                OutlinedButton(onClick = onBuildWithAi) { Text("Build with AI") }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(workspace.workspace?.name ?: "Workspace", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                if (workspace.remoteWorkspace != null) "GitHub · " + workspace.remoteWorkspace!!.branch else "Local workspace",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { createKind = "choose"; createName = "" }) {
                            Text("+", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                if (workspace.breadcrumbs.size > 1) {
                    item { Breadcrumbs(workspace) }
                }
                if (workspace.isLoading) {
                    item { LoadingCard("Loading project tree…") }
                } else if (workspace.entries.isEmpty()) {
                    item { InfoCard("Empty workspace", "Use + to create a file or folder.") }
                } else {
                    items(workspace.entries, key = { it.uri.toString() }) { entry ->
                        TextButton(
                            onClick = {
                                if (entry.isDirectory) workspace.openDirectory(entry) else editor.open(entry)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (entry.isDirectory) WorkspaceFolderIcon(entry.name) else WorkspaceLanguageIcon(entry.name)
                                Spacer(Modifier.width(10.dp))
                                Text(entry.name, Modifier.weight(1f), maxLines = 1)
                                if (entry.isDirectory) Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
    if (createKind == "choose") {
        AlertDialog(
            onDismissRequest = { createKind = null },
            title = { Text("Create in workspace") },
            text = { Text("Choose what you want to create.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { createKind = "file"; createName = "" }) { Text("File") }
                    Button(onClick = { createKind = "folder"; createName = "" }) { Text("Folder") }
                }
            },
            dismissButton = { TextButton(onClick = { createKind = null }) { Text("Cancel") } },
        )
    } else if (createKind == "file" || createKind == "folder") {
        AlertDialog(
            onDismissRequest = { createKind = null; createName = "" },
            title = { Text(if (createKind == "file") "New file" else "New folder") },
            text = {
                OutlinedTextField(
                    value = createName,
                    onValueChange = { createName = it.take(255) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (createKind == "file") workspace.createFile(createName) else workspace.createFolder(createName)
                        createKind = null
                        createName = ""
                    },
                    enabled = createName.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { createKind = null; createName = "" }) { Text("Cancel") } },
        )
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorWorkspaceMenu(
    workspace: WorkspaceViewModel,
    editor: EditorViewModel,
    github: GitHubRepositoryViewModel,
    onClose: () -> Unit,
    onPublishLocalProject: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var createKind by rememberSaveable { mutableStateOf<String?>(null) }
    var createName by rememberSaveable { mutableStateOf("") }
    var openingRepository by remember { mutableStateOf<String?>(null) }

    var drawerRenameTarget by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var drawerRenameName by rememberSaveable { mutableStateOf("") }
    var drawerDeleteTarget by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var drawerMoveTarget by remember { mutableStateOf<WorkspaceEntry?>(null) }

    LaunchedEffect(Unit) {
        if (github.state.accountName == null && !github.state.isLoading) {
            github.refreshRepositories()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClose),
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f),
        ) {}
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.86f)
                .widthIn(max = 380.dp)
                .align(Alignment.CenterStart),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close workspace drawer")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            workspace.workspace?.name ?: "Workspace",
                            maxLines = 1,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            when {
                                workspace.remoteWorkspace != null ->
                                    workspace.remoteWorkspace!!.owner + "/" + workspace.remoteWorkspace!!.repository
                                workspace.localGitHubLink != null ->
                                    "GitHub · " + workspace.localGitHubLink!!.owner + "/" + workspace.localGitHubLink!!.repository
                                workspace.workspace != null -> "Local workspace"
                                else -> "No workspace"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    IconButton(
                        onClick = workspace::refresh,
                        enabled = workspace.workspace != null && !workspace.isLoading,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh file tree")
                    }
                    IconButton(
                        onClick = {
                            createKind = "choose"
                            createName = ""
                        },
                        enabled = workspace.workspace != null,
                    ) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Divider()
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (workspace.workspace != null) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (workspace.remoteWorkspace != null) Icons.Default.Cloud else Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    workspace.breadcrumbs.lastOrNull()?.name ?: workspace.workspace!!.name,
                                    maxLines = 1,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.weight(1f))
                                if (workspace.isLoading) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                        if (workspace.breadcrumbs.size > 1) item { Breadcrumbs(workspace) }
                        items(workspace.entries, key = { it.uri.toString() }) { entry ->
                            var menuExpanded by remember(entry.uri) { mutableStateOf(false) }
                            Box(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (entry.isDirectory) {
                                                    workspace.openDirectory(entry)
                                                } else {
                                                    editor.open(entry)
                                                    onClose()
                                                }
                                            },
                                            onLongClick = {
                                                if (entry.name != ".git") menuExpanded = true
                                            },
                                        )
                                        .padding(horizontal = 7.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Spacer(Modifier.width(8.dp))
                                    if (entry.isDirectory) {
                                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(21.dp))
                                    } else {
                                        WorkspaceLanguageIcon(entry.name)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(entry.name, Modifier.weight(1f), maxLines = 1)
                                    if (entry.isDirectory) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Open folder", modifier = Modifier.size(18.dp))
                                    }
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        enabled = entry.name != ".git",
                                        onClick = {
                                            menuExpanded = false
                                            drawerRenameTarget = entry
                                            drawerRenameName = entry.name
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        enabled = entry.name != ".git",
                                        onClick = {
                                            menuExpanded = false
                                            drawerDeleteTarget = entry
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Change file path") },
                                        enabled = entry.name != ".git",
                                        onClick = {
                                            menuExpanded = false
                                            drawerMoveTarget = entry
                                        },
                                    )
                                }
                            }
                        }
                        if (workspace.entries.isEmpty() && !workspace.isLoading) {
                            item {
                                Text(
                                    "No files in this folder.",
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        item {
                            InfoCard(
                                "No workspace selected",
                                "Choose a local workspace or a GitHub repository below.",
                            )
                        }
                    }

                    if (workspace.workspace != null && workspace.remoteWorkspace == null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        if (workspace.localGitHubLink == null) "Local project" else "GitHub-linked local project",
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        if (workspace.localGitHubLink == null) {
                                            "Work locally without GitHub, or upload this project to an existing repository."
                                        } else {
                                            "Linked repository: " + workspace.localGitHubLink!!.owner + "/" + workspace.localGitHubLink!!.repository
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Button(
                                        onClick = onPublishLocalProject,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(if (workspace.localGitHubLink == null) "Upload to GitHub" else "Update GitHub repository")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Divider(Modifier.padding(vertical = 8.dp))
                        Text(
                            "Workspaces",
                            Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (workspace.workspaces.isEmpty()) {
                        item {
                            Text(
                                "No saved workspaces.",
                                Modifier.fillMaxWidth().padding(8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(workspace.workspaces, key = { "ws:" + it.id }) { item ->
                            val active = item.id == workspace.workspace?.id
                            TextButton(
                                onClick = { if (!active) workspace.switchWorkspace(item.id) },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 7.dp),
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (GitHubWorkspaceUris.isRemote(item.treeUri)) Icons.Default.Cloud else Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, maxLines = 1, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                                        Text(if (active) "Open" else "Select", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (active) Text("✓")
                                }
                            }
                        }
                    }

                    item {
                        Divider(Modifier.padding(vertical = 8.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("GitHub", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    github.state.accountName?.let { "Connected as " + it } ?: "Connect GitHub in Settings",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { github.refreshRepositories() },
                                enabled = github.state.accountName != null && !github.state.isLoading,
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh GitHub repositories")
                            }
                        }
                    }

                    if (github.state.isLoading && github.state.repositories.isEmpty()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Fetching repositories…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else if (github.state.accountName == null) {
                        item {
                            Text(
                                "Connect GitHub to load your repositories here.",
                                Modifier.fillMaxWidth().padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(github.state.repositories, key = { "gh:" + it.id }) { repo ->
                            val fullName = repo.fullName
                            TextButton(
                                onClick = {
                                    if (openingRepository != fullName) {
                                        openingRepository = fullName
                                        scope.launch {
                                            val result = workspace.openOrActivateGitHubRepository(
                                                repo.owner,
                                                repo.name,
                                                repo.defaultBranch,
                                            )
                                            openingRepository = null
                                            if (result.isSuccess) onClose()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 7.dp),
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(repo.name, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                        Text(repo.owner + " · " + (if (repo.isPrivate) "Private" else "Public"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                    if (openingRepository == fullName) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else if (fullName == workspace.remoteWorkspace?.let { it.owner + "/" + it.repository }) Text("✓")
                                }
                            }
                        }
                        if (github.state.truncated) {
                            item {
                                Text(
                                    "Repository list reached the connector page limit.",
                                    Modifier.fillMaxWidth().padding(8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    drawerMoveTarget?.let { entry ->
        ChangePathDialog(
            entry = entry,
            workspace = workspace,
            onDismiss = { drawerMoveTarget = null },
            onChoose = { destination ->
                workspace.moveEntry(entry, destination)
                drawerMoveTarget = null
            },
        )
    }

    drawerRenameTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { drawerRenameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = drawerRenameName,
                    onValueChange = { drawerRenameName = it.take(255) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("New name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        workspace.renameEntry(entry, drawerRenameName)
                        drawerRenameTarget = null
                    },
                    enabled = drawerRenameName.isNotBlank(),
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { drawerRenameTarget = null }) { Text("Cancel") } },
        )
    }

    drawerDeleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { drawerDeleteTarget = null },
            title = { Text("Delete " + entry.name + "?") },
            text = { Text("This removes the item from the workspace.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        workspace.deleteEntry(entry)
                        drawerDeleteTarget = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { drawerDeleteTarget = null }) { Text("Cancel") } },
        )
    }

    if (createKind == "choose") {
        AlertDialog(
            onDismissRequest = { createKind = null },
            title = { Text("Create in workspace") },
            text = { Text("Choose what you want to create.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { createKind = "file"; createName = "" }) { Text("File") }
                    Button(onClick = { createKind = "folder"; createName = "" }) { Text("Folder") }
                }
            },
            dismissButton = { TextButton(onClick = { createKind = null }) { Text("Cancel") } },
        )
    } else if (createKind == "file" || createKind == "folder") {
        AlertDialog(
            onDismissRequest = { createKind = null; createName = "" },
            title = { Text(if (createKind == "file") "New file" else "New folder") },
            text = {
                OutlinedTextField(
                    value = createName,
                    onValueChange = { createName = it.take(255) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (createKind == "file") workspace.createFile(createName)
                        else workspace.createFolder(createName)
                        createKind = null
                        createName = ""
                    },
                    enabled = createName.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { createKind = null; createName = "" }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevForgeTopBar(
    workspace: WorkspaceViewModel,
    screenTitle: String,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onTerminal: () -> Unit,
) {
    var workspaceMenuOpen by remember { mutableStateOf(false) }

    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    screenTitle,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    maxLines = 1,
                )
                TextButton(
                    onClick = { workspaceMenuOpen = true },
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                ) {
                    Text(
                        workspace.workspace?.name ?: "No workspace",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Select workspace",
                        Modifier.size(16.dp),
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
            IconButton(onClick = onTerminal) {
                Icon(Icons.Default.Terminal, contentDescription = "Terminal")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )

    DropdownMenu(
        expanded = workspaceMenuOpen,
        onDismissRequest = { workspaceMenuOpen = false },
    ) {
        workspace.workspaces.forEach { item ->
            DropdownMenuItem(
                text = {
                    Text(
                        (if (item.id == workspace.workspace?.id) "✓ " else "") + item.name,
                        maxLines = 1,
                    )
                },
                onClick = {
                    workspaceMenuOpen = false
                    workspace.switchWorkspace(item.id)
                },
            )
        }
        if (workspace.workspaces.isEmpty()) {
            DropdownMenuItem(
                text = { Text("No workspaces") },
                onClick = { workspaceMenuOpen = false },
                enabled = false,
            )
        }
    }
}

@Composable
private fun NavigationBottom(
    showEditor: Boolean,
    current: DevForgeDestination,
    onOpenEditor: () -> Unit,
    onSelect: (DevForgeDestination) -> Unit,
) {
    val primary = listOf(
        DevForgeDestination.Files,
        DevForgeDestination.Git,
        DevForgeDestination.Build,
    )

    NavigationBar {
        NavigationBarItem(
            selected = showEditor,
            onClick = onOpenEditor,
            icon = { Icon(Icons.Default.Code, contentDescription = "Editor navigation") },
            label = { Text("Editor", maxLines = 1) },
        )
        primary.forEach { item ->
            NavigationBarItem(
                selected = !showEditor && current == item,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, item.label + " navigation") },
                label = { Text(item.label, maxLines = 1) },
            )
        }
        NavigationBarItem(
            selected = !showEditor && current == DevForgeDestination.More,
            onClick = { onSelect(DevForgeDestination.More) },
            icon = { Icon(Icons.Default.Menu, contentDescription = "More navigation") },
            label = { Text("More") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationSide(
    showEditor: Boolean,
    current: DevForgeDestination,
    onOpenEditor: () -> Unit,
    onSelect: (DevForgeDestination) -> Unit,
) {
    val primary = listOf(
        DevForgeDestination.Files,
        DevForgeDestination.Git,
        DevForgeDestination.Build,
    )
    NavigationRail(Modifier.fillMaxHeight().width(88.dp)) {
        Spacer(Modifier.height(18.dp))
        NavigationRailItem(
            selected = showEditor,
            onClick = onOpenEditor,
            icon = { Icon(Icons.Default.Code, contentDescription = "Editor") },
            label = { Text("Editor") },
        )
        primary.forEach { item ->
            NavigationRailItem(
                selected = !showEditor && current == item,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, item.label) },
                label = { Text(item.label) },
            )
        }
        NavigationRailItem(
            selected = !showEditor && current == DevForgeDestination.More,
            onClick = { onSelect(DevForgeDestination.More) },
            icon = { Icon(Icons.Default.Menu, contentDescription = "More") },
            label = { Text("More") },
        )
    }
}

@Composable
private fun DestinationScreen(
    destination: DevForgeDestination,
    workspace: WorkspaceViewModel,
    editor: EditorViewModel,
    build: BuildViewModel,
    settings: DevForgeSettingsViewModel,
    onCommitPending: () -> Unit,
    onOpenCommitHistory: () -> Unit,
    onOpenDiffs: () -> Unit,
    settingsSection: String,
    onSettingsSectionChange: (String) -> Unit,
    appSettingsSection: String,
    onAppSettingsSectionChange: (String) -> Unit,
    buildRepositoryPickerOpen: Boolean,
    onBuildRepositoryPickerChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onMoreDestination: (DevForgeDestination) -> Unit,
    onOpenFeatureGuide: () -> Unit,
) {
    when (destination) {
        DevForgeDestination.Files -> FilesScreen(workspace, editor, onCommitPending)
        DevForgeDestination.Git -> GitRemoteOverviewScreen(
            onCommitPending = onCommitPending,
            onOpenCommitHistory = onOpenCommitHistory,
            onOpenDiffs = onOpenDiffs,
        )
        DevForgeDestination.Diffs -> GitDiffScreen()
        DevForgeDestination.Build -> BuildCenterScreen(
            repositoryPickerOpen = buildRepositoryPickerOpen,
            onRepositoryPickerChange = onBuildRepositoryPickerChange,
        )
        DevForgeDestination.LiveActions -> LiveActionsScreen(onBack = onBack)
        DevForgeDestination.Connections -> AiGitHubHubScreen(buildViewModel = build, onBack = onBack)
        DevForgeDestination.Terminal -> TerminalScreen(onBack = onBack)
        DevForgeDestination.Approvals -> ApprovalCenterScreen()
        DevForgeDestination.Settings -> SettingsScreen(
            settings = settings,
            section = settingsSection,
            onSectionChange = onSettingsSectionChange,
            appSettingsSection = appSettingsSection,
            onAppSettingsSectionChange = onAppSettingsSectionChange,
        )
        DevForgeDestination.More -> MoreScreen(
            onSelect = { target -> onMoreDestination(target) },
            onOpenFeatureGuide = onOpenFeatureGuide,
        )
    }
}

@Composable
private fun MoreScreen(
    onSelect: (DevForgeDestination) -> Unit,
    onOpenFeatureGuide: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize().testTag("more-screen-list"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "More",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "AI & GitHub, approvals, live actions, and project utilities.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item {
            Card(
                onClick = onOpenFeatureGuide,
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Info, contentDescription = null)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Help & guide", fontWeight = FontWeight.Bold)
                        Text(
                            "ⓘ Learn what every DevForge feature does, where to find it, and how to use its symbols.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }
        item {
            SimpleSettingsTile(
                title = "Settings",
                subtitle = "Build, terminal, privacy, appearance and editor preferences",
                onClick = { onSelect(DevForgeDestination.Settings) },
            )
        }
        item {
            SimpleSettingsTile(
                title = "Live GitHub Actions",
                subtitle = "Realtime workflow status, animated running state, jobs, and live logs",
                onClick = { onSelect(DevForgeDestination.LiveActions) },
            )
        }
        item {
            SimpleSettingsTile(
                title = "AI & GitHub",
                subtitle = "Choose GitHub, AI Models, or AI Tools",
                onClick = { onSelect(DevForgeDestination.Connections) },
                modifier = Modifier.semantics {
                    contentDescription = "AI & GitHub management"
                },
            )
        }
        item {
            SimpleSettingsTile(
                title = "Approvals",
                subtitle = "Review actions waiting for permission",
                onClick = { onSelect(DevForgeDestination.Approvals) },
            )
        }
        item {
            SimpleSettingsTile(
                title = "Report a bug",
                subtitle = "Open a GitHub issue and tell us what went wrong",
                onClick = {
                    runCatching {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/MrRedhood/DevForge/issues/new"),
                        )
                        context.startActivity(intent)
                    }
                },
            )
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "DevForge 1.0",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Created by MrRedhood",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FilesScreen(
    workspace: WorkspaceViewModel,
    editor: EditorViewModel,
    onCommitPending: () -> Unit,
) {
    val git: GitViewModel = viewModel()
    var showCreateWorkspace by rememberSaveable { mutableStateOf(false) }
    var showGithubImport by rememberSaveable { mutableStateOf(false) }
    var workspaceName by rememberSaveable { mutableStateOf("") }
    var createKind by rememberSaveable { mutableStateOf<String?>(null) }
    var createName by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var renameName by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var moveTarget by remember { mutableStateOf<WorkspaceEntry?>(null) }

    val context = LocalContext.current

    LaunchedEffect(workspace.workspace?.id) {
        if (workspace.workspace != null) git.inspectWorkspace()
    }

    LaunchedEffect(Unit) {
        PickerBridge.results.collectLatest { result ->
            if (result.kind != SystemPickerActivity.KIND_WORKSPACE || result.cancelled) return@collectLatest
            val uri = result.uris.firstOrNull() ?: return@collectLatest
            workspace.openWorkspace(uri = uri, workspaceName = workspaceName)
            workspaceName = ""
            showCreateWorkspace = false
        }
    }

    if (showGithubImport) {
        BackHandler(enabled = true) { showGithubImport = false }
        GitHubWorkspaceImportScreen(
            workspaceViewModel = workspace,
            onBack = { showGithubImport = false },
        )
        return
    }

    fun launchWorkspacePicker() {
        val activity = context as? FragmentActivity
        if (activity == null) {
            workspace.reportWorkspacePickerError(IllegalStateException("Unable to access the current Activity."))
            return
        }
        runCatching {
            activity.startActivityForResult(
                Intent(context, SystemPickerActivity::class.java)
                    .putExtra(SystemPickerActivity.EXTRA_KIND, SystemPickerActivity.KIND_WORKSPACE),
                SystemPickerActivity.PICKER_REQUEST_CODE,
            )
        }.onFailure { workspace.reportWorkspacePickerError(it) }
    }

    BackHandler(enabled = workspace.breadcrumbs.size > 1) { workspace.goUp() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Files", fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Text(
                            workspace.workspace?.name ?: "No workspace",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = { showCreateWorkspace = true }) { Text("Add workspace") }
                    OutlinedButton(onClick = { showGithubImport = true }) { Text("Open GitHub repo") }
                    if (workspace.workspace != null) {
                        if (workspace.remoteWorkspace != null) {
                            OutlinedButton(onClick = workspace::pullRemote) { Text("Pull") }
                            Button(
                                onClick = onCommitPending,
                                enabled = GitHubPendingChanges.batch(workspace.workspace?.id.orEmpty()) != null,
                            ) { Text("Push") }
                        } else {
                            OutlinedButton(
                                onClick = git::pullRemote,
                                enabled = git.capabilities.pullRemote == CapabilityAvailability.Available && !git.isExecuting,
                            ) { Text("Pull") }
                            Button(
                                onClick = git::pushRemote,
                                enabled = git.capabilities.pushRemote == CapabilityAvailability.Available && !git.isExecuting,
                            ) { Text("Push") }
                        }
                        TextButton(onClick = { createKind = "file"; createName = "" }) { Text("New file") }
                        TextButton(onClick = { createKind = "folder"; createName = "" }) { Text("New folder") }
                        IconButton(onClick = workspace::refresh) { Icon(Icons.Default.Refresh, "Refresh files") }
                    }
                }
            }
        }

        if (workspace.workspace == null) {
            item { InfoCard("Create a workspace", "Pick your project folder once. DevForge remembers it.") }
        } else {
            item { Breadcrumbs(workspace) }
            if (workspace.workspace != null && !git.operationMessage.isNullOrBlank()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Text(
                            git.operationMessage.orEmpty(),
                            Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            workspace.knowledgeMessage?.let { message ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(message, Modifier.weight(1f))
                            TextButton(onClick = workspace::clearKnowledgeMessage) { Text("Close") }
                        }
                    }
                }
            }
            if (workspace.isSearching || workspace.searchResults.isNotEmpty()) {
                item { WorkspaceSearchResults(workspace, editor) }
            } else if (workspace.isLoading) {
                item { LoadingCard("Loading files…") }
            } else if (workspace.entries.isEmpty()) {
                item { InfoCard("Empty folder", "Use New file or New folder.") }
            } else {
                items(workspace.entries, key = { it.uri.toString() }) { entry ->
                    FileRow(
                        entry = entry,
                        onOpen = {
                            if (entry.isDirectory) workspace.openDirectory(entry) else editor.open(entry)
                        },
                        onRename = {
                            renameTarget = entry
                            renameName = entry.name
                        },
                        onDelete = { deleteTarget = entry },
                        onChangePath = { moveTarget = entry },
                    )
                }
            }
        }

        if (workspace.workspaces.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Workspaces", fontWeight = FontWeight.Bold)
                        workspace.workspaces.forEach { item ->
                            val active = item.id == workspace.workspace?.id
                            TextButton(
                                onClick = { workspace.switchWorkspace(item.id) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text((if (active) "✓ " else "") + item.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateWorkspace) {
        AlertDialog(
            onDismissRequest = { showCreateWorkspace = false; workspaceName = "" },
            title = { Text("New workspace") },
            text = {
                OutlinedTextField(
                    workspaceName,
                    { workspaceName = it.replace(Regex("[\r\n]"), " ").take(120) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { showCreateWorkspace = false; launchWorkspacePicker() }) { Text("Choose folder") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateWorkspace = false; workspaceName = "" }) { Text("Cancel") }
            },
        )
    }

    createKind?.let { kind ->
        AlertDialog(
            onDismissRequest = { createKind = null; createName = "" },
            title = { Text(if (kind == "file") "New file" else "New folder") },
            text = {
                OutlinedTextField(
                    createName,
                    { createName = it.take(255) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (kind == "file") workspace.createFile(createName) else workspace.createFolder(createName)
                        createKind = null
                        createName = ""
                    },
                    enabled = createName.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { createKind = null; createName = "" }) { Text("Cancel") } },
        )
    }

    renameTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    renameName,
                    { renameName = it.take(255) },
                    Modifier.fillMaxWidth(),
                    label = { Text("New name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        workspace.renameEntry(entry, renameName)
                        renameTarget = null
                    },
                    enabled = renameName.isNotBlank(),
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }

    moveTarget?.let { entry ->
        ChangePathDialog(
            entry = entry,
            workspace = workspace,
            onDismiss = { moveTarget = null },
            onChoose = { destination ->
                workspace.moveEntry(entry, destination)
                moveTarget = null
            },
        )
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete " + entry.name + "?") },
            text = { Text("This removes the item from the workspace.") },
            confirmButton = {
                TextButton(onClick = {
                    workspace.deleteEntry(entry)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun WorkspaceSearchResults(workspace: WorkspaceViewModel, editor: EditorViewModel) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Search results", fontWeight = FontWeight.Bold)
                    Text(
                        workspace.searchQuery,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = workspace::clearSearch) { Text("Clear") }
            }
            if (workspace.isSearching) {
                LoadingCard("Searching workspace…")
            } else if (workspace.searchResults.isEmpty()) {
                Text("No matching files or folders.")
            } else {
                workspace.searchResults.take(60).forEach { result ->
                    FileRow(
                        entry = WorkspaceEntry(
                            uri = result.uri,
                            name = result.name,
                            isDirectory = result.isDirectory,
                            sizeBytes = result.sizeBytes,
                        ),
                        onOpen = {
                            if (result.isDirectory) {
                            workspace.openDirectory(
                                WorkspaceEntry(
                                    uri = result.uri,
                                    name = result.name,
                                    isDirectory = true,
                                    sizeBytes = result.sizeBytes,
                                )
                            )
                            workspace.clearSearch()
                        } else {
                            editor.open(
                                WorkspaceEntry(
                                    uri = result.uri,
                                    name = result.name,
                                    isDirectory = false,
                                    sizeBytes = result.sizeBytes,
                                )
                            )
                            workspace.clearSearch()
                            }
                        },
                        onRename = {},
                        onDelete = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(workspace: WorkspaceViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        workspace.breadcrumbs.forEachIndexed { index, crumb ->
            if (index > 0) Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp).alpha(.45f))
            FilterChip(
                selected = index == workspace.breadcrumbs.lastIndex,
                onClick = { workspace.goToBreadcrumb(index) },
                label = { Text(crumb.name, maxLines = 1) },
            )
        }
    }
}

private data class PathPickerLevel(
    val uri: android.net.Uri,
    val name: String,
)

@Composable
private fun ChangePathDialog(
    entry: WorkspaceEntry,
    workspace: WorkspaceViewModel,
    onDismiss: () -> Unit,
    onChoose: (android.net.Uri) -> Unit,
) {
    val root = workspace.rootUri
    if (root == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Change file path") },
            text = { Text("No active workspace is available.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        )
        return
    }

    val rootName = workspace.workspace?.name ?: "Root"
    var stack by remember(root, entry.uri) {
        mutableStateOf(listOf(PathPickerLevel(root, rootName)))
    }
    var children by remember(root, entry.uri) { mutableStateOf<List<WorkspaceEntry>>(emptyList()) }
    var loading by remember(root, entry.uri) { mutableStateOf(true) }
    var error by remember(root, entry.uri) { mutableStateOf<String?>(null) }

    val current = stack.last()

    LaunchedEffect(current.uri) {
        loading = true
        error = null
        runCatching { workspace.listDirectory(current.uri) }
            .onSuccess { children = it }
            .onFailure {
                children = emptyList()
                error = it.message ?: "Unable to read this folder."
            }
        loading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.82f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            if (stack.size > 1) stack = stack.dropLast(1) else onDismiss()
                        },
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = if (stack.size > 1) "Go to parent folder" else "Close",
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Change file path", fontWeight = FontWeight.Bold)
                        Text(
                            stack.joinToString(" / ") { it.name },
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Divider()

                if (loading) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (error != null) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        item {
                            Text(
                                "Destination folder",
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (children.isEmpty()) {
                            item {
                                Text(
                                    "This folder is empty.",
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(children, key = { it.uri.toString() }) { child ->
                                TextButton(
                                    onClick = {
                                        if (child.isDirectory) {
                                            stack = stack + PathPickerLevel(child.uri, child.name)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = child.isDirectory,
                                ) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (child.isDirectory) Icons.Default.Folder else Icons.Default.Code,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(child.name, Modifier.weight(1f), maxLines = 1)
                                        if (child.isDirectory) {
                                            Icon(
                                                Icons.Default.ChevronRight,
                                                contentDescription = "Open folder",
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Divider()
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onChoose(current.uri) },
                        enabled = !loading && error == null,
                    ) {
                        Text("Choose this path")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: WorkspaceEntry,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onChangePath: (() -> Unit)? = null,
) {
    var menuExpanded by remember(entry.uri) { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { menuExpanded = true },
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (entry.isDirectory) {
                    WorkspaceFolderIcon(entry.name, modifier = Modifier.size(22.dp))
                } else {
                    WorkspaceLanguageIcon(entry.name)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        if (entry.isDirectory) "Folder" else formatBytes(entry.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRename, enabled = entry.name != ".git") {
                    Icon(Icons.Default.Edit, "Rename")
                }
                IconButton(onClick = onDelete, enabled = entry.name != ".git") {
                    Icon(Icons.Default.Delete, "Delete")
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                enabled = entry.name != ".git",
                onClick = {
                    menuExpanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                enabled = entry.name != ".git",
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
            if (onChangePath != null) {
                DropdownMenuItem(
                    text = { Text("Change file path") },
                    enabled = entry.name != ".git",
                    onClick = {
                        menuExpanded = false
                        onChangePath()
                    },
                )
            }
        }
    }
}

@Composable
private fun RemoteCommitDialog(
    batch: GitHubPendingChangeBatch,
    onDismiss: () -> Unit,
    onCommit: (branch: String, message: String) -> Unit,
    onDiscard: () -> Unit,
) {
    var branch by remember(batch.workspaceId, batch.branch) { mutableStateOf(batch.branch) }
    var message by remember(batch.workspaceId, batch.changes.map { it.path + it.delete + (it.content?.hashCode() ?: 0) }) {
        mutableStateOf(
            if (batch.changes.size == 1) {
                (if (batch.changes.first().delete) "Delete " else "Update ") + batch.changes.first().path
            } else {
                "Update " + batch.changes.size + " files"
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Commit GitHub changes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (batch.folderPath.isBlank()) {
                        "Commit to repository root in " + batch.branch
                    } else {
                        "Commit to " + batch.folderPath + " in " + batch.branch
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it.take(200) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Branch") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Commit message") },
                    minLines = 2,
                    maxLines = 4,
                )
                Text(
                    "Changes",
                    fontWeight = FontWeight.SemiBold,
                )
                batch.changes.take(20).forEach { change ->
                    Text(
                        (if (change.delete) "− " else "+ ") + change.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (change.delete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (batch.changes.size > 20) {
                    Text(
                        "+" + (batch.changes.size - 20) + " more changes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCommit(branch.trim(), message.trim()) },
                enabled = branch.trim().isNotBlank() && message.trim().isNotBlank(),
            ) {
                Text("Commit")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) { Text("Discard") }
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        },
    )
}

@Composable
private fun EditorChangesPanel(
    active: EditorTab,
    repositoryDocuments: List<GitDiffDocument>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val localDocument = remember(active.content, active.savedContent) {
        if (active.content == active.savedContent) {
            null
        } else {
            GitDiffDocument(
                path = active.name + " (unsaved)",
                status = GitFileStatus.Modified,
                sections = listOf(
                    GitDiffSection(
                        title = "Editor changes",
                        beforeLabel = "Saved",
                        afterLabel = "Current",
                        lines = DiffEngine().compare(active.savedContent, active.content),
                    ),
                ),
            )
        }
    }

    val documents = buildList {
        localDocument?.let(::add)
        repositoryDocuments
            .filterNot { document -> localDocument != null && document.path == active.name }
            .forEach(::add)
    }.filter { document -> document.sections.any { section -> section.lines.any { line -> line.kind != DiffKind.CONTEXT } } }

    val additions = documents.sumOf { document ->
        document.sections.sumOf { section -> section.lines.count { it.kind == DiffKind.ADDED } }
    }
    val removals = documents.sumOf { document ->
        document.sections.sumOf { section -> section.lines.count { it.kind == DiffKind.REMOVED } }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onExpandedChange(!expanded) }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = if (expanded) "Collapse all changed files" else "Show all changed files",
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("Changes", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        if (documents.isEmpty()) {
                            "No code changes"
                        } else {
                            documents.size.toString() +
                                " file" +
                                (if (documents.size == 1) "" else "s") +
                                " · +" + additions + " −" + removals
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onExpandedChange(!expanded) }) {
                    Text(if (expanded) "Collapse all" else "Show all")
                }
            }

            if (expanded && documents.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    documents.take(80).forEach { document ->
                        EditorChangeDocumentRow(document)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorChangeDocumentRow(document: GitDiffDocument) {
    var expanded by remember(document.path) { mutableStateOf(false) }
    val additions = document.sections.sumOf { section -> section.lines.count { it.kind == DiffKind.ADDED } }
    val removals = document.sections.sumOf { section -> section.lines.count { it.kind == DiffKind.REMOVED } }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Default.ArrowDropDown else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse changed file" else "Show changed code",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(document.path, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    document.status.name.replace('_', ' ') + " · +" + additions + " −" + removals,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        if (expanded) {
            document.sections.forEach { section ->
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(section.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        section.lines.take(160).forEach { line ->
                            val prefix = when (line.kind) {
                                DiffKind.ADDED -> "+ "
                                DiffKind.REMOVED -> "- "
                                DiffKind.CONTEXT -> "  "
                            }
                            val lineColor = when (line.kind) {
                                DiffKind.ADDED -> MaterialTheme.colorScheme.primary
                                DiffKind.REMOVED -> MaterialTheme.colorScheme.error
                                DiffKind.CONTEXT -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                prefix + line.text,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = lineColor,
                            )
                        }
                    }
                }
            }
            document.unavailableReason?.let { reason ->
                Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}
@Composable
private fun EditorScreen(
    editor: EditorViewModel,
    settings: DevForgeSettingsViewModel,
    workspace: WorkspaceViewModel,
    onOpenDestination: (DevForgeDestination) -> Unit,
    onOpenIdeTool: (IdeTool) -> Unit,
) {
    val active = editor.activeTab
    if (active == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(28.dp),
            ) {
                if (editor.isLoading) {
                    CircularProgressIndicator()
                    Text("Opening file…", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(
                        "Unable to open file",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    editor.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = editor::retryOpen) { Text("Retry") }
                        TextButton(onClick = editor::dismissError) { Text("Dismiss") }
                    }
                }
            }
        }
        return
    }
    val gitDiffViewModel: GitDiffViewModel = viewModel()
    var fieldValue by remember(active.uri) { mutableStateOf(TextFieldValue(active.content)) }
    var showFind by remember(active.uri) { mutableStateOf(false) }
    var showGoToLine by remember(active.uri) { mutableStateOf(false) }
    var showSymbols by remember(active.uri) { mutableStateOf(false) }
    var showCommandPalette by remember(active.uri) { mutableStateOf(false) }
    var commandQuery by remember(active.uri) { mutableStateOf("") }
    var showQuickOpen by remember(active.uri) { mutableStateOf(false) }
    var splitUri by rememberSaveable(active.uri) { mutableStateOf<android.net.Uri?>(null) }
    var findQuery by remember(active.uri) { mutableStateOf("") }
    var replaceQuery by remember(active.uri) { mutableStateOf("") }
    var replaceMessage by remember(active.uri) { mutableStateOf<String?>(null) }
    var lineQuery by remember(active.uri) { mutableStateOf("") }
    var collapsedStarts by remember(active.uri) { mutableStateOf(emptySet<Int>()) }
    var changesExpanded by rememberSaveable(active.uri) { mutableStateOf(false) }
    val horizontalEditorScroll = rememberScrollState()

    LaunchedEffect(active.uri) {
        gitDiffViewModel.refresh()
    }

    LaunchedEffect(
        active.uri,
        active.content,
        settings.settings.autoSaveEnabled,
        settings.settings.autoSaveIntervalMs,
    ) {
        if (settings.settings.autoSaveEnabled && active.isDirty) {
            delay(settings.settings.autoSaveIntervalMs.coerceIn(1_000L, 10_000_000_000L))
            if (editor.activeTab?.uri == active.uri && editor.activeTab?.isDirty == true) {
                editor.saveActive()
            }
        }
    }

    LaunchedEffect(changesExpanded, active.uri) {
        if (changesExpanded) {
            while (isActive) {
                gitDiffViewModel.refresh()
                delay(1500)
            }
        }
    }

    val estimatedBytes = remember(active.content) { active.content.length.toLong() * 2L }
    val richCodeRendering = remember(estimatedBytes) { estimatedBytes <= 64L * 1024L }
    var foldRanges by remember(active.uri) { mutableStateOf<List<EditorFoldRange>>(emptyList()) }

    LaunchedEffect(active.content) {
        if (fieldValue.text != active.content) {
            fieldValue = TextFieldValue(active.content, TextRange(active.content.length))
        }
    }

    LaunchedEffect(active.uri, active.content, richCodeRendering) {
        if (!richCodeRendering) {
            foldRanges = emptyList()
            collapsedStarts = emptySet()
            return@LaunchedEffect
        }
        delay(220)
        val snapshot = active.content
        val computed = withContext(Dispatchers.Default) {
            EditorFolding.ranges(snapshot, maxRanges = 40)
        }
        if (editor.activeTab?.uri == active.uri && editor.activeTab?.content == snapshot) {
            foldRanges = computed
            val validStarts = computed.map { it.startOffset }.toSet()
            collapsedStarts = collapsedStarts.intersect(validStarts)
        }
    }

    val activeFolds = remember(foldRanges, collapsedStarts) {
        foldRanges.filter { it.startOffset in collapsedStarts }
    }
    val language = remember(active.name) { EditorLanguage.detect(active.name) }
    val syntaxPrimary = MaterialTheme.colorScheme.primary
    val syntaxTertiary = MaterialTheme.colorScheme.tertiary
    val syntaxOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val syntaxSecondary = MaterialTheme.colorScheme.secondary
    val syntax = if (richCodeRendering) {
        remember(
            language,
            syntaxPrimary,
            syntaxTertiary,
            syntaxOnSurfaceVariant,
            syntaxSecondary,
        ) {
            CodeSyntaxVisualTransformation(
                language = language,
                keywordColor = syntaxPrimary,
                stringColor = syntaxTertiary,
                commentColor = syntaxOnSurfaceVariant,
                numberColor = syntaxSecondary,
            )
        }
    } else {
        VisualTransformation.None
    }
    val baseTransformation: VisualTransformation =
        if (activeFolds.isEmpty()) syntax
        else ChainedVisualTransformation(FoldingVisualTransformation(active.content, activeFolds), syntax)
    val transformation: VisualTransformation =
        if (settings.settings.showInvisibles && richCodeRendering) {
            ChainedVisualTransformation(baseTransformation, VisibleWhitespaceVisualTransformation())
        } else {
            baseTransformation
        }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(active.name, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    language.name.replace('_', ' ') + " · " + formatEditorSize(estimatedBytes) + " · " + active.content.length + " chars",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (active.isDirty) {
                Text("Unsaved", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (editor.tabs.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(editor.tabs, key = { it.uri.toString() }) { tab ->
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (tab.uri == active.uri) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { editor.select(tab.uri) }) {
                                Text((if (tab.isDirty) "● " else "") + tab.name, maxLines = 1)
                            }
                            IconButton(onClick = { editor.close(tab.uri) }, modifier = Modifier.size(28.dp)) {
                                Text("×")
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = editor::undo, enabled = richCodeRendering) { Text("↶") }
            IconButton(onClick = editor::redo, enabled = richCodeRendering) { Text("↷") }
            IconButton(onClick = { showQuickOpen = true }) { Icon(Icons.Default.Search, "Quick open") }
            IconButton(onClick = { showFind = true }) { Text("⌕") }
            IconButton(onClick = { showGoToLine = true }) { Text("#") }
            TextButton(onClick = { fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length)) }) { Text("All", maxLines = 1) }
            TextButton(onClick = {
                val cursor = fieldValue.selection.start.coerceIn(0, fieldValue.text.length)
                val startLine = (fieldValue.text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)) + 1).coerceAtLeast(0)
                val endLine = fieldValue.text.indexOf('\n', cursor).let { if (it < 0) fieldValue.text.length else it }
                fieldValue = fieldValue.copy(selection = TextRange(startLine, endLine))
            }) { Text("Line", maxLines = 1) }
            Surface(shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(language.name.replace('_', ' '), Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            TextButton(onClick = { showSymbols = true }, enabled = richCodeRendering) { Text("Symbols", maxLines = 1) }
            if (editor.tabs.size > 1) {
                TextButton(onClick = { splitUri = if (splitUri == null) editor.tabs.firstOrNull { it.uri != active.uri }?.uri else null }) {
                    Text(if (splitUri == null) "Split" else "Unsplit", maxLines = 1)
                }
            }
            if (foldRanges.isNotEmpty()) {
                IconButton(onClick = { collapsedStarts = foldRanges.map { it.startOffset }.toSet() }) { Text("⌄") }
                IconButton(onClick = { collapsedStarts = emptySet() }) { Text("⌃") }
            }
            IconButton(onClick = editor::refreshActive) { Icon(Icons.Default.Refresh, "Refresh file") }
            IconButton(onClick = editor::saveActive, enabled = active.isDirty) { Icon(Icons.Default.Save, "Save") }
            IconButton(onClick = { showCommandPalette = true }) { Icon(Icons.Default.MoreVert, "Commands") }
        }

        if (workspace.breadcrumbs.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                workspace.breadcrumbs.takeLast(5).forEachIndexed { index, crumb ->
                    if (index > 0) {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        crumb.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(active.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }

        EditorChangesPanel(
            active = active,
            repositoryDocuments = gitDiffViewModel.documents,
            expanded = changesExpanded,
            onExpandedChange = { changesExpanded = it },
        )

        if (!richCodeRendering) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Text(
                    "Fast editor mode: syntax highlighting, folding and diagnostics are reduced above 64 KiB or 2,000 lines. Editing remains supported up to 8 MiB, with bounded undo history.",
                    Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        editor.gitSyncMessage?.let { message ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (message.contains("failed", true)) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                ),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        message,
                        Modifier.weight(1f),
                        color = if (message.contains("failed", true)) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = editor::clearGitSyncMessage) { Text("Dismiss") }
                }
            }
        }

        editor.error?.let { message ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        message,
                        Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = editor::dismissError) {
                        Text("Dismiss")
                    }
                }
            }
        }

        if (editor.diagnostics.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Problems · " + editor.diagnostics.size, fontWeight = FontWeight.Bold)
                    editor.diagnostics.take(8).forEach { diagnostic ->
                        TextButton(
                            onClick = {
                                diagnostic.location?.line?.let { line ->
                                    val offset = editor.lineStartOffset(line)
                                    fieldValue = fieldValue.copy(selection = TextRange(offset))
                                }
                            },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            val location = diagnostic.location?.let { ":" + (it.line ?: "") + ":" + (it.column ?: "") }.orEmpty()
                            Text(
                                (diagnostic.code?.let { it + " · " }.orEmpty()) + diagnostic.message + location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val cursor = fieldValue.selection.start.coerceIn(0, fieldValue.text.length)
            val line = fieldValue.text.take(cursor).count { it == '\n' } + 1
            val column = cursor - (fieldValue.text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)) + 1) + 1
            Text("Ln " + line + ", Col " + column, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(language.name.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Divider()
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            val editorModifier = if (settings.settings.wordWrap) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxSize().horizontalScroll(horizontalEditorScroll)
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = {
                    fieldValue = it
                    editor.updateContent(it.text)
                },
                modifier = editorModifier.weight(if (splitUri == null) 1f else 0.5f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = settings.settings.editorFontSize.sp.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                visualTransformation = transformation,
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp)) { inner() }
                },
            )
            val splitTab = splitUri?.let { uri -> editor.tabs.firstOrNull { it.uri == uri } }
            if (splitTab != null) {
                Divider(Modifier.width(1.dp).fillMaxHeight())
                var splitValue by remember(splitTab.uri, splitTab.content) { mutableStateOf(TextFieldValue(splitTab.content)) }
                LaunchedEffect(splitTab.uri, splitTab.content) {
                    if (splitValue.text != splitTab.content) splitValue = TextFieldValue(splitTab.content)
                }
                val splitLanguage = remember(splitTab.name) { EditorLanguage.detect(splitTab.name) }
                val splitKeywordColor = MaterialTheme.colorScheme.primary
                val splitStringColor = MaterialTheme.colorScheme.tertiary
                val splitCommentColor = MaterialTheme.colorScheme.onSurfaceVariant
                val splitNumberColor = MaterialTheme.colorScheme.secondary
                val splitSyntax = remember(
                    splitLanguage,
                    splitKeywordColor,
                    splitStringColor,
                    splitCommentColor,
                    splitNumberColor,
                ) {
                    CodeSyntaxVisualTransformation(
                        language = splitLanguage,
                        keywordColor = splitKeywordColor,
                        stringColor = splitStringColor,
                        commentColor = splitCommentColor,
                        numberColor = splitNumberColor,
                    )
                }
                BasicTextField(
                    value = splitValue,
                    onValueChange = {
                        splitValue = it
                        editor.updateContentFor(splitTab.uri, it.text)
                    },
                    modifier = Modifier.weight(0.5f).fillMaxSize().padding(start = 4.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = settings.settings.editorFontSize.sp.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                    visualTransformation = if (splitValue.text.toByteArray(Charsets.UTF_8).size <= 64 * 1024 && splitValue.text.count { it == '\n' } + 1 <= 2_000) splitSyntax else VisualTransformation.None,
                    decorationBox = { inner -> Box(Modifier.fillMaxSize().padding(vertical = 2.dp)) { inner() } },
                )
            }
        }
    }

    if (showQuickOpen) {
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showQuickOpen = false },
            title = { Text("Quick open") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it.take(160)
                            workspace.search(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("File or folder") },
                    )
                    val results: List<WorkspaceEntry> = if (query.isBlank()) {
                        workspace.entries.take(40)
                    } else {
                        workspace.searchResults.take(40).map { result ->
                            WorkspaceEntry(
                                uri = result.uri,
                                name = result.name,
                                isDirectory = result.isDirectory,
                                sizeBytes = result.sizeBytes,
                            )
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(results, key = { it.uri.toString() }) { entry ->
                            TextButton(
                                onClick = {
                                    if (entry.isDirectory) workspace.openDirectory(entry) else editor.open(entry)
                                    showQuickOpen = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    if (entry.isDirectory) WorkspaceFolderIcon(entry.name) else WorkspaceLanguageIcon(entry.name)
                                    Spacer(Modifier.width(8.dp))
                                    Text(entry.name, Modifier.weight(1f), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQuickOpen = false }) { Text("Close") } },
        )
    }

    if (showCommandPalette) {
        val commands = listOf(
            "Find and replace", "Go to line", "Symbols", "Problems", "Save",
            "Select line", "Duplicate line", "Delete line", "Move line up",
            "Move line down", "Toggle comment", "Split editor", "Git", "Build",
            "Terminal", "Approvals", "Settings", "Overview", "Project map",
            "Dependencies", "Activity", "Local history", "Logs",
        )
        val filteredCommands = commands.filter { command ->
            commandQuery.isBlank() || command.contains(commandQuery.trim(), ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { showCommandPalette = false; commandQuery = "" },
            title = { Text("Command palette") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = commandQuery,
                        onValueChange = { commandQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search commands") },
                    )
                    LazyColumn {
                        items(filteredCommands) { command ->
                            TextButton(
                                onClick = {
                                    showCommandPalette = false
                                    commandQuery = ""
                                    when (command) {
                                        "Find and replace" -> showFind = true
                                        "Go to line" -> showGoToLine = true
                                        "Symbols" -> showSymbols = true
                                        "Problems" -> { /* problems are visible in the editor */ }
                                        "Save" -> editor.saveActive()
                                        "Select line" -> {
                                            editor.selectCurrentLine(fieldValue.selection.start)?.let { edit ->
                                                fieldValue = TextFieldValue(edit.content, TextRange(edit.selectionStart, edit.selectionEnd))
                                            }
                                        }
                                        "Duplicate line" -> {
                                            editor.duplicateCurrentLine(fieldValue.selection.start)?.let { edit ->
                                                fieldValue = TextFieldValue(edit.content, TextRange(edit.cursor))
                                            }
                                        }
                                        "Delete line" -> {
                                            editor.deleteCurrentLine(fieldValue.selection.start)?.let { edit ->
                                                fieldValue = TextFieldValue(edit.content, TextRange(edit.cursor))
                                            }
                                        }
                                        "Move line up" -> {
                                            editor.moveCurrentLine(fieldValue.selection.start, -1)?.let { edit ->
                                                fieldValue = TextFieldValue(edit.content, TextRange(edit.cursor))
                                            }
                                        }
                                        "Move line down" -> {
                                            editor.moveCurrentLine(fieldValue.selection.start, 1)?.let { edit ->
                                                fieldValue = TextFieldValue(edit.content, TextRange(edit.cursor))
                                            }
                                        }
                                        "Toggle comment" -> {
                                            editor.toggleCurrentLineComment(fieldValue.selection.start)?.let { edit ->
                                                fieldValue = TextFieldValue(edit.content, TextRange(edit.cursor))
                                            }
                                        }
                                        "Split editor" -> {
                                            splitUri = if (splitUri == null) editor.tabs.firstOrNull { it.uri != active.uri }?.uri else null
                                        }
                                        "Git" -> onOpenDestination(DevForgeDestination.Git)
                                        "Build" -> onOpenDestination(DevForgeDestination.Build)
                                        "Terminal" -> onOpenDestination(DevForgeDestination.Terminal)
                                        "Approvals" -> onOpenDestination(DevForgeDestination.Approvals)
                                        "Settings" -> onOpenDestination(DevForgeDestination.Settings)
                                        "Overview" -> onOpenIdeTool(IdeTool.OVERVIEW)
                                        "Project map" -> onOpenIdeTool(IdeTool.PROJECT_MAP)
                                        "Dependencies" -> onOpenIdeTool(IdeTool.DEPENDENCIES)
                                        "Activity" -> onOpenIdeTool(IdeTool.ACTIVITY)
                                        "Local history" -> onOpenIdeTool(IdeTool.LOCAL_HISTORY)
                                        "Logs" -> onOpenIdeTool(IdeTool.LOGS)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(command, Modifier.fillMaxWidth()) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCommandPalette = false; commandQuery = "" }) { Text("Close") } },
        )
    }
    if (editor.refreshConfirmationRequired) {
        AlertDialog(
            onDismissRequest = editor::cancelRefreshConfirmation,
            title = { Text("Refresh file?") },
            text = { Text("This file has unsaved changes. Refreshing replaces them with the current version from the active workspace.") },
            confirmButton = { TextButton(onClick = editor::confirmRefresh) { Text("Refresh") } },
            dismissButton = { TextButton(onClick = editor::cancelRefreshConfirmation) { Text("Cancel") } },
        )
    }

    if (showFind) {
        AlertDialog(
            onDismissRequest = { showFind = false },
            title = { Text("Find & replace") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = findQuery,
                        onValueChange = { findQuery = it.take(1_000); replaceMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Find") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = replaceQuery,
                        onValueChange = { replaceQuery = it.take(4_000); replaceMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Replace with") },
                        singleLine = true,
                    )
                    replaceMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val count = editor.replaceAll(findQuery, replaceQuery)
                    replaceMessage = count.toString() + " replacements"
                }) { Text("Replace all") }
            },
            dismissButton = { TextButton(onClick = { showFind = false }) { Text("Done") } },
        )
    }

    if (showGoToLine) {
        AlertDialog(
            onDismissRequest = { showGoToLine = false },
            title = { Text("Go to line") },
            text = {
                OutlinedTextField(
                    value = lineQuery,
                    onValueChange = { lineQuery = it.take(10) },
                    label = { Text("Line number") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val line = lineQuery.toIntOrNull() ?: 1
                    val offset = editor.lineStartOffset(line)
                    fieldValue = fieldValue.copy(selection = TextRange(offset))
                    showGoToLine = false
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { showGoToLine = false }) { Text("Cancel") } },
        )
    }

    if (showSymbols) {
        AlertDialog(
            onDismissRequest = { showSymbols = false },
            title = { Text("Symbols") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(editor.symbolCandidates(), key = { it.name + ":" + it.line }) { symbol ->
                        TextButton(onClick = {
                            val offset = editor.lineStartOffset(symbol.line)
                            fieldValue = fieldValue.copy(selection = TextRange(offset))
                            showSymbols = false
                        }) {
                            Text(
                                symbol.name + " · " + symbol.kind + " · line " + symbol.line,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSymbols = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun SettingsScreen(
    settings: DevForgeSettingsViewModel,
    section: String,
    onSectionChange: (String) -> Unit,
    appSettingsSection: String,
    onAppSettingsSectionChange: (String) -> Unit,
) {
    when (section) {
        "security" -> CredentialSecurityScreen()
        "app" -> DevForgeSettingsScreen(
            viewModel = settings,
            section = appSettingsSection,
            onSectionChange = onAppSettingsSectionChange,
        )
        else -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Keep setup and maintenance in a few clear sections.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                SimpleSettingsTile(
                    title = "Security",
                    subtitle = "Keystore and biometric protection",
                    onClick = { onSectionChange("security") },
                )
            }
            item {
                SimpleSettingsTile(
                    title = "App settings",
                    subtitle = "Build, terminal, privacy, appearance and editor preferences",
                    onClick = { onSectionChange("app") },
                )
            }
        }
    }
}

@Composable
private fun SimpleSettingsTile(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoCard(title: String, message: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(message, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoadingCard(message: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(message)
        }
    }
}

private fun formatBytes(value: Long?): String = when {
    value == null || value < 0L -> "File"
    value < 1024L -> "$value B"
    value < 1024L * 1024L -> "${value / 1024L} KB"
    else -> "${value / (1024L * 1024L)} MB"
}


private const val MAX_DESTINATION_HISTORY = 32


private fun formatEditorSize(estimatedBytes: Long): String = when {
    estimatedBytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", estimatedBytes / (1024f * 1024f))
    estimatedBytes >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", estimatedBytes / 1024f)
    else -> estimatedBytes.toString() + " B"
}
