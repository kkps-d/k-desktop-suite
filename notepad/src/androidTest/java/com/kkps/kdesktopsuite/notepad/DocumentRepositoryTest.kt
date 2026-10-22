package com.kkps.kdesktopsuite.notepad

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DocumentRepositoryTest {
    private val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    private val repository = AndroidDocumentRepository(resolver)
    private fun uri() = Uri.parse("content://com.kkps.kdesktopsuite.notepad.test.documents/${UUID.randomUUID()}.txt")

    @Test
    fun providerRoundTripTruncatesOldContentsAndRetainsBomAndNewlines() = runBlocking {
        val uri = uri()
        val original = TextCodec.encode("A long original\nမြန်မာ\n", TextFormat(crlf = true, bom = true))
        resolver.openOutputStream(uri, "wt")!!.use { it.write(original) }
        val loaded = repository.read(uri.toString())
        assertEquals(uri.lastPathSegment, loaded.name)
        assertEquals("A long original\nမြန်မာ\n", loaded.text)
        val saved = repository.write(uri.toString(), "Short\n", loaded.format, loaded.fingerprint)
        assertEquals("Short\n", saved.text)
        assertArrayEquals(
            TextCodec.encode("Short\n", TextFormat(crlf = true, bom = true)),
            resolver.openInputStream(uri)!!.use { it.readBytes() },
        )
    }

    @Test
    fun conflictingWriteLeavesExternalContentsUntouched() = runBlocking {
        val uri = uri()
        resolver.openOutputStream(uri, "wt")!!.use { it.write("original".toByteArray()) }
        val loaded = repository.read(uri.toString())
        resolver.openOutputStream(uri, "wt")!!.use { it.write("external".toByteArray()) }
        try {
            repository.write(uri.toString(), "mine", loaded.format, loaded.fingerprint)
            fail("Expected a conflict")
        } catch (_: FileChangedException) {
            assertEquals("external", repository.read(uri.toString()).text)
        }
    }

    @Test
    fun unreadableAndInvalidDocumentsFailWithoutInventingEmptyContent() = runBlocking {
        val missing = uri()
        try {
            repository.read(missing.toString())
            fail("Missing documents must not become empty documents")
        } catch (_: java.io.FileNotFoundException) { }
        resolver.openOutputStream(missing, "wt")!!.use { it.write(byteArrayOf(0xc3.toByte(), 0x28)) }
        try {
            repository.read(missing.toString())
            fail("Invalid UTF-8 must not be decoded lossily")
        } catch (_: UnsupportedTextException) { }
    }
}
