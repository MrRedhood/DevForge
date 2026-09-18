package com.mrredhood.devforge.core.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAttachmentTypeTest {
    @Test
    fun mediaTypesStaySeparated() {
        assertTrue(ChatAttachmentType.PHOTO.accepts("image/png"))
        assertTrue(ChatAttachmentType.VIDEO.accepts("video/mp4"))
        assertTrue(ChatAttachmentType.AUDIO.accepts("audio/mpeg"))
        assertFalse(ChatAttachmentType.PHOTO.accepts("video/mp4"))
        assertFalse(ChatAttachmentType.VIDEO.accepts("image/png"))
        assertFalse(ChatAttachmentType.AUDIO.accepts("application/pdf"))
    }

    @Test
    fun documentTypesRejectUnrelatedBinaryMimeTypes() {
        assertTrue(ChatAttachmentType.DOCUMENT.accepts("application/pdf"))
        assertTrue(ChatAttachmentType.DOCUMENT.accepts("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertTrue(ChatAttachmentType.DOCUMENT.accepts("text/plain"))
        assertFalse(ChatAttachmentType.DOCUMENT.accepts("application/zip"))
        assertFalse(ChatAttachmentType.DOCUMENT.accepts("application/vnd.android.package-archive"))
        assertFalse(ChatAttachmentType.DOCUMENT.accepts("image/png"))
    }
}
