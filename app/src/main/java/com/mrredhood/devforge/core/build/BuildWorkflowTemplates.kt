package com.mrredhood.devforge.core.build

/**
 * Canonical GitHub Actions workflows provisioned by DevForge.
 * These templates mirror the live repository workflows so provisioning cannot downgrade CI security/features.
 */
object BuildWorkflowTemplates {
    val ci = """
name: Android CI
# Release APK CI is triggered by a push whose commit message contains [release-apk].

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:
    inputs:
      target:
        description: Build target
        required: true
        default: debug_apk
        type: choice
        options:
          - debug_apk
          - release_apk
          - release_aab
      build_artifact:
        description: Build the selected APK/AAB output
        required: true
        default: 'true'
        type: choice
        options:
          - 'true'
          - 'false'
      publish_artifacts:
        description: Upload build outputs to GitHub Actions artifacts
        required: true
        default: 'true'
        type: choice
        options:
          - 'true'
          - 'false'
      lint_report:
        description: Upload the Android lint report
        required: true
        default: 'true'
        type: choice
        options:
          - 'true'
          - 'false'
      unit_test_report:
        description: Upload unit-test reports
        required: true
        default: 'true'
        type: choice
        options:
          - 'true'
          - 'false'
      dependency_report:
        description: Generate and upload Gradle dependency information
        required: true
        default: 'false'
        type: choice
        options:
          - 'true'
          - 'false'

permissions:
  contents: read

concurrency:
  group: ${'$'}{{ github.workflow }}-${'$'}{{ github.ref }}
  cancel-in-progress: ${'$'}{{ github.event_name != 'workflow_dispatch' }}

env:
  JAVA_VERSION: '17'
  ANDROID_PLATFORM: 'android-36'
  ANDROID_BUILD_TOOLS: '36.0.0'
  GRADLE_OPTS: '-Dorg.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8'

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - name: Checkout
        uses: actions/checkout@v5

      - name: Set up JDK 17
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: ${'$'}{{ env.JAVA_VERSION }}
          cache: gradle

      - name: Install required Android SDK components
        shell: bash
        run: |
          set -euo pipefail
          SDKMANAGER="${'$'}{ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
          if [ ! -x "${'$'}SDKMANAGER" ]; then
            SDKMANAGER="${'$'}(command -v sdkmanager)"
          fi
          echo "ANDROID_SDKMANAGER=${'$'}SDKMANAGER" >> "${'$'}GITHUB_ENV"
          echo "${'$'}(dirname "${'$'}SDKMANAGER")" >> "${'$'}GITHUB_PATH"
          yes | "${'$'}SDKMANAGER" --licenses >/dev/null || true
          "${'$'}SDKMANAGER" "platform-tools" "platforms;${'$'}{ANDROID_PLATFORM}" "build-tools;${'$'}{ANDROID_BUILD_TOOLS}"
          BUILD_TOOLS_DIR="${'$'}{ANDROID_HOME}/build-tools/${'$'}{ANDROID_BUILD_TOOLS}"
          test -x "${'$'}{BUILD_TOOLS_DIR}/apksigner"
          echo "${'$'}BUILD_TOOLS_DIR" >> "${'$'}GITHUB_PATH"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          cache-read-only: ${'$'}{{ github.event_name == 'pull_request' }}

      - name: Resolve build target
        id: target
        shell: bash
        env:
          EVENT_TARGET: ${'$'}{{ inputs.target }}
          EVENT_NAME: ${'$'}{{ github.event_name }}
          COMMIT_MESSAGE: ${'$'}{{ github.event.head_commit.message }}
        run: |
          set -euo pipefail
          TARGET="${'$'}{EVENT_TARGET:-debug_apk}"
          if [[ "${'$'}EVENT_NAME" == "push" && "${'$'}COMMIT_MESSAGE" == *"[release-apk]"* ]]; then
            TARGET="release_apk"
          fi
          case "${'$'}TARGET" in
            debug_apk)
              TASK=":app:assembleDebug"
              ARTIFACT_NAME="devforge-debug-apk"
              ARTIFACT_PATH="app/build/outputs/apk/debug/app-debug.apk"
              ;;
            release_apk)
              TASK=":app:assembleRelease"
              ARTIFACT_NAME="devforge-release-apk"
              ARTIFACT_PATH="app/build/outputs/apk/release/app-release.apk"
              ;;
            release_aab)
              TASK=":app:bundleRelease"
              ARTIFACT_NAME="devforge-release-aab"
              ARTIFACT_PATH="app/build/outputs/bundle/release/app-release.aab"
              ;;
            *)
              echo "Unsupported build target: ${'$'}TARGET" >&2
              exit 1
              ;;
          esac
          {
            echo "target=${'$'}TARGET"
            echo "task=${'$'}TASK"
            echo "artifact_name=${'$'}ARTIFACT_NAME"
            echo "artifact_path=${'$'}ARTIFACT_PATH"
          } >> "${'$'}GITHUB_OUTPUT"

      - name: Prepare release signing
        if: ${'$'}{{ (inputs.build_artifact == '' || inputs.build_artifact == 'true') && (steps.target.outputs.target == 'release_apk' || steps.target.outputs.target == 'release_aab') }}
        shell: bash
        env:
          RELEASE_KEYSTORE_BASE64: ${'$'}{{ secrets.DEVFORGE_RELEASE_KEYSTORE_BASE64 }}
          RELEASE_KEYSTORE_PASSWORD: ${'$'}{{ secrets.DEVFORGE_RELEASE_KEYSTORE_PASSWORD }}
          RELEASE_KEY_ALIAS: ${'$'}{{ secrets.DEVFORGE_RELEASE_KEY_ALIAS }}
          RELEASE_KEY_PASSWORD: ${'$'}{{ secrets.DEVFORGE_RELEASE_KEY_PASSWORD }}
        run: |
          set -euo pipefail
          for name in RELEASE_KEYSTORE_BASE64 RELEASE_KEYSTORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD; do
            if [[ -z "${'$'}RELEASE_KEYSTORE_BASE64" || -z "${'$'}RELEASE_KEYSTORE_PASSWORD" || -z "${'$'}RELEASE_KEY_ALIAS" || -z "${'$'}RELEASE_KEY_PASSWORD" ]]; then
              echo "Release signing secrets are required for release builds." >&2
              exit 1
            fi
          done

          KEYSTORE_FILE="${'$'}{RUNNER_TEMP}/devforge-release.keystore"
          printf '%s' "${'$'}RELEASE_KEYSTORE_BASE64" |
            tr -d '[:space:]' |
            base64 --decode > "${'$'}KEYSTORE_FILE"
          test -s "${'$'}KEYSTORE_FILE"
          if ! keytool -list -keystore "${'$'}KEYSTORE_FILE" \
              -storetype PKCS12 \
              -storepass "${'$'}RELEASE_KEYSTORE_PASSWORD" \
              -alias "${'$'}RELEASE_KEY_ALIAS" >/dev/null 2>&1; then
            echo "The configured release keystore secret is not a valid PKCS12 keystore for the supplied alias/password." >&2
            exit 1
          fi

          {
            echo "DEVFORGE_RELEASE_SIGNING_CONFIGURED=true"
            echo "DEVFORGE_RELEASE_KEYSTORE_FILE=${'$'}KEYSTORE_FILE"
            echo "DEVFORGE_RELEASE_KEYSTORE_PASSWORD=${'$'}RELEASE_KEYSTORE_PASSWORD"
            echo "DEVFORGE_RELEASE_KEY_ALIAS=${'$'}RELEASE_KEY_ALIAS"
            echo "DEVFORGE_RELEASE_KEY_PASSWORD=${'$'}RELEASE_KEY_PASSWORD"
          } >> "${'$'}GITHUB_ENV"

          echo "Release signing is configured."

      - name: Verify toolchain
        shell: bash
        run: |
          set -euo pipefail
          java -version
          ./gradlew --version
          "${'$'}ANDROID_SDKMANAGER" --list_installed | grep -E "platform-tools|platforms;${'$'}{ANDROID_PLATFORM}|build-tools;${'$'}{ANDROID_BUILD_TOOLS}"

      # Release targets are intentionally fail-closed on signing configuration and are verified below.
      - name: Build selected target
        if: ${'$'}{{ inputs.build_artifact == '' || inputs.build_artifact == 'true' }}
        run: ./gradlew "${'$'}{{ steps.target.outputs.task }}" --stacktrace --no-daemon --max-workers=2

      - name: Run unit tests
        if: ${'$'}{{ inputs.unit_test_report == '' || inputs.unit_test_report == 'true' }}
        run: ./gradlew :app:testDebugUnitTest --stacktrace --no-daemon --max-workers=2

      - name: Generate dependency report
        if: ${'$'}{{ inputs.dependency_report == 'true' }}
        shell: bash
        run: |
          mkdir -p app/build/reports/devforge
          ./gradlew :app:dependencies --configuration debugRuntimeClasspath --no-daemon --max-workers=2 > app/build/reports/devforge/dependencies.txt

      - name: Run Android lint
        if: ${'$'}{{ inputs.lint_report == '' || inputs.lint_report == 'true' }}
        run: ./gradlew :app:lintDebug --stacktrace --no-daemon --max-workers=2

      - name: Upload lint report
        if: ${'$'}{{ always() && (inputs.lint_report == '' || inputs.lint_report == 'true') }}
        uses: actions/upload-artifact@v4
        with:
          name: devforge-lint-report
          path: |
            app/build/reports/lint-results-debug.html
            app/build/reports/lint-results-debug.xml
            app/build/reports/lint-results-debug.sarif
          if-no-files-found: ignore
          retention-days: 14

      - name: Upload unit-test report
        if: ${'$'}{{ always() && (inputs.unit_test_report == '' || inputs.unit_test_report == 'true') }}
        uses: actions/upload-artifact@v4
        with:
          name: devforge-unit-test-report
          path: |
            app/build/test-results/**/*.xml
            app/build/reports/tests/**/*
          if-no-files-found: ignore
          retention-days: 14

      - name: Upload dependency report
        if: ${'$'}{{ always() && inputs.dependency_report == 'true' }}
        uses: actions/upload-artifact@v4
        with:
          name: devforge-dependency-report
          path: app/build/reports/devforge/dependencies.txt
          if-no-files-found: ignore
          retention-days: 14

      - name: Verify release APK signature
        if: ${'$'}{{ (inputs.build_artifact == '' || inputs.build_artifact == 'true') && steps.target.outputs.target == 'release_apk' }}
        shell: bash
        run: |
          set -euo pipefail
          APK="app/build/outputs/apk/release/app-release.apk"
          test -s "${'$'}APK"
          APK_SIGNER="${'$'}{ANDROID_HOME}/build-tools/${'$'}{ANDROID_BUILD_TOOLS}/apksigner"
          test -x "${'$'}APK_SIGNER"
          "${'$'}APK_SIGNER" verify --verbose "${'$'}APK"

      - name: Verify release AAB signature
        if: ${'$'}{{ (inputs.build_artifact == '' || inputs.build_artifact == 'true') && steps.target.outputs.target == 'release_aab' }}
        shell: bash
        run: |
          set -euo pipefail
          AAB="app/build/outputs/bundle/release/app-release.aab"
          test -s "${'$'}AAB"
          jarsigner -verify -verbose -certs "${'$'}AAB" >/dev/null

      - name: Verify selected artifact
        if: ${'$'}{{ inputs.build_artifact == '' || inputs.build_artifact == 'true' }}
        shell: bash
        env:
          ARTIFACT_PATH: ${'$'}{{ steps.target.outputs.artifact_path }}
        run: |
          set -euo pipefail
          shopt -s nullglob
          files=( ${'$'}ARTIFACT_PATH )
          if [ "${'$'}{#files[@]}" -eq 0 ]; then
            echo "Expected artifact was not produced: ${'$'}ARTIFACT_PATH" >&2
            exit 1
          fi
          for file in "${'$'}{files[@]}"; do
            test -s "${'$'}file"
            sha256sum "${'$'}file"
          done

      - name: Upload selected artifact
        if: ${'$'}{{ (inputs.publish_artifacts == '' || inputs.publish_artifacts == 'true') && (inputs.build_artifact == '' || inputs.build_artifact == 'true') }}
        uses: actions/upload-artifact@v4
        with:
          name: ${'$'}{{ steps.target.outputs.artifact_name }}
          path: ${'$'}{{ steps.target.outputs.artifact_path }}
          if-no-files-found: error
          retention-days: 14

      - name: Remove temporary release signing material
        if: ${'$'}{{ always() && env.DEVFORGE_RELEASE_SIGNING_CONFIGURED == 'true' && (steps.target.outputs.target == 'release_apk' || steps.target.outputs.target == 'release_aab') }}
        shell: bash
        run: |
          set -euo pipefail
          rm -f "${'$'}{RUNNER_TEMP}/devforge-release.keystore"

""".trimIndent()

    val ui = """
name: Android UI Tests

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: ${'$'}{{ github.workflow }}-${'$'}{{ github.ref }}
  cancel-in-progress: ${'$'}{{ github.event_name != 'workflow_dispatch' }}

env:
  JAVA_VERSION: '17'

jobs:
  instrumentation:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - name: Checkout
        uses: actions/checkout@v5

      - name: Set up JDK 17
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: ${'$'}{{ env.JAVA_VERSION }}
          cache: gradle

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          cache-read-only: ${'$'}{{ github.event_name == 'pull_request' }}

      - name: Enable KVM
        shell: bash
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm

      - name: Run Compose UI tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 35
          target: google_apis
          arch: x86_64
          profile: pixel_2
          cores: 2
          ram-size: 4096M
          heap-size: 512M
          disk-size: 8G
          force-avd-creation: true
          emulator-boot-timeout: 900
          disable-animations: true
          emulator-options: -no-window -no-snapshot -no-boot-anim -noaudio -gpu swiftshader_indirect
          script: timeout 120 adb wait-for-device && test "$(adb get-state 2>/dev/null || true)" = "device" && ./gradlew :app:connectedDebugAndroidTest --stacktrace --no-daemon --max-workers=2

      - name: Upload instrumentation test reports
        if: ${'$'}{{ always() }}
        uses: actions/upload-artifact@v4
        with:
          name: devforge-instrumentation-reports
          path: |
            app/build/reports/androidTests
            app/build/outputs/androidTest-results
          if-no-files-found: ignore
          retention-days: 14

""".trimIndent()

    val release = """
name: Release Build Validation

on:
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: ${'$'}{{ github.workflow }}-${'$'}{{ github.ref }}
  cancel-in-progress: ${'$'}{{ github.event_name != 'workflow_dispatch' }}

env:
  JAVA_VERSION: '17'
  ANDROID_PLATFORM: 'android-36'
  ANDROID_BUILD_TOOLS: '36.0.0'
  GRADLE_OPTS: '-Dorg.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8'

jobs:
  release:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - name: Checkout
        uses: actions/checkout@v5

      - name: Set up JDK 17
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: ${'$'}{{ env.JAVA_VERSION }}
          cache: gradle

      - name: Install required Android SDK components
        shell: bash
        run: |
          set -euo pipefail
          SDKMANAGER="${'$'}{ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
          if [ ! -x "${'$'}SDKMANAGER" ]; then
            SDKMANAGER="${'$'}(command -v sdkmanager)"
          fi
          echo "ANDROID_SDKMANAGER=${'$'}SDKMANAGER" >> "${'$'}GITHUB_ENV"
          echo "${'$'}(dirname "${'$'}SDKMANAGER")" >> "${'$'}GITHUB_PATH"
          yes | "${'$'}SDKMANAGER" --licenses >/dev/null || true
          "${'$'}SDKMANAGER" "platform-tools" "platforms;${'$'}{ANDROID_PLATFORM}" "build-tools;${'$'}{ANDROID_BUILD_TOOLS}"
          BUILD_TOOLS_DIR="${'$'}{ANDROID_HOME}/build-tools/${'$'}{ANDROID_BUILD_TOOLS}"
          test -x "${'$'}{BUILD_TOOLS_DIR}/apksigner"
          echo "${'$'}BUILD_TOOLS_DIR" >> "${'$'}GITHUB_PATH"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          cache-read-only: ${'$'}{{ github.event_name == 'pull_request' }}

      - name: Prepare release signing
        shell: bash
        env:
          RELEASE_KEYSTORE_BASE64: ${'$'}{{ secrets.DEVFORGE_RELEASE_KEYSTORE_BASE64 }}
          RELEASE_KEYSTORE_PASSWORD: ${'$'}{{ secrets.DEVFORGE_RELEASE_KEYSTORE_PASSWORD }}
          RELEASE_KEY_ALIAS: ${'$'}{{ secrets.DEVFORGE_RELEASE_KEY_ALIAS }}
          RELEASE_KEY_PASSWORD: ${'$'}{{ secrets.DEVFORGE_RELEASE_KEY_PASSWORD }}
        run: |
          set -euo pipefail
          if [[ -z "${'$'}RELEASE_KEYSTORE_BASE64" || -z "${'$'}RELEASE_KEYSTORE_PASSWORD" || -z "${'$'}RELEASE_KEY_ALIAS" || -z "${'$'}RELEASE_KEY_PASSWORD" ]]; then
            echo "Release signing secrets are required." >&2
            exit 1
          fi
          KEYSTORE_FILE="${'$'}{RUNNER_TEMP}/devforge-release.keystore"
          printf "%s" "${'$'}RELEASE_KEYSTORE_BASE64" |
            tr -d '[:space:]' |
            base64 --decode > "${'$'}KEYSTORE_FILE"
          test -s "${'$'}KEYSTORE_FILE"
          if ! keytool -list -keystore "${'$'}KEYSTORE_FILE" \
              -storetype PKCS12 \
              -storepass "${'$'}RELEASE_KEYSTORE_PASSWORD" \
              -alias "${'$'}RELEASE_KEY_ALIAS" >/dev/null 2>&1; then
            echo "The configured release keystore secret is not a valid PKCS12 keystore for the supplied alias/password." >&2
            exit 1
          fi
          {
            echo "DEVFORGE_RELEASE_SIGNING_CONFIGURED=true"
            echo "DEVFORGE_RELEASE_KEYSTORE_FILE=${'$'}KEYSTORE_FILE"
            echo "DEVFORGE_RELEASE_KEYSTORE_PASSWORD=${'$'}RELEASE_KEYSTORE_PASSWORD"
            echo "DEVFORGE_RELEASE_KEY_ALIAS=${'$'}RELEASE_KEY_ALIAS"
            echo "DEVFORGE_RELEASE_KEY_PASSWORD=${'$'}RELEASE_KEY_PASSWORD"
          } >> "${'$'}GITHUB_ENV"
          echo "Release signing is configured."

      - name: Verify toolchain
        shell: bash
        run: |
          set -euo pipefail
          java -version
          ./gradlew --version
          "${'$'}ANDROID_SDKMANAGER" --list_installed | grep -E "platform-tools|platforms;${'$'}{ANDROID_PLATFORM}|build-tools;${'$'}{ANDROID_BUILD_TOOLS}"

      - name: Build signed release APK and AAB
        shell: bash
        run: |
          set -euo pipefail
          START=${'$'}(date +%s)
          ./gradlew :app:assembleRelease :app:bundleRelease --stacktrace --no-daemon --max-workers=2
          echo "RELEASE_BUILD_SECONDS=${'$'}((${'$'}(date +%s) - START))" >> "${'$'}GITHUB_ENV"

      - name: Verify release artifacts and budgets
        shell: bash
        run: |
          set -euo pipefail
          bash tools/check-build-budget.sh release-apk app/build/outputs/apk/release/app-release.apk 80 "${'$'}RELEASE_BUILD_SECONDS" 900
          bash tools/check-build-budget.sh release-aab app/build/outputs/bundle/release/app-release.aab 80

      - name: Verify release APK signature
        shell: bash
        run: |
          set -euo pipefail
          APK_SIGNER="${'$'}{ANDROID_HOME}/build-tools/${'$'}{ANDROID_BUILD_TOOLS}/apksigner"
          test -x "${'$'}APK_SIGNER"
          "${'$'}APK_SIGNER" verify --verbose app/build/outputs/apk/release/app-release.apk

      - name: Verify release AAB signature
        shell: bash
        run: |
          set -euo pipefail
          jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab >/dev/null

      - name: Upload signed release validation artifacts
        uses: actions/upload-artifact@v4
        with:
          name: devforge-release-validation
          path: |
            app/build/outputs/apk/release/app-release.apk
            app/build/outputs/bundle/release/app-release.aab
          if-no-files-found: error
          retention-days: 14

      - name: Remove temporary release signing material
        if: ${'$'}{{ always() }}
        shell: bash
        run: |
          rm -f "${'$'}{RUNNER_TEMP}/devforge-release.keystore"

""".trimIndent()
}
