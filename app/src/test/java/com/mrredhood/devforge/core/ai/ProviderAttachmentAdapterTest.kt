package com.mrredhood.devforge.core.ai

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAttachmentAdapterTest {

    private fun attachment(
        name: String,
        mimeType: String,
        type: ChatAttachmentType = ChatAttachmentType.ANY_FILE,
    ) = ChatAttachment(
        uri = Uri.parse("content://devforge/" + name),
        name = name,
        mimeType = mimeType,
        sizeBytes = 1024,
        type = type,
    )

    @Test
    fun textAndCodeAttachmentsAreHandledAsContextWithoutBinaryUpload() {
        assertTrue(isTextLikeAttachment(attachment("Main.kt", "text/x-kotlin")))
        assertTrue(isTextLikeAttachment(attachment("data.json", "application/json")))
        assertFalse(isBinaryAttachment(attachment("Main.kt", "text/x-kotlin")))
    }

    @Test
    fun mediaAndPdfAttachmentsRequireProviderUpload() {
        assertTrue(isBinaryAttachment(attachment("photo.jpg", "image/jpeg", ChatAttachmentType.PHOTO)))
        assertTrue(isBinaryAttachment(attachment("recording.mp3", "audio/mpeg", ChatAttachmentType.AUDIO)))
        assertTrue(isBinaryAttachment(attachment("clip.mp4", "video/mp4", ChatAttachmentType.VIDEO)))
        assertTrue(isBinaryAttachment(attachment("report.pdf", "application/pdf", ChatAttachmentType.DOCUMENT)))
    }

    @Test
    fun unsupportedProviderAdapterRejectsBinaryAttachments() = kotlinx.coroutines.test.runTest {
        val adapter = UnsupportedProviderAttachmentAdapter(AIProvider.OPENAI)

        var failed = false
        try {
            adapter.prepare(
                apiKey = "test-key",
                attachments = listOf(attachment("photo.jpg", "image/jpeg", ChatAttachmentType.PHOTO)),
            )
        } catch (error: UnsupportedProviderAttachmentsException) {
            failed = true
        }

        assertTrue(failed)
    }
}
