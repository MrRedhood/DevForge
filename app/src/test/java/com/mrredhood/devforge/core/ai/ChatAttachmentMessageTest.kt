package com.mrredhood.devforge.core.ai

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAttachmentMessageTest {
    @Test
    fun attachmentMetadataRoundTripsAndVisibleMessageIsStripped() {
        val attachments = listOf(
            ChatAttachment(
                uri = Uri.parse("content://devforge/file/image.png"),
                name = "image.png",
                mimeType = "image/png",
                sizeBytes = 1234L,
                type = ChatAttachmentType.ANY_FILE,
            ),
            ChatAttachment(
                uri = Uri.parse("content://devforge/file/video.mp4"),
                name = "video.mp4",
                mimeType = "video/mp4",
                sizeBytes = 5678L,
                type = ChatAttachmentType.ANY_FILE,
            ),
        )

        val encoded = "Look at these files." + encodeChatMessageAttachments(attachments)
        val decoded = parseChatMessageAttachments(encoded)

        assertEquals(2, decoded.size)
        assertEquals(attachments[0].uri, decoded[0].uri)
        assertEquals("image.png", decoded[0].name)
        assertEquals("image/png", decoded[0].mimeType)
        assertEquals(5678L, decoded[1].sizeBytes)
        assertEquals("Look at these files.", stripChatMessageAttachmentMetadata(encoded))
        assertTrue(encoded.contains("<devforge_attachments>"))
    }
}
