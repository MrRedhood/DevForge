# DevForge Implementation Tracker

> Living implementation record for the DevForge Android application. Update this file after every repository implementation change so the project always has a durable source of truth for what is actually implemented.

## Status Legend

- **Implemented** — code/documentation exists in the repository and is part of the current implementation.
- **Validated** — implemented and verified by CI or another concrete validation step.
- **In progress** — actively being implemented; not yet complete.
- **Planned** — agreed direction from the product/architecture plans; no implementation yet.
- **Deferred** — intentionally postponed; record the reason.

## Product Direction

DevForge is an Android-first, mobile-native development environment combining workspace/file management, an editor, Git, remote/cloud builds, an AI software-engineering agent, terminal capabilities, automation, approvals, recovery, and a modern adaptive UI.

The UI should be original, stylish, modern, distinctive, highly usable on phones, adaptive to larger screens, and should not imitate another product's visual identity.

## Architecture Baseline

- Kotlin + Jetpack Compose
- Android-first architecture
- Modular core with clear domain boundaries
- Repository/data/domain/UI separation
- Capability-based tool execution
- Explicit approval and policy gates for risky operations
- GitHub as the remote Git/CI/build backbone
- No bundled heavyweight Android SDK/NDK/toolchains in the application
- Cloud-first builds where practical
- Durable local state and offline-friendly UX

## Implemented

### Repository Foundation

- [x] Android/Kotlin/Compose project foundation
- [x] Gradle project configuration
- [x] Android application module
- [x] Compose UI foundation
- [x] Material 3 foundation
- [x] Adaptive layout foundation
- [x] GitHub Actions Android CI workflow
- [x] Repository-level `.gitignore`

### App Shell & Navigation

- [x] Main application shell
- [x] Adaptive compact/expanded navigation
- [x] Chat destination
- [x] Files destination
- [x] Git destination
- [x] Build destination
- [x] Settings destination
- [x] Workspace-aware top bar
- [x] Activity/notification/security status affordances

### Visual Design Foundation

- [x] DevForge-specific visual language
- [x] Dark-first technical aesthetic
- [x] Custom surface hierarchy
- [x] Strong typography hierarchy
- [x] Status pills and contextual indicators
- [x] Modern cards and action surfaces
- [x] Responsive/adaptive spacing foundations
- [x] UI designed from DevForge requirements rather than copied from an existing app

### Initial Screens

- [x] Chat/control-center landing surface
- [x] Files starter surface
- [x] Git starter surface
- [x] Build starter surface
- [x] Settings starter surface

### Workspace Foundation

- [x] Android Storage Access Framework workspace selection
- [x] Persisted selected workspace metadata
- [x] Persistable URI permission handoff
- [x] Workspace-aware global header
- [x] Real workspace root file/folder enumeration
- [x] Recursive folder listing through SAF document-tree APIs
- [x] Nested folder navigation state
- [x] Workspace breadcrumbs
- [x] Back/up navigation through breadcrumb hierarchy
- [x] File/folder loading state
- [x] Empty workspace/folder state
- [x] Workspace refresh action
- [x] Lightweight file metadata presentation
- [x] Background file-tree loading with bounded enumeration per folder

### Editor Foundation

- [x] SAF-backed file read pipeline
- [x] SAF-backed file write pipeline
- [x] Open-file editor tabs
- [x] Active-tab switching
- [x] Dirty-state tracking
- [x] Explicit save action
- [x] Unsaved-change close protection
- [x] Local recovery draft storage
- [x] Delayed recovery snapshot after edits
- [x] Mobile editor surface with monospace editing
- [x] Editor mode hides primary navigation for focused phone editing
- [x] Content SHA-256 hashing utility
- [x] Immutable content snapshot model
- [x] Bounded per-file snapshot persistence
- [x] Snapshot state ViewModel
- [x] Lightweight line-oriented diff engine
- [x] Shared diff model suitable for editor review and future Git/AI patch tooling
- [x] Snapshot creation connected to file-open baseline
- [x] Snapshot creation connected to successful file save
- [x] Snapshot creation connected to delayed recovery drafts
- [x] Recovery snapshot capture on opening a file with an existing recovery draft
- [x] Content-hash de-duplication prevents redundant checkpoint growth

### Domain / Safety Foundation

- [x] Capability model foundation
- [x] Risk classification foundation
- [x] Permission/approval domain model foundation
- [x] Action request foundation
- [x] Approval state foundation
- [x] Foundation for future policy-gated AI tool execution

### Documentation

- [x] Product contract documentation
- [x] Architecture contract documentation
- [x] Roadmap overview documentation
- [x] This implementation tracker

## Validation

- [x] GitHub Actions Android CI configured
- [x] CI runs triggered by repository implementation commits
- [ ] Full Android build validated successfully on the latest implementation commit
- [ ] Unit tests established
- [ ] UI tests established
- [ ] Static analysis/lint established
- [ ] Release APK build validated

## In Progress

### Next implementation slice

1. File preview/search safeguards
2. Durable multi-workspace database strategy (Room after the state model stabilizes)
3. Git repository state model and status UI
4. Build configuration/state model
5. Approval UI connected to actual action requests

### Workspace persistence decision

- SharedPreferences is currently used only for the small, stable selected-workspace record so the first workspace flow stays dependency-light.
- Room is intentionally not introduced yet; it should be added when DevForge begins persisting richer relational state such as open tabs, snapshots, Git metadata, agent tasks, build history, and automation runs.

### Editor recovery decision

- Recovery drafts currently use a small local SharedPreferences store keyed by document URI.
- Recovery is deliberately separate from normal save state: editing a document never silently overwrites the selected workspace file.
- Content hashes and snapshots now form a common identity/checkpoint layer for recovery, AI patches, Git diffs, and future rollback workflows.

## Planned — AI & Agent

- [ ] Provider abstraction
- [ ] Secure API-key handling
- [ ] Model catalog/configuration
- [ ] Context assembly pipeline
- [ ] Workspace indexing/search
- [ ] Tool registry
- [ ] Tool schemas and validation
- [ ] Capability/policy enforcement
- [ ] Approval queue
- [ ] Agent task state machine
- [ ] Plan/execute/review flow
- [ ] Patch generation and application
- [ ] Diff-first review
- [ ] Verification loop
- [ ] Recovery/rollback loop
- [ ] Cost/token telemetry
- [ ] Multi-provider routing

## Planned — Workspace & Editor

- [x] SAF workspace selection
- [ ] Workspace database
- [x] File tree root enumeration
- [x] Recursive folder navigation
- [x] Breadcrumb navigation
- [ ] Search
- [ ] File preview
- [x] File open/read/write pipeline
- [x] Editor tabs
- [ ] Syntax highlighting
- [ ] Diagnostics
- [ ] Undo/redo
- [x] Autosave/recovery draft hook
- [x] Crash-recovery draft storage foundation
- [x] Content hashing
- [x] Snapshot model and bounded store
- [x] Diff engine foundation
- [ ] Diff viewer UI
- [ ] Restore points UI
- [ ] Symbol navigation
- [ ] Large-file safeguards

## Planned — Git

- [ ] Repository detection
- [ ] Branch list
- [ ] Commit history
- [ ] Diff/status view
- [ ] Stage/unstage
- [ ] Commit flow
- [ ] Push/pull
- [ ] Fetch
- [ ] Branch creation/switching
- [ ] Merge/rebase flows
- [ ] Conflict UI
- [ ] Safe-operation confirmation
- [ ] GitHub integration

## Planned — Remote Build / CI

- [ ] GitHub account connection
- [ ] Repository selection
- [ ] Workflow discovery
- [ ] Build configuration
- [ ] Workflow dispatch
- [ ] Live build status
- [ ] Logs
- [ ] Artifact discovery
- [ ] APK/AAB artifact handling
- [ ] Download/install handoff
- [ ] Build history
- [ ] Failure diagnostics

## Planned — Terminal

- [ ] Terminal surface
- [ ] Session management
- [ ] Command execution abstraction
- [ ] Output streaming
- [ ] Process lifecycle
- [ ] Cancellation
- [ ] Working-directory state
- [ ] Environment model
- [ ] Safe command policy
- [ ] Permission prompts
- [ ] Remote execution integration where appropriate

## Planned — Automation

- [ ] Automation model
- [ ] Trigger definitions
- [ ] Action definitions
- [ ] Condition engine
- [ ] Permission model
- [ ] Approval requirements
- [ ] Scheduling
- [ ] Event-driven triggers
- [ ] Run history
- [ ] Retry/backoff
- [ ] Failure recovery
- [ ] Automation editor UI

## Planned — Security & Privacy

- [ ] Android Keystore integration
- [ ] Encrypted local secrets
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
- [ ] Editor settings
- [ ] Workspace settings
- [ ] Git settings
- [ ] Build settings
- [ ] AI providers
- [ ] Models
- [ ] Agent behavior
- [ ] Permissions
- [ ] Security
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
- [ ] Workspace setup flow
- [x] Workspace empty state
- [x] Workspace loading state
- [x] Workspace root browser state
- [x] Recursive folder browser surface
- [x] Workspace breadcrumb surface
- [x] Editor workspace chrome foundation
- [x] Unsaved-change confirmation
- [ ] Diff viewer surface
- [ ] Snapshot/recovery history surface
- [ ] Error/recovery states
- [ ] Approval bottom sheets/dialogs
- [ ] Command palette
- [ ] Global search
- [ ] Quick actions
- [ ] Activity center
- [ ] Agent task timeline
- [ ] Build detail screen
- [ ] Git detail screens
- [ ] Full editor workspace chrome
- [ ] Tablet/foldable layouts
- [ ] Accessibility semantics
- [ ] Motion/transition system
- [ ] Haptics where appropriate

## Planned — Quality & Observability

- [ ] Unit test suite
- [ ] Repository/data tests
- [ ] Editor recovery tests
- [ ] Snapshot/hash tests
- [ ] Diff engine tests
- [ ] Agent/tool contract tests
- [ ] Policy tests
- [ ] UI tests
- [ ] Build smoke tests
- [ ] Static analysis
- [ ] Performance profiling
- [ ] Crash reporting strategy
- [ ] Structured local diagnostics
- [ ] Privacy-preserving telemetry strategy

## Implementation Rules

1. **This file must be updated after every meaningful implementation change.**
2. Never mark a feature implemented merely because its interface was designed; the repository must contain the working implementation.
3. Move an item from Planned/In progress to Implemented only when the corresponding code or configuration exists.
4. Mark validation separately from implementation.
5. Record important architectural decisions here when they materially change the implementation plan.
6. Preserve the Android-first/mobile-first constraint unless the product plan explicitly changes it.
7. Keep heavyweight SDK/NDK/toolchains out of the app package; prefer remote/cloud build infrastructure.
8. AI actions must remain capability-scoped, policy-checked, and approval-aware.
9. Destructive or externally consequential actions must have explicit safeguards and recoverability.
10. UI work must prioritize originality, clarity, touch usability, adaptive layouts, accessibility, and a distinctive DevForge identity.
11. Keep the first persistence layer minimal; introduce Room when the product has enough durable relational state to justify it.
12. Workspace access must use least-privilege Android storage APIs; do not request broad storage permissions for the core workspace flow.
13. File editing must separate in-memory buffers, persisted file state, and recovery drafts so AI/automation features can later add reviewable patches without bypassing user control.
14. Content identity should be content-based rather than timestamp-based where correctness matters, so snapshots, diffs, recovery, and future agent patches can detect actual changes.
15. Keep snapshot storage bounded per file until a proper database-backed history layer is introduced.
16. Recursive workspace browsing must operate through the selected SAF tree permission; do not expand storage scope just to traverse nested folders.

## Change Log

### 2026-09-17 — Initial tracker

- Created `IMPLEMENTATION.md`.
- Recorded the current repository foundation and initial UI/domain implementation.
- Recorded current CI validation state.
- Established the rule that this file is updated after every implementation change.

### 2026-09-17 — Workspace foundation

- Added workspace and file-entry domain models.
- Added persisted selected-workspace storage using a small SharedPreferences record.
- Added Android Storage Access Framework folder selection with persistable URI permissions.
- Added background workspace root enumeration through `DocumentsContract`.
- Replaced the Files mock surface with a real workspace browser state, loading state, empty state, refresh action, and file metadata rows.
- Updated the global header to display the active workspace name.
- Kept Room deferred until richer relational state such as tabs, snapshots, agent tasks, and build/automation history is introduced.
- CI validation of this implementation is pending.

### 2026-09-17 — Editor foundation

- Added `EditorTab` and recovery-draft domain models.
- Added SAF-backed file read/write repository operations.
- Added open-file tabs and active-tab state.
- Added dirty-state tracking based on saved-vs-buffer content.
- Added explicit save behavior that writes back to the selected document URI.
- Added delayed recovery draft persistence after edits without overwriting the source file.
- Added unsaved-change close protection.
- Connected workspace file rows to the editor so real files open from the Files screen.
- Added a focused mobile editor surface with tab chrome, save control, monospace editing, and error presentation.
- Hid primary navigation while editing on compact screens to maximize editor space.
- CI validation of this implementation is pending.

### 2026-09-17 — Content identity, snapshots, and diff foundation

- Added SHA-256 content hashing for stable file content identity.
- Added immutable `ContentSnapshot` and snapshot-reason models.
- Added bounded local snapshot storage with de-duplication by content hash and a per-file history limit.
- Added `SnapshotViewModel` for snapshot loading, creation, and diff state.
- Added a lightweight line-oriented `DiffEngine` with context, added, and removed line entries.
- Established a common foundation for future editor review, Git status/diffs, AI patch preparation, recovery checkpoints, and rollback workflows.
- Deliberately kept the snapshot layer lightweight until Room becomes justified by broader relational state.
- CI validation of this implementation is pending.

### 2026-09-17 — Editor snapshot lifecycle

- Connected snapshot creation to the editor file-open lifecycle as a baseline checkpoint.
- Connected snapshots to successful saves using content-hash de-duplication.
- Connected delayed recovery-draft creation to recovery snapshots so unsaved work has a durable checkpoint distinct from the actual workspace file.
- Added recovery snapshot capture when opening a document with an existing recovery draft.
- Preserved separation between source-file writes and local recovery/snapshot state.
- CI validation of this implementation is pending.

### 2026-09-17 — Recursive workspace navigation

- Extended the SAF file-tree abstraction to list any selected document-tree folder, not only the workspace root.
- Added nested-folder navigation state to `WorkspaceViewModel`.
- Added breadcrumb hierarchy, root navigation, and back/up behavior.
- Updated the Files UI so folder rows navigate deeper while file rows continue opening in the editor.
- Added horizontally scrollable breadcrumb chips for phone-sized layouts.
- Kept enumeration bounded per folder to avoid unbounded reads on-device.
- CI validation of this implementation is pending.

### Future entries

Add a dated entry after each implementation slice using:

```text
### YYYY-MM-DD — Short implementation title

- Added ...
- Changed ...
- Validated ...
- Remaining ...
```
