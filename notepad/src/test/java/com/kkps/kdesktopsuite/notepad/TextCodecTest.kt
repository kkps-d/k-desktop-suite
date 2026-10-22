package com.kkps.kdesktopsuite.notepad

import org.junit.Assert.*
import org.junit.Test

class TextCodecTest {
    @Test
    fun unicodeAndWindowsLineEndingsRoundTripWithBom() {
        val text = "Hello မြန်မာ 👋\nSecond line\n"
        val bytes = TextCodec.encode(text, TextFormat(crlf = true, bom = true))
        val (decoded, format) = TextCodec.decode(bytes)
        assertEquals(text, decoded)
        assertTrue(format.bom)
        assertTrue(format.crlf)
        assertArrayEquals(bytes, TextCodec.encode(decoded, format))
    }

    @Test
    fun newDocumentUsesUtf8WithoutBomAndLf() {
        val bytes = TextCodec.encode("one\ntwo", TextFormat())
        assertArrayEquals("one\ntwo".toByteArray(Charsets.UTF_8), bytes)
        assertEquals("one\ntwo" to TextFormat(), TextCodec.decode(bytes))
        assertEquals("" to TextFormat(), TextCodec.decode(byteArrayOf()))
    }

    @Test
    fun invalidUtf8AndBinaryDataAreRejectedInsteadOfReplacingCharacters() {
        listOf(
            byteArrayOf(0xc3.toByte(), 0x28),
            byteArrayOf(0xe2.toByte(), 0x82.toByte()),
            byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0x41, 0),
            byteArrayOf(0x41, 0, 0x42),
        ).forEach { bytes ->
            assertThrows(UnsupportedTextException::class.java) { TextCodec.decode(bytes) }
        }
    }

    @Test
    fun diskFingerprintDetectsEncodingChangesEvenWhenTextIsTheSame() {
        assertNotEquals(
            TextCodec.fingerprint(TextCodec.encode("same\ntext", TextFormat())),
            TextCodec.fingerprint(TextCodec.encode("same\ntext", TextFormat(crlf = true))),
        )
    }
}
