# DevForge Roadmap

## Stage 0 — Foundation (implemented)

- Android-first project structure
- Kotlin + Compose build system
- Material 3 visual foundation
- Adaptive compact/expanded navigation
- Chat / Files / Git / Build / Settings shell
- Workspace-aware global header
- AI/provider policy primitives
- CI build and unit-test workflow

## Stage 1 — Workspace

- App-private workspace abstraction
- Clone/import/export
- File tree and search
- Hashing and indexing state
- Editor shell
- Snapshots and restore
- Diff viewer

## Stage 2 — Git + safety

- Status/history/branches
- Stage/unstage
- Commit preparation
- Conflict state
- Capability-based policy engine
- Approval objects
- Snapshot-backed rollback
- Audit receipts

## Stage 3 — AI core

- Provider abstraction
- Streaming chat
- Deterministic context retrieval
- Context manifests
- Typed tool gateway
- Plan/action/diff cards
- Verification and recovery

## Stage 4 — GitHub + Build Center

- OAuth/PKCE or GitHub App integration
- Repository discovery
- Actions workflow discovery
- Explicit workflow dispatch approval
- Run monitoring and logs
- Failure diagnosis
- Artifact download and verification

## Stage 5 — Recovery + intelligence

- Durable activity/task inbox
- Process-death recovery
- Offline queue/reconciliation
- Notifications
- Project health dashboard
- AI run inspector
- Project memory

## Stage 6 — Expansion

- Multiple AI providers
- Optional gateway/routing
- Automation engine
- Bounded specialist agents
- Semantic retrieval
- Optional LSP integration
- Selective KMP extraction

### Non-goals for early releases

- Bundled Android SDK/NDK
- Emulator
- Unrestricted shell
- Unbounded agent swarms
- Arbitrary MCP execution
- Automatic release/deployment
- Full cloud IDE

The benchmark is the inspect → plan → approve → change → verify → build → recover loop, not the number of AI features.
