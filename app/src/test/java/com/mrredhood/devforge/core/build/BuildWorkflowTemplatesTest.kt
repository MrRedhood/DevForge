package com.mrredhood.devforge.core.build

import org.junit.Assert.assertTrue
import org.junit.Test

class BuildWorkflowTemplatesTest {

    @Test
    fun ciTemplateMatchesReleaseFirstMainWorkflowContract() {
        val ci = BuildWorkflowTemplates.ci
        assertTrue(ci.contains("# Main-branch Android CI is release-first"))
        assertTrue(ci.contains("default: release_apk"))
        assertTrue(ci.contains("pull_request:"))
        assertTrue(ci.contains("TARGET=\"debug_apk\""))
        assertTrue(ci.contains("if [[ \"\$EVENT_NAME\" == \"push\" ]]; then"))
        assertTrue(ci.contains("""TARGET="release_apk""""))
        assertTrue(ci.contains("devforge-release-apk"))
        assertTrue(ci.contains("DEVFORGE_RELEASE_KEYSTORE_BASE64"))
    }

    @Test
    fun uiTemplateKeepsEmulatorHardeningAndRetryContract() {
        val ui = BuildWorkflowTemplates.ui
        assertTrue(ui.contains("api-level: 35"))
        assertTrue(ui.contains("emulator-boot-timeout: 900"))
        assertTrue(ui.contains("-camera-back none"))
        assertTrue(ui.contains("Gradle/UI test retry 2/3"))
        assertTrue(ui.contains("Gradle/UI test retry 3/3"))
    }

    @Test
    fun releaseTemplateVerifiesBudgetsAndBothSignedOutputs() {
        val release = BuildWorkflowTemplates.release
        assertTrue(release.contains("check-build-budget.sh release-apk"))
        assertTrue(release.contains("check-build-budget.sh release-aab"))
        assertTrue(release.contains("APK_SIGNER="))
        assertTrue(release.contains("\"$" + "APK_SIGNER\" verify"))
        assertTrue(release.contains("jarsigner -verify"))
        assertTrue(release.contains("devforge-release-validation"))
    }
}
