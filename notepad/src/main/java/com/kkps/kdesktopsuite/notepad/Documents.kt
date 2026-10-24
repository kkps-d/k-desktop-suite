package com.kkps.kdesktopsuite.notepad

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID

internal data class TextFormat(val crlf: Boolean = false, val bom: Boolean = false)

internal class UnsupportedTextException : Exception()

internal object TextCodec {
    fun decode(bytes: ByteArray): Pair<String, TextFormat> {
        val bom = bytes.take(3) == listOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte())
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(bytes, if (bom) 3 else 0, bytes.size - if (bom) 3 else 0)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw UnsupportedTextException()
        }
        if ('\u0000' in text) throw UnsupportedTextException()
        return text.replace("\r\n", "\n").replace('\r', '\n') to TextFormat("\r\n" in text, bom)
    }

    fun encode(text: String, format: TextFormat): ByteArray {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val body = (if (format.crlf) normalized.replace("\n", "\r\n") else normalized).toByteArray(Charsets.UTF_8)
        return if (format.bom) byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + body else body
    }

    fun fingerprint(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}

internal data class DocumentSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val untitledNumber: Int = 1,
    val name: String? = null,
    val uri: String? = null,
    val text: String = "",
    val savedText: String = "",
    val diskFingerprint: String? = null,
    val format: TextFormat = TextFormat(),
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val scroll: Int = 0,
    val horizontalScroll: Int = 0,
)

internal data class SessionSnapshot(
    val documents: List<DocumentSnapshot>,
    val activeId: String,
    val zoom: Int = 100,
    val nextUntitled: Int = 2,
    val wordWrap: Boolean = true,
    val showStatusBar: Boolean = true,
)

internal class DocumentSession(snapshot: DocumentSnapshot) {
    val id = snapshot.id
    val untitledNumber = snapshot.untitledNumber
    val editor = TextFieldState(
        snapshot.text,
        TextRange(snapshot.selectionStart.coerceIn(0, snapshot.text.length), snapshot.selectionEnd.coerceIn(0, snapshot.text.length)),
    )
    val scroll = ScrollState(snapshot.scroll.coerceAtLeast(0))
    val horizontalScroll = ScrollState(snapshot.horizontalScroll.coerceAtLeast(0))
    var name by mutableStateOf(snapshot.name)
    var uri by mutableStateOf(snapshot.uri)
    var savedText by mutableStateOf(snapshot.savedText)
    var diskFingerprint by mutableStateOf(snapshot.diskFingerprint)
    var format by mutableStateOf(snapshot.format)
    var saving by mutableStateOf(false)
    val dirty: Boolean get() = editor.text.toString() != savedText

    fun snapshot() = DocumentSnapshot(
        id, untitledNumber, name, uri, editor.text.toString(), savedText, diskFingerprint, format,
        editor.selection.start, editor.selection.end, scroll.value, horizontalScroll.value,
    )
}
