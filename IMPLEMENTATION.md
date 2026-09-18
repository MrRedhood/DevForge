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
- [x] Bounded SAF tools: read_file, list_files, search_workspace, patch_file and write_file
- [x] Typed shared-memory and agent-handoff coordination tools
- [x] Persistent bounded agent-task engine with resumable steps, cancellation, receipts/results and approval waiting
- [x] Persistent multi-agent workspace coordinator with up to 4 concurrent agents
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