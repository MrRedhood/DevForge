package com.mrredhood.devforge.core.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class GuideTopic(
    val title: String,
    val summary: String,
    val howTo: List<String>,
    val whereToFind: String,
    val symbols: List<String> = emptyList(),
    val shortcuts: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
)

data class GuideCategory(
    val title: String,
    val summary: String,
    val topics: List<GuideTopic>,
)

private fun t(
    title: String,
    summary: String,
    howTo: List<String>,
    where: String,
    symbols: List<String> = emptyList(),
    shortcuts: List<String> = emptyList(),
    tips: List<String> = emptyList(),
) = GuideTopic(title, summary, howTo, where, symbols, shortcuts, tips)

private val guideCategories = listOf(
    GuideCategory(
        "Editor",
        "Your main coding surface: the project drawer, file tree, tabs, editing, refresh, code navigation, diagnostics, and recovery.",
        listOf(
            t("Editor project drawer","The Editor hamburger opens a left-side, file-tree-first drawer inspired by the useful project-tree interaction in Acode.",
                listOf("Tap the hamburger in the Editor.","Browse the current folder, open folders, and open files.","Use + to create a file or folder.","Use Refresh to reload the visible tree after direct, remote, or AI changes.","Use the GitHub section to load and open repositories after GitHub is connected.","The drawer contains the project tree/workspace flow only; Terminal, plugins, notifications and unrelated tools are not placed inside it."),
                "Editor → hamburger project drawer.",
                listOf("☰ = project drawer","↻ = refresh","+ = create","cloud = GitHub repository")),
            t("Tabs","Keep multiple files open.",
                listOf("Open more files.","Tap a tab to switch.","Tap × to close.","Dirty tabs stay marked until saved."),
                "Editor tab strip.",
                listOf("● = dirty","× = close")),
            t("Refresh file","Reload the active file from the current workspace without closing the tab.",
                listOf("Tap Refresh in the Editor top bar or editor toolbar.","Clean files refresh immediately.","Dirty files show a confirmation before unsaved content is replaced."),
                "Editor top bar or editor toolbar.",
                listOf("↻ = refresh file")),
            t("Split editor","View two open files together.",
                listOf("Open at least two files.","Choose Split.","Use the second pane for another open tab.","Choose Unsplit to return to one pane."),
                "Editor toolbar → Split or Command palette → Split editor.",
                listOf("Split = two panes","Unsplit = one pane")),
            t("Save and auto-save","Write the active file back to its workspace.",
                listOf("Tap Save when a file is dirty.","Enable auto-save in Settings → Editor when desired.","Remote GitHub files are queued for synchronization after save."),
                "Editor save icon; Settings → Editor.",
                listOf("● / Unsaved = not saved","💾 = save")),
            t("Undo and redo","Move backward and forward through recent edits.",
                listOf("Tap ↶ to undo.","Tap ↷ to redo."),
                "Editor toolbar.",
                listOf("↶ = undo","↷ = redo")),
            t("Mobile power editing","Vim-inspired editing actions without requiring a hardware keyboard.",
                listOf(
                    "Open the Command palette.",
                    "Use Select line, Duplicate line, Delete line, Move line up/down, or Toggle comment.",
                    "These actions use the active cursor position and preserve the active document state.",
                ),
                "Editor → Command palette.",
                listOf("Select line = structural selection","Duplicate = repeat line","Move = reorder line","Comment = toggle current line")),
            t("Find and replace","Search and change text in the active file.",
                listOf("Tap Find.","Enter the text.","Enter replacement text.","Choose Replace all."),
                "Editor toolbar → Find.",
                listOf("⌕ = find","Replace all = every match")),
            t("Quick open","Jump to a file or folder quickly.",
                listOf("Tap the search/quick-open icon.","Type a filename or path fragment.","Tap the result."),
                "Editor toolbar → Search.",
                listOf("🔎 = quick open"),
                listOf("Use it when you know even part of a filename.")),
            t("Go to line","Jump directly to a line.",
                listOf("Tap #.","Enter the line.","Tap Go."),
                "Editor toolbar → #.",
                listOf("# = line navigation")),
            t("Select all / line","Use compact selection actions.",
                listOf("Tap All for the full document.","Tap Line for the current line."),
                "Editor toolbar.",
                listOf("All = document","Line = current line")),
            t("Symbols","Jump to detected classes, functions, interfaces, and similar code symbols.",
                listOf("Tap Symbols.","Choose a symbol.","The cursor moves to its reported line."),
                "Editor toolbar → Symbols.",
                listOf("class / fun / interface entries = code symbols")),
            t("Folding","Collapse code blocks for a cleaner view.",
                listOf("Use the fold control when foldable ranges exist.","Use unfold to expand them."),
                "Editor toolbar.",
                listOf("⌄ = fold","⌃ = unfold")),
            t("Syntax highlighting","Color common code constructs based on the filename language.",
                listOf("Open a supported source file.","DevForge detects the language from the name."),
                "Automatic inside editor.",
                listOf("language chip = detected language")),
            t("Supported programming languages","Filename-based language detection and syntax highlighting cover the editor's supported language set.",
                listOf(
                    "Kotlin (.kt, .kts) = Android/JVM applications and scripts.",
                    "Java (.java) = JVM and Android source.",
                    "JavaScript (.js, .jsx) = browser/Node-style scripting.",
                    "TypeScript (.ts, .tsx) = typed JavaScript applications.",
                    "Python (.py) = scripting and AI-driven action.",
                    "Go (.go) = compiled services and tooling.",
                    "Rust (.rs) = systems and performance-oriented software.",
                    "C/C++ (.c, .h, .cpp, .cc, .cxx, .hpp) = native code.",
                    "JSON (.json) = structured data/configuration.",
                    "XML (.xml, .xsd) = Android/configuration markup.",
                    "HTML (.html, .htm) = web markup.",
                    "CSS (.css) = web styling.",
                    "SQL (.sql) = database queries/schema.",
                    "YAML (.yaml, .yml) = configuration and CI files.",
                    "Shell (.sh, .bash, Dockerfile) = command scripts/container instructions.",
                    "Markdown (.md, .markdown) = documentation.",
                    "Dart (.dart) = Dart/Flutter source.",
                    "C# (.cs) = .NET applications and services.",
                    "Swift (.swift) = Apple/platform application source.",
                    "PHP (.php) = server-side web applications.",
                    "Ruby (.rb) = Ruby applications and scripts.",
                    "Lua (.lua) = embedded and scripting workloads.",
                    "Scala (.scala, .sc) = JVM/Scala applications.",
                    "Groovy (.groovy, .gradle) = JVM and build scripts.",
                    "R (.r, R) = statistical/data analysis scripts.",
                    "Perl (.pl, .pm) = scripting and text-processing workloads.",
                    "Haskell (.hs, .lhs) = functional applications.",
                    "Elixir (.ex, .exs) = BEAM/Elixir applications.",
                    "MATLAB (.m, .mat) = numerical/scientific scripts.",
                    "Plain text = unrecognized extensions."
                ),
                "Open a recognized file; the detected language appears in the editor toolbar/status.",
                listOf("language chip = detected language","Plain = no specialized highlighting"),
                tips = listOf("Advanced highlighting is bounded for large files so editing remains available.")),
            t("Word wrap and invisibles","Control long lines and whitespace visibility.",
                listOf("Open Settings → Editor.","Enable Word wrap for wrapped lines.","Enable Show invisibles when whitespace matters."),
                "Settings → Editor.",
                listOf("wrap = visual wrapping","invisibles = whitespace view")),
            t("AI Mission","AI Chat presents project work as one mission: understand → plan → inspect → execute → verify → review.",
                listOf(
                    "Open the floating AI Chat control.",
                    "Send a normal coding request; DevForge creates the mission automatically.",
                    "Expand AI Mission to see activity progress and the verification receipt.",
                    "Internal tool runtime details remain behind the workflow surface.",
                ),
                "Floating AI Chat → AI Mission card.",
                listOf("mission = current workflow","✓ = verification","timeline = activity")),
            t("AI project rules and skills","Every AI Mission receives DevForge engineering policy and a matched skill profile, while project-specific rules can live in .devforge/rules/ and .devforge/skills/.",
                listOf(
                    "Create or maintain project guidance under .devforge/rules/ when a workspace needs persistent engineering constraints.",
                    "Use natural language requests such as Android, security, testing, performance, UI or GitHub tasks to trigger the corresponding built-in skill guidance.",
                    "The AI treats project rules as higher-priority workspace constraints and surfaces conflicts instead of silently overriding them.",
                ),
                "Workspace .devforge/ plus AI Mission plan activity.",
                listOf("rules = project constraints","skills = reusable expertise","plan = selected skill profile")),
            t("AI Activity Timeline","Search, reads, writes, commands, approvals and verification are normalized into one compact timeline.",
                listOf(
                    "Open AI Chat during a coding task.",
                    "Expand AI Mission.",
                    "Review the live execution history.",
                ),
                "AI Mission → Show activity.",
                listOf("search = inspect","write = mutation","verify = final checks")),
            t("Mission change summary","AI Mission Review summarizes writes, deletes, commands and observed paths before you inspect the detailed activity timeline.",
                listOf(
                    "Wait for the mission to reach Review.",
                    "Read the compact change summary.",
                    "Expand activity to inspect individual operations and paths.",
                ),
                "AI Mission card → Review.",
                listOf("writes = files changed","deletes = paths removed","paths = observed workspace paths")),
            t("Verification receipt","AI completion produces an explicit verification result instead of relying on a plain done message.",
                listOf(
                    "Wait for the mission to enter Review.",
                    "Read passed, failed and skipped checks.",
                    "Build Center, test and lint receipts remain authoritative when those operations were requested.",
                ),
                "AI Mission → Verification.",
                listOf("passed = observed success","skipped = not executed","failed = verification problem")),
            t("Build and CI reliability","Repository settings, workspace publishing and AI verification use typed models so compiler failures are caught before a release receipt is considered valid.",
                listOf(
                    "Use Build Center for authoritative build, test and lint receipts.",
                    "Repository edit screens preserve merge settings returned by GitHub.",
                    "Local workspace publishing reports a typed success or failure instead of a raw operation result.",
                ),
                "Build Center and GitHub repository settings.",
                listOf("build receipt = authoritative","publish = typed result","repository settings = persisted")),
            t("AI location","AI is intentionally available through Chat rather than inside the file editor.",
                listOf("Open the floating AI Chat control for questions, workspace context, tool calls, code changes, or execution requests.","The file editor stays focused on direct editing, review and navigation."),
                "Floating AI Chat button.",
                listOf("✦ = AI Chat","Editor = code")),
            t("Problems","See diagnostics for the active file.",
                listOf("Read the Problems card.","Tap a problem when a location is available.","Use the dedicated IDE Problems screen for the full list."),
                "Editor Problems card; Editor command palette → Problems.",
                listOf("error = high severity","warning = caution")),
            t("Breadcrumbs","See the path from the workspace root to the open file.",
                listOf("Open a nested file.","Read the breadcrumb row above the editor."),
                "Above the editor.",
                listOf("› = deeper path")),
            t("Local history","Inspect and restore bounded local content snapshots.",
                listOf("Open Editor command palette → Local history.","Pick a snapshot.","Tap Restore."),
                "Hamburger → IDE → Local history.",
                listOf("snapshot = saved checkpoint","Restore = put snapshot content into file")),
            t("Command palette","One list for common editor, IDE, Git, Build, Terminal, approval, and settings actions.",
                listOf("Tap ⋮ in the editor toolbar.","Choose an action."),
                "Editor toolbar → ⋮.",
                listOf("⋮ = command palette"),
                listOf("Use it when you remember what you want but not where the control lives.")),
            t("Large-file safeguard","Keep editing available while reducing expensive features for very large files.",
                listOf("Open a large file normally.","DevForge automatically reduces expensive syntax/folding work above the supported advanced threshold."),
                "Editor status and safeguard message.",
                listOf("large-file safeguard = lighter processing")),
            t("Editor shortcuts","Fast UI paths that replace long navigation chains.",
                listOf("Use Quick open for file jumps.","Use Go to line for position jumps.","Use Command palette for actions.","Use toolbar icons for save/undo/redo."),
                "Editor toolbar.",
                listOf("🔎 Quick open","# Go to line","⋮ Commands","💾 Save")),
        ),
    ),
    GuideCategory(
        "AI Chat",
        "The full assistant surface for questions, code context, files, models, sessions, streaming, and generated output.",
        listOf(
            t("AI Chat","Open the single main AI as a full-screen workspace.",
                listOf("Tap the chat + star floating button.","AI Chat opens full-screen.","Use Back to return to the current DevForge surface."),
                "Bottom-right floating AI button.",
                listOf("chat bubble + ✦ = AI Chat")),
            t("Workspace context","The selected workspace is the project context for workspace-aware AI actions.",
                listOf("Select the workspace first.","Open Chat.","Keep the workspace selected until the project changes."),
                "Editor hamburger → workspace selection.",
                listOf("✓ = current workspace")),
            t("File context and mentions","Give AI focused file context.",
                listOf("Open Chat.","Use its file/context controls.","Review attached context before sending."),
                "Chat composer/context controls.",
                listOf("@file style mention = file reference")),
            t("Providers and models","Choose the service and model that will answer a request.",
                listOf("Open Settings → AI.","Configure the provider.","Choose the model in Chat where available."),
                "Settings → AI; Chat model controls."),
            t("Streaming","Responses can appear progressively.",
                listOf("Send a prompt.","Read the response as it streams.","Tap Stop to cancel the active generation immediately."),
                "Chat composer and response.",
                listOf("streaming = incremental output","Stop = cancels the active request")),
            t("AI tool approvals","A tool approval pauses the current Chat tool call and resumes that exact call after you approve it.",
                listOf("Send a request that needs a protected file operation.","Open More → Approvals.","Approve or dismiss the exact action.","Approve lets the waiting Chat tool loop continue; dismiss stops that action."),
                "More → Approvals.",
                listOf("pending = waiting for you","approved = exact request may execute","dismissed = action stops")),
            t("Markdown","Structured AI answers can display headings, lists, inline code, and code blocks.",
                listOf("Ask for a structured explanation or code sample.","Read the formatted response directly."),
                "Chat response area."),
            t("Chat sessions","Separate unrelated conversations.",
                listOf("Open session controls.","Create or switch sessions as the project/task changes."),
                "Chat session controls."),
            t("Slash commands","Run supported chat commands from the composer.",
                listOf("Type /.","Choose the command.","Review the result."),
                "Chat composer.",
                listOf("/ = command prefix")),
            t("Artifacts","Fetch supported generated artifacts.",
                listOf("Ask for an artifact-producing result.","Use the artifact action in the response when offered."),
                "Chat response actions.",
                listOf("Artifact = generated output file")),
            t("AI safety","AI output is treated as untrusted input.",
                listOf("Review code proposals.","Approve side-effecting actions.","Keep secrets in protected credential settings."),
                "AI Chat and AI Tools.",
                listOf("approval = explicit permission","proposal = review before apply")),
            t("Cloud-only AI","DevForge uses configured cloud AI providers; it does not expose an on-device/local model runtime.",
                listOf("Open More → AI & GitHub.","Choose AI Models.","Configure a supported cloud provider and choose a model from its catalog."),
                "More → AI & GitHub → AI Models.",
                listOf("local workspace = supported","local model execution = not a DevForge feature")),

            t("AI factuality and live state","DevForge uses live DevForge state and authoritative tool results for current project questions instead of guessing.",
                listOf(
                    "Tool-count and tool-list questions are answered from the live registered tool runtime and current tool settings, not from model memory.",
                    "The live answer distinguishes registered, user-configurable, enabled, disabled, internal and currently-usable tools.",
                    "For current workspace, build, Git, settings and codebase questions, AI is instructed to verify state through authoritative tools before answering.",
                    "When a current-state question has no verified evidence, Chat returns an explicit verification-required response instead of inventing an answer.",
                    "Tool availability is never described as successful execution. Registered, enabled, called, completed and verified are separate states.",
                ),
                "AI Chat and More → AI Tools.",
                listOf("live registry = source of truth","no evidence = no guess","enabled ≠ successfully executed")),
            t("AI Tools","Manage the tools available to the single main DevForge AI.",
                listOf(
                    "Open More → AI Tools.",
                    "Enable the tools you want available to AI.",
                    "Ask AI to search, scrape, inspect files, calculate, or perform workspace changes.",
                    "During execution, Chat shows compact expandable task labels. Tap the arrow to inspect the tool name and result detail.",
                    "When a tool requires approval, the same Chat turn waits for the exact approval and resumes automatically after approval.",
                    "Stopping generation cancels the active provider request and rejects any pending Chat-only approval.",
                     "Chat Stop cancels the active Chat generation. There is one main AI execution path; no worker controls exist.",
                     "Persistent capability grants use an R3 approval-bypass ceiling for every grantable tool, bounded by the selected workspace/path scope.",
                     "GitHub-backed AI file and folder mutations commit directly to the configured branch after execution.",
                    "Folder creation uses .gitkeep because Git tracks files, not empty directories; folder deletion removes all tracked files under that folder.",
                    "The tool loop reuses the configured AI gateway, so existing provider attachment handling remains available.",
                    "Workspace mutations remain bounded by DevForge policy, path scopes, leases, preconditions and approval rules.",
                ),
                "More → AI Tools.",
                listOf("on = available to all AI models","off = blocked at the execution gateway","Delete path = off by default")),
        ),
    ),
    GuideCategory(
        "Editor performance",
        "Keep the code editor responsive on mobile by automatically reducing expensive rendering work for large files.",
        listOf(
            t("Fast editor mode","For files above 64 KiB or 2,000 lines, DevForge reduces syntax highlighting and folding work and debounces diagnostics off the UI thread.",
                listOf("Open a large source file.","Continue editing normally.","Diagnostics update after a short pause instead of running on every keystroke."),
                "Editor → large-file notice.",
                listOf("rich rendering = ≤64 KiB and ≤2,000 lines","fast mode = larger files","maximum supported content = 8 MiB")),
            t("Editor diagnostics","Syntax diagnostics are calculated on a background dispatcher and debounced while typing.",
                listOf("Edit code quickly.","Keep typing without waiting for diagnostics.","Pause briefly to let the latest diagnostics settle."),
                "Editor → diagnostics panel."),
        ),
    ),
    GuideCategory(
        "Extensions",
        "Install compatible Acode ZIP and VS Code VSIX packages without pretending to provide unsupported runtimes.",
        listOf(
            t("Extension manager","Discover live Acode and VS Code extensions and manage the packages installed in DevForge.",
                listOf(
                    "Open More → Extensions.",
                    "Use Discover to search the live Acode plugin registry and VS Code Marketplace.",
                    "Filter by All, Acode, or VS Code.",
                    "Install supported free catalog packages directly; DevForge downloads the package and validates its manifest/runtime before installation.",
                    "Open Installed to disable, activate/verify, apply icon themes, and uninstall packages.",
                    "You can also install a local ZIP / VSIX package directly when needed.",
                ),
                "More → Extensions.",
                listOf("Discover = live catalogs","Installed = local management","off = disabled extension")),
        ),
    ),
    GuideCategory(
        "Workspaces",
        "Manage many local folders and GitHub repositories and control the current project context.",
        listOf(
            t("Create local workspace","Add another local project without replacing existing workspace records.",
                listOf("Open the editor hamburger.","Use the local workspace picker/add action.","Name the workspace."),
                "Editor hamburger → local workspace tools."),
            t("Switch workspace","Change the current project.",
                listOf("Open the hamburger.","Tap a workspace.","It becomes the current editor and AI context."),
                "Editor hamburger → workspace list.",
                listOf("✓ = selected")),
            t("Persistent state","Workspace selection and related persisted state survive normal app restarts when storage permissions remain valid.",
                listOf("Switch normally.","Reopen DevForge later.","Choose the workspace again when needed."),
                "Workspace system."),
            t("Workspace tree","Browse files and folders.",
                listOf("Choose a workspace.","Tap folders to enter them.","Tap files to open them."),
                "Editor hamburger → file tree.",
                listOf("folder = directory","file = document")),
            t("Create file/folder","Create project items.",
                listOf("Open Files.","Choose create file or create folder.","Enter a name.","Confirm."),
                "Files screen.",
                listOf("+ = create/add")),
            t("Rename","Change an item name.",
                listOf("Open Files.","Tap rename.","Enter the new name.","Confirm."),
                "Files screen → item actions.",
                listOf("pencil = rename")),
            t("Delete","Remove an item from the workspace.",
                listOf("Open Files.","Tap delete.","Confirm."),
                "Files screen → item actions.",
                listOf("trash = delete"),
                listOf("Repository metadata such as .git is guarded.")),
            t("Workspace search","Search filenames and paths.",
                listOf("Open workspace search.","Type a query.","Open a matching result."),
                "Workspace search; Quick open for fast editor navigation.",
                listOf("🔎 = search")),
            t("AI current workspace","Workspace selection also selects the project context for workspace-aware AI.",
                listOf("Select the project once.","Open Chat or AI.","Keep working until another workspace is selected."),
                "Editor hamburger → workspace."),
            t("Build with AI","Create a real local project first and then hand the project goal to plan-first AI without requiring GitHub.",
                listOf("From an empty Editor, tap Build with AI.","Enter a project name and describe the goal.","Choose the device folder where the project should live.","DevForge creates and activates the local workspace and opens AI Chat with a prepared plan-first request.","GitHub remains optional."),
                "Editor home → Build with AI.",
                listOf("✦ = AI Chat","folder = local project")),
            t("Save a GitHub repository on device","Clone a selected GitHub repository into a device folder and work on it as a normal local workspace.",
                listOf("Open the GitHub repository picker.","Select a repository.","Tap Save on device.","Choose the destination folder.","DevForge creates a local workspace and remembers the GitHub repository link."),
                "Editor → hamburger → GitHub → repository picker.",
                listOf("cloud = GitHub","folder = local copy")),
            t("Upload a local project to GitHub","Publish a local workspace snapshot to an existing repository without creating or uploading a ZIP manually.",
                listOf("Open the hamburger for a local workspace.","Tap Upload to GitHub.","Select the target repository.","Confirm the upload.","DevForge creates a normal Git commit and pushes without force-push."),
                "Editor → hamburger → local project → Upload to GitHub.",
                listOf("↑ = publish","linked = GitHub destination")),
        ),
    ),
    GuideCategory(
        "GitHub",
        "Connect a GitHub account, choose repositories, browse remote trees, edit files, and synchronize changes.",
        listOf(
            t("Connect GitHub","Configure a protected GitHub credential.",
                listOf("Open Settings → GitHub.","Enter credentials using the protected flow.","Validate the connection."),
                "Settings → GitHub.",
                tips = listOf("Do not paste tokens into ordinary Chat messages or source files.")),
            t("Repository picker","List repositories visible to the connected GitHub account.",
                listOf("Open editor hamburger.","Expand GitHub.","Choose a repository."),
                "Editor hamburger → GitHub.",
                listOf("cloud = remote repository")),
            t("Repository workflow management","Inspect and remove GitHub Actions workflow definition files from the repository screen.",
                listOf("Open a connected repository.","Review Workflows.","Use the trash action for one workflow or select multiple workflow checkboxes and tap Delete.","Confirm the operation.","DevForge commits deletion of the selected .github/workflows/*.yml or *.yaml files."),
                "GitHub repository screen → Workflows.",
                tips = listOf("This removes workflow definitions; existing historical workflow runs are not deleted.")),
            t("Delete GitHub repository","Permanently delete any repository shown by the connected GitHub account when the GitHub account has permission to delete it.",
                listOf("Open Build Center → GitHub repository.","Use the trash action on a repository row, or open a repository and tap Delete repository.","Type the exact repository name.","Confirm Delete permanently.","After a successful deletion, DevForge removes matching GitHub-backed workspace records, pending GitHub edits, terminal sessions and per-repository build-output settings.","If the deleted repository was the active workspace, the editor state is invalidated so stale remote tabs cannot remain open."),
                "Build Center → GitHub repository → repository list or repository details.",
                listOf("exact name = required confirmation","delete = permanent remote operation","GitHub still enforces repository-owner/admin permissions")),
            t("Open remote worktree","Treat a GitHub repository as a DevForge workspace.",
                listOf("Choose a repository.","DevForge activates or creates the matching remote workspace.","Browse its tree."),
                "Editor hamburger → GitHub → repository."),
            t("Remote file editing","Edit a GitHub-backed file in the same editor.",
                listOf("Open the remote file.","Edit it.","Save.","Review queued remote changes."),
                "GitHub workspace → editor.",
                listOf("● = unsaved","💾 = save/queue")),
            t("Pending GitHub changes","Review remote changes before committing.",
                listOf("Save remote files.","Open pending changes.","Review changed paths.","Commit or discard as appropriate."),
                "GitHub workspace Git controls.",
                listOf("+ = added/updated","− = deleted")),
            t("Commit and push","Create a remote commit from queued changes.",
                listOf("Open pending changes.","Select target branch.","Enter commit message.","Commit."),
                "GitHub workspace → pending changes → commit.",
                listOf("commit = saved remote change set")),
            t("Pull/refresh","Refresh the latest remote tree.",
                listOf("Make sure pending remote changes are handled first.","Refresh/pull.","Continue from the updated tree."),
                "GitHub workspace controls.",
                tips = listOf("DevForge blocks unsafe pulls when pending changes would be overwritten.")),
            t("Repository branches","Keep the remote workspace tied to its selected branch.",
                listOf("Select/configure the branch in the GitHub workflow or repository configuration.","Confirm the branch before edits."),
                "GitHub repository/workspace configuration."),
            t("Edit repository metadata","Change an existing repository's name, description, homepage, visibility, default branch and supported GitHub repository settings from DevForge.",
                listOf("Open More → GitHub repository management.","Select a repository.","Tap Edit repository.","Review the metadata and merge settings.","Save.","Repository renames also migrate DevForge's stored workspace/build/link mappings."),
                "GitHub repository management → repository → Edit repository.",
                listOf("edit = repository metadata")),
        ),
    ),
    GuideCategory(
        "Live GitHub Actions",
        "Monitor current GitHub Actions runs in DevForge with frequent refresh and in-app job logs.",
        listOf(
            t("Live workflow list", "See active and recent workflows across connected repositories.",
                listOf("Open More → Live GitHub Actions.","Workflow state refreshes from GitHub every 5 seconds while open.","Tap a run once to expand its job logs; the expanded run stays stable without restarting the log loader on every status poll.","Use the toolbar Refresh action for an immediate status refresh."),
                "More → Live GitHub Actions.",
                listOf("5s = automatic workflow status refresh","↻ = manual refresh","expanded run = stable log view")),
            t("Running animation", "Running workflows use an animated state indicator and progress bar.",
                listOf("Queued/requested/pending/waiting and in-progress runs are shown as active.","Completed runs show their GitHub conclusion."),
                "Live GitHub Actions workflow cards.",
                listOf("pulse = active workflow","progress bar = live/active state")),
            t("Live job logs", "Open a workflow and inspect its available jobs and latest fetched logs inside DevForge.",
                listOf("Tap a workflow card.","The card expands and fetches job logs directly from GitHub.","Status polling updates the run state without restarting or clearing the selected run's expanded/log state."),
                "Live GitHub Actions → workflow card."),
        ),
    ),
    GuideCategory(
        "Git",
        "Inspect repository state, diffs, history, reversals, and synchronization.",
        listOf(
            t("Git status","See repository state.",
                listOf("Open Git.","Inspect changed files and current repository state."),
                "Git screen."),
            t("Diffs","Compare file versions at line level.",
                listOf("Open Diffs.","Expand a file.","Read added, removed, and context lines."),
                "Git → Diffs; editor change panel.",
                listOf("+ = added","− = removed","context = unchanged")),
            t("Commit history","Inspect previous commits.",
                listOf("Open Git.","Open Commit history.","Select a commit."),
                "Git → Commit history.",
                listOf("commit = historical change set")),
            t("Commit details","Understand what a selected commit changed.",
                listOf("Tap a commit.","Review affected files and change information.","Use the available reverse action only after reviewing the target."),
                "Git → Commit history → commit details."),
            t("Reverse a commit","Create a new change that reverses a previous commit.",
                listOf("Select the commit.","Review the proposed reversal.","Confirm the reverse operation.","Push the resulting new change if desired."),
                "Commit details → reverse.",
                listOf("reverse ≠ delete history")),
            t("Sync changes","Push local Git changes when a remote is configured.",
                listOf("Review diff.","Use the Git synchronization action.","Check the remote result."),
                "Git repository controls."),
        ),
    ),
    GuideCategory(
        "Build outputs & reports",
        "Choose per-repository artifact/report behavior, see outputs inside DevForge, and download them directly to the Android device.",
        listOf(
            t("Repository build settings", "Configure outputs when choosing a GitHub repository.",
                listOf("Open Build Center and choose a repository.","Use the Build outputs & reports switches on the selected repository.","Settings are stored per repository on the device."),
                "Build Center → Choose repository.",
                listOf("on = enabled for the next DevForge workflow dispatch","off = disabled for that repository")),
            t("Build target", "Choose and persist the APK/AAB target for the selected repository.",
                listOf("Open the GitHub repository picker from Build Center.","Select Debug APK, Release APK, or Release AAB under Build outputs & reports.","The selected target is remembered for that repository and becomes the target dispatched by Build Center."),
                "Build Center → Choose repository → Build outputs & reports.",
                listOf("Debug APK = debug_artifact","Release APK = signed release APK","Release AAB = signed App Bundle"),
                tips = listOf("Changing the target no longer silently falls back to Debug APK.")),
            t("Build selected APK/AAB", "Control whether the selected build output is actually produced by the DevForge workflow.",
                listOf("Enable Build selected APK/AAB when you want the selected Debug APK, Release APK, or Release AAB to be built.","Disable it when you want reports without the selected package."),
                "Repository selection → Build outputs & reports."),
            t("Upload build artifact", "Control whether a successfully built APK/AAB is uploaded to GitHub Actions artifacts.",
                listOf("Enable Upload build artifact to make the build file available in Build Center.","Disable it to keep the workflow from publishing the build output as an Actions artifact."),
                "Repository selection → Build outputs & reports."),
            t("Lint report", "Generate Android lint results and expose the report in DevForge.",
                listOf("Enable Lint report.","The workflow runs Android lint and uploads HTML/XML results.","Build Center shows the report artifact for direct download."),
                "Repository selection → Build outputs & reports."),
            t("Unit-test report", "Upload Gradle unit-test result and report files.",
                listOf("Enable Unit-test report.","The workflow executes the debug unit tests and uploads the generated XML/report files."),
                "Repository selection → Build outputs & reports."),
            t("Dependency report", "Generate Gradle dependency information and make it downloadable.",
                listOf("Enable Dependency report.","The workflow generates a dependency report and uploads it as a GitHub Actions artifact.","Build Center exposes it with the other reports."),
                "Repository selection → Build outputs & reports."),
            t("Download artifact or report", "Pull the files into Android directly from DevForge.",
                listOf("Open the completed build in Build Center.","Find the build file or report under Build artifacts & reports.","Tap Download to device.","DevForge downloads the GitHub artifact ZIP, extracts its contents, and saves the files under Downloads/DevForge/<artifact>."),
                "Build Center → Build artifacts & reports.",
                listOf("APK = Android application package","AAB = Android App Bundle","report = lint/test/dependency output")),
            t("Apply workflow configuration", "Make sure the selected repository has the DevForge workflow contract that accepts artifact/report preferences.",
                listOf("Choose the repository.","Use the workflow setup controls in Build Center when needed.","DevForge commits the canonical Android CI workflow with the artifact/report inputs."),
                "Build Center → Workflow setup."),
        ),
    ),
    GuideCategory(
        "Build & CI",
        "Build Android artifacts remotely, monitor workflows, inspect logs, and retrieve artifacts.",
        listOf(
            t("Build Center","Control remote Android builds.",
                listOf("Open Build from the bottom navigation.","Select repository/workflow.","Choose target.","Start CI."),
                "Bottom navigation → Build."),
            t("Debug APK","Development/test APK.",
                listOf("Choose Debug APK.","Start CI.","Wait for the artifact."),
                "Build Center → target selection.",
                listOf("Debug APK = development build")),
            t("Release APK","Signed release APK.",
                listOf("Choose Release APK in the repository build settings or Build Center.","Confirm release signing configuration.","Tap Build Release APK.","Verify the final signed artifact/signature."),
                "Build Center → Build target → Release APK.",
                listOf("Release APK = signed APK"),
                tips = listOf("Release builds fail closed when required signing secrets are not configured.")),
            t("Release AAB","Signed Android App Bundle for distribution workflows.",
                listOf("Choose Release AAB in the repository build settings or Build Center.","Confirm release signing configuration.","Tap Build Release AAB.","Download the resulting signed AAB."),
                "Build Center → Build target → Release AAB.",
                listOf("AAB = Android App Bundle"),
                tips = listOf("Release signing is validated before Gradle builds the bundle.")),
            t("CI logs","Read exact workflow errors and diagnose UI-test workflow failures.",
                listOf("Open the active/finished run.","Open logs.","Find the first concrete error line.","The Android UI workflow uses a fixed emulator profile/resources, explicit boot timeout, forced AVD recreation, and no manual ADB reconnect loop to reduce emulator startup races.","UI tests follow the current Editor-first bottom-navigation workflow and use explicit waits for asynchronous navigation controls."),
                "Build Center → run details/logs.",
                tips = listOf("UI workflow startup is isolated from the manual ADB reconnect race.")),
            t("Artifacts","Retrieve the exact output selected for the workflow run.",
                listOf("Open a finished run.","Open Build artifacts & reports.","Select the APK/AAB/report you need.","Release APK runs publish the verified signed app-release.apk; release validation also publishes the signed APK/AAB pair."),
                "Build Center → run → Build artifacts & reports.",
                listOf("artifact = CI output file","signed release APK = app-release.apk")),
            t("Build history/receipts","Keep recent run records for later review.",
                listOf("Open build history or IDE → Activity.","Select a previous recorded run."),
                "Build history; IDE → Activity."),
            t("Release signing","Use protected signing material only inside CI.",
                listOf("Configure GitHub Actions signing secrets.","Start a release build.","CI prepares and validates the temporary keystore.","Signature verification runs before upload."),
                "GitHub Actions release configuration.",
                listOf("keystore = signing identity","signature check = artifact verification")),
            t("Cancel a build","Stop an active remote workflow using the protected cancellation flow.",
                listOf("Open the active run.","Request cancellation.","Approve the cancellation request when prompted."),
                "Build Center → active run."),
        ),
    ),
    GuideCategory(
        "Terminal",
        "Run commands inside the selected development workspace.",
        listOf(
            t("Terminal","Run shell commands against the current workspace environment.",
                listOf("Select a workspace first.","Open Terminal.","Enter a command.","Review output.","Direct user terminal input supports normal shell composition where the Android shell permits it.","AI terminal execution uses a separate bounded command allowlist."),
                "More → Terminal.",
                listOf("> = command","output = command result","AI command = policy-gated terminal tool")),
            t("AI terminal commands","Allow AI to execute a bounded workspace command and return its result to Chat.",
                listOf("Open More → AI Tools.","Enable Run terminal command.","AI uses run_command with a bounded command, optional workingDirectory and timeoutMs.","Review the approval request before execution when policy requires it."),
                "More → AI Tools; AI Chat and AI.",
                listOf("R2 = terminal actions are approval-gated by default","run_command = command + bounded result")),
            t("AI Git log","Let AI inspect recent repository commits without dumping the repository into context.",
                listOf("Open More → AI Tools.","Enable Read Git log.","AI can call get_git_log to inspect recent local Git commits or the active GitHub-backed repository history."),
                "More → AI Tools; AI Chat and AI.",
                listOf("read-only = no repository mutation","limit = bounded recent commit count")),
            t("Workspace boundary","Terminal operations use the selected workspace boundary.",
                listOf("Choose the correct workspace.","Open Terminal.","Run the command from that project context."),
                "Workspace selection + Terminal."),
            t("Output limits","Keep large command results manageable.",
                listOf("Prefer focused commands for huge output.","Use filtering or smaller scopes when diagnostics are hard to read."),
                "Terminal output."),
        ),
    ),
    GuideCategory(
        "Approvals",
        "The common permission system for builds, Git, AI-driven actions, and other side effects.",
        listOf(
            t("In-app approval card","Review side-effecting actions from a compact card near the bottom-right of DevForge.",
                listOf("Wait for the approval card to appear above the bottom-right navigation area.","Read the exact action summary.","Tap Approve or Reject."),
                "Bottom-right in-app approval surface.",
                listOf("Approve = execute","Reject = refuse")),
            t("15-second window","Approvals have a short in-app countdown.",
                listOf("The countdown starts when the approval is created.","Approve or reject before 15 seconds expires.","Expired approvals are automatically removed and cannot execute."),
                "Bottom-right in-app approval card.",
                listOf("15s = approval lifetime")),
            t("Approval Center","Keep all unresolved approvals in one place when the card is not convenient.",
                listOf("Open More → Approvals.","Review pending actions.","Approve or reject."),
                "More → Approvals.",
                tips = listOf("Approval requests are not delivered through the Android notification shade.")),

            t("Persistent capability grants","Reuse bounded permission for supported capabilities.",
                listOf("Choose a grantable capability.","Set an optional path scope.","Grant it only when understood.","Revoke later if needed."),
                "Approval Center → persistent grants.",
                listOf("grant = reusable permission","path scope = where it applies")),
            t("Audit history","Review approval/execution/expiry/grant events.",
                listOf("Open Approval Center.","Scroll to Audit history."),
                "Approval Center → Audit history."),
        ),
    ),
    GuideCategory(
        "Settings",
        "Configure GitHub, build, terminal, privacy, appearance, and editor behavior.",
        listOf(
            t("AI & GitHub hub","A single management surface for GitHub, AI Models, and AI Tools.",
                listOf(
                    "Open More.",
                    "Choose AI & GitHub.",
                    "Use the separate GitHub, AI Models, or AI Tools surface.",
                ),
                "More → AI & GitHub.",
                listOf("AI & GitHub management = shared management hub")),
            t("GitHub management","Account and repository integration settings.",
                listOf("Open More → AI & GitHub.","Choose GitHub.","Validate the connection and use repository/workspace tools."),
                "More → AI & GitHub → GitHub."),
            t("Build settings","Workflow, repository, branch, and build defaults.",
                listOf("Open Settings → Build.","Review repository/workflow defaults.","Save changes."),
                "Settings → Build."),
            t("Terminal settings","Terminal behavior exposed by DevForge.",
                listOf("Open Settings → Terminal.","Adjust supported options."),
                "Settings → Terminal."),
            t("Privacy settings","Privacy-oriented app choices.",
                listOf("Open Settings → Privacy.","Review enabled behavior before connecting services."),
                "Settings → Privacy."),
            t("Appearance","Theme and density.",
                listOf("Open Settings → Appearance.","Choose theme/density."),
                "Settings → Appearance.",
                listOf("theme = light/dark/system","density = compact/expanded")),
            t("Editor settings","Font size, word wrap, invisibles, auto-save, and editor preferences.",
                listOf("Open Settings → Editor.","Change a setting.","Return to the editor."),
                "Settings → Editor."),
        ),
    ),
    GuideCategory(
        "Security",
        "Protect credentials, workspace boundaries, approvals, and AI-driven side effects.",
        listOf(
            t("Protected credentials","Keep provider/GitHub secrets out of normal UI state and logs.",
                listOf("Enter credentials through dedicated settings screens.","Never paste secrets into ordinary Chat or source files."),
                "More → AI & GitHub → AI Models and Settings → Security."),
            t("Approval binding","Approvals correspond to the exact action state they were created for.",
                listOf("Read the exact summary.","Approve only the request you intended to allow."),
                "Approval system.",
                listOf("approval = explicit authorization")),
            t("Workspace boundary","File tools are constrained to the selected workspace.",
                listOf("Select the correct workspace before terminal/editor operations.","Switch workspace when changing projects."),
                "Workspace selector."),
            t("Untrusted AI output","AI output must be reviewed before side effects.",
                listOf("Review code proposals.","Approve destructive/build/Git side effects.","Keep protected secrets in credential storage."),
                "AI Chat and AI."),
            t("Repository metadata protection","Protect .git and similar project metadata from casual file operations.",
                listOf("Use Git features for repository operations.","Use normal files actions for source/project files."),
                "Files and Git."),
        ),
    ),
    GuideCategory(
        "Diagnostics & IDE",
        "Developer tools that inspect project health without requiring a separate desktop IDE.",
        listOf(
            t("Overview","Workspace and health snapshot.",
                listOf("Open hamburger.","Tap IDE → Overview."),
                "Editor command palette → Overview."),
            t("Problems","Dedicated list of editor diagnostics.",
                listOf("Open IDE → Problems.","Read severity/location.","Return to the editor file."),
                "Editor command palette → Problems.",
                listOf("error = high severity","warning = caution")),
            t("Project map","Browse indexed symbols grouped by file/path.",
                listOf("Index the workspace when needed.","Open Project map.","Browse symbols by file."),
                "Editor command palette → Project map."),
            t("Build Diagnosis","Group known workflow failure signatures into useful findings with log evidence.",
                listOf("Open More → AI & GitHub → Build Diagnosis.","Open or refresh Build Center logs first.","Review grouped categories and the evidence line before deciding what to change."),
                "More → AI & GitHub → Build Diagnosis.",
                listOf("error = likely blocking failure","warning = likely contributing issue")),
            t("Dependencies","Detect common Gradle dependency manifests.",
                listOf("Open Dependencies.","Review detected files."),
                "Editor command palette → Dependencies."),
            t("Activity","Review recent audit and build events.",
                listOf("Open Activity.","Scroll through recent entries."),
                "Editor command palette → Activity."),
            t("Local history","Restore previous local snapshots.",
                listOf("Open Local history.","Choose a snapshot.","Tap Restore."),
                "Editor command palette → Local history."),
            t("Logs","Inspect recent logcat output for runtime diagnosis.",
                listOf("Open Logs.","Scroll through output.","Use entries to locate runtime failures."),
                "Editor command palette → Logs."),
        ),
    ),
    GuideCategory(
        "Navigation & UI",
        "A compact map of the current editor-first interface, including the file-tree drawer and Chat-only AI boundary.",
        listOf(
            t("Editor hamburger","The Editor-only project drawer and file-tree refresh surface.",
                listOf("Tap ☰ at the top-left.","Browse the current folder and open folders/files.","Use + to create a file or folder.","Use ↻ to refresh the visible tree.","After GitHub is connected, use the GitHub section to load and open repositories."),
                "Top-left of Editor.",
                listOf("☰ = project drawer","↻ = refresh","✓ = active workspace","+ = create"),
                tips = listOf("This drawer contains only the project-tree/workspace flow; Terminal, Git, Build, IDE tools, notifications and plugins are outside it.")),
            t("Back arrow","Return to the previous screen/menu.",
                listOf("Tap ← at top-left.","Nested workspace menus return to their parent."),
                "Secondary screen top-left.",
                listOf("← = back")),
            t("More","Secondary app functions that do not need permanent bottom-navigation space.",
                listOf("Open More.","Choose Settings, AI-driven actions, Approvals, AI Tools, Live GitHub Actions, AI & GitHub, repository creation, or Help & guide.","More opens as a full app surface with no duplicate navigation bar."),
                "Bottom navigation → More."),
            t("Settings","The single app-settings destination, opened from More.",
                listOf("Open More.","Tap Settings.","Choose AI, Security, App settings, or the related settings section.","Settings opens as a full app surface; the primary bottom/side navigation is hidden while it is open."),
                "More → Settings."),
            t("Help & guide","This feature guide.",
                listOf("Open More.","Tap the info/help entry.","Choose a category.","Choose a topic."),
                "More → ⓘ Help & guide.",
                listOf("ⓘ = help")),
            t("Floating AI button","Global AI Chat access without using a navigation slot.",
                listOf("Tap the bottom-right floating AI button.","Chat opens in the only intentional popup surface.","The button is available from normal non-chat screens while other full-screen surfaces are open.","AI Chat is not a permanent destination or bottom-navigation item."),
                "Bottom-right floating action button.",
                listOf("chat + ✦ = AI Chat")),
            t("Compact controls","Frequent actions use icons while less-used actions move into menus.",
                listOf("Use toolbar icons for daily actions.","Use Command palette for the long tail of commands."),
                "Editor toolbar.",
                listOf("⋮ = more commands")),
        ),
    ),
    GuideCategory(
        "Recovery & Persistence",
        "Durable state that helps preserve editing and task continuity.",
        listOf(
            t("Durable editor tabs","Persist open tabs so they can be restored after recreation.",
                listOf("Open files normally.","Restart/recreate the app.","DevForge restores persisted tabs when available."),
                "Automatic editor persistence."),
            t("Recovery drafts","Keep a recovery version of active edits.",
                listOf("Edit a file.","If the app is recreated before a normal save, reopen the file and use the recovery state when presented."),
                "Editor recovery flow."),
            t("Snapshots","Bounded content checkpoints for recovery and restore.",
                listOf("Use Local history.","Choose a snapshot.","Restore it into the open file."),
                "Editor command palette → Local history."),
            t("Build receipts","Persist recent remote build state.",
                listOf("Complete a CI run.","Review it later in build history or IDE Activity."),
                "Build history / Editor command palette → Activity."),
            t("AI execution","The single main AI plans and executes coding work directly through the bounded tool runtime.",
                listOf("Ask DevForge AI for a coding change.","AI creates an ordered plan before execution.","Watch the main AI search, read, write, delete, command and verification activity.","Expand an execution label to inspect its tool name and result detail."),
                "AI Chat → expandable execution labels; More → AI & GitHub → AI Tools.",
                listOf("user = reviewer","AI = sole executor","approval = required for protected side effects")),
t("Plans & execution","Chat shows the main AI plan and each search/execution step as it happens.",
                listOf("Open AI Chat.","Send a coding request.","Review the numbered plan phases.","Watch searches, file reads, edits, checks and other tool steps complete in order."),
                "AI Chat → expandable execution labels.",
                listOf("plan = ordered reasoning and execution stages","tool steps = sequential and bounded")),

        ),
    ),
    GuideCategory(
        "Files & Attachments",
        "File-focused actions used by the editor and AI surfaces.",
        listOf(
            t("File attachments","Attach bounded files directly in AI Chat, including files, photos, videos, audio and documents.",
                listOf("Open AI Chat.","Tap + in the composer.","Choose File, Photo, Video, Audio or Document.","Review the attachment chip.","Send the message."),
                "AI Chat composer → + attachment menu.",
                listOf("File = 15 MiB max","Photo = 15 MiB max","Video = 50 MiB max","Audio = 30 MiB max","Document = 10 MiB max","up to 8 pending attachments and 80 MiB combined")),
            t("Images/media","DevForge uses the Android document picker and infers MIME type from both the document provider and filename when necessary.",
                listOf("Open the attachment picker.","Choose an image or video even when the provider reports application/octet-stream.","DevForge validates the file using the MIME type and common filename extension.","Review the attachment before sending."),
                "AI Chat → + → Photo or Video.",
                listOf("binary content is never silently converted to text","provider support can still limit whether a selected binary attachment can be sent")),
            t("Attachment provider support","Attachment options are checked against the selected provider before a file is queued.",
                listOf("Gemini uses the Gemini Files API for binary uploads.","OpenRouter currently accepts binary image and PDF attachments through its OpenAI-compatible adapter; its video and audio options are disabled.","Providers without a binary attachment adapter reject unsupported binary files before send.","When an AI request fails, DevForge keeps the selected attachments available for retry instead of silently discarding them."),
                "AI Chat → + attachment menu and send path.",
                listOf("text-like files can be sent as prompt context","binary transport = provider-specific","failed send = attachments retained for retry")),
            t("File preview","Open a workspace file in the editor.",
                listOf("Browse a workspace.","Tap a file.","The file opens as an editor tab."),
                "Workspace tree."),
            t("File operations","Create, modify, rename, and delete project files using the workspace/editor tools.",
                listOf("Select the workspace.","Use Files for tree operations.","Use Editor for content operations."),
                "Files + Editor."),
        ),
    ),
    GuideCategory(
        "Quality & Reliability",
        "Operational safety, workspace health, security checks, mobile performance budgets, release readiness, and recovery guidance.",
        listOf(
            t("Quality & Reliability center","Open one dashboard for the quality systems that protect day-to-day development.",
                listOf(
                    "Open More → AI & GitHub.",
                    "Choose Quality & Reliability.",
                    "Review Operations, Workspace integrity, Security scan, Mobile performance budget, and Release quality gate.",
                ),
                "More → AI & GitHub → Quality & Reliability.",
                listOf("PASS = current check satisfied","CHECK = action still required")),
            t("Operation lifecycle","Track long-running work without tying it to the current screen.",
                listOf(
                    "Build operations report queued, running, successful, failed, or cancelled state.",
                    "Navigate away from Build or other future operation surfaces without losing the lifecycle record.",
                    "Review the most recent operations in Quality & Reliability.",
                ),
                "More → AI & GitHub → Quality & Reliability → Operations.",
                listOf("queued = waiting to start","running = active","waiting = blocked on a decision","succeeded = finished successfully","failed = ended with an error","cancelled = intentionally stopped")),
            t("Workspace integrity","Check whether the selected workspace is still safely accessible.",
                listOf(
                    "Open Quality & Reliability.",
                    "Review persisted SAF access, workspace root readability, saved workspace metadata, and Git detection.",
                    "Repair the workspace or reselect it when a required check fails.",
                ),
                "More → AI & GitHub → Quality & Reliability → Workspace integrity.",
                listOf("PASS = healthy","WARN = usable but needs attention","FAIL = access or persistence problem")),
            t("Security scan","Run a bounded best-effort scan for obvious secret-like content before sharing or committing open files.",
                listOf(
                    "Open Quality & Reliability.",
                    "Review Security scan results.",
                    "Inspect any flagged open file and move real credentials into protected credential storage.",
                ),
                "More → AI & GitHub → Quality & Reliability → Security scan.",
                listOf("review = secret-like pattern detected"),
                tips = listOf("The current scan intentionally checks open editor content only; it is not a complete repository secret scanner.")),
            t("Editor performance mode","Keep code navigation responsive on mobile devices by moving expensive editor analysis off the typing and scrolling path.",
                listOf("Files up to 64 KiB can use rich syntax and folding rendering.","Larger files switch automatically to fast plain-text rendering.","Fold calculation is debounced and moved off the UI thread.","Diagnostics are debounced, moved off the UI thread and skipped above 256 KiB.","Undo bookkeeping is bounded without rescanning the entire undo history on every keystroke."),
                "Editor → open a large code file.",
                listOf("rich rendering threshold = 64 KiB","diagnostic ceiling = 256 KiB","editor content limit remains 8 MiB")),
            t("Performance budget","Keep editor state within mobile-friendly bounds.",
                listOf(
                    "Check open tab count and total open editor content.",
                    "Close unused tabs or split large tasks when the budget is exceeded.",
                    "Large-file editor safeguards remain separate and continue to protect syntax/highlighting work.",
                ),
                "More → AI & GitHub → Quality & Reliability → Mobile performance budget.",
                listOf("within budget = normal","over budget = simplify current editor state")),
            t("Release quality gate","Use explicit pre-release checks before treating a build as ready.",
                listOf(
                    "Build the intended release target.",
                    "Review artifact availability and build logs.",
                    "Ensure there are no unsaved editor changes.",
                    "Confirm build configuration is complete.",
                    "Resolve any failed gate item before release.",
                ),
                "More → AI & GitHub → Quality & Reliability → Release quality gate.",
                listOf("PASS = satisfied","CHECK = not satisfied yet")),
            t("Recovery and persistence","DevForge keeps bounded editor snapshots, AI execution state, build receipts, and recovery paths.",
                listOf(
                    "Use IDE → Local history for file snapshots.",
                    "Use Workspace Backup for bounded editor/workspace export and restore.",
                    "Use Activity or the relevant feature screen to inspect durable task/build history after recreation.",
                ),
                "More → AI & GitHub → Workspace Backup; Editor command palette → Local history.",
                listOf("snapshot = recoverable content checkpoint","backup = bounded exported state")),
        ),
    ),
    GuideCategory(
        "GitHub Repository Creation",
        "Create a repository directly from DevForge using the connected GitHub account.",
        listOf(
            t("Create repository","Create a real GitHub repository with repository-creation options.",
                listOf("Open More → Create GitHub repository, or AI & GitHub → GitHub Hub → Create repository.","Enter the repository name.","Choose description, homepage, visibility, initialization, GitHub features, templates and default branch.","Tap Create repository."),
                "More → Create GitHub repository.",
                listOf("private = repository visibility","README = initialize the repository","issues/projects/wiki/discussions = GitHub feature switches")),
            t("GitHub creation options","Configure the repository creation fields supported by the connected GitHub account.",
                listOf(
                    "Choose Personal account or an available organization owner.",
                    "Choose Public/Private visibility; organization repositories can also use Internal when GitHub permits it.",
                    "Optionally initialize with README and select .gitignore and license templates.",
                    "Enable Issues, Projects, Wiki, Discussions and Downloads.",
                    "Choose merge behavior: squash, merge commit, rebase and auto-merge.",
                    "Configure delete-branch-on-merge and the default squash/merge commit title/message rules.",
                    "For organizations, optionally enter Team ID and custom properties JSON.",
                    "Set the requested default branch. A custom initial branch requires an initialized repository so DevForge can create and select that branch safely.",
                ),
                "Create GitHub repository screen."),
            t("Open created repository","Immediately use the new repository as the current DevForge workspace.",
                listOf("After creation, tap Open as workspace.","DevForge activates the GitHub-backed worktree and makes it the current AI/editor context."),
                "Create GitHub repository → Open as workspace."),
        ),
    ),
    GuideCategory(
        "App identity",
        "The DevForge launcher identity and the visual language behind the app icon.",
        listOf(
            t("DevForge launcher logo","The app launcher uses the DevForge coding-and-forge emblem: a cyan/blue D-shaped arc, code brackets, forge/anvil geometry, orange sparks, and a dark technical background.",
                listOf("Use the Android launcher icon to identify DevForge on the home screen and app drawer.","The adaptive icon automatically applies the device launcher mask while preserving the central emblem.","The logo is intentionally vector-based so it stays sharp at different launcher sizes."),
                "Android home screen and app drawer.",
                listOf("cyan/blue arc = DevForge identity","</> = coding","anvil/forge = build/create","orange sparks = active creation")),
        ),
    ),
    GuideCategory(
        "Linux-style Terminal",
        "The Terminal uses a sandboxed Android /system/bin/sh with an expanded Linux-style command workflow.",
        listOf(
            t("Shell execution","Terminal commands run through the existing workspace sandbox. Local workspaces are mirrored into an isolated terminal root before execution.",
                listOf("Use normal shell syntax: quoting, variables, pipes, redirects, &&, ||, ; and command substitution.","cd updates the DevForge session working directory.","GitHub-backed workspaces continue to use their remote terminal path where supported."),
                "More → Terminal."),
            t("Core command set","The bounded AI terminal catalog now covers common navigation, text, file and diagnostic commands.",
                listOf("Read/search: pwd, ls, cat, head, tail, grep, find, sort, uniq, cut, tr, wc.","Inspect: date, df, du, stat, readlink, realpath, basename, dirname, uname, id, whoami, env, printenv, which, ps, getprop, sha256sum, cmp, diff.","Write: sed, mkdir, rmdir, touch, rm, cp, mv and chmod."),
                "Terminal command palette/help."),
            t("Longer engineering commands","Terminal timeouts now support longer bounded tasks so test/build commands are practical on Android.",
                listOf("The saved terminal timeout can be configured up to 60 seconds.","Interactive shell execution supports a separate bounded ceiling up to 120 seconds.","Approval, sandbox and output limits remain enforced."),
                "Settings → App settings → Terminal."),
            t("Security boundary","The shell is Linux-style, not unrestricted root access.",
                listOf("Workspace paths remain sandboxed.","Sensitive mutations continue through DevForge capability policy for AI/tool execution.","Credentials are not copied into shell environment variables by DevForge."),
                "Terminal and More → Approvals."),
        ),
    ),
    GuideCategory(
        "Symbols & Signs",
        "Quick reference for common icons and status marks.",
        listOf(
            t("Common symbols","The everyday icons used across DevForge.",
                listOf("Use this topic when an icon is unfamiliar.","The same symbol usually keeps the same meaning across screens."),
                "Across the app.",
                listOf("← back","☰ workspace/tools","ⓘ help","🔎 search","💾 save","↶ undo","↷ redo","⌄ fold","⌃ unfold","✦ AI","✓ selected","● dirty","⟳ refresh/resend","+ add","− remove/delete","⋮ more")),
            t("Status words","Understand compact status labels.",
                listOf("Dirty/Unsaved = content differs from saved version.","Pending = waiting for a decision.","Running = work in progress.","Completed = finished."),
                "Editor, Build, AI, Approval, and Activity views."),
            t("Git symbols","Understand diff notation.",
                listOf("+ lines were added/changed.","− lines were removed.","A commit groups a repository change set."),
                "Git and pending GitHub changes.",
                listOf("+ = added","− = removed")),
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureGuideScreen(onClose: () -> Unit) {
    var categoryIndex by remember { mutableStateOf<Int?>(null) }
    var topicIndex by remember { mutableStateOf<Int?>(null) }
    val category = categoryIndex?.let { guideCategories.getOrNull(it) }
    val selectedTopic = if (category != null) topicIndex?.let { category.topics.getOrNull(it) } else null
    val title = when {
        selectedTopic != null -> selectedTopic.title
        category != null -> category.title
        else -> "Help & guide"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            selectedTopic != null -> topicIndex = null
                            category != null -> categoryIndex = null
                            else -> onClose()
                        }
                    }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        when {
            selectedTopic != null -> GuideTopicPage(selectedTopic, padding)
            category != null -> GuideCategoryPage(category, padding) { topicIndex = it }
            else -> GuideRootPage(padding) { categoryIndex = it; topicIndex = null }
        }
    }
}

@Composable
private fun GuideRootPage(padding: PaddingValues, onCategory: (Int) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Everything in one place", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(
                        "Choose a feature, then a topic. Every topic explains what it does, how to use it, where to find it, and its signs or quick actions.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(guideCategories.withIndex().toList(), key = { it.index }) { item ->
            GuideCategoryCard(item.value) { onCategory(item.index) }
        }
    }
}

@Composable
private fun GuideCategoryPage(category: GuideCategory, padding: PaddingValues, onTopic: (Int) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(category.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text(category.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(category.topics.withIndex().toList(), key = { it.index }) { item ->
            Card(
                onClick = { onTopic(item.index) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.value.title, fontWeight = FontWeight.SemiBold)
                        Text(item.value.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun GuideTopicPage(topic: GuideTopic, padding: PaddingValues) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { GuideSection("What it does", listOf(topic.summary)) }
        item { GuideSection("How to use it", topic.howTo) }
        item { GuideSection("Where to find it", listOf(topic.whereToFind)) }
        if (topic.symbols.isNotEmpty()) item { GuideSection("Symbols & signs", topic.symbols) }
        if (topic.shortcuts.isNotEmpty()) item { GuideSection("Quick actions & shortcuts", topic.shortcuts) }
        if (topic.tips.isNotEmpty()) item { GuideSection("Tips", topic.tips) }
    }
}

@Composable
private fun GuideCategoryCard(category: GuideCategory, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(category.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(category.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(category.topics.size.toString() + " topics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun GuideSection(title: String, lines: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Divider()
            lines.forEach { line ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("•", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(line, Modifier.weight(1f))
                }
            }
        }
    }
}
