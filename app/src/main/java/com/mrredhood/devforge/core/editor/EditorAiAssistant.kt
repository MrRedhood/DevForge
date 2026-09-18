package com.mrredhood.devforge.core.editor

import android.content.Context
import com.mrredhood.devforge.core.ai.AIChatGateway
import com.mrredhood.devforge.core.ai.AIModelInfo
import com.mrredhood.devforge.core.ai.AISettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EditorAiProposal(
    val uri: android.net.Uri,
    val fileName: String,
    val original: String,
    val proposed: String,
    val instruction: String,
) {
    val addedLines: Int get() = diffLines(original, proposed).count { it.startsWith("+ ") }
    val removedLines: Int get() = diffLines(original, proposed).count { it.startsWith("- ") }
    fun preview(maxLines: Int = 24): List<String> = diffLines(original, proposed).take(maxLines)
}

class EditorAiAssistant(context: Context) {
    private val settings = AISettingsRepository(context)
    private val gateway = AIChatGateway()

    suspend fun propose(fileName: String, content: String, instruction: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val provider = settings.selectedProvider()
            val modelId = settings.selectedModelId(provider) ?: error("Select an AI model in Chat first.")
            val key = settings.getApiKey(provider) ?: error("AI API key is not configured.")
            val model = AIModelInfo(provider = provider, id = modelId, displayName = modelId)
            val response = gateway.send(
                model = model,
                apiKey = key,
                history = emptyList(),
                userInstruction = buildPrompt(fileName, content, instruction),
                customBaseUrl = settings.customBaseUrl(provider),
            )
            extractCode(response).takeIf { it.isNotBlank() && it != content }
                ?: error("AI returned no file change.")
        }
    }

    private fun buildPrompt(fileName: String, content: String, instruction: String): String = buildString {
        append("You are DevForge inline code editor assistant.\n")
        append("Edit exactly one file: ").append(fileName).append(".\n")
        append("User request: ").append(instruction.trim()).append("\n")
        append("Return ONLY the complete updated file. Wrap it in one Markdown code fence. No explanation.\n")
        append("Preserve unrelated code and formatting.\n")
        append("Current file begins below.\n---FILE---\n")
        append(content)
        append("\n---END FILE---")
    }

    private fun extractCode(response: String): String {
        val fence = Regex("(?s)```(?:[A-Za-z0-9_+#.-]+)?\\s*\\n?(.*?)```").find(response)
        return fence?.groupValues?.getOrNull(1)?.trimEnd('\n') ?: response.trim()
    }
}

private fun diffLines(before: String, after: String): List<String> {
    val oldLines = before.split("\n")
    val newLines = after.split("\n")
    val result = mutableListOf<String>()
    var i = 0
    var j = 0
    while (i < oldLines.size || j < newLines.size) {
        val old = oldLines.getOrNull(i)
        val next = newLines.getOrNull(j)
        when {
            old == next -> { result += "  " + (old ?: ""); i++; j++ }
            old != null && (j >= newLines.size || newLines.drop(j + 1).firstOrNull() == old) -> { result += "- " + old; i++ }
            next != null -> { result += "+ " + next; j++ }
        }
        if (result.size > 500) break
    }
    return result
}
