# DevForge Package Permissions v1

Packages must declare every requested permission.

## Low risk

ui.contribute
commands.register
settings.read
notifications
workspace.read
files.read
editor.read
language.register
build.read
artifact.create
storage.read

## Medium risk

settings.write
workspace.write
files.write
editor.write
terminal.read
git.write
github.read
build.execute
build.cancel
ai.use
ai.tools.register
agents.create
workflows.register
workflow.execute
automation.register
automation.execute
network.access
storage.write
artifact.delete

## High risk

files.delete
terminal.execute
git.push
git.reset
git.rebase
github.write
github.delete
agents.execute
network.unrestricted
secure_storage
workspace.delete

Dependencies:

- network.unrestricted requires network.access.
- agents.execute requires agents.create.
- Commands, panels and language contributions require their matching registration permission.

Declaring a permission never bypasses DevForge's runtime approval policy.
