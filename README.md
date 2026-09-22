# DevForge

## Code on Android. Build from anywhere. Ship without a desktop.

**DevForge turns an Android phone into a real development workshop — code, AI, AI-managed agents, Git, GitHub, terminal, automation, and cloud Android builds in one workflow.**

> **Write code. Ask AI. Plan. Let AI deploy the workers. Verify. Commit. Build. Ship.**

DevForge starts from a simple challenge: what happens when Android is not the backup machine, but the primary development screen?

The answer is an Android-first coding environment designed around fast project navigation, controlled AI actions, GitHub-native workflows, a bounded terminal, and cloud-powered APK/AAB builds.

### The development loop

**Open → Understand → Ask → Plan → Inspect → Execute → Verify → Review → Commit → Build → Ship**

### Built for real project work

- Local-first and GitHub-backed workspaces, including device-local repository saves and direct local-project publishing
- Code editing and workspace navigation
- AI chat with project-aware context
- AI Mission workflow with plan → inspect → execute → verify → review, normalized activity timeline and durable recovery
- Mobile power-editing commands inspired by Vim, plus a searchable VS Code-style Command Palette
- AI project rules and reusable skill profiles for Android, UI, testing, security, performance, and Git/GitHub work
- Mission change summaries with observed paths, write/delete/command counts, and Build Center-backed build verification
- AI-managed coding agents with bounded planning, ordered phases, live tool telemetry, verification and completion summaries
- Controlled AI terminal execution
- Git status, diffs, history, commit inspection, and reversible operations
- GitHub repositories, files, issues, pull requests, repository creation, metadata editing, and local-project publishing
- Build Center with debug APK, release APK, and release AAB workflows
- Build artifacts plus lint, unit-test, and dependency reports
- Diagnostics, recovery, offline actions, workspace backup, and Build with AI local project bootstrap
- Security-first handling of AI actions and credentials

### AI with boundaries

DevForge treats AI output as untrusted input. Tool access is capability-scoped, terminal execution is constrained, and mutating agent actions require the appropriate approval path.

Users do not launch or operate agents directly. AI can deploy bounded internal agents when a task benefits from parallel work. The user sees the plan, searches, tool execution, provider/model, elapsed time, current progress and final change overview.

DevForge uses cloud AI providers only. It intentionally does not expose an on-device/local inference runtime.

### Android-first by design

DevForge is not a desktop IDE squeezed into a phone. Its navigation, workspace model, AI-managed execution, responsive mobile editor, terminal, GitHub integration, and cloud build flow are designed around development from Android.

### Cloud builds without breaking the loop

**Build → monitor → inspect logs → download the artifact.**

### Project documentation

- [IMPLEMENTATION.md](IMPLEMENTATION.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)

### The idea behind DevForge

**Your phone is already a computer. DevForge turns it into a workshop.**


### AI-managed development

For coding requests, DevForge can create an engineering plan, inspect the workspace, deploy internal agents, execute bounded tool steps, verify results and return a structured overview of the work. Independent agents can run together inside a plan phase; later phases wait for earlier work to finish.

The user-facing workflow is intentionally observational: there is no agent launcher, agent model picker, agent stop button or agent replay control.

### Engineering Power

More → AI Tools now provides one unified engineering surface for repeatable project tasks, AI Mission phase graph and proof package, development profiles, reviewer/refactor/debugger foundations, UI journey and Compose preview models, device-matrix targets, remote-development definitions, MCP registry, evidence-backed AI memory, learned-rule proposals, resource-aware cloud routing, automation blueprints, and native DevForge extension contracts. These features reuse the existing approval, workspace, Build Center and AI Mission boundaries rather than creating duplicate agent dashboards.

### Linux-style terminal

The terminal runs a sandboxed Android `/system/bin/sh` for local workspaces and supports normal shell syntax such as quoting, variables, pipes, redirects, `&&`, `||`, `;` and command substitution. The bounded AI command catalog now includes common navigation, file, text-processing, hashing, comparison, diagnostics, environment and write operations. Interactive terminal timeouts can be configured up to 60 seconds, with a separate bounded interactive ceiling.

### Mobile editor performance

Files above 64 KiB automatically enter fast rendering mode with reduced syntax and folding work. Fold calculation is debounced off the UI thread, diagnostics are bounded and debounced, and undo bookkeeping avoids rescanning the entire history on every keystroke so continuous typing and scrolling stay responsive on lower-end Android devices.