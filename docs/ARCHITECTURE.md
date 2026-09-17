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
Context assembly, provider adapters, planning, task state, memory and verification.

### Execution plane
Typed tools, Git mutations, build dispatch, background tasks and artifact handling.

### Policy plane
Capability checks, risk classification, approval, scope validation, limits and audit identity.

## Immutable action lifecycle

```text
request
→ context manifest
→ plan
→ typed action
→ policy decision
→ approval
→ snapshot
→ precondition validation
→ execution
→ verification
→ receipt
→ recovery
```

A model response is data. It is never an authorization to execute.

## Primary navigation

`Chat / Files / Git / Build / Settings`

Editor, Activity and Diagnostics are contextual surfaces in the mobile MVP.

## Long-term boundaries

- Provider-neutral AI interface
- Capability-based tool gateway
- Optional Nexus gateway later
- Selective KMP for platform-neutral domain/protocol/state code
- Sandboxed MCP/extensions behind the same policy gateway
- GitHub Actions as the default remote build path

## UI direction

DevForge uses a custom Material 3 visual language rather than cloning an existing product. Dark mode is the primary visual target, with expressive gradients, technical status surfaces, high information density, strong hierarchy and restrained animation.
