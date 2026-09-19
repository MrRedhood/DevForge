package com.mrredhood.devforge.core.editor

import android.content.Context
import com.mrredhood.devforge.core.ai.AIChatGateway
import com.mrredhood.devforge.core.ai.AIModelInfo
import com.mrredhood.devforge.core.ai.AIProvider
import com.mrredhood.devforge.core.ai.AISettingsRepository
import com.mrredhood.devforge.core.ai.ModelCatalogService
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
    private val catalogService = ModelCatalogService()

    suspend fun propose(fileName: String, content: String, instruction: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(fileName.isNotBlank()) { "The editor file name is missing." }
            require(instruction.isNotBlank()) { "Enter an AI edit request." }
            require(content.toByteArray(Charsets.UTF_8).size <= MAX_EDITOR_AI_FILE_BYTES) {
                "AI edit is limited to 2 MB per file."
            }
            val provider = settings.selectedProvider()
            val key = settings.getApiKey(provider) ?: error(
                "No API key is configured for " + provider.displayName + ". Add one in Settings → AI & models.",
            )
            val savedModelId = settings.selectedModelId(provider)?.trim().orEmpty()
            val model = if (savedModelId.isNotBlank() && !savedModelId.contains("embedding", true)) {
                AIModelInfo(provider = provider, id = savedModelId, displayName = savedModelId)
            } else {
                chooseFallbackModel(provider, key)
                    ?: error("Select a text-capable model in Chat before using AI edit.")
            }
            var proposed = extractCode(gateway.send(
                model = model,
                apiKey = key,
                history = emptyList(),
                userInstruction = buildPrompt(fileName, content, instruction, model.provider, model.id),
                customBaseUrl = settings.customBaseUrl(provider),
            ))
            if (proposed == content) {
                proposed = extractCode(gateway.send(
                    model = model,
                    apiKey = key,
                    history = emptyList(),
                    userInstruction = buildPrompt(
                        fileName,
                        content,
                        instruction + "\nIMPORTANT: apply the requested edit and return the changed complete file; do not return the original unchanged.",
                        model.provider,
                        model.id,
                    ),
                    customBaseUrl = settings.customBaseUrl(provider),
                ))
            }
            require(proposed.isNotBlank()) { "AI returned an empty file." }
            require(proposed != content) { "AI returned the file unchanged. Try a more specific edit request." }
            require(!looksLikeDiff(proposed)) { "AI returned a diff instead of the complete updated file. Try the edit again." }
            proposed
        }
    }

    private suspend fun chooseFallbackModel(provider: AIProvider, key: String): AIModelInfo? {
        val catalog = catalogService.load(provider, key, settings.customBaseUrl(provider))
        val model = catalog.models.firstOrNull { it.isTextCapable && !it.isEmbedding } ?: return null
        settings.setSelectedModelId(provider, model.id)
        return model
    }

    private fun buildPrompt(fileName: String, content: String, instruction: String, provider: AIProvider, modelId: String): String = buildString {
        append("You are the DevForge inline code editor.\n")
        append("Provider: ").append(provider.displayName).append("\n")
        append("Model: ").append(modelId).append("\n")
        append("Edit exactly one workspace file: ").append(fileName).append(".\n")
        append("User request: ").append(instruction.trim()).append("\n")
        append("Return ONLY the complete updated file content. Do not return a diff, explanation, commentary, JSON, or multiple files.\n")
        append("Preserve unrelated code, imports, formatting, and behavior.\n")
        append("Current file:\n<DEVFORGE_FILE>\n")
        append(content)
        append("\n</DEVFORGE_FILE>")
    }


    private fun looksLikeDiff(value: String): Boolean {
        val lines = value.lineSequence().toList()
        val headers = lines.count { it.startsWith("+++ ") || it.startsWith("--- ") || it.startsWith("@@ ") }
        return headers >= 2
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
