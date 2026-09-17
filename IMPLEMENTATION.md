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
- [x] Transactional workspace activation

### Editor / Recovery / Change Model

- [x] SAF-backed file read/write
- [x] Editor tabs and active-tab switching
- [x] Dirty-state tracking and explicit save
- [x] Unsaved-change protection
- [x] Local recovery drafts and delayed checkpoints
- [x] Mobile monospace editor surface
- [x] SHA-256 content hashing
- [x] Immutable bounded snapshots
- [x] Snapshot ViewModel
- [x] Line-oriented diff engine
- [x] Snapshot lifecycle connected to open/save/recovery flows
- [x] Hash-based duplicate checkpoint suppression

### Git Foundation

- [x] Git repository state domain model
- [x] SAF `.git` directory detection
- [x] HEAD metadata and branch-name extraction
- [x] Detached-HEAD recognition and basic detached revision capture
- [x] `origin` remote URL extraction
- [x] Explicit unsupported state for linked/worktree `.git` files
- [x] Detection automatically follows the active Room workspace
- [x] Loose local branch discovery
- [x] Packed branch discovery
- [x] Bounded branch traversal
- [x] Bounded workspace inventory/status-observation foundation
- [x] Small-file content hashes for observation
- [x] Generated-directory exclusions
- [x] Explicit partial/truncated observation states
- [x] Git repository state dashboard surface
- [x] Local branch list presentation
- [x] Workspace observation dashboard surface
- [ ] Native working-tree status inspection
- [ ] Staged/unstaged file model
- [ ] Branch switching
- [ ] Commit history
- [ ] Git diff UI
- [ ] Local mutation operations
- [ ] GitHub remote integration

### Remote Build / CI Foundation

- [x] Declarative build configuration model
- [x] Debug APK / release APK / release AAB target model
- [x] Workflow-file, branch, Gradle-task, and artifact-name configuration
- [x] Explicit build lifecycle state model
- [x] Remote capability availability model
- [x] Build ViewModel with target selection and configuration updates
- [x] Build Center UI connected to the Build destination
- [x] Explicit dispatch-unavailable state instead of pretending a remote run started
- [x] GitHub credential storage boundary
- [x] GitHub Actions REST dispatch gateway foundation
- [ ] GitHub account identity verification
- [ ] OAuth / GitHub App authentication
- [ ] Repository selection
- [ ] Workflow discovery
- [ ] Workflow dispatch UI execution
- [ ] Live run status
- [ ] Logs
- [ ] Artifact discovery/download
- [ ] Build history
- [ ] Failure diagnostics

### Security / Domain Foundation

- [x] Capability model
- [x] Risk classification
- [x] Permission/approval model
- [x] Action request model
- [x] Approval state foundation
- [x] Foundation for policy-gated AI tool execution
- [x] `SecretStore` abstraction
- [x] Android Keystore-backed AES/GCM secret storage
- [x] Secret redaction boundary for GitHub API errors

## Persistence Architecture

### Room workspace database — Implemented

- Database: `devforge.db`
- Room version: 2.8.5 stable
- KSP-based Room compiler
- `WorkspaceEntity`, `WorkspaceDao`, `DevForgeDatabase`, `WorkspaceDatabaseRepository`
- Flow-based active/all workspace state
- Transactional activate/save behavior
- One-time legacy selected-workspace migration
- Room schema output under `app/schemas`

### Intentionally not persisted in Room yet

Room expansion remains planned for open tabs, snapshots, Git metadata, agent tasks, build history, automation runs, and other relational state once those models stabilize.

## Validation

- [x] GitHub Actions workflow configured
- [x] CI triggered by implementation commits
- [ ] Latest implementation commit passes Android build
- [ ] Unit tests established
- [ ] UI tests established
- [ ] Static analysis/lint established
- [ ] Release APK build validated

### Latest CI findings

- API 37 was not resolvable on the GitHub runner, including the preview-channel installation path.
- The app therefore uses stable API 36 for `compileSdk` and `targetSdk`.
- CI requests `platform-tools` and `platforms;android-36` and is intended to reach real Gradle/Kotlin compilation.
- Build validation is still pending for the newest GitHub/security implementation commits.

## In Progress / Next

1. Wire authenticated GitHub repository selection and workflow discovery into Build Center.
2. Complete workflow dispatch execution with explicit repository/ref selection.
3. Add live run status, logs, and artifact handling.
4. Native Git execution/index-aware status capability.
5. Approval UI connected to real action requests.
6. Diff viewer and snapshot/recovery history UI.
7. Workspace switcher UI and richer Room entities after core models stabilize.

## Planned — AI & Agent

- [ ] Provider abstraction
- [ ] Secure API-key handling
- [ ] Model catalog/configuration
- [ ] Context assembly
- [ ] Workspace indexing/search
- [ ] Tool registry and schemas
- [ ] Capability/policy enforcement
- [ ] Approval queue
- [ ] Agent task state machine
- [ ] Plan/execute/review flow
- [ ] Patch generation/application
- [ ] Diff-first review
- [ ] Verification loop
- [ ] Recovery/rollback loop
- [ ] Cost/token telemetry
- [ ] Multi-provider routing

## Planned — Workspace & Editor

- [x] SAF workspace selection
- [x] Workspace database foundation
- [x] Recursive folder navigation
- [x] Breadcrumb navigation
- [x] Filename search
- [x] Safe preview foundation
- [x] File read/write
- [x] Editor tabs
- [ ] Syntax highlighting
- [ ] Diagnostics
- [ ] Undo/redo
- [x] Recovery draft hook
- [x] Snapshot/hash foundation
- [x] Diff engine foundation
- [ ] Diff viewer UI
- [ ] Restore points UI
- [ ] Content indexing/full-text search
- [ ] Symbol navigation
- [x] Large-file preview safeguard
- [ ] Large-file editor safeguard

## Planned — Git

- [x] Repository detection foundation
- [x] Basic branch/HEAD metadata model
- [x] Basic remote-origin metadata model
- [x] Local branch discovery foundation
- [x] Workspace observation/status foundation
- [x] Git repository state surface
- [x] Branch list surface
- [x] Workspace observation surface
- [ ] Native working-tree status
- [ ] Staged/unstaged file model
- [ ] Branch switching
- [ ] Commit history
- [ ] Status/diff view
- [ ] Stage/unstage
- [ ] Commit flow
- [ ] Push/pull/fetch
- [ ] Merge/rebase
- [ ] Conflict UI
- [ ] Safe-operation confirmation
- [ ] GitHub integration

## Planned — Remote Build / CI

- [x] Build configuration model
- [x] Build target selection model
- [x] Build lifecycle state model
- [x] Build Center presentation
- [x] Secure credential-storage foundation
- [x] GitHub Actions API gateway foundation
- [ ] GitHub account verification
- [ ] OAuth/GitHub App connection
- [ ] Repository selection
- [ ] Workflow discovery
- [ ] Workflow dispatch
- [ ] Live status
- [ ] Logs
- [ ] Artifact discovery
- [ ] APK/AAB handling
- [ ] Install/download handoff
- [ ] Build history
- [ ] Failure diagnostics

## Planned — Terminal

- [ ] Terminal surface
- [ ] Sessions
- [ ] Command execution abstraction
- [ ] Output streaming
- [ ] Process lifecycle/cancellation
- [ ] Working directory
- [ ] Environment model
- [ ] Safe command policy
- [ ] Permission prompts
- [ ] Remote execution integration

## Planned — Automation

- [ ] Automation model
- [ ] Triggers/actions
- [ ] Conditions
- [ ] Permission/approval model
- [ ] Scheduling
- [ ] Event triggers
- [ ] Run history
- [ ] Retry/backoff
- [ ] Failure recovery
- [ ] Automation editor UI

## Planned — Security & Privacy

- [x] Android Keystore foundation
- [x] Encrypted secret storage foundation
- [x] GitHub error secret-redaction boundary
- [ ] Secret redaction across all logs/UI
- [ ] API-key lifecycle
- [ ] OAuth/token lifecycle
- [ ] Capability registry
- [ ] Least-privilege defaults
- [ ] Confirmation policy
- [ ] Audit log
- [ ] Sensitive-file controls
- [ ] Network policy
- [ ] Export/delete controls

## Planned — Settings

- [ ] Appearance
- [ ] Editor
- [ ] Workspace
- [ ] Git
- [ ] Build
- [ ] GitHub connection
- [ ] AI providers/models
- [ ] Agent behavior
- [ ] Permissions/security
- [ ] Terminal
- [ ] Automation
- [ ] Notifications
- [ ] Performance
- [ ] Accessibility
- [ ] Storage/cache
- [ ] Diagnostics
- [ ] About/license

## Planned — UI/UX Expansion

- [ ] Onboarding
- [x] Workspace empty/loading/root states
- [x] Recursive workspace browser
- [x] Breadcrumb surface
- [x] Search surface foundation
- [x] Safe preview foundation
- [x] Editor workspace chrome foundation
- [x] Unsaved-change confirmation
- [ ] Workspace switcher surface
- [x] Git repository state surface
- [x] Git branch list surface
- [ ] Git status/diff surface
- [ ] Diff viewer
- [ ] Snapshot/recovery history
- [ ] Error/recovery states
- [ ] Approval sheets/dialogs
- [ ] Command palette
- [ ] Global search
- [ ] Quick actions
- [ ] Activity center
- [ ] Agent timeline
- [x] Build Center surface
- [ ] Build detail
- [ ] Git detail screens
- [ ] Full editor chrome
- [ ] Tablet/foldable layouts
- [ ] Accessibility semantics
- [ ] Motion/transition system
- [ ] Haptics

## Planned — Quality & Observability

- [ ] Unit tests
- [ ] Repository/data tests
- [ ] Editor recovery tests
- [ ] Snapshot/hash tests
- [ ] Diff tests
- [ ] Workspace search tests
- [ ] Preview-policy tests
- [ ] Git metadata tests
- [ ] Git branch discovery tests
- [ ] Git workspace observation tests
- [ ] Agent/tool contract tests
- [ ] Policy tests
- [ ] UI tests
- [ ] Build smoke tests
- [ ] Static analysis
- [ ] Performance profiling
- [ ] Crash reporting strategy
- [ ] Structured local diagnostics
- [ ] Privacy-preserving telemetry

## Implementation Rules

1. Update this file after every meaningful implementation change.
2. Never mark interface-only work as implemented.
3. Keep validation status separate from implementation status.
4. Preserve Android/mobile-first constraints.
5. Do not bundle heavyweight Android SDK/NDK/toolchains.
6. AI actions remain capability-scoped, policy-checked, and approval-aware.
7. Destructive/external actions require safeguards and recovery.
8. UI must remain original, stylish, touch-friendly, adaptive, accessible, and recognizably DevForge.
9. Use least-privilege SAF workspace access.
10. Keep editing buffers, saved file state, and recovery drafts separate.
11. Prefer content-based identity for snapshots/diffs/recovery correctness.
12. Keep local snapshot/history stores bounded until richer database models stabilize.
13. Keep workspace search/preview bounded and fail safely on large/binary content.
14. Use Room for durable relational state once the model warrants it; do not prematurely persist every transient UI state.
15. Treat SAF Git metadata as capability-dependent: absence of `.git` visibility or worktree indirection must surface as an explicit unsupported/unknown state rather than a false repository state.
16. Keep Git metadata and workspace observation scanning bounded; native Git mutation/status requires a dedicated execution layer rather than unsafe ad-hoc file manipulation.
17. Never store GitHub or AI secrets in source-controlled configuration.
18. Runtime credentials must stay behind `SecretStore` and must never be rendered, logged, or exposed through general UI state.
19. Remote actions remain capability-gated until repository/ref/permission requirements are verified.

## Change Log

### 2026-09-17 — Initial tracker

- Created the living implementation tracker and recorded the initial app foundation.

### 2026-09-17 — Workspace foundation

- Added SAF workspace selection, real workspace enumeration, loading/empty states, refresh, and workspace-aware header.
- Added durable workspace persistence after the Room migration slice.

### 2026-09-17 — Editor foundation

- Added SAF file read/write, tabs, dirty state, explicit save, unsaved-change protection, and local recovery drafts.

### 2026-09-17 — Content identity, snapshots, and diff foundation

- Added SHA-256 hashing, immutable snapshots, bounded snapshot storage, SnapshotViewModel, and the line-oriented diff engine.

### 2026-09-17 — Editor snapshot lifecycle

- Connected snapshots to file-open, successful save, recovery-draft creation, and recovery reopening.

### 2026-09-17 — Recursive workspace navigation

- Added nested SAF folder traversal, breadcrumb hierarchy, and back/up/root navigation.

### 2026-09-17 — Workspace search and safe preview

- Added bounded recursive filename search, safe preview handling, binary detection, search-to-editor navigation, and large-preview safeguards.

### 2026-09-17 — Durable workspace persistence with Room

- Added KSP and AndroidX Room 2.8.5 stable dependencies, Room database/DAO/repository layers, Flow-based active/all workspace state, transactional workspace activation, legacy migration, and schema generation.
- Kept richer entities such as snapshots, Git, agent tasks, builds, and automations for later model stabilization.

### 2026-09-17 — Git repository detection foundation

- Added `GitRepositoryState`, explicit detection states, SAF `.git` detection, HEAD parsing, origin URL extraction, unsupported worktree reporting, and active-workspace-aware Git detection.

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
- Added explicit lifecycle states for ready, dispatching, running, succeeded, failed, and cancelled builds.
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
