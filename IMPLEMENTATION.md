# DevForge Implementation Tracker

> Living implementation record for the DevForge Android application. Update this file after every meaningful repository implementation change so the project always has a durable source of truth for what is actually implemented.

## Status Legend

- **Implemented** — code/configuration exists in the repository.
- **Validated** — implemented and verified by CI or another concrete check.
- **In progress** — actively being implemented; not yet complete.
- **Planned** — agreed direction with no implementation yet.
- **Deferred** — intentionally postponed with a recorded reason.

## Product Direction

DevForge is an Android-first, mobile-native development environment combining workspace/file management, an editor, Git, remote/cloud builds, an AI software-engineering agent, terminal capabilities, automation, approvals, recovery, and a modern adaptive UI.

UI direction: original, stylish, modern, distinctive, mobile-first, adaptive to larger screens, touch-friendly, and not visually copied from another product.

## Architecture Baseline

- Kotlin + Jetpack Compose
- Android-first architecture
- Clear domain/data/UI boundaries
- SAF for user-selected workspace access
- Capability-based execution with policy/approval gates
- GitHub as remote Git/CI/build backbone
- No bundled Android SDK/NDK/toolchains
- Cloud-first builds where practical
- Durable local state with bounded mobile resource usage

## Implemented

### Repository / App Foundation

- [x] Android/Kotlin/Compose application
- [x] Gradle project configuration
- [x] Material 3 + adaptive layout foundation
- [x] Compact navigation bar and expanded navigation rail
- [x] Chat / Files / Git / Build / Settings destinations
- [x] DevForge-specific visual language
- [x] GitHub Actions Android CI workflow
- [x] Repository `.gitignore`
- [x] Stable Android API 36 compile/target configuration for reproducible CI

### Workspace

- [x] SAF workspace selection and persistable URI permission handling
- [x] Workspace-aware global header
- [x] Real workspace enumeration and recursive folder navigation
- [x] Breadcrumbs and back/up/root navigation
- [x] Bounded per-folder enumeration
- [x] Loading/empty states
- [x] Bounded recursive filename search
- [x] Safe text preview policy with 512 KiB ceiling
- [x] Binary-content detection
- [x] Multiple workspace records persisted with Room
- [x] Active workspace state persisted with Room
- [x] Legacy SharedPreferences workspace migration
- [x] Workspace open/switch foundation
- [x] Workspace refresh and active-root state handling

### Editor / Recovery

- [x] Multi-tab editor state foundation
- [x] Active tab and dirty-state tracking
- [x] Explicit save action
- [x] Unsaved-change protection foundation
- [x] Bounded recovery drafts/checkpoints
- [x] SHA-256 content identity
- [x] Immutable bounded snapshots
- [x] Line-oriented diff engine
- [x] Duplicate checkpoint suppression
- [x] Snapshot lifecycle tied to open/save/recovery events
- [x] Snapshot ViewModel foundation

### Git / Repository Observation

- [x] `.git` repository detection through SAF
- [x] HEAD parsing and current-branch extraction
- [x] Detached-HEAD detection
- [x] Origin remote URL parsing
- [x] Linked/worktree `.git` unsupported-state reporting
- [x] Loose branch discovery
- [x] Packed-refs branch discovery
- [x] Bounded repository traversal
- [x] Workspace observation inventory
- [x] Generated-directory exclusions for `.git`, build, and `.gradle`
- [x] Optional small-file hashing for workspace observation
- [x] Explicit partial/truncated observation state
- [x] Git dashboard metadata and branch presentation
- [x] Explicitly unavailable native-Git mutation/status affordances

### Build Center / Remote CI Foundation

- [x] Build targets for debug APK, release APK, and release AAB
- [x] Build configuration model with workflow/ref/task/artifact metadata
- [x] Build lifecycle state model
- [x] Remote capability availability model
- [x] Build Center UI
- [x] Dispatch capability gating before authentication/workflow wiring
- [x] GitHub Actions API gateway foundation
- [x] Bounded/redacted GitHub error handling

### Security / Policy Foundation

- [x] Capability model
- [x] Risk classification foundation
- [x] Approval-state foundation
- [x] Policy-gated execution foundation
- [x] Action-request model
- [x] SecretStore abstraction
- [x] Android Keystore-backed AES/GCM secret storage
- [x] Redacted secret/error handling

### GitHub Connection Foundation

- [x] GitHub connection screen
- [x] Secure manual credential entry/removal foundation
- [x] Secret storage through Android Keystore boundary
- [x] Authenticated GitHub Actions gateway foundation
- [ ] OAuth / GitHub App connection flow
- [ ] Repository selection and validation
- [ ] Workflow discovery and dispatch wiring

## In Progress / Next Implementation Sequence

1. Authenticated GitHub repository selection and workflow discovery.
2. End-to-end workflow dispatch from Build Center.
3. Live run status, logs, artifacts, and build-history surfaces.
4. Native/index-aware Git execution and status.
5. Approval center and action review UI.
6. Snapshot/history and structured diff viewer UI.
7. Richer Room entities for workspaces, tabs, snapshots, builds, agent tasks, and automation state.

## Planned

### AI / Agent

- [ ] Provider-neutral AI interface
- [ ] Gemini / OpenRouter / other provider adapters
- [ ] Workspace context assembly
- [ ] File-aware context and symbol extraction
- [ ] Planning / task graph
- [ ] Typed tool gateway
- [ ] Patch generation and structured code changes
- [ ] Diff-first proposal UI
- [ ] Verification loop
- [ ] Persistent agent task state
- [ ] Memory and workspace knowledge
- [ ] Context/token budgeting
- [ ] Model capability matrix
- [ ] Fallback/provider routing

### Editor Intelligence

- [ ] Full syntax highlighting
- [ ] Language-aware editing foundation
- [ ] Diagnostics and problems panel
- [ ] Undo/redo stack
- [ ] Find/replace
- [ ] Go-to-line/symbol
- [ ] Code folding
- [ ] Selection/edit actions
- [ ] Large-file performance safeguards
- [ ] Editor preferences

### Git

- [ ] Native/index-aware Git status
- [ ] Diff computation from index/worktree
- [ ] Stage/unstage
- [ ] Commit
- [ ] Branch create/switch/delete
- [ ] Fetch/pull/push through capability gateway
- [ ] Merge/rebase/cherry-pick planning and execution
- [ ] Conflict handling UI
- [ ] Commit history and file history

### Remote Build / GitHub

- [ ] OAuth/GitHub App authentication
- [ ] Repository picker
- [ ] Workflow discovery
- [ ] Workflow/ref validation
- [ ] Dispatch execution
- [ ] Run polling/state updates
- [ ] Live logs
- [ ] Artifact listing/download
- [ ] Build history and receipts
- [ ] Cancellation
- [ ] Release build workflow support

### Terminal / Execution

- [ ] Sandboxed terminal capability
- [ ] Command request model
- [ ] Argument and path validation
- [ ] Resource/time limits
- [ ] Streaming output
- [ ] Cancel/terminate
- [ ] Command history
- [ ] Terminal tabs/sessions
- [ ] No unrestricted arbitrary AI shell access

### Automation

- [ ] Automation definitions
- [ ] Triggers and schedules
- [ ] Step graph
- [ ] Capability-scoped actions
- [ ] Approval checkpoints
- [ ] Retry/backoff
- [ ] Run history
- [ ] Pause/resume/cancel
- [ ] Idempotency/duplicate-run protection
- [ ] Recovery/receipts

### Security / Privacy

- [ ] Approval Center UI
- [ ] Per-capability grants
- [ ] Workspace/path scopes
- [ ] Secret lifecycle UI
- [ ] Audit trail
- [ ] Action receipts
- [ ] Export/delete privacy controls
- [ ] Redaction policy testing
- [ ] Lockscreen/biometric secret access option

### Settings

- [ ] AI provider/model settings
- [ ] API key/credential management
- [ ] GitHub account/repository settings
- [ ] Build settings
- [ ] Terminal limits
- [ ] Automation settings
- [ ] Security/approval settings
- [ ] Privacy/data retention
- [ ] Appearance/theme/density
- [ ] Editor settings
- [ ] Storage/cache controls
- [ ] Diagnostics/log export
- [ ] About/version/license screen

### UI / UX

- [x] Modern dark-first Material 3 foundation
- [x] Adaptive compact/expanded navigation
- [x] Original DevForge visual direction
- [x] Touch-friendly controls
- [ ] Tablet two-pane refinements
- [ ] Foldable posture refinements
- [ ] Rich activity/notification surface
- [ ] Command/search launcher
- [ ] Keyboard shortcuts on hardware keyboards
- [ ] Accessibility audit and semantic pass
- [ ] Motion/reduced-motion support

### Quality / Observability

- [ ] Unit-test suite established
- [ ] Compose/UI test suite established
- [ ] Static analysis/lint pipeline
- [ ] Release build validation
- [ ] Performance/budget checks
- [ ] Structured diagnostic logging
- [ ] Crash-safe recovery checks
- [ ] End-to-end build/dispatch tests
- [ ] Security regression tests

## Validation

- [x] GitHub Actions workflow configured
- [x] CI triggered by implementation commits
- [x] Latest implementation-changing commit passes Android build
- [ ] Unit tests established
- [ ] UI tests established
- [ ] Static analysis/lint established
- [ ] Release APK build validated

### Latest CI findings

- API 37 was not resolvable on the GitHub runner, including the preview-channel installation path.
- The app therefore uses stable API 36 for `compileSdk` and `targetSdk`.
- CI requests `platform-tools` and `platforms;android-36` and is intended to reach real Gradle/Kotlin compilation.
- CI run #86 reached Gradle compilation but failed AAR metadata validation because the Compose BOM and related AndroidX dependencies pulled versions requiring compileSdk 37.
- The dependency set was aligned back to an API-36-compatible line without changing the app's stable SDK target.
- CI run #96 reached Kotlin compilation and exposed an experimental Material3 API usage in `MainActivity.kt` at the expanded navigation rail.
- The navigation rail call was explicitly opted into with `ExperimentalMaterial3Api`; the earlier Compose compatibility fixes remain intact.
- CI run #102 reached toolchain verification but failed because the direct `sdkmanager` installation path was not available through the plain `sdkmanager` command during the verification step.
- CI run #103 passed the toolchain verification, debug APK build, unit-test task, APK verification, and artifact upload after the workflow reused the resolved `sdkmanager` path.

## Implementation Rules

- Never claim a feature is implemented unless the repository contains the supporting code/configuration.
- Never fake Git status, build execution, GitHub authentication, agent execution, or terminal execution.
- Keep unavailable capabilities explicitly visible in the UI rather than silently pretending they work.
- AI/model output is untrusted data, not authorization.
- Every privileged or side-effecting action must pass through typed capabilities and policy/approval checks.
- Prefer bounded traversal, bounded file reads, explicit limits, and partial-state reporting on mobile.
- Preserve manual workflows even when AI features are unavailable.
- Keep GitHub and remote CI as the cloud backbone; do not bundle heavyweight Android SDK/NDK toolchains.
- Update this file after every meaningful implementation change.

## Change Log

### 2026-09-17 — Initial implementation tracker

- Added `IMPLEMENTATION.md` as the living source of truth for implementation state.

### 2026-09-17 — Workspace foundation

- Added SAF workspace picker with persistable permissions.
- Added Room-backed workspace records and active-workspace persistence.
- Added workspace browser state, bounded enumeration, navigation, refresh, and empty/loading states.

### 2026-09-17 — Editor foundation

- Added multi-tab editor state, dirty tracking, explicit save, and unsaved-change protection foundation.
- Added bounded recovery drafts/checkpoints.

### 2026-09-17 — Content identity / snapshots / diff foundation

- Added SHA-256 content hashing.
- Added immutable bounded snapshots and a line-oriented diff engine.
- Added duplicate checkpoint suppression.

### 2026-09-17 — Editor snapshot lifecycle

- Connected snapshot creation to editor open/save/recovery paths.
- Added `SnapshotViewModel` foundation.

### 2026-09-17 — Recursive workspace navigation

- Added recursive folder navigation, breadcrumbs, root/back/up navigation, and per-folder bounded enumeration.

### 2026-09-17 — Workspace search / safe preview

- Added bounded recursive filename search.
- Added binary-content detection and a 512 KiB safe text-preview ceiling.

### 2026-09-17 — Durable workspace persistence with Room

- Added Room database, workspace entity/DAO/repository, migration from legacy SharedPreferences workspace state, and active-workspace persistence.

### 2026-09-17 — Git repository detection foundation

- Added `.git` detection, HEAD parsing, origin URL extraction, unsupported worktree reporting, and active-workspace-aware Git detection.

### 2026-09-17 — Git branch discovery foundation

- Added structured local branch records, loose and packed branch discovery, bounded traversal, and current-branch marking.

### 2026-09-17 — CI workflow hardening

- Updated Java setup to v5 and Android SDK setup to avoid the obsolete `tools` package.
- Follow-up CI showed API 37 was not resolvable on the runner, including preview-channel installation.

### 2026-09-17 — Workspace observation status foundation

- Added bounded file inventory/observation, optional small-file hashes, generated-directory exclusions, explicit partial/truncated states, and GitViewModel integration.
- Kept this explicitly separate from native Git working-tree status.

### 2026-09-17 — Git dashboard presentation

- Added `GitDashboardScreen` and connected it to the Git destination.
- Added repository metadata presentation for branch, HEAD, origin, and root.
- Added local branch list presentation with current-branch emphasis.
- Added workspace observation metrics and explicit scan-confidence messaging.
- Added disabled native-Git action affordances explaining why status/diff/commit remain unavailable until an index-aware execution layer is implemented.

### 2026-09-17 — Stable Android CI target

- Reverted the app build target from preview API 37 to stable API 36 because the GitHub runner could not resolve Android 17/API 37 even on the preview SDK channel.
- Updated CI to install `platform-tools` and `platforms;android-36` through `android-actions/setup-android@v3`.

### 2026-09-17 — Build configuration/state foundation

- Added `BuildConfiguration` with workflow, branch, Gradle task, artifact, and target metadata.
- Added build targets for debug APK, release APK, and release AAB.
- Added explicit lifecycle states for ready, dispatching, running, succeeded, and cancelled builds.
- Added capability availability states for remote dispatch, live logs, and artifact discovery.
- Added `BuildViewModel` for target/configuration selection and explicit dispatch gating.

### 2026-09-17 — Build Center surface

- Added the Build Center UI and connected the existing Build destination to it.
- Added target selection, execution-plan presentation, remote capability status, and build lifecycle messaging.
- Kept "Start remote build" explicitly blocked until GitHub authentication and workflow dispatch are implemented.

### 2026-09-17 — GitHub credential security foundation

- Added `SecretStore` as the runtime secret boundary.
- Added Android Keystore-backed AES/GCM storage using encrypted SharedPreferences ciphertext and a non-exportable Keystore key.
- Added GitHub connection state and a ViewModel for secure manual credential storage/removal.
- Added a dedicated GitHub connection screen as the current manual credential setup surface; OAuth/GitHub App authentication remains the future connection path.

### 2026-09-17 — GitHub Actions API gateway foundation

- Added `GitHubActionsGateway` for `workflow_dispatch` requests.
- Added Bearer-token handling behind `SecretStore`, GitHub API version header, explicit repository/workflow/ref inputs, and bounded error-message redaction.
- Added Android INTERNET permission for the future authenticated GitHub API path.
- Kept the Build Center capability gate closed until repository selection, credential verification, and end-to-end dispatch wiring are implemented.

### 2026-09-17 — AndroidX dependency alignment for API 36 CI

- CI run #86 exposed 16 AAR metadata failures caused by newer Compose, Navigation, and Adaptive artifacts requiring compileSdk 37 while DevForge intentionally targets stable API 36 on the current runner.
- Aligned the Compose BOM to `2026.06.01`, Activity Compose to `1.12.4`, Material3 Adaptive to `1.2.0`, and Navigation Compose to `2.9.8`.
- Kept Material3 `1.4.0`, Room `2.8.5`, Java 17, and the API 36 compile/target configuration unchanged.
- Preserved the remote-build architecture; this change only restores dependency/SDK compatibility for CI.

### 2026-09-17 — MainActivity Compose compatibility and Git dashboard wiring

- Reworked `MainActivity.kt` to use current Compose API signatures for cards and to connect the Git destination to `GitDashboardScreen`.
- Restored the existing editor ViewModel save semantics and fixed the workspace observation hashing input type.
- Updated the GitHub connection layout to use supported Compose layout APIs.

### 2026-09-17 — Material3 NavigationRail opt-in fix

- CI run #96 reached Kotlin compilation and reported an experimental Material3 API usage at the expanded `NavigationRail` surface in `MainActivity.kt`.
- Added the explicit `ExperimentalMaterial3Api` opt-in only to the navigation-rail composable, preserving the existing window-size opt-in and avoiding a broader global opt-in.
- Corrected the `BackHandler` import while applying the fix.
- CI run #103 completed successfully: toolchain verification, debug APK assembly, unit-test task, APK verification, and artifact upload all passed.

### 2026-09-17 — CI sdkmanager verification-path fix

- The attached CI handoff matched the repository application source and tracker; its workflow copy was older than the already-applied CI setup fix.
- CI run #102 reached toolchain verification but failed because `sdkmanager` was not available on `PATH` even though SDK installation had succeeded.
- Updated the workflow to persist the resolved `sdkmanager` path and add its directory to `GITHUB_PATH`, then reused that exact path for verification.
- CI run #103 passed toolchain verification, `:app:assembleDebug`, `:app:testDebugUnitTest`, APK verification, and artifact upload.
- No application-source changes from the supplied bundle were needed because the bundle's application files matched the repository state.
