# DevForge Platform API v1

Version: 1.0

The DevForge Platform API is the stable developer surface for first-party and marketplace packages.

## Public namespaces

- app
- ui
- workspace
- files
- editor
- languages
- terminal
- git
- github
- build
- artifacts
- ai
- agents
- tools
- workflows
- automation
- commands
- settings
- storage
- network
- notifications
- events
- capabilities

The Kotlin contract is in app/src/main/java/com/mrredhood/devforge/core/extension/api/DevForgeApi.kt.
The TypeScript contract is in sdk/devforge/devforge.d.ts.

## Contract rule

Third-party packages depend on public contracts only. They must not reference Android Context, Compose internals, Room entities, private ViewModels, or other internal implementation classes.

## Safety

API calls are permission-scoped. Permission declaration does not bypass DevForge's existing capability, scope, precondition, lease, or approval policies.

High-risk actions such as terminal execution, deletion, Git push/reset/rebase, GitHub writes/deletion, unrestricted networking, and agent execution remain approval-sensitive.

## Events

Stable events have a stable string name and integer schema version. Additive optional fields are compatible; changing the meaning or type of an existing field requires a new event schema version.

## Artifact isolation

Artifacts created through devforge.artifacts are temporary and separate from the active workspace unless the user explicitly saves/copies them into a workspace.

## Runtime boundary

The current implementation establishes the v1 contract, manifest validation, permission model, TypeScript SDK declarations, and schema first. Concrete marketplace transport and full runtime adapters are implemented against this contract rather than changing it.
