### 2026-09-22 — CI unit-test repair: workspace root and coding-tool defaults
- [x] Fixed `AgentWorkspacePath.canonicalize` so root markers (`. `, `./`, and `/`) resolve to the active workspace root when empty paths are explicitly allowed, matching the main AI workspace-tool behavior.
- [x] Updated the stale `DevForgeToolCatalogTest` expectation to reflect the current single-AI policy: workspace mutation tools, including delete-path, are enabled by default while destructive deletion remains approval-gated by the capability/policy layer.
- [x] Release compilation already succeeded on the failing CI run; this repair targets the two failing unit tests only.
- [ ] Fresh Android CI and Android UI Tests validation is required for this repair commit.

### 2026-09-22 — CI repair: AI & GitHub state restoration import
- [x] Triggered fresh push-based Android CI and Android UI Tests validation from the repaired `main` state after the Git ref update itself did not create a workflow event.

- [x] Fixed `AiGitHubHubScreen.kt` to import `rememberSaveable` from `androidx.compose.runtime.saveable`, matching the Compose API package used by the project and removing the remaining Android CI/UI-test compilation failure.
- [x] No architecture or runtime behavior was changed by this repair; the shared AI & GitHub hub still uses the existing saved section state.
- [ ] Fresh Android CI and Android UI Tests validation is required for this repair commit.

### 2026-09-22 — CI follow-up repair for Connections screen
- [x] Fixed the Connections settings callback to use the existing destination-navigation callback instead of a local `navigateTo` function that is out of scope inside `DestinationScreen`.
- [x] Preserved `rememberSaveable` in the AI & GitHub hub and explicitly opted its Material 3 top-app-bar surfaces into the experimental API required by the project's warning-as-error compilation settings.
- [ ] Fresh Android CI and Android UI Tests validation is required for the new repair head.

### 2026-09-22 — CI compile repair after AI/Extensions navigation cleanup
- [x] Restored the missing `BuildViewModel` parameter on `DestinationScreen`, matching the existing `build = build` call site.
- [x] Updated `AgentCommandBridge` to the renamed `AiCommandCatalog`.
- [x] Added the missing Compose icon imports for Extensions and expandable AI execution.
- [x] Added the Material 3 experimental opt-in required by the dedicated AI Tools top app bar.
- [x] Preserved the explicit `rememberSaveable` import in the AI & GitHub hub surface.
- [ ] Fresh Android CI and Android UI Tests validation is required for the repair head.

### 2026-09-22 — CI repair after single-AI cleanup
- [x] Fixed the failed Android CI compile caused by the single-AI cleanup: restored the BuildViewModel dependency in DestinationScreen, aligned the renamed AiCommandCatalog, imported the expandable execution icon, replaced the unavailable Extensions navigation icon, and opted the AI Tools top app bar into Material 3 experimental API.
- [x] Verified the existing AiGitHubHubScreen rememberSaveable import is present on the current main head.
- [ ] Fresh Android CI and Android UI Tests validation remains required for the repair commits.
### 2026-09-22 — Final navigation surface cleanup
- [x] Removed the standalone AI Tools destination; AI Tools now exists only inside the shared AI & GitHub hub, while remaining separately selectable there.
- [x] Removed the remaining Automation entry from Help & guide and App Settings.
- [x] Kept GitHub, AI Models, and AI Tools as separate child surfaces inside the shared hub so the three areas are not mixed together.
- [x] Added/updated navigation regression coverage for the shared AI & GitHub hub and its AI Tools child screen.
- [ ] Fresh Android CI and Android UI Tests validation is still required for this final main head.

### 2026-09-22 — Single-AI navigation and workspace execution cleanup
- [x] Replaced the old multi-agent Chat execution path with one main AI tool loop. Coding requests now stay inside one plan → inspect → execute → verify flow.
- [x] Removed the user-facing Engineering Power surface and deleted its source/test screens.
- [x] Added a shared full-screen AI & GitHub hub available from both Settings and More. GitHub, AI Models, and AI Tools open as separate child screens rather than being mixed together.
- [x] Moved GitHub management into the shared hub so Settings and More no longer expose separate GitHub management surfaces.
- [x] Restored AI Tools as its own dedicated screen with per-tool controls and an explicit "Enable coding tools" action.
- [x] Main AI workspace inspection/mutation tools are enabled by default for upgraded installs; create file, create folder, write/patch, and delete tools remain bounded by the approval and workspace policy layers.
- [x] Fixed workspace-root normalization so root paths such as ".", "./", "/", and the workspace-name-only form can be accepted by root-aware AI listing/search tools without producing "Workspace path is invalid".
- [x] Increased the main AI coding tool loop budget from 6 to 24 tool steps/calls and explicitly instructs the model to plan, inspect, execute, and verify instead of stopping at a plan.
- [x] Replaced the old vertical agent execution card with compact expandable AI execution labels showing tool state, name, and detail.
- [x] Made AI Chat full-screen and removed the user-facing worker/agent execution surface.
- [x] Removed DevForge Center from navigation and deleted its dedicated screen.
- [x] Removed Automation from More and primary destinations. Background automation scheduling is no longer initialized; startup disables legacy scheduled automation work from previous installs.
- [x] Removed Automation settings controls and the old GitHub settings duplicate from App Settings; the shared AI & GitHub hub is now the single entry point for GitHub and AI setup.
- [x] Updated More, Settings, Help & guide, and README to reflect the single-AI architecture and removed user-facing Agents, Automation, and Engineering Power documentation.
- [ ] Fresh Android CI and Android UI Tests validation remains required for this main head.

### 2026-09-22 — Single main AI and dedicated AI Tools
- [x] Removed the user-facing Engineering Power surface and its dedicated source/test files; AI work is no longer split across a separate engineering control room.
- [x] Restored More → AI Tools as a dedicated full-screen tool-management surface with per-tool enable/disable controls, a coding-tools enable action, and reset behavior.
- [x] Made the main AI's workspace inspection and mutation tool set enabled by default, including create file, create folder, write/patch, and delete-path tools; destructive deletion remains approval-gated.
- [x] Removed the normal Chat path's delegation into the multi-agent squad planner. Coding requests now stay in the single main AI tool loop: plan → inspect → execute bounded tools → verify.
- [x] Fixed workspace-root tool path normalization so empty/root requests such as `.`, `./`, and `/` resolve to the active workspace root instead of failing as an invalid workspace path.
- [x] Removed the old agent run card, agent overview, agent history, and worker controls from the user-facing Chat/Project Activity surfaces.
- [x] Converted live AI tool execution into compact expandable task labels showing running/completed/failed state, tool name, and execution detail.
- [x] Made AI Chat a full-screen surface instead of a constrained dialog.
- [x] Updated Help & guide and README to describe the single main AI workflow and the dedicated AI Tools screen; removed the Engineering Power and user-facing Agents guide categories.
- [ ] Fresh Android CI and Android UI Tests validation is required for the current main head.

### 2026-09-22 — Dedicated Extensions UI

- [x] Promoted the real extension manager to a dedicated top-level `Extensions` destination instead of embedding it inside Engineering Power or another utility screen.
- [x] Added a direct More → Extensions entry for installing and managing Acode ZIP and VS Code VSIX packages.
- [x] Kept the existing real install/analyze/store/runtime/icon-theme behavior intact inside the dedicated screen.
- [x] Removed the embedded Engineering Power extension surface so extension management has one clear entry point and lifecycle.
- [x] Updated More → ⓘ Help & guide and README to document the dedicated Extensions path.
- [ ] Fresh Android CI and Android UI Tests validation is required for the current main head.

### 2026-09-22 — Fix README screenshot formats
- [x] Verified that all five uploaded screenshot assets are JPEG images even though they were stored with `.png` filenames.
- [x] Renamed the repository screenshot paths from `.png` to `.jpg` without altering the image blobs.
- [x] Updated README image references to the correct `.jpg` paths so GitHub can render the screenshots.
- [x] Updated this implementation record in the same repository change.

### 2026-09-22 — Publish repository screenshots
- [x] Verified the supplied Editor, AI Agents, GitHub/Files, Terminal, and Build Center screenshots are present under `docs/screenshots/` on `main`.
- [x] Replaced the README visual placeholders with the actual repository-hosted screenshots.
- [x] Removed the obsolete `docs/screenshots/.gitkeep` placeholder now that the directory contains the real image assets.
- [x] Updated `IMPLEMENTATION.md` in the same repository change.
- [x] No application source code or runtime behavior was changed by this update.

### 2026-09-22 — DevForge README landing page, metadata and visual slots
- [x] Reworked README.md into a public landing page centered on the requested DevForge positioning: AI-powered Android coding environment, portable Android development workflow, AI agents, Git/GitHub, terminal, automation, and cloud APK/AAB builds.
- [x] Added the requested repository description text and keyword set to the README as repository metadata documentation.
- [x] Added the requested “Who is DevForge for?”, capabilities, FAQ, Open Source, Download, and landing-page feature sections.
- [x] Added Android CI and Android UI Tests badges and direct GitHub/Download links.
- [x] Added dedicated visual slots and labels for the supplied Editor, Agents, GitHub/Files, Terminal, and Build Center screenshots.
- [ ] GitHub repository description/topics are documented in README because the available GitHub write connector exposes file/content mutations but not repository metadata/topic mutation.
- [x] The supplied screenshot binaries are now committed under `docs/screenshots/` and are embedded in the README visual sections.

### 2026-09-21 — Unit-test placement fix after release CI validation
- [x] Moved the new AI terminal/Git-history regression tests inside their JUnit test classes. The previous append placed them at file scope, producing JUnit initialization errors for the generated *TestKt classes.
- [ ] Fresh release CI and Android UI Tests validation is pending for the corrected main commit.

### 2026-09-21 — AI terminal commands, Git history access and release-first Android CI
- [x] Expanded the bounded terminal command set with practical Android utilities for inspection, path handling, hashing and comparison.
- [x] Added provider-neutral AI run_command and get_git_log tools. Terminal execution uses the central capability/approval policy; Git history remains read-only and bounded.
- [x] Registered Git/Terminal access scopes, exposed the tools in More → AI Tools, and updated the agent planner contract.
- [x] Extended the GitHub-backed virtual terminal with Git log/status/branch/show plus file/path inspection utilities.
- [x] Updated More → ⓘ Help & guide with the AI terminal and Git-history workflows.
- [x] Made automatic main-branch Android CI release-first: push-to-main builds the release APK; manual dispatch retains explicit target selection for Build Center compatibility.
- [x] Removed the obsolete PR trigger from the Android CI workflow so automatic CI is an artifact-producing main/release pipeline; Android UI Tests remains the dedicated UI validation workflow.
- [ ] Fresh CI/UI validation is pending for the new commits.
- [ ] Earlier-session screenshots are not available in the accessible repository/File Library sources, so no unrelated screenshots were added.

### 2026-09-21 — CI compile repair after feature implementation
- [x] Fixed the first debug CI compile failures introduced by the requested UI changes: restored the Compose `size` import for compact AI tool chips, removed stale editor-AI job cleanup, and restored the missing GitHub workflow-deletion `TextButton` import.
- [ ] Re-run Debug Android CI artifact and Android UI Tests on this corrected main commit; release artifact remains blocked until both debug validation workflows pass.

### 2026-09-21 — Final navigation/documentation cleanup before validation
- [x] Removed the unused editor-only AI assistant implementation after migrating the Editor to Chat-only AI.
- [x] Updated the Editor navigation UI regression test for the Acode-style project drawer.
- [x] Corrected DevForge Center language-intelligence text so it no longer advertises editor-embedded AI editing.
- [x] Corrected guide paths for command-palette tools, approval behavior, settings, and project-drawer scope.
- [ ] Final debug Android CI artifact and Android UI Tests validation is required before a release artifact is triggered.


### 2026-09-21 — Chat-only AI, Acode-style project drawer, refresh, workflow deletion
- [x] Removed the editor-local AI edit entry point, AI proposal surface, editor AI busy/stop state and editor AI assistant wiring. AI is now exposed from Chat; the file editor remains focused on editing, review, navigation and source tooling.
- [x] Added Refresh to the Editor top bar and editor toolbar. Clean files reload immediately; dirty files require explicit confirmation before unsaved content is replaced.
- [x] Rebuilt the Editor hamburger as a left-side, file-tree-first drawer inspired by the file-tree interaction in Acode. It contains the current tree, saved workspaces, file/folder creation and GitHub repositories, while unrelated notification/plugin surfaces are excluded.
- [x] GitHub repository loading now paginates up to the configured safety ceiling and the Editor drawer fetches the repository list automatically after GitHub is connected, with a manual repository Refresh action.
- [x] Changed AI Chat tool activity presentation from stacked result rows to compact horizontal status chips so completed tool calls no longer create tall vertical success messages.
- [x] Added single and multi-select GitHub Actions workflow deletion in the repository screen. DevForge deletes the selected workflow definition files under .github/workflows/ by committing the removal to the repository's default branch.
- [x] Historical GitHub Actions runs remain untouched because workflow deletion removes repository definition files rather than past run records.
- [x] Updated More → ⓘ Help & guide across the current Editor, Chat, Workspaces, GitHub, Build/CI, Terminal, Agents, Automations, Approvals/Security, DevForge Center, Settings, Recovery and Navigation surfaces.
- [ ] Fresh debug Android CI artifact and Android UI Tests validation must pass for this main commit before a release artifact is triggered.


### 2026-09-21 — Separate AI Chat and Agent Stop controls
- [x] Kept AI Chat Stop scoped to the current Chat generation and any agent task created by that Chat turn; it no longer cancels unrelated Agent Center work.
- [x] Restored Agent Center Stop to the individual agent task; stopping one agent does not stop Chat or other agents.
- [x] Removed the application-wide cross-cancellation callback/query that made the two surfaces behave as one Stop action.
- [x] Kept the R3 persistent approval-bypass ceiling for every grantable capability and updated the in-app guide to describe the separate Stop controls.
- [x] Fixed the Android UI Tests workflow retry parsing and the Chat approval continuation Kotlin string-literal syntax.

### 2026-09-21 — Deletion grant regression coverage
- [x] Added a policy regression test proving a scoped DELETE_FILES R3 grant bypasses the normal approval gate only inside the granted path scope.
- [x] Extended the Help & guide with the GitHub folder mutation behavior and deletion grant coverage.

### 2026-09-21 — AI mutation authorization and direct GitHub workspace commits
- [x] Exposed DELETE_FILES as an explicit persistent capability grant. Deletion grants use an R3 ceiling and remain bounded by the selected workspace/path scope.
- [x] Restored persisted capability grants into the in-memory authorization registry during app startup so grants continue to work after process restarts.
- [x] Changed GitHub-backed agent create/modify/delete operations to commit and push immediately instead of only queuing an in-memory pending batch. Folder creation uses .gitkeep; folder deletion removes all tracked files recursively.
- [x] Moved direct approved-tool execution in Approval Center off the main thread.
- [x] Updated the in-app Help & guide to document DELETE_FILES grants and immediate GitHub-backed mutation commits.

### 2026-09-21 — AI generation cancellation and resumable Chat approvals
- [x] Stop now immediately clears the Chat running state and visible stream, invalidates stale callbacks from the cancelled generation, cancels active coding-agent tasks, and cancels the provider request coroutine.
- [x] Chat-owned tool approvals no longer end the model turn. The exact approval-bound tool call waits for the user's decision and continues automatically after approval.
- [x] Chat-owned approvals are not executed a second time by Approval Center; the waiting Chat coroutine claims the approved request.
- [x] Stopping Chat while an approval is pending rejects that orphaned Chat approval.
- [x] Approval Center executes direct approved tool operations on Dispatchers.IO instead of blocking the UI thread.
- [x] Updated More → ⓘ Help & guide with the new Stop and approval behavior.

### 2026-09-21 — UI workflow Gradle download resilience
- Diagnosed Android UI Tests #1043: the emulator booted successfully, but the job failed before tests because the Gradle 9.6 distribution download hit the Gradle wrapper's 10-second network timeout.
- Increased the Gradle wrapper network timeout to 120 seconds.
- Added up to three complete Gradle UI-test invocation attempts so transient hosted-runner distribution/network failures are retried instead of being reported as application/UI failures.
- Kept the emulator configuration and test command unchanged once Gradle is available.
- [ ] Fresh Android UI/CI validation is pending for this main-branch repair; no unverified green status is claimed.
### 2026-09-21 — More-screen Back navigation
- Fixed Back handling from More at the app root so it returns to the Editor main menu instead of immediately opening the exit dialog.
- A second Back press from the restored main menu now follows the existing root behavior and shows the “Exit DevForge?” confirmation.
- Added an Android navigation regression test covering More → Editor main menu → exit confirmation.
- Updated More → ⓘ Help & guide with the corrected Back behavior.
- [ ] Post-change Android CI/UI validation remains pending for this main-branch change; no unverified green status is claimed.

### 2026-09-21 — Target-aware Build Center and UI workflow reliability
- Fixed Build Center target handling so Debug APK, Release APK, and Release AAB map to canonical Gradle tasks and artifact names instead of relying on duplicated per-call mappings.
- Persisted the selected build target in per-repository BuildOutputSettings, so choosing Release APK/AAB no longer silently falls back to Debug APK after repository selection or app recreation.
- Replaced the generic Build Center dispatch action with a target-aware build action that explicitly dispatches the currently selected target and refuses to build when APK/AAB generation is disabled.
- Added target selection to the GitHub repository build-output settings surface and updated the release messaging to reflect the fail-closed signing requirement.
- Fixed Android CI release artifact resolution to upload the verified signed app-release.apk and exact app-release.aab paths rather than a release-APK wildcard.
- Fixed Release Build Validation to publish the signed app-release.apk instead of the unsigned intermediate APK after signature verification.
- Hardened release signing validation by removing redundant checks and making the required signing-secret step explicitly non-optional.
- Stabilized the Android UI-test workflow by using a fixed emulator resource profile, explicit boot timeout, forced AVD recreation, and no manual ADB reconnect loop that could race emulator startup.
- Updated BuildWorkflowTemplates.kt so workflows created from DevForge use the same corrected release artifact paths and UI-test reliability settings.
- Added regression coverage proving a Release APK dispatch sends target=release_apk and artifact/report inputs to the workflow dispatch endpoint.
- Updated More → ⓘ Help & guide with target persistence, release-artifact behavior, signed-release requirements, and the UI-workflow reliability changes.
- [ ] Post-change Android CI/UI validation remains pending for the final main-branch commit; no unverified green status is claimed.
### 2026-09-21 — Release APK CI dispatch
- Triggered the repository's release APK CI path from main using the workflow's [release-apk] push trigger.
- Release target is release_apk and the workflow will build, verify the APK signature, and upload devforge-release-apk when release signing secrets are configured.
- This dispatch intentionally requests only the release APK target; it does not trigger the release AAB target.
### 2026-09-21 — UI test API compatibility fix
- Android UI Tests #1037 reached compilation and failed because the test suite imported the unavailable Compose fetchSemanticsNodes() API.
- Replaced the unsupported semantics-node inspection with a Compose-version-compatible waitUntil + assertExists() probe.
- Removed the corresponding unsupported import and updated the More-navigation assertion to use the supported single-node API.
- [ ] Fresh Android UI validation is pending for the latest main commit.
### 2026-09-21 — UI workflow test alignment
- Investigated Android UI Tests run #1033 from main commit 8ce3b832b028b219d4663c18884ecf9a9ed80492 using the uploaded instrumentation report.
- The application compiled successfully; the UI job failed in MainActivityNavigationTest because tests still navigated through the old workspace hamburger contract even though Editor is now the launch screen and Files/Git/Build/More are bottom-navigation destinations.
- Updated bottom navigation icons with stable content descriptions for Editor, Files, Git, Build, and More.
- Reworked MainActivityNavigationTest to use the current Editor-first flow, direct bottom-navigation semantics, and explicit waits for asynchronously rendered navigation/FAB nodes.
- Removed stale test navigation through the old workspace menu, eliminating false failures in AI Chat, Files, More, and back-navigation tests.
- [ ] Fresh Android UI validation is pending for the new main commit.
### 2026-09-21 — CI compile failure fix
- Investigated the latest Android CI and Android UI Tests runs for main commit 6078d89cb59bb247453788da38fe1b51f37ad425.
- Android CI and UI Tests both failed during Kotlin compilation because the Editor/menu refactor had accidentally removed the shared DevForgeTopBar composable.
- The same build also reported nullable JSONObject access in ApprovalCenterViewModel.executeApprovedTool.
- Restored DevForgeTopBar with its existing workspace selector/search/terminal behavior and made approved-tool payload handling explicitly non-null before accessing arguments, allowedPrefixes, and access.
- Pushed the fixes on main as e4040a212dd0bf29b47d5635fbdd1334a3e730b1 and 097d598cb9395754822a9b3cd7876059a9df88a0.
- GitHub has started fresh Android CI run 1520 and Android UI Tests run 1031 for e4040a212dd0bf29b47d5635fbdd1334a3e730b1, followed by newer pending runs for 097d598cb9395754822a9b3cd7876059a9df88a0.
- [ ] Final green CI result is pending; the corrected runs were still queued/in progress during this update.
### 2026-09-21 — Editor workspace workflow, reliable file opening, AI deletion approvals, and in-app approval surface
- Reworked the Editor into a primary navigation destination while keeping the app launch state on Editor.
- Replaced the old Editor hamburger/workbench menu with a workspace-only menu: current/available workspaces, Open this Workspace, project tree navigation, file opening, folder navigation, and + creation for files/folders.
- Added the same project-tree-first flow to the Editor home screen so opening a workspace immediately exposes its tree.
- Added a retryable Editor open-error state. Failed file opens now remain in Editor with a concrete error and Retry/Dismiss controls instead of silently returning to the project tree.
- Added in-app bottom-right approval cards with a 15-second expiry and Approve/Reject actions; approval requests are no longer emitted through the Android notification shade.
- Removed notification/reminder controls from Approval Center in favor of the in-app approval surface.
- Fixed approved Chat/AI workspace tool mutations so non-durable chat approvals can be reconstructed from the persisted approval payload and executed through the same exact approval gateway. Durable agent approvals continue through their persisted task resume path.
- Persisted approved tool access in approval payloads so replay remains bound to the original workspace/path/access context.
- Fixed remote AI workspace path handling for github:// and DevForge workspace URIs and added explicit relative path fields to workspace search results, preventing the Workspace path is invalid failure when tools chain search results into search_content or file operations.
- Fixed approved deletion of GitHub-backed files and folders by distinguishing file targets from directory targets before constructing the queued GitHub delete changes.
- Disabled the legacy approval notification publisher/receiver so approval execution is exclusively handled by the in-app 15-second approval surface while existing activity notifications remain separate.
- Kept Git/.git traversal protections and bounded workspace scopes intact.
- Updated More → ⓘ Help & guide for the new Editor workspace menu and in-app approval workflow.
- [ ] Post-change debug Android CI/UI validation remains pending for the final main-branch commit; no unverified green status is claimed.
### 2026-09-21 — DevForge launcher logo
- [x] Added the new DevForge launcher identity based on the generated modern coding/forge emblem: cyan-blue DevForge arc, code brackets, anvil/forge geometry, metallic accents, orange sparks, and deep charcoal background.
- [x] Added an Android adaptive launcher icon for API 26+ using a dedicated transparent foreground vector and dark background so the logo remains recognizable across launcher masks.
- [x] Wired both standard and round launcher icon references into AndroidManifest.xml.
- [x] Updated More → ⓘ Help & guide with the new DevForge launcher identity and where it is used.
- [x] Kept the launcher icon asset lightweight and vector-based for crisp rendering and low APK overhead.
- [ ] Post-change Android CI/UI validation must be taken from a new main-branch run; no unverified green status is claimed.

### 2026-09-20 — Chat message editing/copy, expanded languages, durable context, semantic retrieval, LSP facade, CI health, agent graph, extensions and packed Git
- Implemented two-way Chat message actions: every persisted user and assistant message now exposes Copy and Edit controls.
- Copy uses the Android clipboard service and strips the internal attachment-summary suffix from user messages before copying.
- Editing an assistant message updates that message in place and records `editedAtEpochMs`; it does not trigger a new model generation.
- Editing a user message updates the original message, removes all later persisted messages, reloads the fresh database history, and resends the edited request so the assistant response is regenerated from the corrected conversation point.
- Chat edit state is persisted safely through Room migration 12→13; message editing remains bounded by the existing 256 KiB message limit and secret redaction path.
- Expanded editor language detection and syntax highlighting with C#, Swift, PHP, Ruby, Lua, Scala, Groovy, R, Perl, Haskell, Elixir and MATLAB in addition to the existing Kotlin, Java, JavaScript, TypeScript, Python, Go, Rust, C/C++, JSON, XML, HTML, CSS, SQL, YAML, Shell, Markdown and Dart support.
- Added regression coverage for the expanded language extension set and retained bounded syntax highlighting behavior for large files.
- Added a persistent `workspace_context_ledger` Room entity/DAO/repository, storing bounded workspace context version, recently accessed paths and the latest compact snapshot.
- Chat workspace context now carries bounded recently accessed path hints from the durable ledger instead of requiring whole-workspace prompt injection.
- Added internal `retrieve_relevant_context`, kept outside the 20-tool user-facing catalog, which ranks workspace symbols/files for a coding question without loading complete files.
- Wired semantic retrieval into both Chat's tool loop and the multi-agent planner; existing symbol indexing remains the fast symbol source, with bounded workspace search providing complementary file candidates.
- Agent tool execution now records bounded affected paths into the persistent context ledger and continues invalidating the process-local freshness marker for side-effecting mutations.
- Added an internal LSP-compatible facade with structured diagnostics, symbols, definition, references and safe regex-bounded rename operations backed by DevForge's existing editor intelligence and symbol/retrieval services.
- The LSP layer is deliberately a bounded internal facade rather than a bundled external language-server process; external servers remain optional and are not granted unrestricted execution.
- Added `SandboxedExtensionRegistry` and bounded extension manifest validation with explicit READ_WORKSPACE, EDIT_WORKSPACE, NETWORK, GIT and BUILD capability declarations. Arbitrary extension/MCP execution remains disabled until routed through the existing capability/policy/approval/audit gateway.
- Added bounded `AgentExecutionGraphBuilder` for persisted agent plans and handoffs, producing sequence/handoff graph nodes and edges with hard graph-size limits.
- Agent Center now shows a compact execution-graph summary for each task.
- Added `CiHealthAggregator` and Build Center CI health card, summarizing recent GitHub Actions runs into running/healthy/failed/mixed/unknown states while preserving the existing per-run monitoring and artifact/log flows.
- Existing offline queue, recovery, project-memory/knowledge, patch-preview/approval and multi-agent shared-memory/handoff systems were reused rather than duplicated; the new context ledger and execution graph extend those existing durable systems.
- Added bounded packed Git object support: Git object lookup now checks loose objects first and then Git pack index v2 entries, reads packed commit/tree/blob/tag objects and resolves OFS/REF delta chains within strict byte, pack-count, object-count and recursion limits.
- Git workspace status messaging now reflects the bounded loose/packed reader rather than claiming packed storage is categorically unsupported.
- Added regression tests for semantic-retrieval access authorization, CI aggregation, agent execution graphs and sandboxed extension manifest validation.
- Preserved the user-facing AI tool catalog at exactly 20 tools; the durable context and semantic retrieval tools remain internal capabilities rather than new user-toggleable tools.
- Documentation invariant remains in force: every repository modification in this implementation session is recorded here, including UI, persistence, tests, hardening and one-line corrections.
- Source-level validation completed after writes: re-fetched modified Kotlin files, verified the AgentToolGateway restoration after an accidental overwrite was caught and corrected, verified Room migration versions, verified message-edit history refresh, verified packed-object wiring, and checked newly added tests for model-field/schema compatibility.
- GitHub Actions validation is evidence-driven and must not be marked green until the latest main-branch runs complete successfully.

### 2026-09-20 — Release signing CI failure repair
- Diagnosed Android CI #1142: the release signing step failed before Gradle because the GitHub keystore secret could not be decoded as strict Base64.
- Hardened both Android CI and Release Build Validation to strip incidental whitespace before Base64 decoding and immediately validate the decoded PKCS12 keystore with the configured alias/password.
- Release signing remains fail-closed; an invalid keystore secret now reports a clear validation error instead of reaching Gradle with unusable signing material.
- Triggered a fresh release APK validation from main after the repair.

### 2026-09-20 — Signed release APK/AAB CI
- Added GitHub Actions release signing using the repository secrets `DEVFORGE_RELEASE_KEYSTORE_BASE64`, `DEVFORGE_RELEASE_KEYSTORE_PASSWORD`, `DEVFORGE_RELEASE_KEY_ALIAS`, and `DEVFORGE_RELEASE_KEY_PASSWORD`.
- Release APK/AAB builds now fail closed when signing secrets are missing instead of silently producing unsigned release artifacts.
- Android CI verifies the signed release APK with `apksigner` and the release AAB with `jarsigner` before artifact upload.
- Release Build Validation now produces `app-release.apk` and a signed `app-release.aab` rather than an unsigned APK.
- Temporary CI keystore material is removed from the GitHub Actions runner after the release validation job.

### 2026-09-20 — Centered screen titles and release APK CI dispatch
- Replaced the persistent DevForge top-left title with a screen-aware centered title bar; the active workspace remains available as a centered secondary selector.
- Moved app-level Back navigation to the upper-left navigation slot, including nested Settings and Build repository-picker states.
- Removed nested Settings “Back” bars and the obsolete instructional back-navigation footer.
- GitHub repository picker no longer renders a second back header; it uses the global top bar.
- Terminal and commit-history surfaces now use centered screen titles with upper-left Back navigation.
- Added a one-tap Build Center action to dispatch the Android CI workflow specifically for a release APK target.
- Updated the generated Android CI workflow template so `android.yml` includes the fixed `workflow_dispatch` target contract for debug APK, release APK, and release AAB builds.

### 2026-09-20 — Workspace-aware chat agent and Build Center workflow creation
- Fixed GitHub-backed workspace agent planning: the agent inventory now reads remote GitHub contents through the repository gateway instead of treating the devforge://github/... workspace URI as a SAF document URI.
- Kept local SAF workspace inventory behavior unchanged, including path-scope limits and the 1,200-node safety bound.
- Build Center now observes the active DevForge workspace and automatically inherits its GitHub owner, repository, and branch when the active workspace is GitHub-backed.
- Workflow creation controls now require both a repository owner and repository name, preventing misleading partially enabled buttons.
- Removed dependency on re-selecting the same GitHub repository in Build Center when it is already the active workspace.

### 2026-09-20 — Final agent-tool retrieval and failure-path hardening
- Completed the remaining implementation gaps found during the repository-wide audit of the workspace-aware AI/tool flow.
- AgentWorkspaceTools.ReadFileTool now accepts optional 1-based startLine / endLine arguments and returns the selected range plus startLine, endLine, totalLines, and ranged metadata.
- Line-range reads are bounded to a maximum of 400 lines, while the existing 128 KiB text-read ceiling remains in force.
- GitHub-backed agent reads are now subject to the same 128 KiB byte ceiling as local SAF reads, preventing large remote files from bypassing the bounded-read contract.
- ChatToolOrchestrator now treats malformed tool envelopes and disabled-tool requests as controlled chat results instead of throwing an uncaught IllegalArgumentException.
- Generic tool execution failures are returned to the model in the bounded tool transcript so the model can try a different relevant tool within the existing six-call/six-step safety envelope; approval-required mutations still stop and require the user approval flow.
- Updated the Chat tool-use instruction to explicitly direct models to use startLine / endLine when a targeted code range is sufficient, supporting evidence-first codebase inspection without unnecessary file-content injection.
- Removed the obsolete, now-unused recursive workspaceInventory() implementation from ParallelAgentCoordinator; planner context uses the authoritative bounded WorkspaceContextService path instead.
- Cleaned imports left behind by that removed inventory implementation.
- No new user-facing tool was added and the user-facing catalog remains exactly 20 tools; the internal get_workspace_context capability remains separate and non-toggleable.
- Documentation invariant remains in force: this implementation record is updated in the same session as every repository modification, including these final hardening edits.
- Validation performed: fetched the resulting main files after each write, confirmed the new read-range contract is present, confirmed remote reads are bounded, confirmed the obsolete inventory method/imports are absent, and confirmed the tool loop now handles malformed/disabled calls without throwing.
- GitHub Actions build/test status for these latest commits is not marked successful unless a completed run/status is available through the connected GitHub surface.

# DevForge Implementation Tracker

> Living implementation record for DevForge. Updated after meaningful repository changes so the repository remains the source of truth for implemented vs planned work.

## Product / Architecture Rules
- [x] Android-first Kotlin + Jetpack Compose + Material 3 foundation
- [x] SAF remains the canonical workspace boundary; no hidden filesystem assumptions
- [x] Git, AI, build and automation side effects use typed capabilities and policy/approval gates
- [x] AI output is untrusted and never grants authorization
- [x] GitHub Actions remains the cloud-build backbone; no Android SDK/NDK/toolchains are bundled
- [x] Durable state remains bounded by file, task, graph, receipt and audit limits
- [x] Secrets remain outside ordinary action payloads, receipts and logs

## Implemented

### App Foundation / UX
- [x] Kotlin/Compose/Material 3 foundation
- [x] Adaptive compact/expanded navigation
- [x] Chat, Files, Git, Diffs, Build, Automation, Approvals and Settings destinations
- [x] Dark-first original DevForge visual direction
- [x] Destination history/back handling and nested editor/folder behavior
- [x] Unsaved editor and root-exit confirmations
- [x] API-36 Android CI/toolchain baseline

### Workspace / Editor
- [x] SAF folder picker and persistable permissions
- [x] Room-backed multiple workspaces and active-workspace persistence
- [x] Recursive navigation, breadcrumbs, bounded enumeration and search
- [x] Safe text preview and binary detection
- [x] Multi-tab editor, dirty tracking, save/recovery checkpoints and immutable snapshots
- [x] Bounded editor persistence and recovery state

### Git / Review
- [x] HEAD/index/worktree observation and status classification
- [x] Native SAF stage/unstage/commit/local branch operations without shell commands
- [x] Typed Git capabilities with approval/policy routing
- [x] Safe GitHub HTTPS fetch/pull/push through bounded ephemeral JGit mirrors
- [x] Non-force push and fast-forward-only pull safety rules
- [x] Branch checkout/merge/rebase/cherry-pick with clean-worktree enforcement
- [x] Structured HEAD/index/worktree diff viewer
- [x] Bounded commit history, changed-file review and per-file history
- [x] Interactive isolated conflict-resolution editor for merge/rebase/cherry-pick
- [x] Ours/base/theirs views with bounded text editing and side-selection/deletion
- [x] Conflict continuation/abort with final clean-state and HEAD revalidation before workspace sync

### Build Center / GitHub Actions
- [x] Debug APK, release APK and release AAB targets
- [x] Workflow dispatch contract and durable build receipts
- [x] Approval-backed build dispatch
- [x] Build cancellation
- [x] Live credential runtime validation

### Approval Center / Security Policy
- [x] Durable approval queue with expiry and lifecycle
- [x] Risk/capability/workspace review UI
- [x] Approved → executing → completed/failed flow
- [x] Audit history and bounded retention
- [x] Workspace-scoped persistent capability grants with R2 ceiling
- [x] Protected destructive/high-risk capabilities cannot receive persistent bypass grants
- [x] Path-scoped persistent capability grants
- [x] Biometric-protected secret-store primitive for high-security credentials
- [x] Biometric integration across all existing credential consumers/UI
- [x] Expanded bounded agent execution receipts with scope/capability/risk/approval metadata

### Durable Room State
- [x] Room v12 schema and migrations
- [x] Durable editor tabs/snapshots
- [x] Persistent agent-task state and resumable step pointers
- [x] Automation definition/run persistence foundation
- [x] Durable automation trigger state for repository/build event deduplication
- [x] Durable audit-event storage
- [x] Durable capability-grant storage
- [x] Model-scoped persistent chat sessions/messages

### AI / Agent
- [x] Provider-neutral model domain with Google Gemini, OpenAI, Anthropic, xAI Grok, DeepInfra, Groq, OpenRouter, and custom OpenAI-compatible providers
- [x] Secure provider-key storage through Android Keystore
- [x] Live model catalog loading and model filters
- [x] Model-specific Room chat sessions and bounded context history
- [x] Native Gemini and OpenAI-compatible chat gateway
- [x] 20 slash commands with aliases and suggestions
- [x] Bounded `@file` mention resolution through SAF workspace search
- [x] Shared agent command bridge/catalog
- [x] Provider-neutral typed agent tool definitions and registry
- [x] Capability/policy/approval-aware agent tool gateway
- [x] Bounded SAF tools: read_file, list_files, search_workspace, patch_file and write_file
- [x] Typed shared-memory and agent-handoff coordination tools
- [x] Persistent bounded agent-task engine with resumable steps, cancellation, receipts/results and approval waiting
- [x] Persistent multi-agent workspace coordinator with up to 10 concurrent agents
- [x] Agent task hard limits: 12 steps, bounded payload/result sizes and 60-second execution window
- [x] Explicit per-agent-task path scopes persisted in the task plan
- [x] Per-agent provider/model binding persisted independently
- [x] Approval parameter hashes include the task path scope to prevent stale approval reuse after scope changes
- [x] Workspace tool operations enforce path scopes and continue rejecting traversal/.git access
- [x] Security regression tests for path traversal, scope boundaries, persisted scope and unsafe tool plans
- [x] Streaming responses
- [x] Structured patch generation + diff-first approval workflow
- [x] Workspace symbol extraction/indexing
- [x] Workspace memory/knowledge layer
- [x] Multi-agent shared memory/handoff protocol
- [x] Durable per-file mutation leases and post-lease precondition revalidation

### Automation
- [x] Durable automation definitions and run records
- [x] WorkManager-backed persistent scheduling
- [x] Schedule grammar: `daily@HH:mm`, `interval:N`, `once@ISO-8601`
- [x] Application-start recovery/rescheduling of enabled automations
- [x] Reuse of the typed agent gateway for automation actions
- [x] Automation approval waiting and resumable approval path
- [x] Bounded retry policy with exponential backoff and three total attempts
- [x] Run overlap protection and stale-run recovery after process interruption
- [x] Cancellation path for active automation runs and scheduled work
- [x] Automation start/wait/complete/fail/cancel audit events
- [x] Repository-change triggers with workspace/branch/path filtering
- [x] Build-completion triggers with owner/repository/branch/conclusion/target filtering
- [x] Condition/event matching with bounded wildcard conditions
- [x] Durable event deduplication and first-run baseline seeding
- [x] Bounded event payloads carried into durable automation run receipts
- [x] Periodic background event monitor for repository/build state changes
- [x] Rich automation editor with trigger selection, typed agent steps and explicit path scopes
- [x] Automation run history, enable/disable, run-now and delete controls
- [x] Dedicated Automation primary navigation surface
- [x] Focused automation trigger regression tests

### Settings / UX
- [x] AI provider selection UI
- [x] Secure API-key save/remove UI with masked key field
- [x] AI model defaults/routing settings
- [x] GitHub repository settings
- [x] Build/terminal/automation/security settings
- [x] Privacy/data-retention settings
- [x] Theme/density/editor preferences

### 2026-09-20 — Repository build outputs & direct downloads
- Added per-repository build-output settings stored on-device when choosing a GitHub repository: build selected APK/AAB, upload build artifact, lint report, unit-test report, and dependency report.
- Extended the DevForge Android CI workflow contract with explicit artifact/report inputs and upload steps.
- Build Center now separates build files from reports and lets users download artifacts directly from the app.
- Downloads are fetched from GitHub Actions and extracted into Android Downloads/DevForge, so APK/AAB and enabled report files are present on-device without opening GitHub.
- Build approval hashes/configuration now include artifact/report preferences so approvals bind to the exact requested build settings.

### 2026-09-20 — GitHub Codespaces and live Actions
- Added GitHub Codespaces account/repository management backed by GitHub's Codespaces REST API: list, create, start, stop, delete, machine discovery/change, Git status, idle timeout/retention display, and billing usage.
- Added the official GitHub Codespaces icon path as a native Compose vector.
- Added an in-app GitHub.dev WebView surface so the actual Codespaces editor, terminal, Ports, extensions and other GitHub-provided editor features remain inside DevForge rather than opening an external browser.
- Added Live GitHub Actions monitoring with frequent polling, animated running indicators, expandable workflow job state and in-app job logs.
- Added Help & guide documentation for the new Codespaces and live Actions surfaces.

### 2026-09-20 — Unified DevForge activity notifications
- Added a normal activity notification channel for important build completion/failure events while retaining the dedicated high-priority approval channel and its 15-second action window.
- Documented the notification behavior in Help & guide.

### 2026-09-20 — GitHub Hub issue and pull-request browser
- Added bounded read-only open-issue and open-pull-request retrieval for the selected GitHub repository inside DevForge Center.
- Added repository activity selection and in-app documentation; repository creation remains separately available from the same hub.

### 2026-09-20 — Build failure diagnosis
- Added deterministic Build Center log diagnosis that groups common Kotlin, Gradle, lint, test, signing, GitHub Actions, network and Android SDK failure signatures with bounded evidence lines.
- Exposed the diagnosis through DevForge Center and documented it in Help & guide.

### 2026-09-20 — DevForge Center and direct GitHub repository creation
- Added a DevForge Center surface grouping Project Health, AI Run Inspector, Testing Center, Dependencies, Offline Queue, Workspace Backup, Release Center, GitHub Hub, Automation expansion, Code Intelligence and Extensions/MCP security guidance.
- Added bounded editor/workspace backup export/import without credential material.
- Added a persistent bounded offline action queue primitive for supported remote intents.
- Added direct GitHub repository creation from DevForge using the connected account, including visibility, initialization, GitHub feature toggles, .gitignore, license template and default-branch handling.
- Created repositories can immediately be opened as the active GitHub-backed DevForge workspace and AI context.
- Updated the in-app Help & guide with dedicated documentation for every new surface and repository-creation workflow.

## In Progress / Next Sequence
1. Current CI validation and evidence capture for the newly implemented feature set.
2. Production release-signing validation when production signing material is configured.

## Planned

### Editor Intelligence
- [x] Syntax highlighting/language-aware editing
- [x] Problems/diagnostics panel
- [x] Undo/redo, find/replace and go-to-line/symbol
- [x] Code folding and selection/edit actions
- [x] Large-file safeguards/editor preferences

### Terminal / Execution
- [x] Sandboxed terminal capability
- [x] Command/path/argument validation
- [x] Resource/time limits and bounded combined output
- [x] Cancel/terminate handling for the native process runner
- [x] No unrestricted arbitrary AI shell access
- [x] Terminal capability remains separate from the provider-neutral AI agent tool registry
- [x] Streaming terminal output
- [x] Terminal sessions/tabs

### Automation Expansion
- [x] Repository-change triggers
- [x] Build-completion triggers
- [x] Capability-scoped action graphs through typed agent plans
- [x] Approval checkpoints through the shared agent gateway
- [x] Initial event idempotency/deduplication guard through durable trigger state and WorkManager unique work
- [x] Cross-event orchestration and richer recovery semantics
- [x] Pause/enable/disable controls and automation editor

### Quality / Observability
- [x] Focused agent security regression tests
- [x] Focused automation trigger regression tests
- [x] Maintained pure-Kotlin regression suite for bounded/security-critical domain rules
- [x] Maintained unit-test suite for agent plans, workspace scopes, terminal policy and automation triggers
- [x] Compose/UI tests
- [x] Static analysis/lint pipeline
- [x] Release build validation
- [x] Performance/budget checks
- [x] Structured diagnostics
- [x] Crash/recovery validation
- [x] Security/redaction regression tests
- [x] Agent execution timeline and lifecycle correlation

### 2026-09-19 — Chat model filters and editor AI hardening
- Replaced the model-filter chip strip with a horizontally virtualized row so every filter remains full-width and tappable instead of collapsing the last chip vertically on narrow screens.
- Kept model filtering based on provider-reported pricing/capability metadata; unknown pricing remains explicitly unknown rather than being misclassified.
- Hardened inline Editor AI model selection to prefer the currently saved model only when it is present and text-capable in the live provider catalog, otherwise fall back to a discovered text model or a safe saved model when the catalog endpoint is temporarily unavailable.
- Added an explicit credential-lock error path for Editor AI and restored the bounded 2 MiB editor-AI file-size constant.
- Added regression coverage for workspace/editor path behavior used by agents.

## Validation
- [x] Historical CI toolchain/debug-build validations
- [x] Current agent milestone triggered a fresh Android CI run after commit
- [x] Security hardening changes triggered a fresh Android CI run after commit
- [ ] Interactive conflict editor CI result verification
- [ ] Current automation event-trigger/UI CI result verification
- [x] Fixed CI-reported Kotlin compile errors in navigation, automation scrolling/model syntax and Git history parser
- [ ] Security hardening CI result verification
- [ ] Biometric credential integration CI result verification
- [ ] Parallel workspace agents CI result verification
- [ ] Maintained regression suite CI result verification
- [ ] Path-scoped capability grant CI result verification
- [ ] Structured patch / diff-first approval CI result verification
- [ ] Multi-agent coordination CI result verification
- [ ] Live authenticated remote Git validation
- [ ] Release APK/AAB signing validation
- [ ] Current lint CI result verification
- [ ] Current Compose UI CI result verification
- [ ] Current release validation CI result verification
- [ ] Current crash/recovery CI result verification
- [ ] Current security/redaction CI result verification
- [ ] Current agent observability CI result verification
- [x] Maintained unit/UI/security regression suites

### 2026-09-18 — Android lint validation
- Extended the Android CI workflow with an explicit `:app:lintDebug` validation step after the maintained unit-test suite.
- Lint failures now fail the CI job instead of being hidden behind the APK build result.
- CI retains bounded HTML/XML/SARIF lint reports as a diagnostic artifact when produced; no local or remote CI result is marked successful until observed.

### 2026-09-18 — Compose UI navigation regression coverage
- Added an AndroidX/Compose instrumentation regression test covering Files navigation, one-step Back behavior and the root exit confirmation.
- Configured the Android instrumentation runner and Compose UI test dependencies.
- Added a dedicated Android UI-test workflow with a bounded API-35 emulator, connected Compose UI tests and retained instrumentation reports.
- UI CI verification remains unchecked until an actual GitHub Actions result is observed.

### 2026-09-18 — Release build validation
- Added a dedicated release-validation workflow that builds both the release APK and release AAB without production signing secrets.
- The workflow verifies both unsigned artifacts are non-empty and records SHA-256 hashes before uploading them for inspection.
- Signed release APK/AAB validation remains a separate concern and stays dependent on configured production signing material.

### 2026-09-18 — Performance and build budgets
- Added a reusable `tools/check-build-budget.sh` guard for bounded artifact size, SHA-256 reporting and optional build-time budgets.
- Release validation now enforces an 80 MiB ceiling for the unsigned APK and AAB and a 15-minute combined Gradle release-build budget.
- Budgets are explicit CI guardrails rather than runtime limits; observed CI results remain tracked separately.

### 2026-09-18 — Structured diagnostics
- Added a bounded source-agnostic diagnostic model with severity, source, code, origin and file/line/column location data.
- Added compiler-text and SARIF parsers for normalized diagnostic ingestion.
- Added a versioned JSON codec with bounded output for persistence/transport and fail-closed handling of malformed or unknown data.
- Added regression coverage for parsing, round-tripping, input caps and malformed SARIF.

### 2026-09-18 — Crash and recovery validation
- Centralized interruption recovery policy for agent tasks and stale automation runs.
- Agent planning/running states continue to recover to PAUSED after process interruption; terminal, approval-waiting and completed states are not treated as interrupted work.
- Automation RUNNING/WAITING_APPROVAL runs older than the bounded five-minute threshold are explicitly recoverable; fresh active runs remain protected by overlap checks.
- Added pure-Kotlin regression coverage for recovery state classification and the stale-run boundary.

### 2026-09-18 — Security and redaction regression coverage
- Added a centralized best-effort `SecretRedactor` for data crossing into audit logs, approval payloads and durable execution receipts.
- Redacts credential-named JSON values, bearer/basic authorization material and common provider token formats without altering authorization hashes or live tool arguments.
- Applied redaction to agent task results, agent receipts/audit metadata, automation receipts/audit data and generic audit recording.
- Added regression tests proving credential values are removed while non-secret data remains available and output stays bounded.

### 2026-09-18 — Agent execution observability
- Added first-class agent lifecycle audit events for start, pause, resume, approval wait, completion, failure, cancellation and startup recovery.
- Startup recovery now releases interrupted task file leases immediately instead of waiting for lease expiry.
- Agents UI now shows a bounded execution timeline correlated to each task through its task-scoped audit action IDs.
- Lifecycle summaries and persisted metadata remain redacted/bounded at the audit boundary.

### 2026-09-18 — Comprehensive settings and retention
- Added durable DevForge preferences for AI routing, GitHub repository defaults, build/terminal/automation limits, privacy retention and appearance/editor preferences.
- Wired build polling, automation monitoring cadence and startup data-retention pruning to those settings.
- Integrated configurable theme/density and editor font/wrap/visible-whitespace behavior.

### 2026-09-18 — AI streaming and routing
- Added bounded SSE streaming for Gemini, OpenRouter and OpenAI-compatible chat endpoints.
- Added deterministic cancellation cleanup with partial-response persistence and a Stop control.
- Added fixed, balanced, low-cost and quality model-routing modes while preserving explicit saved-model selection.

### 2026-09-18 — Workspace intelligence
- Added a bounded SAF-backed symbol extractor/index with workspace-wide file budgets.
- Added durable workspace knowledge notes with size limits and likely-secret rejection.
- Fed workspace knowledge into agent planning explicitly as untrusted context.

### 2026-09-18 — Editor intelligence
- Added lightweight language detection, syntax highlighting, bracket/string diagnostics and fold ranges.
- Added undo/redo with bounded memory, find/replace, go-to-line, symbol navigation and selection actions.
- Added large-file safeguards and visible-whitespace transformation.

### 2026-09-18 — Terminal sessions and streaming
- Added bounded terminal sessions/tabs and live chunked native process output.
- Preserved the existing executable allowlist, sandbox, timeout/output limits and approval checks.

### 2026-09-18 — Automation orchestration and recovery
- Extended condition triggers to match multiple repository/build event types through bounded event sets and wildcard conditions.
- Stale automation recovery now cancels linked agent tasks, records recovery audit events and preserves the durable run as the source of truth.
- User cancellation is now explicitly audited.

### 2026-09-18 — Roadmap implementation completion
- Completed all previously unchecked implementation items across AI streaming/routing, workspace symbols/knowledge, settings/retention, editor intelligence, terminal streaming/sessions, and cross-event automation recovery.
- Added regression coverage for model routing, workspace symbol extraction, editor intelligence and cross-event automation matching.
- Corrected source-level integration issues found during review, including editor syntax-regex generation and app-shell editor wiring.
- CI/signing/remote-service validation checkboxes remain separate and are only marked after observable GitHub/remote evidence.

### 2026-09-18 — Placeholder, flaw and hardening audit
- Removed enabled no-op actions from the global search/security shell and made repository visibility status explicitly read-only.
- Added visible workspace search results so the global Search action now completes an actual user-visible flow.
- Bounded non-streaming AI, model-catalog and GitHub REST response bodies; added model-ID and GitHub owner/repository validation.
- Made AI streaming cancellation close the underlying HTTP connection instead of relying only on coroutine cancellation.
- Restricted automatic AI routing to chat-capable text-output models so embedding/image-only models cannot be selected for chat.
- Bound terminal approvals to their originating terminal session and rejected ambiguous oversized SAF document names in the JGit mirror.
- Made automation cadence changes reschedule the background monitor immediately.
- Added GitHub repository gateway regression tests for unsafe identifiers.
- Preserved main-branch CI runs instead of cancelling them on every subsequent push; pull-request concurrency cancellation remains enabled.
- Final placeholder scan: no TODO/FIXME/HACK/stub/coming-soon implementation placeholders; remaining no-op callbacks are disabled/read-only UI affordances.
- CI validation remains pending for the current main snapshot and is intentionally not marked successful without a completed GitHub Actions result.
### 2026-09-18 — AI streaming animation variety
- Replaced the single streaming progress indicator in Chat with a reusable animated status component.
- Added 12 lightweight animation variants: hammer, chainsaw, running man, thinking man, typing, rocket, coffee, gear, spark, wave, fire and robot.
- Each generation selects a stable animation for its lifetime, while the component remains bounded and asset-free for mobile performance.
- Streaming status is now visible immediately while a request is active, including the pre-first-token wait; response text remains optional until content arrives.
- Added regression coverage for the animation catalog size, uniqueness and safe index wrapping.

### 2026-09-18 — Chat attachments and agent launch console
- Streaming animation selection is now randomized at every submitted chat message, with the chosen animation held stable for that generation.
- Added a bounded device attachment menu in the chat composer for files (15 MiB), photos (15 MiB), videos (50 MiB), audio (30 MiB) and documents (10 MiB), with per-URI size validation and an 80 MiB aggregate pending-attachment budget.
- Added the 👤 Agents launcher to the Chat top bar.
- Expanded the agent coordinator to support up to 10 active or queued one-shot agents; execution remains finite and ends when the durable task reaches a terminal state.
- Added batch agent launch with either one shared provider/model or independent provider/model selections for each agent, using the live model catalog where available.
- Added persistent premade/custom agent profiles with editable name, description, instructions and access switches.
- Added explicit agent access policy enforcement at the tool gateway for workspace/file and shared-coordination tools; disabled access cannot be bypassed by model output.
- Agent profiles expose only currently registered execution capabilities; undeployed capabilities are intentionally not presented as permission toggles.
- Added regression coverage for agent access encoding and enforcement.

### 2026-09-18 — Compile and editor hardening follow-up
- Fixed release-compiler integration errors by importing the existing editor-intelligence types in the app shell.
- Updated terminal Compose/coroutine integration for the current Compose API surface and approved-output callback scope.
- Optimized editor fold-range discovery from repeated prefix scans to a single bounded pass, preserving the 80-range ceiling.
- Added a regression test covering the fold-range ceiling on larger generated source input.
- Release/Android/UI validation remains evidence-driven and is not marked successful until GitHub Actions completes.

### 2026-09-18 — Editor word-wrap behavior
- Wired the persisted Word wrap preference into the code editor.
- Word-wrap enabled keeps the editor constrained to the viewport; disabled mode uses bounded horizontal scrolling instead of single-line mode, preserving source newlines.

### 2026-09-18 — Validation follow-up
- Fixed a malformed workspace-symbol unit-test fixture that caused debug unit-test compilation to fail even though the production app compiled successfully.
- Stabilized the Compose navigation regression test by targeting the unique Files navigation content description instead of ambiguous visible text.
- Migrated the navigation test to the Compose UI-test v2 rule API to remove the deprecated test harness.
- Release validation has already completed successfully on the preceding snapshot; fresh Android/unit/UI validation is required for this latest correction.

### 2026-09-18 — Chat attachment validation hardening
- Removed the asynchronous-baseline race from attachment selection by merging validated results against the latest main-thread state.
- Enforced the eight-attachment and 80 MiB pending limits at merge time so overlapping picker callbacks cannot overwrite newer selections.
- Restricted the Document picker category to recognized text/document MIME families instead of accepting arbitrary non-media binaries.
- Added regression tests for media separation and document MIME validation.

### 2026-09-18 — Deep bug, race, and state-integrity hardening
- Fixed agent cancellation races so paused/cancelled tasks cannot be overwritten as generic failures.
- Added durable approval recovery, approval-to-agent resume handling, and rejection/cancellation cleanup for interactive agent tasks.
- Enforced the ten-agent non-terminal task limit atomically at the Room persistence boundary, covering all agent producers.
- Made automation run overlap protection atomic and independent of limited recent-run history.
- Prevented editor open/save races from overwriting newer user edits and made workspace search/refresh operations stale-result safe.
- Prevented stale AI provider/model session selection and bound each request to its captured provider.
- Hardened SAF workspace search with visited-node and depth/result limits plus malformed-cursor guards.
- Fixed GitHub Actions job-log endpoint construction and disabled HTTP redirects across GitHub API requests.
- Distinguished stored-but-unverified GitHub credentials from live verified connections.
- Hardened durable task/profile/approval metadata against secret persistence.
- Fixed GitHub repository ViewModel background-thread Compose state mutation.
- Removed the CI validation backlog pattern by changing push/PR workflows to cancel superseded runs while preserving manually dispatched runs.
- Existing completed validation evidence is retained; the latest post-fix workflows must complete before the current snapshot is marked fully validated.

### 2026-09-18 — Deep repository audit and hardening
- Repaired concrete Kotlin/Compose compile blockers found by GitHub Actions in AI gateway, chat, diagnostics and editor integration.
- Removed stale/fake agent access controls that had no registered execution capabilities; agent permissions now fail closed and are enforced both at planning and execution boundaries.
- Removed the dead Git `NotImplemented` status placeholder state rather than presenting an unreachable fake capability.
- Hardened chat attachments with asynchronous SAF metadata/size validation, attachment-only submission support, bounded text extraction and explicit local-only handling for binary content.
- Hardened AI network handling with provider-specific model-ID validation, disabled redirects on fixed API requests, bounded SSE event lines and guaranteed connection cleanup when bounds are exceeded.
- Redacted durable chat/profile/error/receipt data and rejected secret-like executable agent/automation payloads where redaction would corrupt execution state.
- Closed an agent lifecycle race that could start duplicate executions for the same persisted task.
- Isolated CI validation runs per workflow invocation after observed main-branch cancellation behavior, so later commits no longer intentionally share a cancellation group.
- Repository placeholder/no-op audit remains clear: remaining disabled callbacks are intentionally read-only status affordances; no executable TODO/FIXME/HACK/stub/coming-soon implementation placeholders remain.
- Current external validation is still asynchronous and must remain unchecked until completed GitHub Actions evidence is observed.

## Implementation Rules
- Never fake Git status, builds, authentication, agent execution or terminal execution.
- AI output is untrusted data and cannot grant authorization.
- All consequential side effects remain behind typed capability/policy/approval boundaries.
- Keep mobile operations bounded and report partial/unavailable states explicitly.
- Never persist API keys or secrets in ordinary domain state, approvals, receipts or logs.
- Do not bundle heavyweight Android SDK/NDK/toolchains.
- Avoid broad refactors unless required by the current milestone.
- Update this tracker after every meaningful repository change.

## Change Log
### 2026-09-17 — Foundation through remote build
- Established Android-first architecture, SAF workspace, editor/recovery, Git observation, Build Center, GitHub discovery, capability/policy primitives, Keystore-backed secrets and Android CI.

### 2026-09-17 — Navigation / Chat cleanup
- Added destination history/back behavior, nested folder/editor handling and root exit confirmation.
- Removed unrelated workspace/Git/build/agent status cards from the Chat landing surface.

### 2026-09-17 — Capability-controlled Git execution
- Added native stage/unstage/commit/branch operations, typed capabilities and Approval Center routing.

### 2026-09-17 — Structured Git diff viewer
- Added bounded HEAD/index/worktree diff computation and read-only structured review.

### 2026-09-17 — Safe remote Git transport
- Added validated GitHub HTTPS fetch/pull/push through bounded ephemeral JGit mirrors and Approval Center routing.

### 2026-09-17 — Branch/history execution layer
- Added approval-backed checkout/merge/rebase/cherry-pick execution with clean-worktree and conflict-safe temporary-state rules.

### 2026-09-17 — Durable Room state layer
- Added durable editor, agent-task, automation/run and audit persistence foundations.

### 2026-09-17 — Approval audit and persistent capability grants
- Added workspace-scoped capability grants, policy hydration, approval/grant audit triggers and destructive-capability grant protections.

### 2026-09-17 — AI model catalog, model sessions and command layer
- Added secure provider keys, live model catalogs, model filters, context-aware sessions, slash commands, bounded `@file` mentions and the shared agent command bridge.

### 2026-09-17 — Commit/file history review surfaces
- Added bounded loose-object commit history traversal, changed-file review and per-file history integrated into the existing Git history surface.

### 2026-09-17 — Provider-neutral agent gateway and persistent task engine
- Added typed agent tool registry/gateway with capability and approval enforcement.
- Added bounded SAF read/list/search/write tools without shell access.
- Upgraded Room to v7 for durable task execution state and added resumable/cancellable agent task execution.

### 2026-09-17 — Automation scheduler and run engine
- Added WorkManager-backed durable scheduling with daily, interval and one-shot schedule parsing.
- Added persistent automation execution through the typed agent gateway, approval waiting/resume, bounded retry/backoff, overlap protection, stale-run recovery and cancellation.
- Added automation audit events and application-start rescheduling of enabled automations.

### 2026-09-17 — Security hardening / path scopes, receipts and biometric secret primitive
- Added explicit `WorkspacePathScope` boundaries with traversal and `.git` rejection.
- Persisted agent path scopes inside task plans and included canonical scope data in approval parameter hashes.
- Enforced scope-aware read/list/search/write behavior through the SAF agent tools.
- Expanded agent execution receipts with task/step/tool/capability/risk/workspace/scope/affected-path/approval/timestamp metadata, bounded to the existing receipt limits.
- Added a Keystore-backed biometric-protected secret-store primitive using strong biometric authentication.
- Added focused path/scope/unsafe-plan regression tests and test dependencies.

### 2026-09-17 — Interactive Git conflict-resolution editor
- Preserved merge/rebase/cherry-pick conflicts inside a bounded app-cache JGit session instead of discarding the temporary state.
- Added bounded ours/base/theirs inspection, manual text resolution, side selection, deletion handling and per-file conflict navigation.
- Added Continue/Abort controls with a fresh clean-worktree/HEAD validation before any resolved state is copied back to the SAF workspace.
- Kept conflict sessions approval-aware and bounded by 50 conflict paths, 256 KiB per conflict file and a two-hour session lifetime.

### 2026-09-17 — Automation event triggers and rich automation editor
- Upgraded Room to v8 with durable automation trigger state for event deduplication.
- Added repository-change, build-completion and condition trigger matching with bounded branch/path/build filters and wildcard conditions.
- Added first-run baseline seeding, periodic WorkManager event monitoring, unique-event scheduling and bounded trigger payloads carried into run receipts.
- Added a dedicated Automation destination with rich trigger/action editing, explicit agent path scopes, run-now, enable/disable, delete and run-history controls.
- Added focused regression coverage for repository/build/condition matching and repository fingerprint changes.


### 2026-09-18 — Parallel workspace agents
- Added a durable multi-agent coordinator allowing up to four independent agents to execute concurrently within a workspace.
- Added per-agent provider/model bindings so agents can share one model/provider or mix different models and providers.
- Added model-driven typed planning that converts each agent instruction into the existing bounded tool plan before execution.
- Added per-agent Hold, Resume and Stop controls; holds are durable and survive process restart as paused work.
- Added startup recovery that converts interrupted running/planning tasks to PAUSED instead of silently continuing.
- Added a dedicated Agents primary destination and workspace agent dashboard.
- Kept all agent tool execution behind the existing typed capability/approval/path-scope gateway; model output cannot bypass authorization.

### 2026-09-18 — Biometric credential integration
- Added a centralized security-aware credential facade for AI and GitHub credentials.
- Migrated AI settings, AI chat, GitHub connection/repository discovery, remote Git transport and Build Center to the security-aware credential layer.
- Removed eager API-key decryption from AI settings ViewModel state; stored credentials are now represented only by presence/lock status until explicitly needed for a request.
- Added Settings controls for strong-biometric protection, unlock, immediate lock, safe migration and disable.
- Added the biometric manifest permission and FragmentActivity host support for the AndroidX biometric prompt.
- Kept protected credential migration fail-closed when any known credential cannot be read.

### 2026-09-18 — Maintained regression suite
- Expanded agent-plan tests for legacy-version compatibility, unknown tools and step/argument bounds.
- Expanded workspace path-scope tests for canonicalization, required-path enforcement and structural limits.
- Expanded terminal-policy tests for control separators, unsafe working directories and argument bounds.
- Expanded automation-trigger tests for encode/decode round trips and stable event keys.

### 2026-09-18 — Build cancellation and live credential validation
- Added a typed CANCEL_BUILD capability with R2 policy routing and Approval Center integration.
- Added normal GitHub Actions workflow-run cancellation through the documented cancel endpoint; force-cancel is intentionally not used.
- Added exact owner/repository/run binding to cancellation approvals to prevent stale payload reuse.
- Added a dedicated Cancelling state and Build Center control while preserving polling state until GitHub reports the terminal result.
- Added live GitHub credential validation through the authenticated /user endpoint during connection setup and on-demand verification.
- Build dispatch now performs live credential validation immediately before remote dispatch instead of relying only on stored-credential presence.
- Added deterministic gateway regression tests for credential validation, invalid credentials, cancellation success and cancellation conflicts.

### 2026-09-18 — Terminal capability
- Added a typed native terminal capability with a fixed executable allowlist; arbitrary executable names and shell invocation are not accepted.
- Sandboxed commands run only from an app-private per-workspace directory with inherited environment variables cleared.
- Added strict argument/path, command-size, output-size and 15-second maximum timeout limits.
- Added process cancellation/termination handling and Approval Center routing for R2 terminal commands.
- Kept terminal execution out of the provider-neutral AI agent tool registry so model output cannot directly obtain shell access.
- Added focused terminal command-policy regression tests.

### 2026-09-18 — CI compile failure repair
- Fixed invalid NavigationRail `verticalScroll` usage/import in `MainActivity.kt`; the navigation rail now relies on its bounded destination set without the unavailable modifier.
- Added the missing `horizontalScroll` import to the automation editor.
- Removed the stray Kotlin token in `AutomationModels.kt` that caused a top-level syntax error.
- Renamed the `object` local variable in `GitCommitHistoryService.kt` to `gitObject` to avoid the reserved Kotlin keyword collision.
- Triggered a fresh Android CI run from the repaired `main` state.

### 2026-09-18 — Path-scoped persistent capability grants
- Extended persistent capability grants with canonical workspace-relative path scopes while preserving existing whole-workspace grants.
- Added Room v10 migration and durable scope JSON with bounded prefix/path validation.
- Added synchronous registry enforcement so a grant bypasses approval only when its risk ceiling and path scope both contain the requested action.
- Bound agent action requests to their explicit path scope so scoped grants apply to agent edits without widening workspace authorization.
- Added Approval Center path-scope entry and visible scope details for active grants; blank scope continues to mean the entire workspace.
- Added regression coverage for scope containment, whole-workspace compatibility, path-scoped approval behavior, and risk ceilings.

### 2026-09-18 — Structured patch generation and diff-first approval
- Added a dedicated PATCH_FILE agent tool with a bounded structured patch payload and optional expected content hash.
- Agent planning now emits PATCH_FILE for new edit plans instead of direct write_file operations.
- Captured the exact target pre-image hash before approval and bound it into the durable approval record.
- Revalidated the same pre-image hash immediately before approved execution, preventing stale patches from overwriting intervening edits.
- Added Approval Center diff previews for structured patches, including new-file detection, bounded diff rendering and stale-precondition rejection.
- Kept patch execution behind the existing EDIT_FILES R2 capability, workspace path scopes and approval lifecycle.
- Added structured patch and agent-plan regression coverage; no remote CI result is marked successful without verification.

### 2026-09-18 — Multi-agent shared memory, handoffs and conflict-aware editing
- Upgraded Room from v10 to v12 with durable shared-memory, handoff and file-lease records and sequential migrations.
- Added bounded workspace shared memory with one value per key, source-task attribution, 100-entry retention and secret-pattern rejection.
- Added durable handoffs with optional target-agent routing, pending/claimed/completed lifecycle, claim ownership and workspace validation.
- Added typed coordination tools for reading/writing shared memory and listing, creating, claiming and completing handoffs.
- Extended agent planning so new tasks receive recent shared memory and pending handoff context as untrusted coordination data.
- Added durable per-file mutation leases so concurrent agents cannot silently overwrite the same workspace file; leases expire automatically and are released after each mutation.
- Added post-lease precondition revalidation so a file change between initial review and execution causes a safe retry failure instead of an overwrite.
- Added Agents UI visibility for shared memory, active handoffs and current file locks.
- Added regression coverage for the expanded coordination tool set.

### 2026-09-18 — Multi-agent shared memory, handoffs and conflict-aware editing
- Upgraded Room to v12 with durable shared-memory, handoff and file-lease records plus sequential migrations.
- Added bounded workspace shared memory with key-level replacement, source-task attribution, retention limits and rejection of common credential/secret patterns.
- Added targeted and broadcast-style durable handoffs with pending/claimed/completed lifecycle and claim ownership enforced by task identity.
- Added typed coordination tools for shared memory and handoff creation/consumption, all inside the existing agent capability gateway.
- Agent planning now receives recent shared memory and available handoffs as untrusted workspace coordination context.
- Added durable per-file mutation leases with a two-minute maximum lifetime; overlapping agent mutations of the same file are serialized or rejected.
- Added post-lease precondition revalidation to close the race between initial review and mutation lock acquisition.
- Added Agents UI visibility for recent shared memory, active handoffs and active file locks.
- Added regression coverage for the expanded coordination tool set.
- CI result remains unchecked until a real GitHub Actions run is observed.

## Mobile UI, credential storage, and premade agents — 2026-09-18

- [x] Fixed ordinary credential encryption to initialize Android Keystore AES keys with KeyGenParameterSpec instead of the unsupported bare key-size initialization.
- [x] Keystore credential regression test added for encrypt/decrypt/remove on an Android instrumentation device.
- [x] API provider settings simplified for small screens: provider dropdown, large API-key field, stacked actions, readable status, and no redundant model-catalog card.
- [x] Settings changed from one long stack of nested cards to a small settings hub with focused sections; App settings are also split into individual sections.
- [x] Compact navigation changed from ten horizontally scrolling destinations to five primary destinations plus a More menu, preventing tiny/wrapped labels.
- [x] Simplified the More screen by removing the Diffs entry and the terminal availability note; Terminal remains accessible from the top-right action bar.
- [x] Removed custom agent profile creation, editing, and deletion from the product surface and runtime repository.
- [x] Added 15 immutable premade agents: Coder, Debugger, Reviewer, Security Auditor, Test Engineer, Refactorer, Performance Optimizer, Android UI Engineer, Architecture Analyst, API Integrator, Data & Storage Engineer, Build & CI Analyst, Documentation Writer, Dependency Auditor, and Coordinator.
- [x] Premade agent catalog test verifies count, builtin status, unique IDs, and use of only registered access capabilities.
- [x] Navigation instrumentation test updated for the simplified compact navigation.

### Current validation

`main` currently advances rapidly because each direct commit triggers the push workflows. GitHub Actions is the validation authority; the latest runs must be matched to the newest `main` SHA before claiming validation success.


## Multi-provider AI expansion — 2026-09-18

- [x] Expanded provider registry from Gemini/OpenAI/OpenRouter to Google Gemini, OpenAI, Anthropic Claude, xAI Grok, DeepInfra, Groq, OpenRouter, and a configurable OpenAI-compatible endpoint.
- [x] Added a single provider configuration registry for wire protocol, default API base URL, chat endpoint and model-catalog endpoint resolution.
- [x] Added native Anthropic Messages API transport with `x-api-key`, `anthropic-version`, bounded non-streaming responses and SSE text streaming.
- [x] Added OpenAI-compatible chat transport for xAI Grok, DeepInfra, Groq, OpenRouter, OpenAI, and custom compatible servers.
- [x] Added live model discovery for Anthropic, xAI, DeepInfra, Groq, OpenRouter, OpenAI, and custom OpenAI-compatible `/models` endpoints.
- [x] Added custom OpenAI-compatible base URL and optional fallback model ID settings. HTTPS is required; insecure HTTP is limited to localhost.
- [x] Removed the previous device-side DuckDuckGo context-window lookup fallback; unknown provider context limits now remain unknown instead of triggering an unsolicited external search.
- [x] Agent planning and agent model discovery now use the same provider-specific settings, so newly supported providers are available to agent tasks as well as Chat.
- [x] Preserved provider-safe binary attachment behavior: Gemini has a real Files API adapter; unsupported binary transports remain explicitly rejected rather than silently downgraded.
- [x] Added pure-Kotlin regression coverage for provider registration, endpoint construction, and custom URL safety validation.

Current provider API notes: xAI exposes `/v1/chat/completions` as an OpenAI-compatible predecessor to its newer Responses API; Groq exposes OpenAI-compatible `/openai/v1/chat/completions`; DeepInfra exposes OpenAI-compatible `/v1/openai/chat/completions`; Anthropic uses its Messages API. DevForge uses these documented transports while keeping provider-specific auth and endpoint construction explicit.
## Provider-aware binary attachments — 2026-09-18

- [x] Added a provider-neutral attachment adapter contract.
- [x] Added a streaming/resumable Google Gemini Files API adapter for binary attachments.
- [x] Images, audio, video, and binary documents can now be uploaded to Gemini without converting the full file into a giant Base64 string.
- [x] Attachment upload is cancellation-aware, size-bounded, MIME-aware, and uses strict HTTPS remote-URI validation.
- [x] OpenAI and OpenRouter binary uploads remain explicitly unsupported until provider-specific transport is implemented; DevForge rejects those uploads instead of claiming the binary content was sent.
- [x] Chat now passes pending attachments through the provider adapter path while preserving bounded text extraction for code/text files.
- [x] Added unit coverage for text/binary classification and unsupported-provider rejection.


### 2026-09-20 — Remote terminal, commit recovery, and in-app workflow setup
- Added a GitHub-backed terminal reader for remote workspaces. pwd, ls, cat, head, tail, wc, grep, and find read directly from GitHub instead of mounting devforge://github/... through SAF.
- Added GitHub commit detail retrieval with per-file additions/deletions/status and affected-folder counts in the Git screen.
- Added remote commit reversal that restores parent-tree blobs and pushes a new reverse commit to the selected branch.
- Added in-app creation/repair actions for Android CI, Android UI Tests, and Release validation workflow files.
- Added a live Running Workflows panel backed by GitHub Actions run data, including actual GitHub run numbers.


### 2026-09-20 — Termux-style terminal UI and language-aware file icons
- Replaced the chat/card-style Terminal surface with a black, monospace terminal UI.
- Removed the separate boxed command composer and moved input directly onto the terminal prompt line.
- Removed terminal chat-style action rows; command history remains available through terminal Up/Down keys.
- Kept workspace-aware local and GitHub-backed execution behavior unchanged while changing only the presentation/input surface.
- Added a reusable VS Code-inspired language/file icon pack with extension and special-filename detection for Python, JavaScript/TypeScript, Kotlin, Java, Go, Rust, C/C++, C#, Dart, Swift, Ruby, PHP, HTML/CSS, JSON/XML, YAML, SQL, shells, Gradle, Docker, Git files, media, archives and common text/config formats.
- File-tree badges now derive from the actual filename so newly created or renamed files immediately receive the correct language icon.


### 2026-09-20 — Immersive terminal shell surface
- Terminal is now a full destination-level black shell surface without the normal DevForge top bar, bottom navigation, or navigation rail.
- The terminal keeps system Back navigation through the existing destination history while presenting its own compact terminal chrome.
- The command field remains an actual keyboard-editable shell input, but it is rendered directly after the shell prompt with no textbox/card/container treatment.
- Terminal sessions remain switchable from the terminal session strip, and command history remains available through Up/Down keys.


### 2026-09-20 — Dedicated Git commit history screen
- Commit messages in the GitHub Git activity screen now open a dedicated commit-history screen instead of expanding commit details inline.
- The dedicated screen is intentionally outside bottom navigation and replaces the normal Git destination chrome while open.
- It shows the bounded full commit list for the selected branch, branch switching, refresh, commit SHA/author/time, full commit message, parent commits, file/folder change counts, additions/deletions and every returned changed-file entry.
- Reverse is exposed inside the selected commit detail with an explicit confirmation; it creates and pushes a new reverse commit instead of rewriting existing history.
- Android Back returns from the commit-history screen to the Git screen; navigating to another destination also closes the internal history surface.

### 2026-09-20 — UI cleanup, remote workspace chat, and GitHub workflow provisioning
- Removed redundant settings detail back-navigation guidance, the duplicate empty-state Automation create action, and the redundant Build Center ready-status card.
- Removed the visible chat workspace badge while preserving the active workspace binding used by chat and the agent runtime.
- Fixed GitHub-backed workspace agent search and patch execution so remote `devforge://github/...` workspace URIs never enter SAF/local filesystem code paths.
- GitHub repository selection no longer requires an existing Actions workflow; repositories without workflows can be selected and their CI, UI-test, or release workflows can be created directly from Build Center.
- Build dispatch now verifies that the configured workflow exists and is active before enabling remote execution; creating the configured workflow immediately refreshes that capability.

### 2026-09-20 — Provider-neutral AI tool calling
- Expanded the typed agent tool catalog to 20 user-facing tools spanning workspace inspection/editing, web search/scraping/fetching, link extraction, calculation and current time.
- Added More → AI Tools with persistent per-tool enable/disable settings shared by all AI providers and models. Delete Path is disabled by default.
- Registered the web and utility providers inside the existing typed AgentToolGateway rather than creating a parallel execution path.
- Added bounded public-web search/scrape/fetch/link tools with redirect limits, response-size limits and SSRF protections for localhost/private/link-local destinations.
- Added provider-neutral ChatToolOrchestrator so models without native function calling can use the same tools through a strict XML/JSON tool envelope and bounded tool-use loop.
- Added live Chat tool activity UI showing animated Calling <tool> states and completed/failed results while a tool-use turn is running.
- Normal chat mutation intent now remains in the shared tool loop; explicit /agent continues to use the durable multi-agent runner.
- Added new workspace find/info/count/hash/content-search/tree tools and exposed the expanded catalog to agent planning.
- Documented the feature under More → ⓘ Help & guide → AI Tools and kept execution behind existing workspace scope, policy, file-lease, precondition and approval boundaries.
- Chat tool execution reuses the configured provider gateway and existing attachment adapters; destructive Delete Path remains approval-gated even in autonomous agent mode.
- Added an Android Compose regression test for More → AI Tools covering the shared catalog, web tools, and destructive-tool default state.

## DevForge editor identity and agent UX (2026-09-20)

- Added Project Pulse strip above the active editor with workspace, branch, change count and current build state.
- Added an Activity Rail inside the editor for Project Activity, Agents and Replay/history.
- Added Project Activity / Change Story dialog backed by the workspace audit trail, including agent tool/action events.
- Added DevForge Replay controls that re-run completed/failed/cancelled agent tasks using their persisted instruction/model/access context.
- Moved the Agents action out of the global top bar and into the compact AI Chat popup.
- Changed AI Chat from near-full-screen to a centered compact popup with visible Agents and close controls.
- Expanded built-in agent profiles to use the bounded coding tool surface, including workspace/file tools, web tools and agent coordination tools; destructive actions remain gateway/approval controlled.


## Bounded workspace context and on-demand code retrieval — 2026-09-20

- [x] Added a provider-neutral `WorkspaceContextService` with stable workspace identity, GitHub repository/branch binding, compact context versions, and bounded rich snapshots for files, Git, build receipts and editor state.
- [x] Chat now repeats only a compact authoritative workspace identity contract instead of injecting the workspace root listing or stored workspace notes into every request.
- [x] Added an internal `get_workspace_context` agent tool. It is always available to AI/agents, is not part of the 20 user-toggleable AI tools, and supports summary/files/git/build/editor/all scopes with hard output bounds.
- [x] Workspace mutations invalidate the context version so later context reads cannot silently reuse stale workspace state.
- [x] Chat tool instructions now require evidence-first codebase investigation: for questions such as where a service/class/function is implemented, search workspace names/content first, then read the exact relevant file; continue requesting related files/folders when the evidence is insufficient.
- [x] Existing bounded workspace tools (`search_workspace`, `search_content`, `find_files`, `directory_tree`, `read_file`) remain the source of truth for relevant code retrieval, including GitHub-backed workspaces.
- [x] Agent planning now uses compact workspace context instead of injecting a large recursive repository inventory into the planner prompt while retaining exact-path inspection tools for deeper discovery.
- [x] Removed automatic per-turn workspace-knowledge injection from Chat; saved workspace knowledge remains available to the workspace surfaces and agents but is not silently added to every prompt.
- [x] Added regression coverage for workspace-context version invalidation and internal workspace-context access rules.
- [x] The 20 user-facing AI tools remain unchanged; workspace context is a separate internal capability so normal tool settings stay simple and token use remains bounded.


## Documentation invariant — effective 2026-09-20

- [x] **Every repository modification must update `IMPLEMENTATION.md` in the same implementation session.**
- This applies to feature work, bug fixes, refactors, UI-only changes, configuration changes, tests, CI changes, documentation changes, one-line/tiny fixes, and wiring changes.
- Each entry must record what changed, why it changed, the affected architectural surface/files, important behavior and safety boundaries, tests/validation performed, and any known limitation or unverified CI state.
- Do not treat a change as complete until the corresponding `IMPLEMENTATION.md` entry has been committed to `main`.
- Documentation must describe the implemented state, not merely the requested intent. When a planned architectural component was not implemented, record that explicitly rather than implying it exists.

## Bounded Workspace Context — Complete Implementation Record (2026-09-20)

### Goal

Make AI permanently aware of **which DevForge workspace it is operating in** without repeatedly injecting the workspace's complete file tree, source code, Git metadata, build information, editor state, or saved workspace notes into every model request.

The second requirement is **progressive codebase understanding**: when a user asks a question such as:

> "Where is the search service implemented?"

the AI must use workspace tools to discover the relevant files/classes/functions, read only the necessary code, and continue requesting related files/folders when the first result is insufficient.

### Implemented architecture

```
Active Workspace
      |
      +--> WorkspaceContextService
      |       |
      |       +--> WorkspaceIdentity
      |       +--> Context Version
      |       +--> Bounded Context Snapshot
      |
      +--> Chat / AIChatViewModel
      |       |
      |       +--> compact workspace identity contract
      |       +--> ChatToolOrchestrator
      |
      +--> Durable Agent Planner
      |       |
      |       +--> compact workspace context
      |       +--> evidence-first tool planning
      |
      +--> AgentToolGateway
              |
              +--> get_workspace_context
              +--> search_workspace
              +--> search_content
              +--> find_files
              +--> directory_tree
              +--> list_files
              +--> read_file
              +--> mutation tools
```

### 1. WorkspaceContextService

Added:

`app/src/main/java/com/mrredhood/devforge/core/workspace/WorkspaceContextService.kt`

The service is application-context based and obtains authoritative workspace state from existing DevForge services/DAOs rather than duplicating workspace persistence.

It exposes:

`WorkspaceContextScope`

- `SUMMARY`
- `FILES`
- `GIT`
- `BUILD`
- `EDITOR`
- `ALL`

It also exposes:

`WorkspaceIdentity`

with:

- workspace ID
- workspace display name
- GitHub owner when applicable
- GitHub repository when applicable
- GitHub branch when applicable
- context version

And:

`WorkspaceContextSnapshot`

containing the identity plus a bounded textual snapshot.

### 2. Compact identity contract

Every normal Chat request can carry only this small contract:

`DEVFORGE_WORKSPACE_CONTEXT_V1`

The contract contains:

- `workspace_id`
- `workspace_name`
- GitHub repository when applicable
- branch when applicable
- `context_version`

The contract also instructs the model:

- workspace identity is authoritative
- do not invent repository/file state
- use workspace tools for codebase questions
- fetch only relevant files/lines
- never request/inject the entire repository for a targeted question

The compact contract is capped at 900 characters.

This is deliberately repeated instead of storing hidden prompt state in the persisted chat transcript. The visible persisted chat messages do not contain the hidden workspace contract, so relying on historical messages as a context ledger would incorrectly assume that the model still has the workspace identity in stateless provider requests.

### 3. Context versioning

Added:

`WorkspaceContextVersion`

The version is kept in a process-local concurrent map keyed by workspace ID.

Behavior:

- first access starts at version 1
- repeated reads return the same version until invalidated
- invalidation increments that workspace's version
- invalidation of a blank workspace ID is ignored

This provides a lightweight freshness marker without persisting secrets or large state.

### 4. Internal get_workspace_context tool

Added:

`app/src/main/java/com/mrredhood/devforge/core/agent/WorkspaceContextTool.kt`

The provider registers an internal tool:

`get_workspace_context`

It is intentionally **not** part of the 20 user-toggleable AI tools.

Supported request shape:

```json
{
  "scope": "summary|files|git|build|editor|all",
  "limit": 40
}
```

Safety/bounds:

- scope is enum validated
- result limit is clamped to 1..120
- snapshot output is capped to 10,000 characters
- invalid JSON/scope returns a normal tool failure
- unavailable workspaces return a normal tool failure
- it requires `WORKSPACE_ACCESS`
- it is side-effect free and classified as `READ_WORKSPACE`, risk `R0`

### 5. Files/filesystem context

For `SUMMARY`, `FILES`, and `ALL`, context includes a bounded root listing.

For local workspaces it reads through the existing SAF-backed `WorkspaceFileTree`.

For GitHub-backed workspaces it reads through:

- `GitHubWorkspaceStore`
- `GitHubRepositoryGateway.listContents(...)`

The root listing is bounded by the caller's limit and is **not recursive**.

The service never injects complete source files as part of this normal context snapshot.

### 6. Git context

For `GIT` and `ALL`:

GitHub-backed workspaces report the bound repository/branch.

Local workspaces use:

- `GitRepositoryService.detect()`
- `GitWorkspaceStatusService.inspect()`

The returned Git summary can include:

- branch
- short HEAD revision
- remote URL
- bounded file-state counts such as clean/modified/staged/untracked/conflict

The status inspection is itself bounded to avoid turning Git state into an unlimited prompt payload.

### 7. Build context

For `BUILD` and `ALL`:

The service reads recent durable build receipts from `BuildReceiptDao` and filters them to the active GitHub owner/repository.

Only a small recent subset is returned.

The build summary can include:

- run number
- target
- state
- conclusion
- artifact name

No raw build log is injected into normal context.

### 8. Editor context

For `EDITOR` and `ALL`:

The service checks durable editor tabs and identifies an active tab associated with the workspace URI.

It returns only:

- active file name
- dirty/clean state

Editor source contents are **not** injected into the context summary.

### 9. Chat changes

Modified:

`app/src/main/java/com/mrredhood/devforge/core/ai/AIChatViewModel.kt`

Removed the previous `buildWorkspaceContext()` path that automatically assembled:

- workspace name
- GitHub branch/repository
- top-level workspace entries

and placed them into every normal request.

Also removed automatic per-turn injection of `WorkspaceKnowledgeRepository` notes.

Chat now builds the request from:

1. compact workspace identity
2. user instruction
3. explicit @mentions when the user actually references files
4. explicit attachment context when the user attaches something

This makes the default workspace overhead small while retaining explicit user-requested context.

### 10. Evidence-first codebase retrieval

Modified:

`app/src/main/java/com/mrredhood/devforge/core/ai/ChatToolOrchestrator.kt`

The tool-use instructions now explicitly require:

- do not answer codebase-location questions from model memory
- use `search_workspace` or `search_content` first
- then use `read_file` on the exact relevant path/lines
- continue searching/reading related files when evidence is insufficient
- use `get_workspace_context` for authoritative workspace/Git/build/editor state
- do not use workspace context as a substitute for source-code search
- fetch the smallest relevant ranges
- never dump the repository into the prompt

Example expected behavior:

```
User: "Where is the search service implemented?"

AI:
  search_workspace("search service")
       ->
  search_content("class SearchService")
       ->
  read_file("exact/path/SearchService.kt", relevant lines)
       ->
  search/read related implementation if needed
       ->
  answer with the discovered file/path and evidence
```

### 11. Existing workspace inspection tools are the AI's source of truth

No duplicate giant repository index was introduced.

The existing typed tools remain authoritative:

- `search_workspace`
- `search_content`
- `find_files`
- `directory_tree`
- `list_files`
- `read_file`
- `file_info`
- `count_lines`
- `hash_file`

They are bounded and workspace-scoped.

They support both local SAF workspaces and GitHub-backed remote workspaces through the existing remote/local split in `AgentWorkspaceTools.kt`.

### 12. Chat tool availability

Modified:

`ChatToolOrchestrator.kt`

The orchestrator now always adds:

`AgentToolId.GET_WORKSPACE_CONTEXT`

to the enabled tool set.

This means workspace identity/state retrieval is available even when a user has disabled one or more of the 20 user-facing tools.

The 20 user-facing AI tools remain exactly 20.

### 13. Agent model/tool registration

Modified:

`AgentModels.kt`

Added:

`GET_WORKSPACE_CONTEXT("get_workspace_context")`

Modified:

`AgentAccess.kt`

Authorization:

`GET_WORKSPACE_CONTEXT -> WORKSPACE_ACCESS`

Modified:

`AgentRuntime.kt`

Registered:

`WorkspaceContextToolProvider(appContext)`

through the existing provider-neutral `AgentToolRegistry`.

No parallel execution mechanism was introduced.

### 14. Agent planner changes

Modified:

`ParallelAgentCoordinator.kt`

The durable coding-agent planner now receives compact authoritative workspace context instead of automatically injecting the previous large recursive workspace inventory into its planner prompt.

It still receives:

- workspace scope
- access profile
- workspace identity
- bounded workspace context
- shared memory
- handoffs
- workspace task instruction

and retains the exact workspace inspection tools for deeper discovery.

The planner schema now advertises:

`get_workspace_context`

with the other registered tools.

This preserves the agent's ability to discover exact implementation files while lowering baseline prompt size.

### 15. Mutation-driven context invalidation

Modified:

`AgentToolGateway.kt`

After a successful side-effecting tool execution:

`WorkspaceContextVersion.invalidate(context.workspaceId)`

is invoked.

This covers agent mutations routed through the typed tool gateway and makes later context reads observe a newer version.

The invalidation does not persist file content, secrets, or tool output.

### 16. What is intentionally NOT injected automatically

The following are no longer silently injected into every Chat request:

- complete repository tree
- all file contents
- recursive source index
- full Git status
- build logs
- artifact contents
- all editor tabs/content
- all workspace knowledge notes
- entire agent/task history

These are queried only when relevant.

### 17. Token-safety model

The design separates three concepts:

**Identity:** always available, tiny.

**State:** fetched only when relevant.

**Content:** fetched only when evidence requires it.

This prevents the common failure mode where a large repository is appended to every message until the model context fills up.

Additional existing protections still apply:

- bounded chat history in `AIChatViewModel`
- bounded tool transcript in `ChatToolOrchestrator`
- bounded tool result size
- bounded agent tool arguments
- bounded file reads
- bounded search results
- bounded directory trees
- bounded workspace context snapshots
- bounded build receipt history

There is therefore no design guarantee that a provider can **never** exhaust tokens; provider context limits still exist. The implementation instead prevents DevForge from unnecessarily consuming those tokens with repeated whole-workspace injection.

### 18. Security boundaries

The workspace context layer does not grant authority.

Authorization remains owned by the existing:

`AgentToolGateway -> policy -> access -> path scope -> approval -> precondition -> mutation lease`

chain.

Tool output and workspace content remain untrusted model input.

Workspace context does not allow the AI to escape:

- SAF workspace boundaries
- GitHub-backed workspace boundaries
- `.git` protection
- agent path scopes
- approval rules
- mutation leases
- existing tool argument limits

### 19. Explicit @mentions remain supported

User-provided file mentions are still resolved by the existing `resolveMentionsForActiveWorkspace()` flow.

This is intentionally different from automatic workspace injection:

- `@SomeFile.kt` means the user explicitly requested that file's context
- unrelated repository files remain out of the prompt until tools discover them

### 20. Workspace knowledge behavior

Workspace knowledge is no longer blindly injected into every Chat turn.

This avoids turning manually stored notes into a permanent token tax.

The existing workspace knowledge feature remains available through its existing workspace/agent surfaces and can still be deliberately retrieved where appropriate.

### 21. Remote GitHub workspace behavior

The new context layer respects the existing distinction between:

- local SAF-backed workspaces
- GitHub-backed `devforge://github/...` workspaces

For remote workspaces it uses GitHub APIs rather than attempting to pass remote pseudo-URIs through SAF filesystem APIs.

### 22. Tests added/updated

Added:

`app/src/test/java/com/mrredhood/devforge/core/workspace/WorkspaceContextVersionTest.kt`

Coverage:

- version remains stable until invalidated
- blank workspace IDs do not create a versioned workspace

Updated:

`app/src/test/java/com/mrredhood/devforge/core/agent/AgentAccessWebTest.kt`

Coverage:

- `GET_WORKSPACE_CONTEXT` requires `WORKSPACE_ACCESS`
- the access rule allows the tool for a workspace-enabled agent

Existing tool catalog tests continue to protect the requirement that exactly 20 tools are user-facing.

### 23. Documentation commits from this implementation

The feature was implemented directly on `main` through these repository updates:

- `d62a0d8` — added `WorkspaceContextService.kt`
- `5f7319c` — added `WorkspaceContextTool.kt`
- `0917657` — added internal workspace-context tool ID
- `22a69bc` — authorized workspace-context access
- `2981efa` — registered workspace-context provider
- `c460909` — made Chat tool orchestration workspace-aware
- `131512a` — invalidated workspace context after successful mutations
- `782b348` — removed whole-root workspace injection from normal Chat construction
- `10d0c51` — switched agent planning to compact workspace context
- `fa9998d` — updated planner schema to advertise workspace context
- `292c756` — removed automatic workspace-knowledge injection from Chat
- `a859eb6` — kept compact workspace identity authoritative on every Chat turn
- `7c8d200` — added workspace-context access test coverage
- `561477f` — added workspace-context version regression tests
- `06ce429` — documented the feature in `IMPLEMENTATION.md`

### 24. Validation status

Source-level verification was performed against current `main` after the implementation.

Verified:

- `WorkspaceContextService` exists and is wired.
- `get_workspace_context` exists, is registered and is internally available.
- Chat no longer contains the old `buildWorkspaceContext()` implementation.
- Chat no longer automatically imports/injects `WorkspaceKnowledgeRepository` notes.
- Agent planner uses compact workspace context.
- Mutation invalidation is wired through `AgentToolGateway`.
- The 20 user-facing AI-tool catalog remains unchanged.
- GitHub-backed and local workspace paths continue to use their existing respective access mechanisms.

CI status for the latest documentation commit is **not marked green** because the available GitHub Actions connector did not expose a run/status for the latest direct `main` commits at validation time. No unverified CI result is being represented as successful.

### 25. Known scope/limitations

The implemented version intentionally does not claim a full persistent cross-provider context cache.

Specifically:

- Provider APIs remain request-based; compact identity is still sent with each request so the model cannot lose workspace identity.
- Rich context is retrieved on demand rather than cached inside every model conversation.
- The current context version is process-local rather than a Room-persisted version ledger.
- A dedicated persistent per-session Context Ledger is not yet required because the main token-saving objective is achieved by removing whole-workspace injection and fetching relevant evidence on demand.
- Context invalidation is currently connected directly to successful side-effecting typed agent tools. Additional UI/editor/Git/build event producers can be wired later without changing the AI contract.

### 2026-09-20 — Final repository audit, chat edit scope, language expansion and modern icons

#### 1. Chat behavior
- [x] Assistant/AI message editing was removed from the user interface and ViewModel flow.
- [x] Copy remains available for both user and assistant messages.
- [x] User messages remain editable and are regenerated from the edited point; later messages are removed before regeneration.
- [x] ChatRepository now rejects attempts to edit non-user messages, enforcing the UI rule at the persistence boundary.
- [x] Stale edit state is cleared when the active workspace or provider changes.
- [x] Chat message timestamps are now monotonic within each session. This prevents same-millisecond messages from weakening `deleteMessagesAfter` semantics during user-message editing.
- [x] The previous missing closing block in `AIChatViewModel.submit()` was restored. That parser error had caused a large cascade of misleading unresolved-reference compiler errors across AI, GitHub and Build classes.

#### 2. Programming-language coverage
- [x] Existing coverage remains for Kotlin, Java, JavaScript, TypeScript, Python, Go, Rust, C/C++, C#, Swift, PHP, Ruby, Lua, Scala, Groovy, R, Perl, Haskell, Elixir, MATLAB, JSON, XML, HTML, CSS, SQL, YAML, shell, Markdown and Dart.
- [x] Added detection/highlighting profiles for Objective-C/Objective-C++, D, F#, Visual Basic, Julia, Zig, Nim, Clojure/ClojureScript, GDScript, Solidity, Pascal, Fortran, COBOL, Prolog, OCaml, VHDL, Verilog/SystemVerilog, SCSS, Less, GraphQL, TOML, HCL/Terraform and PowerShell.
- [x] Added JavaScript/TypeScript module extensions, Dockerfile and Makefile detection, MDX, JSONC, XML schema/style files and Terraform variable files.
- [x] Workspace symbol indexing now scans the expanded source-extension set instead of being limited to the original small language subset.
- [x] Editor keyword highlighting now covers the added language profiles.
- [x] SQL, Visual Basic, Fortran, COBOL, PowerShell and Dockerfile keyword matching is case-insensitive where the language requires it.
- [x] Numbers are styled before string/comment layers so numeric literals inside strings/comments do not incorrectly override those higher-priority spans.
- [x] Language regression tests were expanded to cover the new detection set and syntax-highlighting behavior.

#### 3. File/language icons
- [x] Replaced the previous colored rectangular letter-card file badges with transparent-background Material vector icons.
- [x] Workspace files now use category/language-aware modern icons: code/data, web, terminal, database/query, documentation, table, image, audio, video, archive, PDF and configuration icons.
- [x] Icon tint remains language/category-specific while the background stays fully transparent.
- [x] Unknown source files now use a modern code glyph instead of a generic file-page icon.
- [x] Git's empty-repository state now uses a repository/source glyph rather than a generic file glyph.
- [x] File-tree call sites were renamed to use `WorkspaceLanguageIcon`.

#### 4. Git correctness and robustness
- [x] Fixed Git pack-index v2 offset-table lookup: the CRC/offset tables are based on the total number of indexed objects, not the upper bound of the current SHA fanout bucket.
- [x] Added consistency checks for the total fanout/object count while keeping the existing pack/object/offset/delta safety limits.
- [x] Packed Git support continues to handle loose objects first, then bounded pack index v2 lookup and OFS/REF delta resolution.

#### 5. Repository-wide audit checks performed
- [x] Reviewed the full repository tree and Android/Kotlin source/test inventory.
- [x] Inspected the chat persistence/edit path, editor/language path, workspace symbol indexing, file icon layer, packed Git reader, Build/CI integration and the recent agent/context changes.
- [x] Static scans were used for common risk markers including global coroutine usage, blocking calls, TODO/FIXME markers, unbounded byte reads and unsafe null assertions.
- [x] CI compiler logs were inspected rather than treating downstream unresolved-reference errors as independent failures.
- [x] The previously observed CI failure at the pre-audit commit was traced to the `AIChatViewModel` syntax error; the many unresolved `BuildViewModel`, `ChatAttachment`, attachment helper and other references were downstream compiler fallout from that parse failure.

#### 6. Validation and CI state
- [x] Added/updated regression tests for editor language detection and syntax highlighting.
- [x] Repository changes remain on `main`; no feature branch was created.
- [ ] Final green Android CI/UI verification must be taken from the newest run after this documentation commit. Earlier pre-fix runs failed because of the `AIChatViewModel` parser error, so those failures are not treated as final validation.

#### 7. Documentation invariant
- [x] This section records the full audit and every repository modification made in this implementation session, including small corrective edits.
- [x] The existing requirement remains: every later repository modification must also update this file in the same implementation session.
### 2026-09-21 — Quality/reliability layer, baseline compiler repair and in-app guide expansion

#### 1. Baseline compiler repair
- [x] Re-checked Android CI/UI failures against the actual main source rather than relying on the previous session snapshot.
- [x] Restored missing MainActivity imports for BuildViewModel, AgentCenterViewModel, widthIn, and the Material List icon.
- [x] Removed the duplicate private setter declaration in AIChatViewModel.editingMessageId, which was still present in the actual main source despite the previous snapshot describing it as fixed.
- [x] Restored missing ParallelAgentCoordinator imports for workspace tree, GitHub repository and credential services.
- [x] Corrected Room WorkspaceEntity → domain Workspace conversion at workspace context/retrieval boundaries so URI-bearing APIs receive the domain model.

#### 2. Shared operation lifecycle
- [x] Added DevForgeOperationCenter with bounded in-memory operation history, lifecycle states (QUEUED, RUNNING, WAITING, SUCCEEDED, FAILED, CANCELLED), progress reporting and bounded/redacted status text.
- [x] Added operation types for AI, agent, build, Git, terminal, workspace, artifact, sync, diagnostics and backup work.
- [x] Integrated remote Build Center dispatch/monitoring with the operation lifecycle, including credential failure, dispatch failure, run progress and terminal success/failure/cancellation.
- [x] Operation history is exposed through the existing DevForge Center rather than adding another permanent navigation destination.

#### 3. Workspace integrity
- [x] Added WorkspaceIntegrityService to validate persisted SAF access, root readability, workspace metadata persistence and Git detection.
- [x] Added explicit PASS/WARN/FAIL checks so a workspace problem is surfaced as a diagnosable condition rather than an opaque downstream failure.
- [x] Workspace integrity checks remain bounded and run off the UI thread.

#### 4. Security and quality checks
- [x] Added a bounded best-effort secret-like content scanner using the existing SecretRedactor patterns.
- [x] The scanner intentionally targets currently open editor content rather than reading an entire repository implicitly.
- [x] Added mobile performance-budget reporting for open-tab count, dirty tabs and aggregate open-editor content.
- [x] Added a release-quality gate model covering successful build, artifacts, logs, unsaved changes, diagnostics and build configuration completeness.
- [x] Added pure unit tests for operation completion, secret detection, performance budget enforcement and release-gate failure behavior.

#### 5. DevForge Center
- [x] Added Quality & Reliability to the existing DevForge Center.
- [x] The page exposes Operations, Workspace integrity, Security scan, Mobile performance budget and Release quality gate sections.
- [x] Existing Project Health, AI Run Inspector, Testing, Build Diagnosis, Offline Queue, Workspace Backup, Release Center, GitHub Hub, Automation, Code Intelligence and Extensions pages remain the canonical feature surfaces.

#### 6. In-app guide
- [x] Added a dedicated Quality & Reliability guide category.
- [x] Documented operation lifecycle states, workspace integrity checks, secret scanning scope, mobile performance budgets, release gate checks and recovery/backup paths.
- [x] Updated the DevForge Center guide section with a direct Quality & Reliability entry.
- [x] The guide remains the canonical user-facing explanation surface for new features; future repository changes in this project must update it alongside IMPLEMENTATION.md.

#### 7. Validation state
- [x] Source changes were written directly to main; no feature branch was created.
- [x] Regression tests were added for the new quality models.
- [ ] Final Android CI/UI green status is intentionally not claimed here until a post-change GitHub Actions run is observed and its actual jobs complete successfully.

### 2026-09-21 — Remove GitHub Codespaces feature

#### 1. Codespaces feature removal
- [x] Removed the Codespaces navigation destination from DevForge.
- [x] Removed the Codespaces entry from the app settings/more surface.
- [x] Removed the Codespaces screen, ViewModel, API gateway, models, custom icon, and in-app Codespace WebView screen.
- [x] Removed the MainActivity routing/imports that could launch the crashing Codespaces surface.
- [x] Removed the complete Codespaces section from the in-app `ⓘ` Help & guide.
- [x] Verified current `main` source files contain no `Codespaces`/`Codespace` references in the navigation, MainActivity, or guide files.

#### 2. Documentation invariant
- [x] This removal is documented in IMPLEMENTATION.md and the user-facing Help & guide was updated in the same implementation session.
- [x] Future tiny modifications must continue updating the in-app guide and IMPLEMENTATION.md as required.

### 2026-09-21 — Repository-wide reliability/security audit fixes
- [x] Scanned the repository for stale feature references, unsafe coroutine scopes, nullable assertions, unbounded external input, notification/WorkManager lifecycle mistakes, workspace/agent failure handling and obvious TODO/FIXME debt.
- [x] Fixed approval notification progress work so the WorkManager worker awaits the database/notification operation instead of returning success while an unstructured coroutine is still running; transient failures can now retry.
- [x] Fixed approval notification deep links so tapping an approval notification opens DevForge on the Approvals destination, including when the app is already running.
- [x] Fixed GitHub-backed agent patch preview so remote read failures no longer fall through to a nullable local workspace root and crash with a root assertion; only clear not-found responses are treated as new-file previews.
- [x] Replaced the Compose commit action's raw Main dispatcher scope with the composition-bound rememberCoroutineScope already used by the screen.
- [x] Bound Workspace Backup import to a 2 MiB input limit before JSON parsing, preventing oversized backup files from creating avoidable memory pressure.
- [x] Removed a remaining nullable-root assertion from the patch preview path.
- [x] Updated More → ⓘ Help & guide with the affected reliability and backup behavior.
- [ ] Post-change CI/UI validation remains to be run; the user's earlier CI-passed status predates these audit changes and was not independently re-run here.
### 2026-09-21 — Settings navigation cleanup and full-screen surface behavior
- [x] Made Settings a single primary navigation destination (bottom navigation on compact layouts and navigation rail on larger layouts).
- [x] Removed duplicate Settings entries from More and the editor workspace hamburger menu.
- [x] Converted DevForge Center, GitHub repository creation, Help & guide, Agents, Project activity, and the editor workspace menu from popup dialogs to true full-screen surfaces.
- [x] Kept the AI Chat launcher as the intentional popup surface.
- [x] Updated Back handling so full-screen Agents and Project activity surfaces close before destination history/exit handling.
- [x] Updated the in-app Help & guide to describe the single Settings destination and revised More/editor menu navigation.

### 2026-09-21 — Unified navigation, Settings full-screen behavior and floating AI Chat

- [x] Retired the latent `DevForgeDestination.Chat` route so AI Chat has no alternate navigation destination; the floating action is the canonical entry point.
- [x] Removed Chat from permanent bottom/side navigation; AI Chat is opened through the floating AI action instead.
- [x] Reduced permanent navigation to Files, Git, Build and More so the primary bar no longer contains a duplicate Settings slot.
- [x] Moved the single user-facing Settings entry into More; Settings and More hide the primary navigation while open so they behave as full app surfaces.
- [x] Removed the duplicate Chat action from the editor workspace menu so AI entry is consistently the floating action.
- [x] Kept AI Chat as the only intentional popup-style top-level surface; other app-level surfaces continue to use full-screen surfaces.
- [x] Updated More → ⓘ Help & guide to describe the new navigation and AI Chat behavior.

### 2026-09-21 — Stabilize Live GitHub Actions workflow status
- [x] Changed Live GitHub Actions automatic status polling from 2 seconds to 5 seconds.
- [x] Serialized workflow refreshes so a slow GitHub request cannot overlap with another automatic/manual refresh.
- [x] Preserved the selected/expanded workflow state across successful status refreshes.
- [x] Stopped automatic status polling from restarting job-log loading on every cycle; logs load when a run is expanded and remain stable while its status refreshes.
- [x] Cancelled per-run log jobs when a workflow is collapsed or the ViewModel is cleared.
- [x] Preserved the last visible workflow list during transient repository/API failures instead of clearing and rebuilding the screen.
- [x] Updated Live GitHub Actions UI copy and More → ⓘ Help & guide to document stable polling and manual refresh behavior.

### 2026-09-21 — Stable Live Actions selection and in-app GitHub repository deletion
- [x] Fixed Live GitHub Actions polling so refreshed workflow metadata merges into the latest on-screen run state instead of an older snapshot; expanded runs, fetched jobs/logs, loading state and errors are preserved while status polling continues.
- [x] Kept the existing 5-second Live GitHub Actions polling cadence and manual Refresh control; selecting a run no longer causes its card/log surface to reset on each poll.
- [x] Added a GitHub repository DELETE operation to the repository gateway with validated owner/repository identifiers, protected credentials, bounded responses, fixed API-version headers, timeouts and safe error sanitization.
- [x] Added in-app repository deletion to the GitHub repository screen. Deletion requires selecting a repository and typing its exact name before the permanent operation is enabled.
- [x] After a successful deletion, DevForge removes the repository from the current in-app list and clears the selected repository/workflow state without requiring GitHub in a browser.
- [x] Added gateway regression coverage for the DELETE request and updated More → ⓘ Help & guide for both repository deletion and stable Live Actions behavior.
- [ ] Post-change CI/UI validation remains to be run for this change set.
### 2026-09-21 — Release APK CI dispatch
- Triggered the Android CI release APK path from main using the repository's documented [release-apk] push trigger.
- The release workflow target is release_apk only; it is not using the default debug_apk target.
- Release signing remains fail-closed and requires the configured DevForge release signing secrets.
- Build artifact publication remains enabled so the resulting release APK is available directly from GitHub Actions.

## 2026-09-22 — Clean repository bootstrap

Imported the validated DevForge source archive into the new main-only repository from the supplied source snapshot. The bootstrap restores the application source, tests, Gradle configuration, GitHub Actions workflows, tools, and project documentation while starting a fresh repository history.

The repository README was refreshed with DevForge-specific positioning and product messaging.

## 2026-09-22 — Restored project workflows

Restored the three project GitHub Actions workflows through the repository Git integration after the bootstrap import. The temporary importer workflow is not part of the DevForge baseline.


### 2026-09-22 — Fresh CI/UI validation after release-signing secret update
- [x] Triggered a new push-to-main validation run after the GitHub Actions release-signing secrets were updated.
- [x] This main-branch change is intentionally documentation-only; no application source behavior was changed.
- [ ] Android CI and Android UI Tests results are pending completion.


### 2026-09-22 — Repository deletion and attachment reliability hardening
- [x] Added a repository-row delete action so every repository visible in the connected GitHub repository list can be selected for deletion without first opening its details.
- [x] Kept the exact-name permanent-delete confirmation and GitHub permission enforcement already present in the repository deletion flow.
- [x] Cleaned up matching GitHub-backed workspace registrations after successful remote repository deletion so deleted remotes do not remain as broken active workspaces.
- [x] Hardened the Android attachment picker activity lookup by unwrapping Compose context wrappers before launching the system picker.
- [x] Hardened attachment type detection by inferring common image, video, audio and document MIME types from filenames when document providers return a generic or missing MIME type.
- [x] Added regression coverage for extension-based attachment type detection.
- [x] Expanded More → ⓘ Help & guide with the repository deletion entry, attachment limits, picker behavior and provider-specific binary attachment behavior.
- [ ] Fresh Android CI and Android UI Tests validation is required for this main commit.


### 2026-09-22 — Attachment regression-test placement correction
- [x] Corrected the new attachment MIME-fallback regression test so it remains inside the existing JUnit test class and cannot be emitted as a file-scope generated test class.
- [ ] Fresh Android CI and Android UI Tests validation is required for this corrected main commit.


### 2026-09-22 — Approval lifetime hardening
- Standardized all executable approvals on the documented 15-second approval lifetime across agent, build, Git, Git history, and terminal actions.
- Repository-side approval creation clamps any requested expiry to the 15-second maximum, so an individual producer cannot accidentally create a longer-lived executable approval.
- Approval Center actively expires due actions and shows a live remaining-time countdown; persistence and execution claims continue to reject expired approvals.


### 2026-09-22 — Attachment transport hardening
- Added provider-aware attachment preflight so unsupported binary types are rejected at selection time instead of after Send.
- Gemini remains the binary upload path; OpenRouter binary transport is restricted to images and PDF files, with video/audio options disabled there.
- Failed or cancelled AI sends retain submitted attachments for retry while preserving URI-permission ownership.
- Updated Help & guide and added provider transport regression coverage.


### 2026-09-22 — Repository deletion cleanup hardening
- Remote repository deletion now also clears matching terminal sessions, pending GitHub change batches, and per-repository build-output settings in addition to deleting GitHub-backed workspace records.
- Editor ViewModel observes the active workspace lifecycle and invalidates stale remote editor tabs when the active workspace disappears.
- Help & guide now documents the complete deletion cleanup behavior.


### 2026-09-22 — Attachment retry retention correction
- Cancellation during AI generation now restores submitted attachments before their URI permissions are released, matching failed-request retry behavior.


### 2026-09-22 — Approval lifetime compile correction
- Corrected the remaining Build Center approval expiry reference to the centralized `ApprovalRepository.APPROVAL_WINDOW_MS` constant after CI exposed the stale local reference.


### 2026-09-22 — CI compile correction for approval cleanup
- Restored the missing newline in the Build Center companion constants after the approval-lifetime cleanup exposed a formatting-induced Kotlin parse error.


### 2026-09-22 — Fix agent observer and split-editor Compose compilation errors
- [x] Replaced the remaining MainActivity and DevForge Center references to the removed user-facing Agent Center with the read-only Agent Activity observer.
- [x] Fixed split-editor syntax transformation creation so Compose theme colors are captured before the non-composable remember calculation.
- [ ] Fresh Android CI and Android UI Tests validation is pending for this follow-up compile fix.

### 2026-09-22 — Align editor folding threshold
- [x] Aligned the editor's folding calculation guard with the 64 KiB / 2,000-line fast-rendering threshold so large-file folding work is skipped consistently before transformation creation.
- [ ] Fresh Android CI and Android UI Tests validation is pending for this follow-up commit.

### 2026-09-22 — AI-managed agent UX and mobile editor performance hardening
- [x] Removed the remaining user-facing Agent Center ViewModel/API surface; DevForge Center now uses a read-only agent activity observer.
- [x] Users cannot launch, replay, pause, resume or cancel agents from the UI; only AI orchestration can deploy internal agents.
- [x] Kept AI Run Inspector and project activity observational: status, agent count, provider, model, elapsed time, current step, last tool, approvals, step activity, affected paths, errors and final results remain visible.
- [x] Kept AI-generated plans phase-ordered: phases execute sequentially while independent agents within a phase may run concurrently for faster development.
- [x] Hardened editor visual transformations with result caching so repeated scroll/recomposition no longer reruns whole-file syntax, folding or invisible-character processing.
- [x] Reduced rich editor rendering thresholds from 128 KiB/4,000 lines to 64 KiB/2,000 lines and reduced folding to 40 ranges so large source files enter the lighter path earlier on mobile.
- [x] Updated Help & guide to match the agent control boundary and editor performance thresholds.
- [ ] Fresh Android CI and Android UI Tests validation is pending for this commit.

### 2026-09-22 — AI-only routing and editor compile cleanup
- [x] Fixed the AI delegation boundary so explicit slash commands keep their own typed command behavior; ordinary natural-language implementation requests can still enter the AI-managed agent workflow.
- [x] Fixed the editor's remembered syntax transformation construction so Compose theme colors are captured outside the non-composable remember calculation, removing the release-build compile error.
- [x] Fixed the AI squad planner to call the gateway with its required `userInstruction` parameter.
- [x] Fixed agent run state cleanup and affected-path collection in Chat.
- [x] Corrected the live agent counter label and clarified that model discovery is from the selected cloud provider only.
- [x] Kept the cloud-only AI boundary explicit in the provider registry; no on-device/local inference runtime is exposed.
- [ ] Fresh Android CI and Android UI Tests validation is pending for this corrected main commit.

### 2026-09-22 — AI-managed agent orchestration and mobile editor performance

- [x] Removed the user-facing Agent launcher surface and removed the `/agent` command from Chat.
- [x] Kept the underlying agent runtime internal so the Chat AI is the only surface that can deploy coding agents.
- [x] Added an AI-only top-level squad planner that creates bounded agent phases. Agents in the same phase may run concurrently; later phases wait for earlier phases to finish.
- [x] Added a visible Chat plan/activity surface that shows plan phases, agent count, provider, model, elapsed time, current step, last tool, detailed tool steps and affected paths without exposing launch/stop/replay controls.
- [x] Added an automatic final agent overview containing each worker's status, provider/model, elapsed duration, tool steps, changed paths and errors.
- [x] Expanded mutation-intent detection so normal implementation/build/create/fix/refactor requests can automatically enter the AI-managed agent workflow without requiring a special user command.
- [x] Hardened agent cleanup so unexpected Chat failures cancel active internal agent workers instead of leaving orphaned work.
- [x] Kept cloud-only AI as the product model boundary; no on-device/local AI runtime was added.
- [x] Removed the old user-facing Agent Center screen while retaining the underlying runtime/view-model state needed for read-only project activity and internal orchestration.
- [x] Updated Project Activity and AI Run Inspector to be observation-only and to expose provider, model, elapsed time and current execution state.
- [x] Reduced editor rendering cost for large files by automatically disabling expensive syntax/folding/invisible-character transforms above 128 KiB or 4,000 lines.
- [x] Moved editor diagnostics to a debounced background calculation so typing no longer performs diagnostics synchronously on every content update.
- [x] Added a cloud-only AI entry to More → ⓘ Help & guide and documented AI-managed agent plans, telemetry and editor performance behavior.
- [x] Updated README and architecture documentation to describe the new AI-managed workflow and mobile editor performance boundary.
- [x] Updated Android UI coverage so the chat verifies that user-facing Agent controls are absent.
- [ ] Fresh Android CI and Android UI Tests validation is still required for this change set.


### 2026-09-22 — AI agent observability and mobile editor responsiveness hardening

- [x] Kept the AI-managed agent architecture cloud-only. No on-device/local inference runtime is exposed.
- [x] Kept agent deployment internal to the AI workflow. The user remains an observer and cannot launch, stop, replay, or choose individual coding agents.
- [x] Prevented the Chat stop control from cancelling an active AI-managed agent plan; agents now complete their assigned bounded work and terminate at their normal terminal state.
- [x] Added explicit per-step execution detail in the live Chat agent panel for workspace searches, content searches, file/folder paths, commands, web searches, URLs, and workspace-context requests.
- [x] Kept tool telemetry bounded and redacted; file contents and credential-like values are not surfaced as telemetry.
- [x] Preserved ordered plan execution: agents within a phase may run concurrently, while later phases wait for earlier phases to finish.
- [x] Removed the duplicate Project Activity rail action that was labelled like a replay/history control; user-facing project activity remains read-only.
- [x] Reduced rich editor rendering to a 64 KiB content boundary and removed the synchronous fold scan from the typing/recomposition path.
- [x] Moved fold-range calculation to a debounced background calculation.
- [x] Kept split-editor syntax highlighting on the same existing fast-rendering boundary.
- [x] Reworked editor undo bookkeeping to use incremental bounded byte accounting instead of rebuilding byte arrays and rescanning the complete undo stack on every keystroke.
- [x] Increased editor diagnostics debounce and skipped full diagnostics for files above 256 KiB.
- [x] Increased recovery snapshot debounce to reduce background work during continuous typing.
- [x] Updated More → ⓘ Help & guide and README with the AI-only agent boundary and editor performance rules.
- [ ] Fresh Android CI and Android UI Tests validation is required for this main-branch change.


### 2026-09-22 — Restore and preserve the complete Help & guide catalog

- [x] Restored the full current Help & guide catalog from the latest main observer/compile-fix commit instead of dropping unrelated guide categories.
- [x] Updated only the AI-managed agent topic to explicitly state the observer-only control boundary.
- [x] Added the mobile editor performance topic without removing existing Files, DevForge Center, Quality & Reliability, GitHub creation, identity, or symbols topics.
- [ ] Fresh Android CI and Android UI Tests validation is required for this follow-up.


### 2026-09-22 — Bound background editor folding work

- [x] Capped asynchronous fold-range extraction to 40 ranges so background editor analysis cannot grow unbounded on moderately sized files.
- [ ] Fresh Android CI and Android UI Tests validation is still required for the current main head.


### 2026-09-22 — Local-first project lifecycle and GitHub repository management
- [x] Kept GitHub optional for normal local Workspace development; SAF workspaces remain usable without connecting a GitHub account or creating a repository.
- [x] Added device-local GitHub repository saving. The GitHub workspace picker can clone a selected repository into a user-selected device folder and activate it as a local workspace.
- [x] Added Build with AI project bootstrap. DevForge creates the local project folder first and opens AI Chat with a plan-first project-building request; GitHub is not required.
- [x] Added local-project → existing GitHub repository publishing. DevForge snapshots the local SAF project into a temporary JGit worktree, stages additions/deletions, creates a normal commit and pushes without force-push. Binary files are handled by Git rather than text-only REST writes.
- [x] Added persistent local-project → GitHub link state so the workspace can show which repository it is connected to and reuse the Upload/Update action.
- [x] Added existing GitHub repository metadata editing for name, description, homepage, visibility, default branch, repository features, merge behavior and merge-message defaults.
- [x] Repository rename migrations update GitHub-backed workspace mappings, local-project links, per-repository build-output settings and pending remote change state.
- [x] Updated More → ⓘ Help & guide for local-first workspaces, Build with AI, Save on device, local-project publishing, and repository metadata editing.
- [x] Reused the existing AI plan/agent/activity infrastructure rather than introducing a duplicate agent dashboard; these lifecycle features connect into the same AI Chat/workspace workflow.
- [ ] Fresh Android CI and Android UI Tests validation is still required for the new main-branch commits.


### 2026-09-22 — Unified AI Mission, activity timeline, verification, background recovery and Editor 2 foundation
- [x] Added first-class AI Mission workflow state for normal Chat requests: Understand → Plan → Inspect/Execute → Verify → Review.
- [x] Added one normalized AI activity model for search, read, write, delete, command, test/build markers, agents, approvals, verification, errors and information.
- [x] Persisted the current AI Mission and a bounded activity history so leaving Chat does not erase task context.
- [x] Added a WorkManager-backed recovery worker that re-enters the existing durable AgentRuntime recovery path after process recreation.
- [x] Added an explicit verification receipt with passed, failed and skipped checks; Build Center, test and lint receipts remain authoritative for those operations.
- [x] Added the compact AI Mission card to Chat so users see workflow progress without seeing internal agent/runtime controls.
- [x] Added the Editor 2 document foundation: chunked text storage, reusable line indexing, incremental token-cache contract and viewport window model.
- [x] Connected EditorViewModel line navigation and content updates to the Editor 2 document model.
- [x] Added Editor 2 unit coverage.
- [x] Updated More → ⓘ Help & guide and README.
- [ ] Fresh Android CI and Android UI Tests validation is required before claiming this main commit is clean.

## 2026-09-22 — CI compiler-failure repair after AI Mission integration
- [x] Fixed the AI verification receipt Kotlin syntax error that blocked compilation.
- [x] Fixed local GitHub publishing to return a typed `GitRemoteResult.Success` instead of leaking a raw message from the coroutine result.
- [x] Restored expanded GitHub repository merge-setting fields on `GitHubRepository` and parsed them from GitHub REST responses.
- [x] Restored missing Compose/import wiring for local workspace publishing and the Git remote result type in `WorkspaceViewModel`.
- [x] Added Help & guide coverage for Build/CI reliability.


### 2026-09-22 — Mobile-native engineering power layer
- [x] Added cursor-aware Vim-inspired mobile editing actions: select line, duplicate line, delete line, move line up/down, and toggle comment.
- [x] Expanded the VS Code-style Command Palette with searchable commands and direct mobile editor actions.
- [x] Added reusable AI skill profiles for Android, UI, testing, security, performance, and Git/GitHub work.
- [x] Added persistent project-rule guidance: AI Missions now instruct the model to inspect `.devforge/rules/` and `.devforge/skills/` before mutation and to surface rule conflicts.
- [x] Added mission change summaries for writes, deletes, commands and observed workspace paths.
- [x] Added Build Center receipt integration to AI verification so a fresh successful build can produce an actual verification pass; stale or failed receipts never become a false pass.
- [x] Added Android/Gradle project detection to AI verification.
- [x] Added agent completion activity to the unified AI Mission timeline and path extraction for completed tool activity.
- [x] Added unit coverage for mobile power editing and AI skill selection.
- [x] Updated More → ⓘ Help & guide and README with the new mobile-native engineering features.
- [ ] Fresh Android CI and Android UI Tests validation is required before calling this commit green.

### 2026-09-22 — Unit-test framework compatibility repair
- [x] Switched the new mobile power-editing and AI skill unit tests from unavailable `kotlin.test` imports to the repository's existing JUnit 4 test conventions.
- [x] Kept the feature implementation unchanged; this repair only aligns test imports with the established Android JVM test setup.
- [ ] Fresh Android CI and Android UI Tests validation is required for the repaired main head.

### 2026-09-22 — Make Build Center authority explicit in AI policy
- [x] Added an explicit AI engineering-policy rule stating that Build Center and its receipts are authoritative for build, test and lint evidence.
- [x] Kept the rule aligned with the verification engine so model text can never be treated as proof of successful verification.
- [ ] Fresh Android CI and Android UI Tests validation is required for the updated main head.


### 2026-09-22 — Engineering Power hub and expanded Linux-style terminal
- [x] Added a unified More → AI Tools Engineering Power hub for repeatable project tasks, Mission Graph/proof evidence, development profiles and integration catalogs.
- [x] Added reusable contracts for semantic navigation, branching undo, registers, structural/AI refactoring, reviewer/critic roles, debugger state, UI journeys, Compose preview requests, device matrix targets, remote development, extensions, MCP, AI memory, learned rules and engineering automations.
- [x] Added resource-aware cloud routing classes without adding any local/on-device AI runtime.
- [x] Added persistent task, profile, memory, learned-rule and MCP registry stores; external integrations remain registry-first and approval-aware.
- [x] Expanded the bounded AI terminal command catalog with environment, command discovery, boolean/sleep/system-property utilities in addition to the existing file/text/diagnostic commands.
- [x] Extended the real Android `/system/bin/sh` interactive terminal with broader Linux-style help/environment and longer bounded engineering timeouts.
- [x] Extended the configurable terminal timeout ceiling to 60 seconds; interactive shell execution has a separate bounded 120-second ceiling.
- [x] Added regression coverage for Engineering Power routing and Mission Graph behavior.
- [x] Updated More → ⓘ Help & guide and README with the full Engineering Power and Linux-style terminal behavior.
- [ ] Fresh Android CI and Android UI Tests validation is required before calling the current main head green.


### 2026-09-22 — Engineering Power and terminal validation follow-up
- [x] Made repeatable Engineering Power tasks independently enable/disableable and portable for Android shells by invoking Gradle through `sh ./gradlew`.
- [x] Kept task execution on the existing TerminalViewModel/capability path so sandboxing, output limits and approvals are not bypassed.
- [x] Added Linux shell parser regression coverage for expanded bounded commands and interactive shell operators.
- [x] Revalidated Help & guide coverage for Engineering Power and Linux-style Terminal behavior.
- [ ] Fresh Android CI and Android UI Tests validation is still required for the current main head.


### 2026-09-22 — CI failure repair and AI factuality hardening
- [x] Fixed Android CI and Android UI Tests compilation failure caused by missing `description` arguments in the new Engineering Power task definitions.
- [x] Added a live `AiTruthService` that derives tool counts from the actual registered runtime definitions plus current user tool settings instead of model memory.
- [x] Tool-count/list questions are now answered directly from live DevForge state, including registered, user-configurable, enabled, disabled, internal and currently-usable counts.
- [x] Added an authoritative-evidence contract to all Chat paths. Current DevForge/project questions must use authoritative tools or state and cannot be answered from model memory.
- [x] Added a factuality gate that refuses to guess when a current-state question has no verified evidence instead of returning a hallucinated answer.
- [x] Kept the distinction between registered, enabled, callable, attempted and successfully completed tools explicit so tool availability is not misreported as successful execution.
- [x] Kept the live tool-status answer independent of model/API-key availability so it can report the actual DevForge tool state without another AI inference step.
- [x] Updated More → ⓘ Help & guide with the AI factuality/evidence behavior.
- [ ] Fresh Android CI and Android UI Tests validation is required for the repaired main head.


### 2026-09-22 — Latest Android CI unit-test repair
- [x] Inspected Android CI #83 and found the failure was isolated to `TerminalCommandParserTest`: the newly added `env`/expanded terminal commands were declared in `TerminalExecutable` but were missing from the bounded AI parser name map.
- [x] Registered `env`, `printenv`, `which`, `true`, `false`, `sleep`, and `getprop` in `TerminalCommandParser`.
- [x] Expanded terminal parser regression coverage so the new command aliases are verified at the parser boundary.
- [x] Android UI Tests #83 was already successful on the same pre-fix head; the remaining failure was Android unit-test-only.
- [ ] Fresh Android CI and Android UI Tests validation is required for the new repair commit.


### 2026-09-22 — Android CI #86 parser-map repair
- [x] Inspected the latest Android CI #86 failure on main commit `9c739512`; the build and compilation succeeded, but `TerminalCommandParserTest.parsesExpandedReadOnlyCommandsForAiTools` failed because the parser map still lacked the expanded executable names.
- [x] Registered `env`, `printenv`, `which`, `true`, `false`, `sleep`, and `getprop` in the actual `TerminalCommandParser` implementation.
- [x] Preserved the existing bounded AI-command policy and the separate interactive real-shell path; this repair only completes the missing parser registration.
- [ ] Fresh Android CI and Android UI Tests validation is required for the repair commit.


### 2026-09-22 — Real Acode and VS Code extension platform
- [x] Replaced the previous registry-only extension placeholder with a package-aware install/analyze/store pipeline for Acode ZIP and VS Code VSIX packages.
- [x] Added bounded package extraction with file-count, file-size, total-size and path-traversal protections before an extension reaches the installed registry.
- [x] Added compatibility classification for native DevForge languages, declarative contributions, Acode runtime packages and compatible VS Code Web bundles.
- [x] Added a hard rejection path for executable packages that need unsupported runtimes/APIs and for programming-language packages that DevForge cannot actually support.
- [x] Added a persistent extension manager under More → AI Tools → Integrations → Manage Extensions.
- [x] Added real icon-theme indexing for VS Code iconThemes and Acode file_icons/folder_icons metadata; active packs now change file and folder icons in the workspace browser.
- [x] Added AndroidSVG rendering for packaged SVG icon assets and kept custom icon decoding off the UI thread.
- [x] Added a restricted WebView runtime for compatible bundled Acode plugins and VS Code Web extensions, with network/file access blocked by default and command registration surfaced for verification.
- [x] Added native-language detection so existing Kotlin/Java/JavaScript/TypeScript/Python/Go/Rust/C/C++ and the expanded supported language catalog are reported as native rather than falsely treated as newly installed runtimes.
- [x] Added regression tests for native-language recognition, unsupported-language rejection and Acode command-plugin compatibility.
- [x] Updated More → ⓘ Help & guide and README with the real extension behavior and rejection rules.
- [ ] Fresh Android CI and Android UI Tests validation is still required for the current main head.


### 2026-09-22 — Extension runtime hardening and full file-tree icon integration
- [x] Routed every active workspace file/folder row and quick-open browser folder row through the extension-aware icon renderer so an installed icon theme changes actual visible icons rather than only storing theme metadata.
- [x] Added actual SVG/raster icon rendering from installed VS Code/Acode theme assets, with decoding moved to a background dispatcher.
- [x] Rejected Acode plugins that require unsupported runtime modules instead of providing non-functional stubs; the currently executable Acode subset is deliberately limited to the implemented command bridge.
- [x] Added regression coverage proving an unsupported Acode editor API plugin is rejected.
- [x] Added an extension-manager back affordance and lifecycle-aware installation coroutine.
- [x] Kept native-language detection strict: unsupported language engines remain rejected rather than appearing as installed language support.
- [ ] Fresh Android CI and Android UI Tests validation is still required for the latest main head.


### 2026-09-22 — VSIX packaging and extension-manager polish
- [x] Added real VSIX layout discovery for packages that store their manifest and payload under the conventional `extension/` directory.
- [x] Installation now promotes the actual VSIX/Acode package payload as the installed root so entry points and icon resources resolve against the correct files.
- [x] Added regression coverage for nested VSIX icon-theme packages.
- [x] Removed the duplicate extension-manager heading while retaining a direct Back control.
- [ ] Fresh Android CI and Android UI Tests validation is still required for the latest main head.


### 2026-09-22 — CI #126 repair and extension search
- [x] Inspected Android CI #126 and Android UI Tests #126 on main head `71175d1679d64f3371a1285a70c71bdc68ee8b0e`.
- [x] Fixed the release Kotlin compilation error in `ExtensionPackageAnalyzer.kt` caused by malformed quoted-string syntax in the VS Code Web runtime dependency check.
- [x] Fixed the extension-manager `TextButton` unresolved reference by adding the required Compose import.
- [x] Added a real search icon to the Extensions top bar with live filtering across extension name, ID/publisher text, source, package type, version and contributed/native language names.
- [x] Search is UI-backed filtering of the actual installed extension registry; it does not create fake marketplace results.
- [ ] Fresh Android CI and Android UI Tests validation is required for the repair/search commit.
