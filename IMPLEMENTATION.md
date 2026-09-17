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
- [x] Unsaved-change protection foundation
- [x] Recovery drafts/checkpoints
- [x] SHA-256 content identity
- [x] Immutable bounded snapshots
- [x] Line-based diff engine
- [x] Duplicate checkpoint suppression
- [x] Snapshot lifecycle tied to opening/saving/recovery
- [x] Snapshot ViewModel foundation

Still planned: full syntax highlighting, diagnostics, undo/redo, symbol navigation, richer editing tools, and large-file editor safeguards.

### Git Observation

- [x] `.git` detection through SAF
- [x] HEAD parsing
- [x] Current branch extraction
- [x] Detached-HEAD detection
- [x] Origin remote parsing
- [x] Linked/worktree `.git` unsupported-state reporting
- [x] Loose branch discovery
- [x] Packed-refs branch discovery
- [x] Bounded repository traversal
- [x] `.git`, build, and `.gradle` exclusions
- [x] Optional small-file hashing
- [x] Partial/truncated observation reporting
- [x] Git dashboard metadata/branch presentation
- [x] Explicit unavailable-state messaging for native Git mutation/status

Important: current Git support is observation, not a complete native/index-aware Git implementation.

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

Not implemented yet: release-safe workflow inputs, release dispatch, cancellation, and runtime validation with a live user credential.

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

1. Release-safe workflow inputs and release contract.
2. Native/index-aware Git execution and status.
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

- [ ] Native/index-aware Git status
- [ ] Worktree/index diff computation
- [ ] Stage/unstage
- [ ] Commit
- [ ] Branch create/switch/delete
- [ ] Fetch/pull/push through capability gateway
- [ ] Merge/rebase/cherry-pick planning and execution
- [ ] Conflict handling UI
- [ ] Commit/file history

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
- [ ] OAuth/GitHub App authentication
- [ ] Release-safe workflow inputs
- [ ] Release APK workflow support
- [ ] Release AAB workflow support
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
- [ ] Durable build-history implementation final CI validation
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
- CI #126 was superseded/cancelled by concurrency after checkout and is not treated as a failure.
- CI #127 was similarly cancelled as newer commits superseded it.

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
