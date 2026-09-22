# DevForge

## Code on Android. Build from anywhere. Ship without a desktop.

**DevForge turns an Android phone into a real development workshop — code, one main AI, Git, GitHub, terminal, automation, and cloud Android builds in one workflow.**

> **Write code. Ask AI. Plan. Inspect. Execute. Verify. Commit. Build. Ship.**

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
- One main AI that plans, inspects the workspace, uses bounded tools to create/modify/delete files and folders, and verifies results
- Controlled AI terminal execution
- Git status, diffs, history, commit inspection, and reversible operations
- GitHub repositories, files, issues, pull requests, repository creation, metadata editing, and local-project publishing
- Build Center with debug APK, release APK, and release AAB workflows
- Build artifacts plus lint, unit-test, and dependency reports
- Diagnostics, recovery, offline actions, workspace backup, and Build with AI local project bootstrap
- Security-first handling of AI actions and credentials

### AI with boundaries

DevForge treats AI output as untrusted input. Tool access is capability-scoped, terminal execution is constrained, and file/folder mutations require the appropriate approval path.

There is one main AI execution path. The AI creates its own plan, searches and reads the workspace, performs approved mutations, runs bounded commands when needed, and verifies the result. No user-facing worker or multi-agent workflow is exposed.

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


### Single main AI

For coding requests, DevForge's main AI creates a plan, inspects the active workspace, searches and reads relevant files, executes bounded file/folder mutations through the same tool gateway, and verifies the result. Tool execution is shown in Chat as compact expandable task labels so you can inspect what the AI is doing without a separate agent dashboard.

### AI Tools

More → AI Tools is a dedicated full-screen control surface for the tools available to the main AI. Workspace inspection and mutation tools are enabled by default for the main AI; destructive actions such as deletion remain protected by DevForge's approval policy. The screen also provides an "Enable coding tools" action and per-tool switches.

### Linux-style terminal

The terminal runs a sandboxed Android `/system/bin/sh` for local workspaces and supports normal shell syntax such as quoting, variables, pipes, redirects, `&&`, `||`, `;` and command substitution. The bounded AI command catalog now includes common navigation, file, text-processing, hashing, comparison, diagnostics, environment and write operations. Interactive terminal timeouts can be configured up to 60 seconds, with a separate bounded interactive ceiling.

### Mobile editor performance

Files above 64 KiB automatically enter fast rendering mode with reduced syntax and folding work. Fold calculation is debounced off the UI thread, diagnostics are bounded and debounced, and undo bookkeeping avoids rescanning the entire history on every keystroke so continuous typing and scrolling stay responsive on lower-end Android devices.

### Real Acode / VS Code extension support

DevForge now has a dedicated real package-aware extension manager under More → Extensions. It accepts Acode ZIP plugins and VS Code VSIX packages, inspects their manifests and executable entry points, rejects unsupported runtimes instead of installing placeholders, persists compatible packages, and applies supported contributions through DevForge adapters.

Icon themes are real: VS Code/Acode icon metadata is indexed and the active pack changes the workspace's file and folder icons. Programming-language packages are checked against DevForge's existing editor language catalog first; when the language is already supported, DevForge explicitly reports native support rather than claiming the package installed a compiler/runtime. Unsupported language engines are rejected until DevForge has a real language adapter.

Bundled Acode plugins and compatible VS Code Web packages can run through a restricted Android WebView extension host. Runtime JavaScript has file/network access blocked by default and only the supported API subset is exposed.