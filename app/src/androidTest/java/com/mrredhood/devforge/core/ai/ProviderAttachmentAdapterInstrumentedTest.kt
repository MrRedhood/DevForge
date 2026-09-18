package com.mrredhood.devforge.core.ai

import android.net.Uri
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAttachmentAdapterInstrumentedTest {
    @Test
    fun unsupportedProviderAdapterRejectsBinaryAttachments() = kotlinx.coroutines.runBlocking {
        val adapter = UnsupportedProviderAttachmentAdapter(AIProvider.OPENAI)
        val attachment = ChatAttachment(
            uri = Uri.parse("content://devforge/photo.jpg"),
            name = "photo.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 1024,
            type = ChatAttachmentType.PHOTO,
        )
        var failed = false
        try {
            adapter.prepare(apiKey = "test-key", attachments = listOf(attachment))
        } catch (error: UnsupportedProviderAttachmentsException) {
            failed = true
        }
        assertTrue(failed)
    }
}
