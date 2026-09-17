# DevForge Implementation Tracker

> Living implementation record for DevForge. Updated after meaningful repository changes so the repository remains the source of truth for implemented vs planned work.

## Product / Architecture Rules

- Android-first, mobile-native Kotlin + Jetpack Compose + Material 3.
- SAF is the workspace boundary; no hidden filesystem assumptions.
- Git/AI/build/terminal side effects use typed capabilities and policy gates.
- AI output is untrusted data and never grants authorization.
- GitHub Actions is the remote-build backbone; no Android SDK/NDK/toolchain bundled in the app.
- Mobile work is bounded by file, object, index, tree, log, and history limits.
- Secrets are not persisted in action parameters, receipts, logs, or UI state.
- Unavailable capabilities are shown as unavailable rather than simulated.

## Implemented

### App Foundation
- [x] Android/Kotlin/Compose project and Gradle configuration
- [x] Material 3/adaptive UI foundation
- [x] Chat, Files, Git, Diffs, Build, Approvals, Settings destinations
- [x] Modern dark-first DevForge visual direction
- [x] App-level destination back stack
- [x] Nested folder/editor back handling
- [x] Unsaved editor confirmation and root exit confirmation
- [x] API 36 CI/toolchain baseline

### Workspace
- [x] SAF folder picker and persistable permissions
- [x] Room-backed multiple workspace records
- [x] Active workspace persistence/switching
- [x] Recursive navigation, breadcrumbs and bounded enumeration/search
- [x] Safe text preview and binary detection
- [x] Workspace refresh and legacy workspace migration

### Editor / Recovery
- [x] Multi-tab editor and dirty tracking
- [x] Explicit save and save-and-close
- [x] Recovery drafts/checkpoints
- [x] SHA-256 content identity
- [x] Immutable bounded snapshots
- [x] Line-based diff engine and snapshot ViewModel foundation

### Git Observation / Status
- [x] `.git` detection through SAF
- [x] HEAD/branch/detached-HEAD parsing
- [x] Loose and packed local branch discovery
- [x] Origin remote metadata parsing
- [x] Bounded repository traversal with `.git`, `build`, `.gradle` exclusions
- [x] Git index v2/v3 parser and conflict-stage parsing
- [x] Real Git blob SHA-1 hashing for bounded worktree files
- [x] HEAD/index/worktree classification: clean, modified, deleted, untracked, staged, staged+modified, conflict, unchecked
- [x] Loose-object commit/tree reader with bounded decompression
- [x] Safe index/worktree fallback when packed Git objects are inaccessible through SAF
- [x] Git dashboard status presentation

### Capability-Controlled Git Execution
- [x] Typed `STAGE_FILES`, `CREATE_COMMIT`, `CREATE_BRANCH`, `DELETE_BRANCH`, `SWITCH_BRANCH`, `FETCH_REMOTE`, `PULL_REMOTE`, and `PUSH_REMOTE` capability definitions
- [x] Policy integration for Git mutations with R1/R2/R3 risk levels
- [x] Bounded native Git mutation service implemented without shell commands
- [x] Stage files into the real Git index through SAF
- [x] Stage deletions by removing paths from the index
- [x] Unstage paths against readable HEAD trees
- [x] Git index v2 serialization with checksum and deterministic entry ordering
- [x] Loose Git blob/tree/commit object creation with SHA-1 identities
- [x] Commit creation from the real index and configured `.git/config` user identity
- [x] Attached and detached HEAD commit ref updates
- [x] Local branch creation
- [x] Local branch deletion with current-branch protection
- [x] Git dashboard per-file Stage/Unstage actions
- [x] Commit dialog and local branch creation/deletion controls
- [x] Mutation success/failure feedback and bounded refresh after mutation
- [x] Safe HTTPS GitHub fetch through a bounded JGit cache mirror
- [x] Safe fast-forward-only GitHub pull through a bounded JGit cache mirror
- [x] Safe non-force GitHub push through a bounded JGit cache mirror
- [x] Origin URL/host validation before remote transport
- [x] Just-in-time Android Keystore credential retrieval and remote access validation
- [x] Remote fetch/pull/push approval routing through typed capabilities and Approval Center
- [x] Remote action payloads contain no credentials or secrets

Current Git mutation and transport limits:
- 8 MiB maximum staged file size
- 16 MiB maximum Git index input
- 20,000 maximum index entries
- 4 MiB maximum individual generated Git object
- 16-level maximum tree traversal depth
- Mutation is blocked for truncated/partial status where safety cannot be established
- Unstage requires readable HEAD objects
- Commit requires no unresolved index conflict stages and a configured `user.name` / `user.email`
- Remote transport currently supports HTTPS remotes on `github.com` only
- Remote mirror is bounded to 8,000 files / 256 MiB total / 64 MiB per file
- Pull is blocked on dirty worktrees and refuses non-fast-forward/merge operations
- Push is non-force and rejects remote non-fast-forward updates
- Remote transport workspaces are ephemeral cache mirrors and are deleted after execution

### Git Diff / Review
- [x] Structured HEAD/index/worktree diff models
- [x] Bounded diff computation from real Git blob IDs and SAF worktree content
- [x] Staged, unstaged, staged+unstaged, deleted, and untracked diff sections
- [x] Read-only structured diff viewer with section labels and line-level +/- presentation
- [x] Binary/unreadable/conflict-safe unavailable states
- [x] Bounded diff documents and text sizes for mobile performance
- [x] Dedicated Diffs navigation surface
- [ ] Commit/file history viewer

### Build Center / GitHub Actions
- [x] Debug APK, release APK and release AAB targets
- [x] Explicit `workflow_dispatch` target contract
- [x] Fixed target-to-task/artifact mapping; arbitrary Gradle commands rejected
- [x] Authenticated debug/release dispatch wiring
- [x] Correct handling of GitHub `workflow_dispatch` HTTP 204 responses
- [x] Bounded run polling, logs and artifact discovery
- [x] Durable Room-backed build receipts/history
- [x] Release signing via repository-managed secrets
- [x] Release signing preflight and temporary keystore cleanup
- [x] Build dispatch routed through persisted Approval Center review
- [ ] Build cancellation
- [ ] Runtime validation with a live credential

### Approval Center / Action Review
- [x] Room v2 → v3 approval-action persistence migration
- [x] Persisted pending approval queue with bounded 50-action capacity
- [x] Approval expiry and resolved-action pruning
- [x] Risk/capability/workspace summary presentation
- [x] Approve/reject action-review UI
- [x] Approval Center primary navigation surface
- [x] Build dispatch creates a reviewed action before execution
- [x] Git commit and branch-delete actions create reviewed actions before execution
- [x] Remote Git pull/push actions create reviewed actions before execution
- [x] Approved actions have an execution lifecycle: approved → executing → completed/failed
- [x] Approved Git actions re-check repository preconditions before execution
- [x] Approved Git actions can resume by re-detecting the persisted repository URI
- [x] Action payloads contain only execution metadata needed to resume; no credentials or secrets

### Chat / UX
- [x] Essential Chat landing screen only
- [x] Removed workspace/Git/build/agent status labels and unrelated cards

### GitHub Connection / Discovery
- [x] Manual credential foundation
- [x] Android Keystore-backed secret storage
- [x] Authenticated GitHub REST boundary
- [x] Account lookup/repository listing/validation
- [x] Actions workflow discovery and Build Center integration
- [ ] OAuth / GitHub App authentication

### Security / Policy
- [x] R0–R5 risk foundation
- [x] Typed `ActionRequest` / `Approval` models
- [x] Default policy evaluation
- [x] SecretStore abstraction and Android Keystore AES/GCM
- [x] Secret-safe error handling
- [x] `DISPATCH_BUILD` capability gate
- [x] Git mutation capability gates
- [x] Durable Approval Center action gate
- [ ] Per-capability persistent grants
- [ ] Biometric secret protection

## In Progress / Next Sequence

1. Branch switch/checkout, conflict handling, merge/rebase/cherry-pick.
2. Richer Room persistence for tabs, snapshots, agent tasks, automation, approvals, and audit activity.
3. Approval history/audit expansion and per-capability grant controls.
4. Commit/file history and richer Git review surfaces.
5. Remote transport expansion beyond GitHub HTTPS once a safe credential/provider contract exists.

## Planned

### AI / Agent
- [ ] Provider-neutral AI interface
- [ ] Gemini/OpenRouter/other provider adapters
- [ ] Workspace context assembly and symbol extraction
- [ ] Planning/task graph and typed tool gateway
- [ ] Structured patch generation and diff-first proposal UI
- [ ] Verification loop and persistent agent tasks
- [ ] Workspace memory/knowledge
- [ ] Token/context budgeting and provider routing

### Editor Intelligence
- [ ] Syntax highlighting and language-aware editing
- [ ] Diagnostics/problems panel
- [ ] Undo/redo, find/replace, go-to-line/symbol
- [ ] Code folding and selection/edit actions
- [ ] Large-file safeguards and editor preferences

### Git
- [x] Index/worktree and HEAD-aware status
- [x] Stage/unstage
- [x] Commit
- [x] Branch create/delete
- [x] HEAD/index/worktree structured diff viewer
- [x] Fetch/pull/push through capability gateway for validated GitHub HTTPS remotes
- [ ] Branch switch/checkout
- [ ] Commit/file history
- [ ] Merge/rebase/cherry-pick
- [ ] Conflict resolution
- [ ] Additional remote providers/protocols

### Terminal / Execution
- [ ] Sandboxed terminal capability
- [ ] Command request/argument/path validation
- [ ] Resource/time limits and streaming output
- [ ] Cancel/terminate and terminal sessions/tabs
- [ ] No unrestricted arbitrary AI shell access

### Automation
- [ ] Automation definitions and schedules
- [ ] Capability-scoped action graph
- [ ] Approval checkpoints
- [ ] Retry/backoff and run history
- [ ] Pause/resume/cancel
- [ ] Idempotency and recovery/receipts

### Security / Privacy
- [x] Approval Center UI foundation
- [ ] Per-capability grants and workspace/path scopes
- [ ] Secret lifecycle UI
- [ ] Audit trail and action receipts
- [ ] Privacy export/delete controls
- [ ] Redaction regression tests
- [ ] Optional biometric secret access

### Settings
- [ ] AI provider/model settings
- [ ] API keys/credential management
- [ ] GitHub repository settings
- [ ] Build/terminal/automation/security settings
- [ ] Privacy/data retention
- [ ] Appearance/theme/density
- [ ] Editor settings
- [ ] Storage/cache controls
- [ ] Diagnostics/log export
- [ ] About/version/license

### UI / UX
- [x] Adaptive compact/expanded navigation
- [x] Original touch-friendly visual language
- [ ] Tablet two-pane refinements
- [ ] Foldable posture refinements
- [ ] Activity/notification surface
- [ ] Command/search launcher
- [ ] Hardware keyboard shortcuts
- [ ] Accessibility semantic audit
- [ ] Reduced-motion support

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

- [x] CI #103 passed toolchain verification, debug build, unit-test task, APK verification and artifact upload.
- [x] CI #117 passed the Android pipeline after repository/workflow discovery.
- [x] CI #122 passed after authenticated debug workflow dispatch.
- [x] CI #127 was cancelled by workflow concurrency and is not treated as a build failure.
- [ ] Current remote transport JGit compilation/CI validation
- [ ] Current structured Git diff / Approval Center integration CI validation
- [ ] Release APK validation with configured signing secrets
- [ ] Maintained unit-test suite
- [ ] UI tests
- [ ] Static analysis/lint
- [ ] Live authenticated remote fetch/pull/push validation

## Implementation Rules

- Never mark planned functionality as implemented.
- Never fake Git status, GitHub authentication, builds, agent execution, or terminal execution.
- Side effects must use typed capabilities and policy/approval checks.
- AI output is untrusted and must pass through tool/policy boundaries.
- Keep mobile operations bounded and report partial/unavailable states explicitly.
- Do not store tokens, secrets, raw logs, or secret inputs in build receipts.
- Do not bundle heavyweight Android SDK/NDK resources.
- Avoid broad refactors unless required by the current milestone.
- Update this tracker after every meaningful repository change.

## Change Log

### 2026-09-17 — Foundation through remote build
- Established the Android-first DevForge architecture, SAF workspace foundation, editor/recovery foundation, Git observation, Build Center, GitHub discovery, capability/policy primitives, Keystore-backed secrets and Android CI.

### 2026-09-17 — Navigation / Chat cleanup
- Added destination-level back navigation, nested folder/editor back behavior and root exit confirmation.
- Simplified Chat to essential assistant content without workspace/Git/build/agent status cards.

### 2026-09-17 — Release dispatch and signing-safe contract
- Connected debug/release APK/AAB dispatch to fixed workflow inputs.
- Added correct workflow-dispatch run lookup after GitHub's normal HTTP 204 response.
- Added repository-managed release signing preflight and temporary keystore cleanup.

### 2026-09-17 — Capability-controlled local Git execution
- Added explicit Git mutation capability types and policy handling.
- Added a SAF-native Git plumbing mutation service for stage/unstage, commit, and local branch create/delete.
- Added real index serialization and checksum generation plus loose blob/tree/commit object writing.
- Added Git dashboard mutation controls with bounded per-file actions, commit dialog, branch controls and explicit unavailable remote operations.
- Kept remote fetch/pull/push, branch switch, merge/rebase/cherry-pick and conflict resolution out of the implementation until their transport/checkout safety contracts were ready.

### 2026-09-17 — Approval Center and action-review flow
- Added Room v3 approval persistence with bounded pending queue, expiry and resolved-action pruning.
- Added Approval Center UI with risk/capability/workspace context and explicit Approve/Reject controls.
- Routed remote Build Center dispatch through persisted approval review instead of treating the build button as authorization.
- Routed Git commit and branch-delete operations through the same approval gate, with repository precondition re-checks before execution.
- Added approved-action execution lifecycle and safe resumable payloads without storing credentials or secrets.

### 2026-09-17 — Structured Git diff viewer
- Added bounded HEAD/index/worktree diff computation using real Git blob objects and SAF worktree content.
- Added structured per-file diff sections for staged, unstaged, staged+unstaged, deleted and untracked states.
- Added read-only Diffs navigation and line-level structured review UI with explicit unavailable states for conflicts/binary/unreadable files.
- Kept diff computation bounded for mobile memory and I/O safety.

### 2026-09-17 — Safe remote Git transport
- Added JGit-based HTTPS transport for GitHub origins using a private ephemeral workspace mirror.
- Added origin/host/credential validation before each remote operation.
- Added fetch, fast-forward-only pull, and non-force push with bounded mirror/file/byte limits.
- Added Fetch/Pull/Push capability states and explicit dashboard controls.
- Routed pull/push through Approval Center and kept credentials out of persisted approval payloads.
