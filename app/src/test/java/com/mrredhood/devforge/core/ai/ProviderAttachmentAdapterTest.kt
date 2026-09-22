package com.mrredhood.devforge.core.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAttachmentAdapterTest {
    @Test
    fun textAndCodeAttachmentsAreHandledAsContextWithoutBinaryUpload() {
        assertTrue(isTextLikeAttachment("Main.kt", "text/x-kotlin"))
        assertTrue(isTextLikeAttachment("data.json", "application/json"))
        assertFalse(isBinaryMime("Main.kt", "text/x-kotlin"))
    }

    @Test
    fun providerBinarySupportMatchesTransportAdapters() {
        assertTrue(supportsProviderAttachment(AIProvider.GEMINI, "clip.mp4", "video/mp4"))
        assertTrue(supportsProviderAttachment(AIProvider.OPENROUTER, "photo.jpg", "image/jpeg"))
        assertTrue(supportsProviderAttachment(AIProvider.OPENROUTER, "report.pdf", "application/pdf"))
        assertFalse(supportsProviderAttachment(AIProvider.OPENROUTER, "clip.mp4", "video/mp4"))
        assertFalse(supportsProviderAttachment(AIProvider.OPENROUTER, "recording.mp3", "audio/mpeg"))
        assertFalse(supportsProviderAttachment(AIProvider.OPENAI, "photo.jpg", "image/jpeg"))
        assertTrue(supportsProviderAttachment(AIProvider.OPENAI, "Main.kt", "text/x-kotlin"))
    }

    @Test
    fun mediaAndPdfAttachmentsRequireProviderUpload() {
        assertTrue(isBinaryMime("photo.jpg", "image/jpeg"))
        assertTrue(isBinaryMime("recording.mp3", "audio/mpeg"))
        assertTrue(isBinaryMime("clip.mp4", "video/mp4"))
        assertTrue(isBinaryMime("report.pdf", "application/pdf"))
    }

    private fun isBinaryMime(name: String, mimeType: String): Boolean =
        !isTextLikeAttachment(name, mimeType)
}
