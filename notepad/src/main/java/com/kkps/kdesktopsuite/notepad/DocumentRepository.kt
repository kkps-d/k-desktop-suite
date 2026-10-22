package com.kkps.kdesktopsuite.notepad

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

internal data class LoadedDocument(val name: String?, val text: String, val format: TextFormat, val fingerprint: String)

internal class FileChangedException : IOException()

internal interface DocumentRepository {
    suspend fun read(uri: String): LoadedDocument
    suspend fun write(uri: String, text: String, format: TextFormat, expectedFingerprint: String?): LoadedDocument
}

internal class AndroidDocumentRepository(private val resolver: ContentResolver) : DocumentRepository {
    override suspend fun read(uri: String): LoadedDocument = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        val bytes = resolver.openInputStream(parsed)?.use { it.readBytes() } ?: throw IOException("Missing input stream")
        val (text, format) = TextCodec.decode(bytes)
        val name = resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        LoadedDocument(name, text, format, TextCodec.fingerprint(bytes))
    }

    override suspend fun write(
        uri: String, text: String, format: TextFormat, expectedFingerprint: String?,
    ): LoadedDocument = withContext(Dispatchers.IO) {
        // Check immediately before writing; providers do not offer a cross-app compare-and-swap.
        if (expectedFingerprint != null) {
            val current = resolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
                ?: throw IOException("Missing input stream")
            if (TextCodec.fingerprint(current) != expectedFingerprint) throw FileChangedException()
        }
        val bytes = TextCodec.encode(text, format)
        resolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(bytes) }
            ?: throw IOException("Missing output stream")
        val result = read(uri)
        if (result.fingerprint != TextCodec.fingerprint(bytes)) throw IOException("Provider did not retain the saved contents")
        result
    }
}
