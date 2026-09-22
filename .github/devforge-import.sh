#!/usr/bin/env bash
set -euo pipefail

archive="$(find . -maxdepth 1 -type f -name '*.zip' -print -quit || true)"
if [[ -z "$archive" ]]; then
  echo "No DevForge source archive found."
  exit 0
fi

rm -rf /tmp/devforge-import
mkdir -p /tmp/devforge-import
unzip -q "$archive" -d /tmp/devforge-import

src="$(find /tmp/devforge-import -mindepth 1 -maxdepth 1 -type d | head -n 1)"
if [[ -z "$src" ]]; then
  echo "Archive did not contain a top-level project directory."
  exit 1
fi

rm -rf app docs gradle tools
rm -f .github/workflows/android.yml .github/workflows/ui.yml .github/workflows/release-validation.yml
rm -f .github/workflows/import-devforge.yml .github/workflows/bootstrap-import.yml .github/workflows/devforge-import.yml
rm -f .github/devforge-import.sh

cp -a "$src/." .

rm -rf docs/screenshots
rm -f "$archive"

cat > README.md <<'EOF'
# DevForge

## Code on Android. Build from anywhere. Ship without a desktop.

**DevForge turns an Android phone into a real development workshop — code, AI, agents, Git, GitHub, terminal, automation, and cloud Android builds in one workflow.**

> **Write code. Ask AI. Let agents work. Commit. Build. Ship.**

DevForge starts from a simple challenge: what happens when Android is not the backup machine, but the primary development screen?

The answer is an Android-first coding environment designed around fast project navigation, controlled AI actions, GitHub-native workflows, a bounded terminal, and cloud-powered APK/AAB builds.

### The development loop

**Open → Understand → Edit → Ask → Agent → Review → Commit → Build → Ship**

### Built for real project work

- Local and GitHub-backed workspaces
- Code editing and workspace navigation
- AI chat with project-aware context
- AI coding agents with capability and approval controls
- Controlled AI terminal execution
- Git status, diffs, history, commit inspection, and reversible operations
- GitHub repositories, files, issues, pull requests, and repository creation
- Build Center with debug APK, release APK, and release AAB workflows
- Build artifacts plus lint, unit-test, and dependency reports
- Diagnostics, recovery, offline actions, and workspace backup
- Security-first handling of AI actions and credentials

### AI with boundaries

DevForge treats AI output as untrusted input. Tool access is capability-scoped, terminal execution is constrained, and mutating agent actions require the appropriate approval path.

### Android-first by design

DevForge is not a desktop IDE squeezed into a phone. Its navigation, workspace model, agent controls, terminal, GitHub integration, and cloud build flow are designed around development from Android.

### Cloud builds without breaking the loop

**Build → monitor → inspect logs → download the artifact.**

### Project documentation

- [IMPLEMENTATION.md](IMPLEMENTATION.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)

### The idea behind DevForge

**Your phone is already a computer. DevForge turns it into a workshop.**
EOF

cat >> IMPLEMENTATION.md <<'EOF'

## 2026-09-22 — Clean repository bootstrap

Imported the validated DevForge source archive into the new main-only repository from the supplied source snapshot. The bootstrap restores the application source, tests, Gradle configuration, GitHub Actions workflows, tools, and project documentation while starting a fresh repository history.

The repository README was refreshed with DevForge-specific positioning and product messaging.
EOF

chmod +x gradlew 2>/dev/null || true
chmod +x tools/check-build-budget.sh 2>/dev/null || true

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git add -A
git commit -m "Import DevForge source baseline"
git push origin HEAD:main
