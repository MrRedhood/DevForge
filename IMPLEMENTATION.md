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
- [x] Chat, Files, Git, Diffs, Build, Approvals and Settings destinations
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
- [ ] Interactive conflict-resolution editor

### Build Center / GitHub Actions
- [x] Debug APK, release APK and release AAB targets
- [x] Workflow dispatch contract and durable build receipts
- [x] Approval-backed build dispatch
- [ ] Build cancellation
- [ ] Live credential runtime validation

### Approval Center / Security Policy
- [x] Durable approval queue with expiry and lifecycle
- [x] Risk/capability/workspace review UI
- [x] Approved → executing → completed/failed flow
- [x] Audit history and bounded retention
- [x] Workspace-scoped persistent capability grants with R2 ceiling
- [x] Protected destructive/high-risk capabilities cannot receive persistent bypass grants
- [ ] Path-scoped persistent capability grants
- [x] Biometric-protected secret-store primitive for high-security credentials
- [ ] Biometric integration across all existing credential consumers/UI
- [x] Expanded bounded agent execution receipts with scope/capability/risk/approval metadata

### Durable Room State
- [x] Room v7 schema and migrations
- [x] Durable editor tabs/snapshots
- [x] Persistent agent-task state and resumable step pointers
- [x] Automation definition/run persistence foundation
- [x] Durable audit-event storage
- [x] Durable capability-grant storage
- [x] Model-scoped persistent chat sessions/messages

### AI / Agent
- [x] Provider-neutral model domain with Gemini, OpenRouter and OpenAI providers
- [x] Secure provider-key storage through Android Keystore
- [x] Live model catalog loading and model filters
- [x] Model-specific Room chat sessions and bounded context history
- [x] Native Gemini and OpenAI-compatible chat gateway
- [x] 20 slash commands with aliases and suggestions
- [x] Bounded `@file` mention resolution through SAF workspace search
- [x] Shared agent command bridge/catalog
- [x] Provider-neutral typed agent tool definitions and registry
- [x] Capability/policy/approval-aware agent tool gateway
- [x] Bounded SAF tools: read_file, list_files, search_workspace and write_file
- [x] Persistent bounded agent-task engine with resumable steps, cancellation, receipts/results and approval waiting
- [x] Agent task hard limits: 12 steps, bounded payload/result sizes and 60-second execution window
- [x] Explicit per-agent-task path scopes persisted in the task plan
- [x] Approval parameter hashes include the task path scope to prevent stale approval reuse after scope changes
- [x] Workspace tool operations enforce path scopes and continue rejecting traversal/.git access
- [x] Security regression tests for path traversal, scope boundaries, persisted scope and unsafe tool plans
- [ ] Streaming responses
- [ ] Structured patch generation + diff-first approval workflow
- [ ] Workspace symbol extraction/indexing
- [ ] Workspace memory/knowledge layer

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
- [ ] Repository-change triggers
- [ ] Build-completion triggers
- [ ] Condition/event graph evaluation
- [ ] Rich automation UI/editor

### Settings / UX
- [x] AI provider selection UI
- [x] Secure API-key save/remove UI with masked key field
- [ ] AI model defaults/routing settings
- [ ] GitHub repository settings
- [ ] Build/terminal/automation/security settings
- [ ] Privacy/data-retention settings
- [ ] Theme/density/editor preferences

## In Progress / Next Sequence
1. Interactive Git conflict-resolution editor.
2. Automation event triggers and richer automation UI.
3. Terminal capability with strict sandbox/time/argument limits.
4. Integrate biometric protected secrets into existing credential consumers and security UI.
5. Maintained unit/UI/security regression suites and broader validation.

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

### Automation Expansion
- [ ] Repository-change triggers
- [ ] Build-completion triggers
- [ ] Capability-scoped event graphs
- [ ] Approval checkpoints per action
- [ ] Idempotency keys and richer recovery semantics
- [ ] Pause/resume controls and automation editor

### Quality / Observability
- [x] Focused agent security regression tests
- [ ] Maintained unit-test suite
- [ ] Compose/UI tests
- [ ] Static analysis/lint pipeline
- [ ] Release build validation
- [ ] Performance/budget checks
- [ ] Structured diagnostics
- [ ] Crash/recovery validation
- [ ] Security/redaction regression tests

## Validation
- [x] Historical CI toolchain/debug-build validations
- [x] Current agent milestone triggered a fresh Android CI run after commit
- [x] Security hardening changes triggered a fresh Android CI run after commit
- [ ] Current automation scheduler/run engine CI result verification
- [ ] Security hardening CI result verification
- [ ] Live authenticated remote Git validation
- [ ] Release APK/AAB signing validation
- [ ] Maintained unit/UI/security regression suites

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
