# DevForge Implementation Tracker

> Living implementation record for DevForge. This file is updated after meaningful implementation changes so the repository remains the source of truth for what exists versus what is planned.

## Status Legend

- **Implemented** — supporting code/configuration exists.
- **Validated** — implementation has passed a concrete validation such as CI.
- **In progress** — actively being implemented.
- **Planned** — agreed direction without implementation.
- **Deferred** — intentionally postponed.

## Product / Architecture Direction

DevForge is an Android-first, mobile-native development environment combining workspace/file management, an editor, Git, GitHub integration, remote builds, an AI software-engineering agent, terminal capabilities, automation, approvals, recovery, and a modern adaptive UI.

Core rules:

- Kotlin + Jetpack Compose + Material 3.
- SAF for user-selected workspace access.
- Capability-based execution with policy/approval gates.
- AI/model output is untrusted data, never authorization.
- GitHub Actions is the remote build backbone.
- Do not bundle Android SDK/NDK/toolchains.
- Prefer bounded file/network/log operations for mobile resources.
- Secrets never appear in UI, logs, build receipts, or persisted action parameters.
- Preserve manual workflows when AI is unavailable.
- Keep unavailable capabilities explicit instead of simulating support.
- UI direction is original, modern, dark-first, touch-friendly, adaptive, and not copied from another product.

## Implemented

### App Foundation

- [x] Android/Kotlin/Compose application
- [x] Gradle project configuration
- [x] Material 3 and adaptive layout foundation
- [x] Compact navigation bar / expanded navigation rail
- [x] Chat, Files, Git, Build, and Settings destinations
- [x] DevForge-specific visual language
- [x] GitHub Actions Android CI workflow
- [x] Stable API 36 compile/target baseline
- [x] App-level back stack for destination navigation
- [x] Nested back handling for folders and editor tabs
- [x] Exit confirmation at the root navigation state
- [x] Unsaved-editor confirmation on back

### Workspace

- [x] SAF folder picker
- [x] Persistable URI permissions
- [x] Room-backed multiple workspace records
- [x] Active workspace persistence
- [x] Workspace open/switch foundation
- [x] Recursive folder navigation
- [x] Breadcrumbs and back/up/root navigation
- [x] Bounded enumeration
- [x] Bounded filename search
- [x] Safe text preview with 512 KiB ceiling
- [x] Binary-file detection
- [x] Workspace refresh and active-root handling
- [x] Legacy SharedPreferences workspace migration

### Editor / Recovery

- [x] Multi-tab editor foundation
- [x] Active tab and dirty-state tracking
- [x] Explicit save
- [x] Save-and-close path for back navigation
- [x] Unsaved-change protection foundation
- [x] Recovery drafts/checkpoints
- [x] SHA-256 content identity
- [x] Immutable bounded snapshots
- [x] Line-based diff engine
- [x] Duplicate checkpoint suppression
- [x] Snapshot lifecycle tied to opening/saving/recovery
- [x] Snapshot ViewModel foundation

Still planned: full syntax highlighting, diagnostics, undo/redo, symbol navigation, richer editing tools, and large-file editor safeguards.

### Git Observation / Status Foundation

- [x] `.git` detection through SAF
- [x] HEAD parsing
- [x] Current branch extraction
- [x] Attached/detached HEAD revision resolution where refs are readable
- [x] Origin remote parsing
- [x] Linked/worktree `.git` unsupported-state reporting
- [x] Loose branch discovery
- [x] Packed-refs branch discovery
- [x] Bounded repository traversal
- [x] `.git`, build, and `.gradle` exclusions
- [x] Partial/truncated observation reporting
- [x] Git dashboard metadata/branch presentation
- [x] Git index v2/v3 parser with bounded entry count
- [x] Git index conflict-stage parsing
- [x] Git blob SHA-1 hashing for bounded worktree files
- [x] Index-vs-worktree clean/modified/deleted/untracked classification
- [x] Loose-object Git commit/tree reader with bounded decompression
- [x] HEAD/index/worktree staged/unstaged classification when HEAD objects are readable
- [x] Conflict-state detection from index stages
- [x] Explicit fallback to index/worktree status when Git objects are packed or inaccessible through SAF
- [x] Git dashboard surfaces clean/modified/staged/staged+modified/untracked/deleted/conflict/unchecked states

Still not implemented: stage/unstage, commit, branch mutation, fetch/pull/push, merge/rebase/cherry-pick, conflict-resolution mutations, and capability-controlled Git execution.

### Build Center / GitHub Actions

- [x] Debug APK target model
- [x] Release APK target model
- [x] Release AAB target model
- [x] Build configuration/state/capability models
- [x] Build Center source/repository/target/execution UI
- [x] GitHub workflow discovery integration
- [x] Authenticated debug APK workflow dispatch
- [x] Dispatch capability/policy gate
- [x] Explicit one-shot user confirmation before dispatch
- [x] Run ID capture
- [x] Five-second bounded run polling
- [x] Queued/in-progress/waiting/requested/pending handling
- [x] Terminal state mapping
- [x] Bounded job log retrieval
- [x] Artifact discovery and metadata
- [x] Remote GitHub run links
- [x] Build summary/status surfaces
- [x] Session build history surface
- [x] Durable Room-backed build receipts/history
- [x] Room build-history Flow loading at ViewModel start
- [x] Duplicate run-ID suppression
- [x] Bounded history pruning to 20 receipts
- [x] Transactional receipt insert + prune operation
- [x] Room v1 → v2 migration for build receipts
- [x] Explicit GitHub Actions workflow_dispatch target inputs: debug APK, release APK, release AAB
- [x] Fixed target-to-Gradle-task mapping inside CI
- [x] Fixed target-to-artifact mapping inside CI
- [x] CI rejects unsupported target values instead of accepting arbitrary Gradle commands

Not implemented yet: release dispatch wiring in DevForge, signing-specific release policy, cancellation, and runtime validation with a live user credential.

### Chat / UX

- [x] Chat landing screen reduced to essential assistant content
- [x] Removed workspace/git/build/agent status labels and shortcut chips from Chat
- [x] Removed unrelated Git/build activity cards from Chat

### GitHub Connection / Discovery

- [x] Manual GitHub credential entry/removal foundation
- [x] Android Keystore-backed secret storage
- [x] Authenticated GitHub REST boundary
- [x] Current-account lookup
- [x] Bounded repository listing
- [x] Repository access validation
- [x] Actions workflow discovery
- [x] Active workflow selection
- [x] Selected repository/workflow integration into Build Center
- [ ] OAuth / GitHub App authentication

### Security / Policy Foundation

- [x] Capability model
- [x] R0–R5 risk foundation
- [x] Approval-state foundation
- [x] Default policy evaluation
- [x] Typed action requests
- [x] SecretStore abstraction
- [x] Android Keystore AES/GCM storage
- [x] Secret-safe/redacted error handling
- [x] `DISPATCH_BUILD` capability with R2 policy gate

## In Progress / Next Sequence

1. Release dispatch wiring against the explicit workflow target inputs and signing-safe release contract.
2. Capability-controlled Git execution: stage/unstage, commit, branch mutation, and remote operations.
3. Approval Center and action-review UI.
4. Snapshot/history and structured diff viewer UI.
5. Richer Room persistence for tabs, snapshots, agent tasks, automation, approvals, and audit activity.

## Planned

### AI / Agent

- [ ] Provider-neutral AI interface
- [ ] Gemini/OpenRouter/other provider adapters
- [ ] Workspace context assembly
- [ ] File-aware context and symbol extraction
- [ ] Planning/task graph
- [ ] Typed tool gateway
- [ ] Structured patch generation
- [ ] Diff-first proposal UI
- [ ] Verification loop
- [ ] Persistent agent task state
- [ ] Workspace memory/knowledge
- [ ] Token/context budgeting
- [ ] Model capability matrix
- [ ] Provider fallback/routing

### Editor Intelligence

- [ ] Syntax highlighting
- [ ] Language-aware editing foundation
- [ ] Diagnostics/problems panel
- [ ] Undo/redo
- [ ] Find/replace
- [ ] Go-to-line/symbol
- [ ] Code folding
- [ ] Selection/edit actions
- [ ] Large-file performance safeguards
- [ ] Editor preferences

### Git

- [x] Index/worktree read status foundation
- [x] HEAD/index/worktree staged-state read foundation
- [ ] Git diff computation from index/HEAD
- [ ] Stage/unstage
- [ ] Commit
- [ ] Branch create/switch/delete
- [ ] Fetch/pull/push through capability gateway
- [ ] Merge/rebase/cherry-pick planning and execution
- [ ] Conflict handling UI/actions
- [ ] Commit history and file history

### Remote Build / GitHub

- [x] Repository picker
- [x] Workflow discovery
- [x] Repository validation
- [x] Workflow/ref validation for selected repository/default branch
- [x] Debug workflow dispatch
- [x] Run polling
- [x] Live logs
- [x] Artifact metadata
- [x] Durable build receipts/history
- [x] Explicit workflow_dispatch target contract for debug/release APK/release AAB
- [ ] OAuth/GitHub App authentication
- [ ] DevForge release dispatch wiring
- [ ] Signing-safe release configuration and validation
- [ ] Build cancellation
- [ ] Runtime dispatch validation with live credential

### Terminal / Execution

- [ ] Sandboxed terminal capability
- [ ] Command request model
- [ ] Argument/path validation
- [ ] Resource/time limits
- [ ] Streaming output
- [ ] Cancel/terminate
- [ ] Command history
- [ ] Terminal sessions/tabs
- [ ] No unrestricted arbitrary AI shell access

### Automation

- [ ] Automation definitions
- [ ] Triggers/schedules
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
- [ ] Redaction regression tests
- [ ] Optional biometric/lockscreen secret access

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
- [ ] About/version/license

### UI / UX

- [x] Modern dark-first Material 3 foundation
- [x] Adaptive compact/expanded navigation
- [x] Original DevForge visual direction
- [x] Touch-friendly controls
- [ ] Tablet two-pane refinements
- [ ] Foldable posture refinements
- [ ] Rich activity/notification surface
- [ ] Command/search launcher
- [ ] Hardware keyboard shortcuts
- [ ] Accessibility semantic audit
- [ ] Motion/reduced-motion support

### Quality / Observability

- [ ] Maintained unit-test suite
- [ ] Compose/UI tests
- [ ] Static analysis/lint pipeline
- [ ] Release build validation
- [ ] Performance/budget checks
- [ ] Structured diagnostic logging
- [ ] Crash/recovery validation
- [ ] End-to-end build/dispatch tests
- [ ] Security regression tests

## Validation

- [x] GitHub Actions workflow configured
- [x] CI triggered by implementation commits
- [x] CI #103 passed toolchain verification, debug build, unit-test task, APK verification, and artifact upload
- [x] CI #117 passed the Android pipeline after repository/workflow discovery
- [x] CI #122 passed the Android pipeline after authenticated debug workflow dispatch
- [x] CI #127 recorded as cancelled by concurrency, not a build failure
- [ ] Current navigation/chat/release-workflow changes final CI validation
- [ ] Maintained unit-test suite
- [ ] UI tests
- [ ] Static analysis/lint
- [ ] Release APK validation
- [ ] Live authenticated dispatch/runtime validation

### CI Findings

- Stable API 36 is used because the hosted runner could not reliably resolve API 37.
- Compose/AndroidX dependencies were aligned to the API-36-compatible line.
- CI #96 exposed an experimental Material3 `NavigationRail` call; it now has a local opt-in.
- CI #102 exposed `sdkmanager` PATH handling; the workflow now persists the resolved path.
- CI #103 validated the resulting toolchain/build/test/APK pipeline.
- CI #126, #127 and later intermediate runs were superseded by newer commits under workflow concurrency; they are not treated as build failures.

## Implementation Rules

- Never mark planned functionality as implemented.
- Never fake Git status, GitHub authentication, build execution, agent execution, or terminal execution.
- Side effects must use typed capabilities and policy/approval checks.
- AI output is untrusted and must pass through tool/policy boundaries.
- Keep mobile operations bounded and report partial/unavailable states explicitly.
- Do not store tokens, secrets, raw logs, or arbitrary secret inputs in build receipts.
- Do not bundle heavyweight Android SDK/NDK resources.
- Avoid broad refactors unless required by the current milestone.
- Update this tracker after every meaningful repository change.

## Change Log

### 2026-09-17 — Foundation

- Added workspace/SAF persistence, editor/recovery foundation, Git observation, Build Center, GitHub discovery, policy/capability checks, Keystore-backed secrets, and Android CI.

### 2026-09-17 — Authenticated workflow dispatch

- Added authenticated debug workflow dispatch through the typed `DISPATCH_BUILD` capability and policy boundary.
- Captured remote workflow run IDs and preserved release dispatch as unavailable until a release-safe contract exists.

### 2026-09-17 — Live Actions monitoring

- Added typed run/artifact/job-log models, bounded run polling, bounded job logs, artifact metadata, remote run links, and a session history surface.

### 2026-09-17 — Durable build receipts

- Added `BuildReceiptEntity` and Room v1 → v2 migration.
- Added `BuildReceiptDao` with observable recent receipts, duplicate-safe insertion, and transactional pruning.
- Updated `BuildViewModel` to load history from Room and persist completed GitHub Actions runs instead of keeping history only in memory.
- Kept receipts limited to build metadata; no tokens, authorization headers, raw logs, or workflow secret inputs are persisted.
- Bounded persisted history to 20 receipts, matching the mobile-oriented history policy.

### 2026-09-17 — Index-aware Git worktree status foundation

- Added a bounded Git index v2/v3 parser and real Git blob SHA-1 hashing.
- Added clean/modified/deleted/untracked/unchecked classification by comparing the bounded worktree against the index.
- Added index conflict-stage parsing and explicit partial-state limits.

### 2026-09-17 — HEAD-aware Git status

- Added attached-branch HEAD revision resolution plus detached-HEAD support.
- Added bounded loose-object parsing for Git commits and trees.
- Added HEAD/index/worktree comparison for clean, modified, staged, staged+modified, deleted, untracked, and conflict states.
- Added safe fallback to index/worktree-only status when Git object storage is packed or inaccessible through SAF.
- Updated the Git dashboard to surface the richer state model while keeping stage/commit/push controls disabled behind the future capability execution layer.

### 2026-09-17 — Navigation and Chat cleanup

- Added an app-level destination back stack so Android back returns one navigation state at a time instead of immediately closing the app.
- Added nested editor back behavior with save/discard/cancel handling for dirty files.
- Added final-root exit confirmation instead of immediate activity finish.
- Kept Files folder back behavior intact so deeper folders unwind before destination navigation.
- Simplified Chat to its essential assistant landing state and removed workspace/git/build/agent labels and cards.

### 2026-09-17 — Explicit remote build target contract

- Added workflow_dispatch target inputs for debug APK, release APK, and release AAB.
- Added fixed target-to-task and target-to-artifact mappings in CI.
- Rejected arbitrary or unknown build target values in the workflow.
- Kept DevForge release dispatch gated until the app-side dispatch payload and signing-safe release policy are wired to this contract.
