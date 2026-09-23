# DevForge Architecture Contract

## Product layers

```text
Compose UI
  → ViewModels / use cases
  → domain + policy
  → repositories / adapters
  → local state + remote services
```

The UI never executes network or privileged operations directly.

## Core planes

### Workspace plane
Files, indexing, hashing, Git state, snapshots and local source-of-truth data.

### AI / agent plane
Context assembly, provider adapters, top-level AI squad planning, bounded agent task planning, task state, memory, tool telemetry and verification.

AI is the only orchestrator exposed to agent execution. Users observe agent activity but do not launch, stop, replay or select agent workers directly. A squad plan is divided into sequential phases; independent members of the same phase may run concurrently, while each agent's detailed tool plan executes sequentially.

### Execution plane
Typed tools, Git mutations, build dispatch, background tasks and artifact handling.

### Policy plane
Capability checks, risk classification, approval, scope validation, limits and audit identity.

## Immutable action lifecycle

```text
request
→ context manifest
→ AI plan
→ agent assignment
→ detailed tool plan
→ policy decision
→ approval
→ snapshot
→ precondition validation
→ execution
→ verification
→ receipt
→ recovery
→ user-visible overview
```

The UI receives read-only progress from durable task state and audit events. Agent telemetry includes provider, model, elapsed time, current step, last tool, affected paths and terminal result.

## AI runtime boundary

DevForge uses configured cloud AI providers. The product does not expose an on-device/local model execution path. Local Android storage and workspaces remain supported independently of model execution.

## Editor performance boundary

The editor keeps editing state local to the active workspace while moving expensive diagnostics to a debounced background dispatcher. Syntax highlighting, folding and invisible-character rendering are automatically reduced for large files (over 128 KiB or 4,000 lines) to keep scrolling and typing responsive on mobile hardware.

A model response is data. It is never an authorization to execute.

## Primary navigation

`Chat / Files / Git / Build / Settings`

Editor, Activity and Diagnostics are contextual surfaces in the mobile MVP.

## Long-term boundaries

- Provider-neutral AI interface
- Capability-based tool gateway
- Optional Nexus gateway later
- Selective KMP for platform-neutral domain/protocol/state code
- GitHub Actions as the default remote build path

## UI direction

DevForge uses a custom Material 3 visual language rather than cloning an existing product. Dark mode is the primary visual target, with expressive gradients, technical status surfaces, high information density, strong hierarchy and restrained animation.
\n## Quality and reliability plane\n\nThe execution plane now exposes a shared bounded operation lifecycle through DevForgeOperationCenter.\n\n```text\nrequest\n→ operation record\n→ policy / validation\n→ execution\n→ progress\n→ result\n→ bounded operation history\n```\n\nOperations are typed (AI, agent, build, Git, terminal, workspace, artifact, sync, diagnostics and backup), redact status text before retaining it, and keep active/history bounds suitable for mobile devices.\n\nThe Quality & Reliability surface composes existing systems rather than duplicating them:\n\n- workspace integrity checks use the canonical SAF workspace and Git detection services\n- security review reuses the existing secret-redaction patterns\n- performance budgets inspect bounded editor state\n- release readiness consumes Build Center state, artifacts/log availability, editor dirty state and configuration completeness\n- existing recovery, local history, backup, audit, approval and CI subsystems remain authoritative for their respective domains\n\nBuild Center remote dispatch/monitoring reports its lifecycle through the shared operation layer so leaving the screen does not erase the operation state.\n\nThe in-app Help & guide is part of the product documentation contract. New user-visible quality features must be described there as well as in IMPLEMENTATION.md.\n