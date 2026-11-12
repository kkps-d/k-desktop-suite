package com.kkps.kdesktopsuite.notepad

import android.os.Looper
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class SessionStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val file get() = folder.root.resolve("session.json")

    /** Write callbacks arrive on the main looper, which this test thread has to run. */
    private fun runMainLooperUntil(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "Checkpoint did not complete" }
            Thread.sleep(5)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun SessionStore.save(snapshot: SessionSnapshot): Boolean {
        var result: Boolean? = null
        checkpoint(snapshot) { result = it }
        runMainLooperUntil { result != null }
        return result!!
    }

    @Test
    fun missingFileRestoresNothing() = runBlocking<Unit> {
        assertNull(SessionStore(file).read())
    }

    @Test
    fun roundTripPreservesDocumentsAndSettings() = runBlocking<Unit> {
        val saved = DocumentSnapshot(
            name = "notes.txt", uri = "content://docs/notes.txt", text = "Edited မြန်မာ 👋\n",
            savedText = "Original\n", diskFingerprint = "abc123", format = TextFormat(crlf = true, bom = true),
            selectionStart = 2, selectionEnd = 6, scroll = 40, horizontalScroll = 12, readOnly = true,
        )
        val untitled = DocumentSnapshot(text = "Draft")
        val snapshot = SessionSnapshot(
            listOf(saved, untitled), untitled.id, zoom = 150, wordWrap = false, showStatusBar = false,
            appTheme = AppTheme.Dark, fontStyle = NoteFontStyle.System,
            recentFiles = listOf(RecentFile("content://docs/notes.txt", "notes.txt"), RecentFile("content://docs/old", null)),
        )
        assertTrue(SessionStore(file).save(snapshot))
        assertEquals(snapshot, SessionStore(file).read())
    }

    @Test
    fun newerVersionIsRejectedAndNeverOverwritten() = runBlocking<Unit> {
        file.writeText(JSONObject().put("version", 2).put("activeId", "").put("documents", JSONArray()).toString())
        val original = file.readText()
        val store = SessionStore(file)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { store.read() } }
        assertFalse(store.save(SessionSnapshot(emptyList(), "")))
        assertEquals(original, file.readText())
    }

    @Test
    fun duplicateDocumentIdsAreRejected() = runBlocking<Unit> {
        val document = DocumentSnapshot()
        assertTrue(SessionStore(file).save(SessionSnapshot(listOf(document, document), document.id)))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { SessionStore(file).read() } }
    }

    @Test
    fun rapidCheckpointsAllCompleteAndKeepTheNewestSnapshot() = runBlocking<Unit> {
        val store = SessionStore(file)
        val results = mutableListOf<Boolean>()
        val snapshots = (1..20).map { SessionSnapshot(emptyList(), "", zoom = 100 + it) }
        snapshots.forEach { snapshot -> store.checkpoint(snapshot) { results += it } }
        runMainLooperUntil { results.size == snapshots.size }
        assertEquals(List(snapshots.size) { true }, results)
        assertEquals(snapshots.last(), SessionStore(file).read())
    }

    @Test
    fun zoomAndRecentFilesAreNormalized() = runBlocking<Unit> {
        val recent = (1..7).map { RecentFile("content://docs/$it", "$it.txt") }
        val snapshot = SessionSnapshot(emptyList(), "", zoom = 999, recentFiles = listOf(recent[0]) + recent)
        assertTrue(SessionStore(file).save(snapshot))
        val restored = SessionStore(file).read()!!
        assertEquals(300, restored.zoom)
        assertEquals(recent.take(5), restored.recentFiles)
    }
}
