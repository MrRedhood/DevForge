# DevForge

DevForge is an Android-first AI software-engineering control center. It is designed for real project work from a phone: inspect a workspace, understand Git state, ask an AI agent for a change, review a concrete plan and diff, approve controlled actions, verify the result, dispatch cloud builds, and recover safely.

## Product contract

```text
Request
  → scoped context
  → plan
  → typed action proposal
  → policy / approval
  → diff or command preview
  → snapshot
  → precondition validation
  → execution
  → verification
  → receipt
  → rollback / retry
```

AI never receives a direct execution path. Consequential actions must pass through DevForge's typed tool and policy boundaries.

## Core navigation

```text
Chat | Files | Git | Build | Settings
```

Editor, Activity and Diagnostics are contextual surfaces rather than permanent primary navigation destinations in the mobile MVP.

## Architecture

- Kotlin + Jetpack Compose
- Material 3 + adaptive layouts
- Modular Android architecture
- Durable state with Room/DataStore (being introduced incrementally)
- WorkManager for persistent background work
- Android Keystore for credentials
- Provider-neutral AI interfaces
- Capability-based policy engine
- Typed tool gateway
- App-private workspaces as the canonical local source of truth
- GitHub Actions as the default cloud-build backbone
- KMP-ready domain/protocol boundaries for later expansion

## Visual direction

DevForge uses a custom visual language built on Material 3: dark-first, technical, high-information, expressive surfaces, clear status signals, restrained motion, and code-centric typography. The UI intentionally avoids cloning any existing app.

## Implementation stages

1. Android shell, adaptive navigation, theme, and domain/policy foundations
2. Workspace, editor, indexing, hashing, snapshots, and diff
3. Git and approval/rollback system
4. AI context engine, provider abstraction, typed tools, plans and receipts
5. GitHub integration and Build Center
6. Activity, recovery, notifications and offline queue
7. Project health, memory, semantic retrieval and build diagnosis
8. Multi-provider routing, gateway support and automation
9. Bounded specialist agents
10. Sandboxed MCP/extensions and selective KMP expansion

See `docs/ROADMAP.md` and `docs/ARCHITECTURE.md` for the implementation contract.

## Build

The project targets Android API 37 and uses current stable tooling selected for the September 2026 baseline. GitHub Actions provides the reproducible cloud build path.

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

## Principle

**DevForge should become more capable without becoming less predictable.**
