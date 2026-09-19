package com.mrredhood.devforge.core.editor

import android.content.Context
import com.mrredhood.devforge.core.ai.AIChatGateway
import com.mrredhood.devforge.core.ai.AIModelInfo
import com.mrredhood.devforge.core.ai.AIProvider
import com.mrredhood.devforge.core.ai.AISettingsRepository
import com.mrredhood.devforge.core.ai.ModelCatalogService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
        try {
            withTimeout(MAX_AI_EDIT_TIMEOUT_MS) {
                require(fileName.isNotBlank()) { "The editor file name is missing." }
                require(instruction.isNotBlank()) { "Enter an AI edit request." }
                require(content.toByteArray(Charsets.UTF_8).size <= MAX_EDITOR_AI_FILE_BYTES) {
                    "AI edit is limited to 2 MB per file."
                }
                val provider = settings.selectedProvider()
                if (settings.isApiKeyLocked(provider)) {
                    error(
                        "The " + provider.displayName +
                            " credential is locked. Unlock protected credentials in Settings before using AI edit.",
                    )
                }
                val key = settings.getApiKey(provider) ?: error(
                    "No API key is configured for " + provider.displayName + ". Add one in Settings → AI & models.",
                )
                val model = chooseEditorModel(provider, key)
                    ?: error(
                        "No text-capable model is available for " + provider.displayName +
                            ". Select a text/chat model in Chat, then retry AI edit.",
                    )
                var proposed = extractCode(
                    gateway.send(
                        model = model,
                        apiKey = key,
                        history = emptyList(),
                        userInstruction = buildPrompt(fileName, content, instruction, model.provider, model.id),
                        customBaseUrl = settings.customBaseUrl(provider),
                    ),
                )
                if (proposed == content) {
                    proposed = extractCode(
                        gateway.send(
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
                        ),
                    )
                }
                require(proposed.isNotBlank()) { "AI returned an empty file." }
                require(proposed != content) { "AI returned the file unchanged. Try a more specific edit request." }
                require(!looksLikeDiff(proposed)) { "AI returned a diff instead of the complete updated file. Try the edit again." }
                Result.success(proposed)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure<String>(error)
        }
    }

    private suspend fun chooseEditorModel(provider: AIProvider, key: String): AIModelInfo? {
        val savedModelId = settings.selectedModelId(provider)?.trim().orEmpty()
        val catalog = catalogService.load(provider, key, settings.customBaseUrl(provider))
        val discovered = catalog.models.filter { it.isTextCapable && !it.isEmbedding }
        discovered.firstOrNull { it.id == savedModelId }?.let { return it }

        if (catalog.models.isEmpty() && savedModelId.isNotBlank() && isSafeModelId(savedModelId)) {
            return AIModelInfo(
                provider = provider,
                id = savedModelId,
                displayName = savedModelId,
                inputModalities = setOf("text"),
                outputModalities = setOf("text"),
                metadataSource = "Saved editor model",
            )
        }

        val fallback = discovered.firstOrNull() ?: return null
        settings.setSelectedModelId(provider, fallback.id)
        return fallback
    }

    private fun isSafeModelId(value: String): Boolean =
        value.length <= 180 &&
            value.isNotBlank() &&
            Regex("^[A-Za-z0-9_.:/-]+$").matches(value) &&
            !value.contains("..") &&
            !value.contains('?') &&
            !value.contains('#') &&
            !value.contains('\\')
    private fun buildPrompt(fileName: String, content: String, instruction: String, provider: AIProvider, modelId: String): String = buildString {
        append("You are the DevForge inline code editor.\n")
        append("Provider: ").append(provider.displayName).append("\n")
        append("Model: ").append(modelId).append("\n")
        append("Edit exactly one workspace file: ").append(fileName).append(".\n")
        append("User request: ").append(instruction.trim()).append("\n")
        append("Return ONLY the complete updated file content. Do not return a diff, explanation, commentary, JSON, or multiple files.\n")
        append("Return executable source for exactly this file; never return patch headers such as --- a/, +++ b/, or @@.\n")
        append("Preserve unrelated code, imports, formatting, and behavior.\n")
        append("Current file:\n<DEVFORGE_FILE>\n")
        append(content)
        append("\n</DEVFORGE_FILE>")
    }


    private companion object {
        const val MAX_EDITOR_AI_FILE_BYTES = 2 * 1024 * 1024
        const val MAX_AI_EDIT_TIMEOUT_MS = 150_000L
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
