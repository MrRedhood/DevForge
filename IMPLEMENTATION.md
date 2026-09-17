# DevForge Implementation Tracker

> Living implementation record for DevForge. Updated after meaningful repository changes so the repository remains the source of truth for implemented vs planned work.

## Product / Architecture Rules
- Android-first, mobile-native Kotlin + Jetpack Compose + Material 3.
- SAF is the workspace boundary; no hidden filesystem assumptions.
- Git/AI/build/terminal side effects use typed capabilities and policy gates.
- AI output is untrusted and never grants authorization.
- GitHub Actions is the remote-build backbone; no Android SDK/NDK/toolchain bundled in the app.
- Mobile operations are bounded by file, object, index, tree, context, log and history limits.
- Secrets are stored through Android Keystore and are excluded from action payloads, receipts and logs.
- Planned functionality is never marked implemented until repository code exists and validation is performed.

## Implemented

### App Foundation
- [x] Kotlin/Compose/Material 3 foundation
- [x] Adaptive compact/expanded navigation
- [x] Chat, Files, Git, Diffs, Build, Approvals and Settings destinations
- [x] Dark-first original DevForge UI direction
- [x] Destination history/back handling and nested editor/folder back behavior
- [x] Unsaved editor and root-exit confirmations
- [x] API 36 CI/toolchain baseline

### Workspace
- [x] SAF folder picker and persistable permissions
- [x] Room-backed multiple workspaces and active workspace persistence
- [x] Recursive navigation, breadcrumbs, bounded enumeration and search
- [x] Safe text preview and binary detection
- [x] Workspace refresh and legacy-workspace migration

### Editor / Recovery
- [x] Multi-tab editor and dirty tracking
- [x] Explicit save/save-and-close
- [x] Recovery drafts/checkpoints
- [x] SHA-256 content identity and bounded immutable snapshots
- [x] Line-based diff engine foundation
- [x] Room-backed durable editor tabs/snapshots/recovery checkpoints

### Git Observation / Execution
- [x] `.git` detection, HEAD/branch/detached-HEAD parsing
- [x] Loose/packed branch discovery and origin metadata parsing
- [x] Bounded repository traversal and Git index v2/v3 parsing
- [x] HEAD/index/worktree status classification, conflict-stage parsing and real Git blob SHA-1 hashing
- [x] Loose Git object reader and safe SAF fallbacks
- [x] Native SAF Git stage/unstage/commit/local branch operations without shell commands
- [x] Typed stage/commit/branch/switch/fetch/pull/push/merge/rebase/cherry-pick capabilities
- [x] Approval-backed mutations with repository precondition re-checks
- [x] Safe GitHub HTTPS fetch/pull/push through bounded ephemeral JGit mirrors
- [x] Non-force push and fast-forward-only pull safety rules
- [x] Branch/history checkout/merge/rebase/cherry-pick engine with clean-worktree enforcement
- [x] Bounded conflict-path reporting and safe discard of conflicted temporary state
- [x] Git dashboard mutation, remote and history controls

Git safety limits include 8 MiB staged files, 16 MiB index input, 20,000 index entries, 4 MiB generated Git objects, bounded tree depth, bounded remote/history mirrors, and mutation blocking when status is partial/truncated.

### Git Diff / Review
- [x] HEAD/index/worktree structured diff models
- [x] Bounded real-object diff computation
- [x] Staged, unstaged, staged+unstaged, deleted and untracked sections
- [x] Read-only line-level diff viewer
- [x] Binary/unreadable/conflict-safe unavailable states
- [x] Dedicated Diffs destination
- [x] Bounded commit history viewer from readable loose commit objects
- [x] Commit detail with bounded changed-file listing
- [x] Per-file history viewer with added/modified/deleted change markers
- [ ] Interactive conflict-resolution editor

### Build Center / GitHub Actions
- [x] Debug APK, release APK and release AAB targets
- [x] Fixed workflow_dispatch contract and target/task/artifact mapping
- [x] Authenticated dispatch and HTTP 204 follow-up run lookup
- [x] Bounded run polling, logs and artifact discovery
- [x] Durable Room-backed build receipts/history
- [x] Release signing preflight and temporary keystore cleanup
- [x] Build dispatch routed through Approval Center
- [ ] Build cancellation
- [ ] Live credential runtime validation

### Approval Center / Security Policy
- [x] Durable approval queue with Room persistence, expiry and pruning
- [x] Risk/capability/workspace action review UI
- [x] Approve/reject flow and approved → executing → completed/failed lifecycle
- [x] Approval history/audit events and bounded user-facing audit history
- [x] Workspace-scoped persistent capability grants with R2 ceiling
- [x] Protected delete/remote-push/release/secret/rebase capabilities cannot receive persistent bypass grants
- [x] Persistent-grant-aware policy registry hydration
- [ ] Path-scoped grants
- [ ] Biometric secret protection
- [ ] Expanded action receipts/privacy controls

### Durable Room State
- [x] Room v6 schema and migrations
- [x] Durable editor tabs/snapshots
- [x] Agent-task persistence foundation
- [x] Automation definition/run persistence foundation
- [x] Durable audit-event storage with retention pruning
- [x] Durable capability-grant storage/DAO/repository
- [x] Model-scoped persistent chat sessions and bounded chat messages
- [ ] Full persistent agent-task execution engine
- [ ] Automation scheduler/run engine

### AI / Agent
- [x] Provider-neutral AI model domain with Gemini, OpenRouter and OpenAI providers
- [x] Secure per-provider API-key storage via Android Keystore
- [x] Live model catalog loading when Chat model dropdown is opened
- [x] Model search and filters for All, Free, Paid, Voice, Image, Video, Audio, Embedding and Tools
- [x] Provider metadata parsing for pricing, modalities, tool support and context limits where exposed
- [x] Lazy context-limit web fallback for models whose provider metadata does not include context length
- [x] Model-specific Room chat sessions isolated by workspace + provider + model
- [x] Context-budgeted chat history using the selected model's context limit when known
- [x] Native Gemini and OpenAI-compatible chat request gateway
- [x] Slash-command registry with 20 commands and aliases
- [x] Slash command suggestions shown while typing `/`
- [x] Bounded `@file` mention resolution through SAF workspace search and file reads
- [x] Shared agent command bridge/catalog so future agents consume the same command definitions
- [x] Side-effect command semantics remain proposals and still require typed capability/approval authorization
- [ ] Streaming responses
- [ ] Full provider-neutral agent tool gateway
- [ ] Task graph/planning engine and persistent agent execution
- [ ] Structured patch generation + diff-first approval workflow
- [ ] Workspace symbol extraction/indexing for AI context
- [ ] Workspace memory/knowledge layer

Current command catalog:
`/help`, `/explain`, `/debug`, `/fix`, `/refactor`, `/optimize`, `/test`, `/review`, `/summarize`, `/docs`, `/search`, `/find`, `/plan`, `/implement`, `/generate`, `/diff`, `/build`, `/commit`, `/run`, `/agent`.

### Settings / UX
- [x] AI provider selection UI
- [x] Secure API-key save/remove UI with masked key field
- [x] AI catalog/context metadata guidance in Settings
- [ ] AI model defaults/routing settings
- [ ] GitHub repository settings
- [ ] Build/terminal/automation/security settings
- [ ] Privacy/data retention settings
- [ ] Theme/density/editor preferences

## In Progress / Next Sequence
1. Validate the current AI + Room v6 integration in CI and fix any remaining Android/Compose/Room compile issues.
2. Provider-neutral agent tool gateway and persistent agent-task execution.
3. Automation scheduler/run engine.
4. Security hardening: path scopes, biometric secret protection, receipts and regression coverage.
5. Interactive Git conflict-resolution editor.

## Planned

### Editor Intelligence
- [ ] Syntax highlighting/language-aware editing
- [ ] Problems/diagnostics panel
- [ ] Undo/redo, find/replace and go-to-line/symbol
- [ ] Code folding and selection/edit actions
- [ ] Large-file safeguards/editor preferences

### Terminal / Execution
- [ ] Sandboxed terminal capability
- [ ] Command/path/argument validation
- [ ] Resource/time limits and streaming output
- [ ] Cancel/terminate and terminal sessions/tabs
- [ ] No unrestricted arbitrary AI shell access

### Automation
- [ ] Scheduled and event-driven definitions
- [ ] Capability-scoped action graphs
- [ ] Approval checkpoints
- [ ] Retry/backoff, pause/resume/cancel
- [ ] Idempotency, recovery and receipts

### Quality / Observability
- [ ] Maintained unit-test suite
- [ ] Compose/UI tests
- [ ] Static analysis/lint pipeline
- [ ] Release build validation
- [ ] Performance/budget checks
- [ ] Structured diagnostics
- [ ] Crash/recovery validation
- [ ] End-to-end build/dispatch tests
- [ ] Security/redaction regression tests

## Validation
- [x] CI #103 passed toolchain verification, debug build, unit-test task, APK verification and artifact upload.
- [x] CI #117 passed the Android pipeline after repository/workflow discovery.
- [x] CI #122 passed after authenticated debug workflow dispatch.
- [x] CI #127 was cancelled by workflow concurrency and is not treated as a build failure.
- [ ] Current branch/history JGit milestone validation
- [ ] Current Room v5 audit/grant validation
- [ ] Current Room v6 AI/chat model-session validation
- [ ] Current AI model catalog/Compose integration validation
- [ ] Current commit/file history review validation
- [ ] Live authenticated remote fetch/pull/push validation
- [ ] Release APK validation with signing secrets
- [ ] Maintained unit/UI/security test suites

## Implementation Rules
- Never fake Git status, GitHub authentication, builds, agent execution or terminal execution.
- AI output is untrusted data and cannot grant authorization.
- All side effects remain behind typed capability and policy/approval boundaries.
- Keep mobile operations bounded and report partial/unavailable states explicitly.
- Never persist API keys or other secrets in ordinary domain state, approvals, build receipts or logs.
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
- Upgraded Room to v4 and added durable editor, agent-task, automation/run and audit persistence foundations.

### 2026-09-17 — Approval audit and persistent capability grants
- Upgraded Room to v5 with workspace-scoped capability grants and hydrated policy registry.
- Added approval/grant audit triggers and expanded Approval Center history/grant controls.
- Protected destructive/high-risk capabilities from persistent bypass grants.

### 2026-09-17 — AI model catalog, model sessions and command layer
- Added secure Gemini/OpenRouter/OpenAI provider keys and live model catalogs loaded from the Chat model dropdown.
- Added price/capability filters, provider metadata parsing, lazy context-limit web fallback and bounded context-aware request history.
- Added model-isolated Room chat sessions/messages and replaced the Chat placeholder with the live AI chat surface.
- Added 20 slash commands with aliases, command suggestions on `/`, bounded `@file` mentions and a shared agent command bridge.
- Kept side-effecting commands as proposals that continue through DevForge capability/approval policy.
- Added masked API-key input in Settings.
- CI validation for the AI/Room v6 milestone remains pending.

### 2026-09-17 — Commit/file history review surfaces
- Added bounded loose-object commit history traversal from HEAD with author, timestamp, parent and changed-file metadata.
- Added commit detail review with bounded changed-file lists and selectable file paths.
- Added bounded per-file history with added/modified/deleted change markers.
- Integrated the review surface into the existing Git branch/history card without adding a new navigation destination.
- CI validation for the commit/file history milestone remains pending.
