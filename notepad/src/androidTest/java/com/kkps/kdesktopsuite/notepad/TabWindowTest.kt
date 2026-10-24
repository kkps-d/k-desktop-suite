@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kkps.kdesktopsuite.notepad

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class TabWindowTest {
    @get:Rule val compose = createAndroidComposeRule<NotepadActivity>()

    private fun ready(): NotepadViewModel {
        compose.waitUntil(5_000) { !compose.activity.notepad.loading }
        return compose.activity.notepad
    }

    private fun tabPosition(id: String): Offset {
        val bounds = compose.onNodeWithTag("tab-title-$id", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        val origin = IntArray(2)
        compose.runOnIdle {
            val owner = compose.activity.notepad.workspace.ownerOf(id)!!
            windowActivity(owner).window.decorView.getLocationOnScreen(origin)
        }
        return Offset(origin[0] + bounds.left + 8f, origin[1] + bounds.center.y)
    }

    private fun windowActivity(window: NotepadWindow): NotepadActivity = Stage.values().flatMap {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)
    }.filterIsInstance<NotepadActivity>().first { it.notepad.window === window }

    private fun launchWindow(workspace: NotepadWorkspace): NotepadWindow {
        lateinit var window: NotepadWindow
        compose.runOnIdle {
            window = workspace.acquireWindow(null, true)
            TabWindows.showWindow(compose.activity, window.id)
        }
        compose.waitUntil(10_000) { window.attached }
        compose.waitForIdle()
        return window
    }

    /** Inject through Android so the gesture includes the platform drag manager and caption. */
    private fun mouseDrag(from: Offset, to: Offset, beforeRelease: () -> Unit = {}) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, position: Offset) {
            val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
            val coordinates = MotionEvent.PointerCoords().apply { x = position.x; y = position.y; pressure = 1f }
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1,
                arrayOf(properties), arrayOf(coordinates), 0,
                if (action == MotionEvent.ACTION_UP) 0 else MotionEvent.BUTTON_PRIMARY,
                1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, from)
        try {
            for (step in 1..20) {
                send(MotionEvent.ACTION_MOVE, from + (to - from) * (step / 20f))
                SystemClock.sleep(24)
            }
            beforeRelease()
        } finally {
            send(MotionEvent.ACTION_UP, to)
        }
    }

    @Test
    fun nativeMouseDragReordersAndDroppingOnEditorCancels() {
        val notepad = ready()
        compose.runOnIdle {
            notepad.newDocument()
            notepad.active!!.editor.edit { append("native mouse draft") }
            notepad.newDocument()
            notepad.newDocument()
        }
        val original = notepad.documents.takeLast(3)
        compose.runOnIdle { notepad.select(original[0].id) }
        val from = tabPosition(original[0].id)
        val to = tabPosition(original[2].id) + Offset(80f, 0f)
        val header = compose.onNodeWithTag("document-header").fetchSemanticsNode().boundsInWindow
        mouseDrag(from, to) {
            assertEquals(original, notepad.documents.takeLast(3))
            assertEquals(original[0].id, notepad.workspace.dragging?.documentId)
        }
        compose.waitUntil(5_000) { notepad.workspace.dragging == null }
        assertEquals("Drag $from to $to; header=$header", listOf(original[1], original[2], original[0]), notepad.documents.takeLast(3))
        val before = notepad.documents.toList()
        val active = notepad.activeId
        val text = notepad.active!!.editor.text.toString()
        mouseDrag(tabPosition(original[0].id), tabPosition(original[0].id) + Offset(0f, 160f))
        compose.waitUntil(5_000) { notepad.workspace.dragging == null }
        compose.runOnIdle {
            assertEquals(before, notepad.documents)
            assertEquals(active, notepad.activeId)
            assertEquals(text, notepad.active!!.editor.text.toString())
        }
    }

    @Test
    fun movingToExistingWindowClosesEmptySource() {
        val notepad = ready()
        assumeTrue(TabWindows.supported(compose.activity))
        val source = launchWindow(notepad.workspace)
        val document = source.documents.single()
        compose.runOnIdle { document.editor.edit { append("between windows") } }
        val destination = launchWindow(notepad.workspace)
        try {
            compose.runOnIdle {
                val sourceViewModel = windowActivity(source).notepad
                assertTrue(sourceViewModel.moveToWindow(document.id, destination))
            }
            compose.waitUntil(5_000) { source.closed && destination.activeId == document.id }
            compose.runOnIdle {
                assertSame(document, destination.documents.last())
                assertEquals("between windows", document.editor.text.toString())
            }
            compose.waitUntil(5_000) { !source.attached }
        } finally {
            compose.runOnIdle {
                if (!source.closed) notepad.workspace.abandonWindow(source)
                notepad.workspace.abandonWindow(destination)
                notepad.workspace.bringToFront(notepad.window)
            }
        }
    }

    @Test
    fun detachedActivityAcknowledgesTransferAndCanMergeBack() {
        val notepad = ready()
        assumeTrue(TabWindows.supported(compose.activity))
        compose.runOnIdle {
            notepad.newDocument()
            notepad.active!!.editor.edit { append("detached draft") }
            notepad.newDocument()
        }
        val document = notepad.documents[notepad.documents.lastIndex - 1]
        compose.runOnIdle { TabWindows.launch(compose.activity, notepad, document.id) }
        compose.waitUntil(10_000) { notepad.workspace.ownerOf(document.id)?.let { it !== notepad.window && it.attached } == true }
        val destination = notepad.workspace.ownerOf(document.id)!!
        lateinit var previous: NotepadActivity
        compose.runOnIdle {
            previous = windowActivity(destination)
            previous.recreate()
        }
        compose.waitUntil(5_000) { previous.isDestroyed && destination.attached }
        compose.runOnIdle {
            assertFalse(destination.closed)
            assertSame(document, destination.documents.single())
            assertEquals(document.id, destination.activeId)
            document.editor.undoState.undo()
            assertEquals("", document.editor.text.toString())
            document.editor.undoState.redo()
            val transfer = notepad.workspace.beginTransfer(destination, document.id)!!
            assertTrue(notepad.workspace.move(transfer.token, notepad.window, 0))
            assertTrue(destination.closed)
            assertSame(document, notepad.documents.first())
        }
        compose.waitUntil(5_000) { !destination.attached }
    }
}
