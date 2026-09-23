# @devforge/sdk

DevForge Platform SDK v1 contract.

## Public API namespaces

app, ui, workspace, files, editor, languages, terminal, git, github, build, artifacts, ai, agents, tools, workflows, automation, commands, settings, storage, network, notifications, events, capabilities.

Production marketplace packages must declare every requested permission in manifest.json.

## Runtime

The first marketplace runtime is JavaScript in the DevForge sandbox. Packages communicate through the devforge.* contract and do not depend on Android internals.

## Lifecycle

Use activate(context) and deactivate() as package lifecycle hooks.

## Package

The canonical package extension is .devforge.

The SDK contract is now checked by DevForge's native manifest validator. Marketplace transport, publishing, signing and final package execution are implemented separately from this contract layer.
