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

### Workspace

- [x] Android Storage Access Framework workspace selection
- [x] Persistable URI permission handling
- [x] Workspace-aware global header
- [x] Real workspace root enumeration
- [x] Recursive folder listing
- [x] Nested folder navigation
- [x] Breadcrumb hierarchy
- [x] Back/up/root navigation
- [x] Bounded per-folder enumeration
- [x] Loading and empty states
- [x] Filename-first bounded recursive search
- [x] Safe file preview policy
- [x] 512 KiB preview ceiling
- [x] Basic binary-content detection
- [x] Search result navigation/opening
- [x] Multiple workspace records persisted with Room
- [x] Active workspace state persisted with Room
- [x] Legacy SharedPreferences workspace migration
- [x] Transactional workspace activation

### Editor / Recovery / Change Model

- [x] SAF-backed file read/write
- [x] Editor tabs
- [x] Active-tab switching
- [x] Dirty-state tracking
- [x] Explicit save
- [x] Unsaved-change protection
- [x] Local recovery drafts
- [x] Delayed recovery checkpointing
- [x] Mobile monospace editor surface
- [x] Focused editor mode on compact devices
- [x] SHA-256 content hashing
- [x] Immutable content snapshots
- [x] Bounded per-file snapshot history
- [x] Snapshot ViewModel
- [x] Line-oriented diff engine
- [x] Snapshots connected to file-open baseline
- [x] Snapshots connected to successful saves
- [x] Recovery snapshots connected to delayed recovery drafts
- [x] Hash-based duplicate checkpoint suppression

### Git Foundation

- [x] Git repository state domain model
- [x] SAF-based `.git` directory detection
- [x] HEAD metadata reading
- [x] Branch-name extraction from `refs/heads/*`
- [x] Detached-HEAD recognition
- [x] Basic HEAD revision capture for detached HEADs
- [x] `origin` remote URL extraction from `.git/config`
- [x] Explicit unsupported-state reporting for linked/worktree `.git` files
- [x] Git detection automatically follows the active Room workspace
- [ ] Working-tree status inspection
- [ ] Staged/unstaged file model
- [ ] Branch list / switching
- [ ] Commit history
- [ ] Git diff UI
- [ ] Local mutation operations
- [ ] GitHub remote integration

### Domain / Safety

- [x] Capability model
- [x] Risk classification
- [x] Permission/approval model
- [x] Action request model
- [x] Approval state foundation
- [x] Foundation for policy-gated AI tool execution

## Persistence Architecture

### Room workspace database — Implemented

- Database: `devforge.db`
- Room version: 2.8.5 stable
- KSP-based Room compiler
- `WorkspaceEntity`
- `WorkspaceDao`
- `DevForgeDatabase`
- `WorkspaceDatabaseRepository`
- Flow-based active/all workspace state
- Transactional activate/save behavior
- Legacy selected-workspace record migrated once into Room
- Room schema output configured under `app/schemas`

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

### Latest CI finding

- The Room implementation commit reached GitHub Actions, but the run failed during Android SDK setup before Gradle execution because the workflow attempted to install the removed `tools` SDK package.
- The CI workflow was updated to use `actions/setup-java@v5` and explicitly install `platform-tools` plus `platforms;android-37` through `android-actions/setup-android@v3`.
- A new post-fix CI run is required before marking the Android build as validated.

## In Progress / Next

1. Git status/diff/branch presentation
2. Build configuration/state model
3. Approval UI connected to real action requests
4. Diff viewer and snapshot/recovery history UI
5. Workspace switcher UI for selecting among persisted workspaces
6. Richer Room entities after Git/agent/build models stabilize

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
- [ ] Branch list
- [ ] Commit history
- [ ] Status/diff view
- [ ] Stage/unstage
- [ ] Commit flow
- [ ] Push/pull/fetch
- [ ] Branch create/switch
- [ ] Merge/rebase
- [ ] Conflict UI
- [ ] Safe-operation confirmation
- [ ] GitHub integration

## Planned — Remote Build / CI

- [ ] GitHub account connection
- [ ] Repository selection
- [ ] Workflow discovery
- [ ] Build configuration
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

- [ ] Android Keystore
- [ ] Encrypted secrets
- [ ] Secret redaction
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
- [ ] Git repository state surface
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

## Change Log

### 2026-09-17 — Initial tracker

- Created the living implementation tracker and recorded the initial app foundation.

### 2026-09-17 — Workspace foundation

- Added SAF workspace selection and persisted selected-workspace metadata.
- Added real workspace enumeration, loading/empty states, refresh, and workspace-aware header.
- Recorded Room as intentionally deferred until richer durable state justified it.

### 2026-09-17 — Editor foundation

- Added SAF file read/write, tabs, dirty state, explicit save, unsaved-change protection, and local recovery drafts.
- Added focused mobile editor UI.

### 2026-09-17 — Content identity, snapshots, and diff foundation

- Added SHA-256 hashing, immutable snapshots, bounded snapshot storage, SnapshotViewModel, and the line-oriented diff engine.

### 2026-09-17 — Editor snapshot lifecycle

- Connected snapshots to file-open, successful save, recovery-draft creation, and recovery reopening.

### 2026-09-17 — Recursive workspace navigation

- Added nested SAF folder traversal, breadcrumb hierarchy, and back/up/root navigation.

### 2026-09-17 — Workspace search and safe preview

- Added bounded recursive filename search, safe preview handling, 512 KiB preview ceiling, basic binary detection, search-to-editor navigation, and large-preview safeguards.

### 2026-09-17 — Durable workspace persistence with Room

- Added KSP and AndroidX Room 2.8.5 stable dependencies.
- Added Room database, workspace entity, DAO, and repository layers.
- Added Flow-based active/all workspace state.
- Added transactional workspace activation and multi-workspace retention.
- Added one-time migration from the previous SharedPreferences workspace record.
- Removed the superseded single-workspace preference repository.
- Configured Room schema generation under `app/schemas`.
- Kept richer entities such as snapshots, Git, agent tasks, builds, and automations for later model stabilization.
- CI initially failed before Gradle during Android SDK setup; the workflow fix is tracked separately.

### 2026-09-17 — Git repository detection foundation

- Added `GitRepositoryState` and explicit detection states.
- Added SAF-based `.git` directory detection without requesting broader storage permissions.
- Added HEAD parsing for normal branches and detached HEADs.
- Added basic `origin` URL extraction from `.git/config`.
- Added explicit unsupported handling for `.git` files/worktree-style indirection that the current SAF metadata reader does not yet support.
- Added `GitViewModel` that follows the active Room workspace and refreshes Git detection when the workspace changes.
- Deferred working-tree status, stage/unstage, mutation operations, and GitHub remote actions until a dedicated Git operation layer exists.

### 2026-09-17 — CI workflow hardening

- Updated `actions/setup-java` from v4 to v5.
- Updated Android SDK setup to avoid the obsolete `tools` package and explicitly request `platform-tools` and `platforms;android-37`.
- A fresh CI run is required to validate the workflow and the current application code together.
