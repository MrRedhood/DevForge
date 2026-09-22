# DevForge

## Code on Android. Build from anywhere. Ship without a desktop.

**DevForge turns an Android phone into a real development workshop — code, AI, AI-managed agents, Git, GitHub, terminal, automation, and cloud Android builds in one workflow.**

> **Write code. Ask AI. Plan. Let AI deploy the workers. Verify. Commit. Build. Ship.**

DevForge starts from a simple challenge: what happens when Android is not the backup machine, but the primary development screen?

The answer is an Android-first coding environment designed around fast project navigation, controlled AI actions, GitHub-native workflows, a bounded terminal, and cloud-powered APK/AAB builds.

### The development loop

**Open → Understand → Ask → Plan → Inspect → Execute → Verify → Review → Commit → Build → Ship**

### Built for real project work

- Local and GitHub-backed workspaces
- Code editing and workspace navigation
- AI chat with project-aware context
- AI-managed coding agents with bounded planning, ordered phases, live tool telemetry, verification and completion summaries
- Controlled AI terminal execution
- Git status, diffs, history, commit inspection, and reversible operations
- GitHub repositories, files, issues, pull requests, and repository creation
- Build Center with debug APK, release APK, and release AAB workflows
- Build artifacts plus lint, unit-test, and dependency reports
- Diagnostics, recovery, offline actions, and workspace backup
- Security-first handling of AI actions and credentials

### AI with boundaries

DevForge treats AI output as untrusted input. Tool access is capability-scoped, terminal execution is constrained, and mutating agent actions require the appropriate approval path.

Users do not launch or operate agents directly. AI can deploy bounded internal agents when a task benefits from parallel work. The user sees the plan, searches, tool execution, provider/model, elapsed time, current progress and final change overview.

DevForge uses cloud AI providers. It does not expose an on-device/local model runtime.

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

### Mobile editor performance

Large files automatically enter a faster rendering mode that reduces expensive syntax highlighting and folding work. Diagnostics are debounced and calculated off the UI thread so continuous typing and scrolling remain responsive.