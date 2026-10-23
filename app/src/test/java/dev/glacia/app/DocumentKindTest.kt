package dev.glacia.app

import dev.glacia.app.data.DocumentKind
import org.junit.Assert.*
import org.junit.Test

class DocumentKindTest {
    @Test fun recognizesPhotosSentAsFiles() {
        assertTrue(DocumentKind.isImage("image/jpeg", "photo.jpg"))
        assertTrue(DocumentKind.isImage("application/octet-stream", "IMG-2026.JPG"))
        assertTrue(DocumentKind.isImage("", "portrait.png"))
        assertFalse(DocumentKind.isImage("application/zip", "photo.jpg"))
        assertFalse(DocumentKind.isImage("application/zip", "archive.zip"))
    }

    @Test fun savesWithoutPathTraversalNames() {
        assertEquals("photo.jpg", DocumentKind.safeName("../photo.jpg"))
        assertEquals("archive.zip", DocumentKind.safeName("folder\\archive.zip"))
        assertEquals("Telegram document", DocumentKind.safeName(""))
    }
}
