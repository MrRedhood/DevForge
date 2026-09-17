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
- [x] Build configuration model with repository/workflow/ref/task/artifact metadata
- [x] Build lifecycle state model
- [x] Remote capability availability model
- [x] Build Center UI
- [x] Dispatch capability gating before authenticated mutation wiring
- [x] GitHub Actions API gateway foundation
- [x] Bounded/redacted GitHub error handling
- [x] Build Center repository selection entry point
- [x] Authenticated debug-APK workflow dispatch path
- [x] One-shot user confirmation tied to the typed build-dispatch capability/policy boundary
- [x] Remote run ID captured from GitHub's current workflow-dispatch response
- [x] Live workflow-run polling with bounded mobile refresh cadence
- [x] Workflow job log retrieval with a bounded output ceiling
- [x] Run artifact discovery
- [x] Run summary and remote GitHub run-link surface
- [x] Session-scoped build history surface

### Security / Policy Foundation

- [x] Capability model
- [x] Risk classification foundation
- [x] Approval-state foundation
- [x] Policy-gated execution foundation
- [x] Action-request model
- [x] SecretStore abstraction
- [x] Android Keystore-backed AES/GCM secret storage
- [x] Redacted secret/error handling

### GitHub Connection / Repository Discovery

- [x] GitHub connection screen
- [x] Secure manual credential entry/removal foundation
- [x] Secret storage through Android Keystore boundary
- [x] Authenticated GitHub REST boundary
- [x] Current-account discovery through authenticated API
- [x] Bounded authenticated repository listing
- [x] Repository access validation before selection
- [x] GitHub Actions workflow discovery
- [x] Active-workflow selection
- [x] Selected repository/workflow reflected in Build Center configuration
- [ ] OAuth / GitHub App connection flow
- [x] Debug workflow dispatch wiring

## In Progress / Next Implementation Sequence

1. Durable build history / receipts and release-safe workflow inputs.
2. Native/index-aware Git execution and status.
3. Approval center and action review UI.
4. Snapshot/history and structured diff viewer UI.
5. Richer Room entities for workspaces, tabs, snapshots, builds, agent tasks, and automation state.

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

- [x] Repository picker
- [x] Workflow discovery
- [x] Repository access validation
- [x] Active workflow selection
- [ ] OAuth/GitHub App authentication
- [x] Workflow/ref validation for the selected active workflow and repository default branch
- [x] Dispatch execution for the debug APK path
- [x] Run polling/state updates
- [x] Live logs
- [x] Artifact listing/download metadata
- [x] Session build-history surface
- [ ] Durable build receipts/history
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
- [x] CI #122 passed Android build, unit-test task, APK verification, and artifact upload after authenticated workflow-dispatch wiring
- [ ] Current run-monitoring implementation passes final CI validation
- [ ] Unit tests established as a maintained suite
- [ ] UI tests established
- [ ] Static analysis/lint established
- [ ] Release APK build validated
- [ ] Runtime GitHub dispatch integration validated with a live user credential

### CI findings / history

- API 37 was not resolvable on the GitHub runner, including the preview-channel installation path.
- The app therefore uses stable API 36 for `compileSdk` and `targetSdk`.
- CI requests `platform-tools` and `platforms;android-36` and reaches real Gradle/Kotlin compilation.
- CI run #86 reached Gradle compilation but failed AAR metadata validation because newer Compose/AndroidX artifacts required compileSdk 37.
- The dependency set was aligned back to an API-36-compatible line.
- CI run #96 reached Kotlin compilation and exposed an experimental Material3 API usage in the expanded navigation rail; the call now has a local `ExperimentalMaterial3Api` opt-in.
- CI run #102 reached toolchain verification but failed because `sdkmanager` was not on PATH during verification.
- CI run #103 passed toolchain verification, debug APK assembly, the unit-test task, APK verification, and artifact upload after the resolved `sdkmanager` path was reused.
- CI run #117 passed the complete Android build pipeline after repository/workflow discovery was implemented.
- CI run #122 passed the complete Android build pipeline after authenticated debug workflow dispatch was implemented.
- The monitoring implementation intentionally bounds log collection and keeps build history session-scoped until Room-backed durable build receipts are added.

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

- Added `IMPLEMENTATION.md` as the durable source of truth for implementation state.

### 2026-09-17 — Workspace foundation

- Added SAF workspace picker, persistable permissions, Room-backed workspace records, active-workspace persistence, recursive navigation, bounded enumeration, search, and safe previews.

### 2026-09-17 — Editor / recovery foundation

- Added multi-tab editor state, dirty tracking, explicit save, recovery drafts/checkpoints, content hashing, snapshots, and line-oriented diff support.

### 2026-09-17 — Git observation foundation

- Added repository detection, HEAD/branch/origin parsing, bounded workspace observation, generated-directory exclusions, branch discovery, and Git dashboard presentation.
- Kept native index-aware Git status/mutations explicitly unavailable.

### 2026-09-17 — Stable API-36 build baseline

- Moved the app and CI to stable API 36 after API 37 was unavailable on the hosted runner.
- Aligned Compose/AndroidX versions with the API-36-compatible dependency line.

### 2026-09-17 — Build Center / remote CI foundation

- Added build target/configuration models, lifecycle states, capability availability, Build Center UI, and a bounded GitHub Actions gateway.
- Kept remote mutation gated until authentication and repository/workflow discovery were ready.

### 2026-09-17 — GitHub secret boundary

- Added the `SecretStore` abstraction and Android Keystore-backed AES/GCM credential storage.
- Added manual GitHub token connection/removal UI and redacted errors.

### 2026-09-17 — Compose/CI compatibility fixes

- Added the required local Material3 experimental opt-in for `NavigationRail`.
- Hardened the Android CI workflow around JDK 17, Gradle 9.6.0, stable API 36, bounded workers, APK verification, and artifact upload.
- Fixed the `sdkmanager` verification path after CI #102; CI #103 passed build/test/APK validation.

### 2026-09-17 — Authenticated GitHub repository/workflow discovery

- Added typed repository and workflow models.
- Added a read-only GitHub REST gateway for current-account lookup, bounded repository listing, repository access validation, and Actions workflow discovery.
- Added a coroutine-backed repository/workflow ViewModel with bounded mobile state and explicit error reporting.
- Added a repository picker UI with search, private/public visibility, validation status, workflow state, and active-workflow selection.
- Added Build Center integration so the selected GitHub owner/repository, default branch, and active workflow become the actual build configuration.
- Preserved the workflow-dispatch capability gate; this implementation performs discovery/validation only and does not start a remote run.
- Added explicit JSON-array handling for `/user/repos` after validating the GitHub API response shape.
- Kept API version `2026-03-10`, matching the current GitHub REST documentation used by the gateway.

### 2026-09-17 — Authenticated workflow dispatch

- Hardened `GitHubActionsGateway` with bounded timeouts, repository/workflow identifier validation, current API-version handling, and strict workflow-run-ID parsing.
- Converted `BuildViewModel` to observable Android ViewModel state so dispatch lifecycle changes are reflected by Compose.
- Added authenticated `workflow_dispatch` execution behind the typed `DISPATCH_BUILD` capability and policy boundary.
- Treated the explicit Build Center start action as the user's one-shot confirmation until a dedicated Approval Center exists.
- Captured GitHub's returned workflow run ID and transitioned Build Center into a Running state.
- Intentionally kept release dispatch disabled because the current workflow has no explicit release-target inputs or signing-safe release contract.
- CI #122 validated this implementation path through Android build, unit-test task, APK verification, and artifact upload.

### 2026-09-17 — Live GitHub Actions run monitoring and build history

- Added typed GitHub Actions run, artifact, and job-log models.
- Added authenticated run polling with a five-second cadence and explicit terminal-state mapping.
- Added bounded job-log collection to protect mobile memory/UI performance.
- Added artifact metadata discovery and run links back to GitHub.
- Added session-scoped build history capped to 20 runs; durable Room-backed receipts remain a later persistence milestone.
- Added Build Center surfaces for live status, refresh, bounded logs, artifacts, and current-session history.
