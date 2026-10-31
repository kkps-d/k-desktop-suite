package com.kkps.kdesktopsuite.paint

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal class PaintWindow(val id: String, val recovery: PaintRecovery) {
    var hasDraft = false
    var uri: String? = null
    var draftUri: String? = null
    var checkpointSequence = -1L
    var preserveRecovery = false
    val reservedUris = mutableSetOf<String>()
    var closed = false
    var launching = false
    var activity: PaintActivity? = null
    var askStartup = false
}

/** Window membership and file ownership outlive individual Android activities. Access on Main. */
internal class PaintWindows(private val root: File, private val legacy: PaintRecovery) {
    val windows = linkedMapOf<String, PaintWindow>()
    private val storage = Mutex()
    private var initialized = false
    private var focusedId: String? = null
    private var previousIds = emptySet<String>()
    private var startupResolved: CompletableDeferred<Unit>? = null
    private var restoreFocusId: String? = null
    private val restorePending = mutableSetOf<String>()

    private fun window(id: String = UUID.randomUUID().toString()) =
        PaintWindow(id, PaintRecovery(File(root, id)))

    suspend fun acquire(id: String?, resume: Boolean, newWindow: Boolean, behavior: PaintStartupBehavior): PaintWindow {
        val result = acquireWindow(id, resume, newWindow, behavior)
        if (result.id in previousIds && !result.askStartup) {
            windows.values.firstOrNull { it.askStartup }?.activity?.bringWindowToFront()
            startupResolved?.await()
        }
        return result
    }

    private suspend fun acquireWindow(id: String?, resume: Boolean, newWindow: Boolean, behavior: PaintStartupBehavior): PaintWindow = withContext(NonCancellable) {
        storage.withLock {
            if (!initialized) {
                windows.clear()
                val index = withContext(Dispatchers.IO) {
                    val file = AtomicFile(File(root, "session.json"))
                    if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists())
                        file.openRead().bufferedReader().use { JSONObject(it.readText()) } else null
                }
                if (index != null) {
                    require(index.getInt("version") == 1) { "Unsupported Paint window recovery. Its files have been preserved." }
                    val entries = index.getJSONArray("windows")
                    for (i in 0 until entries.length()) {
                        val entry = entries.getJSONObject(i)
                        val key = entry.getString("id")
                        require(UUID.fromString(key).toString() == key && key !in windows) { "Invalid Paint window recovery." }
                        windows[key] = window(key).apply {
                            hasDraft = entry.getBoolean("hasDraft")
                            uri = entry.optString("uri").takeIf { it.isNotEmpty() }
                            draftUri = uri
                        }
                    }
                    focusedId = index.optString("focusedId").takeIf { it in windows }
                    restoreFocusId = focusedId
                } else {
                    // Commit the migrated PNG before publishing membership; leave the old recovery intact.
                    legacy.read()?.let { snapshot ->
                        val migrated = window()
                        migrated.recovery.write(snapshot.copy(sequence = migrated.recovery.nextSequence()))
                        migrated.hasDraft = true
                        migrated.uri = snapshot.uri
                        migrated.draftUri = snapshot.uri
                        windows[migrated.id] = migrated
                    }
                    persist()
                }
                restorePending.clear()
                restorePending.addAll(windows.keys)
                val restoring = resume || (id != null && id in windows)
                if (!restoring && !newWindow && behavior == PaintStartupBehavior.NewImage) {
                    windows.clear()
                    focusedId = null
                    restoreFocusId = null
                    restorePending.clear()
                    persist()
                } else if (!restoring && !newWindow && behavior == PaintStartupBehavior.AskEveryTime && windows.isNotEmpty()) {
                    previousIds = windows.keys.toSet()
                    startupResolved = CompletableDeferred()
                }
                initialized = true
            }
            val target = id?.let { windows[it] }
                ?: if (!newWindow) windows[focusedId] ?: windows.values.firstOrNull { !it.closed }
                else null
            val result = target ?: window().also { windows[it.id] = it; persist() }
            if (previousIds.isNotEmpty() && windows.values.none { it.askStartup }) result.askStartup = true
            result.launching = false
            restorePending.remove(result.id)
            result
        }
    }

    suspend fun resolveStartup(window: PaintWindow, reopen: Boolean) = withContext(NonCancellable) {
        storage.withLock {
            val retained = windows.toMap()
            val hadDraft = window.hasDraft
            val uri = window.uri
            val draftUri = window.draftUri
            if (!reopen) {
                previousIds.filter { it != window.id }.forEach { windows.remove(it)?.closed = true }
                window.hasDraft = false
                window.uri = null
                window.draftUri = null
                restoreFocusId = null
            }
            try { persist() }
            catch (error: Exception) {
                windows.clear()
                windows.putAll(retained)
                retained.values.forEach { it.closed = false }
                window.hasDraft = hadDraft
                window.uri = uri
                window.draftUri = draftUri
                throw error
            }
            previousIds = emptySet()
            restorePending.retainAll(windows.keys)
            window.askStartup = false
            startupResolved?.complete(Unit)
        }
    }

    suspend fun checkpoint(window: PaintWindow, snapshot: RecoverySnapshot) = withContext(Dispatchers.Main.immediate) {
        if (window.closed || windows[window.id] !== window) return@withContext
        // PNG encoding is serialized by this window's recovery writer, not the session index lock.
        window.recovery.write(snapshot)
        storage.withLock {
            if (window.closed || windows[window.id] !== window || snapshot.sequence < window.checkpointSequence) return@withLock
            window.hasDraft = true
            window.draftUri = snapshot.uri
            window.checkpointSequence = snapshot.sequence
            persist()
        }
    }

    suspend fun close(window: PaintWindow) = withContext(NonCancellable) {
        storage.withLock {
            window.closed = true
            try {
                persist()
            } catch (error: Exception) {
                window.closed = false
                throw error
            }
            windows.remove(window.id)
            window.reservedUris.clear()
        }
        window.recovery.retire(removeFiles = !window.preserveRecovery)
    }

    suspend fun focus(window: PaintWindow) = storage.withLock {
        if (!window.closed) { focusedId = window.id; persist() }
    }

    fun reserve(window: PaintWindow, uri: String): Boolean {
        if (owner(uri, window) != null) return false
        window.reservedUris.add(uri)
        return true
    }

    fun owner(uri: String, except: PaintWindow): PaintWindow? = windows.values.firstOrNull {
        it !== except && !it.closed && (it.uri == uri || uri in it.reservedUris)
    }

    fun show(context: Context, window: PaintWindow) {
        window.activity?.let { it.bringWindowToFront(); return }
        if (window.launching || window.closed) return
        window.launching = true
        try { launch(context, window.id) }
        catch (error: Exception) { window.launching = false; throw error }
    }

    fun restoreOthers(context: Context, current: PaintWindow, onError: (Throwable) -> Unit) {
        if (previousIds.isNotEmpty()) return
        windows.values.filter { it.id in restorePending && it !== current && !it.closed && it.activity == null && !it.launching }
            .sortedBy { it.id == restoreFocusId }.forEach {
                try { show(context, it) } catch (error: Exception) { onError(error) }
            }
        if (windows.values.none { it.launching }) {
            restoreFocusId?.let { windows[it]?.activity?.bringWindowToFront() }
            restoreFocusId = null
        }
    }

    private suspend fun persist() {
        val entries = JSONArray()
        windows.values.filter { !it.closed }.forEach {
            entries.put(JSONObject().put("id", it.id).put("hasDraft", it.hasDraft).put("uri", it.draftUri ?: ""))
        }
        val bytes = JSONObject().put("version", 1).put("focusedId", focusedId ?: "").put("windows", entries).toString().toByteArray()
        withContext(Dispatchers.IO) {
            root.mkdirs()
            val file = AtomicFile(File(root, "session.json"))
            val output = file.startWrite()
            try { output.write(bytes); file.finishWrite(output) }
            catch (error: Exception) { file.failWrite(output); throw error }
        }
    }

    companion object {
        const val windowExtra = "paint.windowId"
        const val newWindowExtra = "paint.newWindow"
        fun launch(context: Context, id: String? = null) {
            context.startActivity(Intent(context, PaintActivity::class.java).apply {
                putExtra(windowExtra, id)
                putExtra(newWindowExtra, id == null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
            })
        }
    }
}
