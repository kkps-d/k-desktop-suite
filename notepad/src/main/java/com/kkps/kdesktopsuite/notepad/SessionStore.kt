package com.kkps.kdesktopsuite.notepad

import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal class SessionStore(file: File) {
    private val atomicFile = AtomicFile(file)
    private val fileLock = Mutex()
    private var writable = true
    private val writes = Channel<Pair<SessionSnapshot, (Boolean) -> Unit>>(Channel.UNLIMITED)

    init {
        // Own the writer beyond any Activity/ViewModel so a background flush can finish.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for ((snapshot, complete) in writes) {
                val succeeded = runCatching { fileLock.withLock { write(snapshot) } }.isSuccess
                withContext(Dispatchers.Main.immediate) { complete(succeeded) }
            }
        }
    }

    suspend fun read(): SessionSnapshot? = withContext(Dispatchers.IO) {
        fileLock.withLock {
            try {
                if (!atomicFile.baseFile.exists() && !File(atomicFile.baseFile.path + ".bak").exists()) return@withLock null
                fromJson(JSONObject(atomicFile.openRead().bufferedReader().use { it.readText() }))
            } catch (error: Exception) {
                // A corrupt/newer session must never be replaced by an empty startup document.
                writable = false
                throw error
            }
        }
    }

    fun checkpoint(snapshot: SessionSnapshot, complete: (Boolean) -> Unit = {}) {
        writes.trySend(snapshot to complete)
    }

    private fun write(snapshot: SessionSnapshot) {
        check(writable) { "Previous recovery file needs to be preserved" }
        val output = atomicFile.startWrite()
        try {
            output.write(toJson(snapshot).toString().toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    private fun toJson(session: SessionSnapshot) = JSONObject().apply {
        put("version", 1)
        put("activeId", session.activeId)
        put("zoom", session.zoom)
        put("wordWrap", session.wordWrap)
        put("showStatusBar", session.showStatusBar)
        put("appTheme", session.appTheme.name)
        put("fontStyle", session.fontStyle.name)
        put("recentFiles", JSONArray().apply {
            session.recentFiles.forEach { file ->
                put(JSONObject().apply {
                    put("uri", file.uri)
                    put("name", file.name)
                })
            }
        })
        put("documents", JSONArray().apply {
            session.documents.forEach { doc ->
                put(JSONObject().apply {
                    put("id", doc.id)
                    put("name", doc.name)
                    put("uri", doc.uri)
                    put("text", doc.text)
                    put("savedText", doc.savedText)
                    put("diskFingerprint", doc.diskFingerprint)
                    put("crlf", doc.format.crlf)
                    put("bom", doc.format.bom)
                    put("selectionStart", doc.selectionStart)
                    put("selectionEnd", doc.selectionEnd)
                    put("scroll", doc.scroll)
                    put("horizontalScroll", doc.horizontalScroll)
                })
            }
        })
    }

    private fun fromJson(json: JSONObject): SessionSnapshot {
        require(json.getInt("version") == 1) { "Unsupported recovery version" }
        val array = json.getJSONArray("documents")
        val documents = List(array.length()) { index ->
            val doc = array.getJSONObject(index)
            DocumentSnapshot(
                id = doc.getString("id"),
                name = if (doc.isNull("name")) null else doc.getString("name"),
                uri = if (doc.isNull("uri")) null else doc.getString("uri"),
                text = doc.getString("text"),
                savedText = doc.getString("savedText"),
                diskFingerprint = if (doc.isNull("diskFingerprint")) null else doc.getString("diskFingerprint"),
                format = TextFormat(doc.getBoolean("crlf"), doc.getBoolean("bom")),
                selectionStart = doc.getInt("selectionStart"),
                selectionEnd = doc.getInt("selectionEnd"),
                scroll = doc.getInt("scroll"),
                horizontalScroll = doc.optInt("horizontalScroll", 0),
            )
        }
        require(documents.map { it.id }.distinct().size == documents.size)
        val activeId = json.getString("activeId")
        require(if (documents.isEmpty()) activeId.isEmpty() else documents.any { it.id == activeId })
        val recent = json.optJSONArray("recentFiles") ?: JSONArray()
        val recentFiles = List(recent.length()) { index ->
            val file = recent.getJSONObject(index)
            RecentFile(file.getString("uri"), if (file.isNull("name")) null else file.getString("name"))
        }.distinctBy { it.uri }.take(5)
        return SessionSnapshot(
            documents, activeId, json.getInt("zoom").coerceIn(50, 300),
            json.optBoolean("wordWrap", true), json.optBoolean("showStatusBar", true),
            AppTheme.entries.firstOrNull { it.name == json.optString("appTheme") } ?: AppTheme.Auto,
            NoteFontStyle.entries.firstOrNull { it.name == json.optString("fontStyle") } ?: NoteFontStyle.Monospace,
            recentFiles,
        )
    }
}
