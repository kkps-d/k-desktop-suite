package com.kkps.kdesktopsuite.paint

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/** Observes input for the debug page without consuming or changing its dispatch. */
internal class PaintPenStatus {
    var attached: Boolean? by mutableStateOf(null)
        private set
    var hovering by mutableStateOf(false)
        private set
    var buttonHeld by mutableStateOf(false)
        private set
    private var observing = false
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!observing || intent.action != "com.samsung.pen.INSERT" || !intent.hasExtra("penInsert")) return
            attached = intent.getBooleanExtra("penInsert", false)
            if (attached == true) clearInput()
        }
    }

    fun start(context: Context) {
        if (observing) return
        observing = true
        attached = null
        try {
            // This OEM broadcast comes from outside our app and may provide a sticky initial state.
            val initial = ContextCompat.registerReceiver(context, receiver,
                IntentFilter("com.samsung.pen.INSERT"), ContextCompat.RECEIVER_EXPORTED)
            registered = true
            initial?.let { receiver.onReceive(context, it) }
        } catch (_: SecurityException) {
            // Attachment detection is optional; hover and button diagnostics still work.
        }
    }

    fun stop(context: Context) {
        observing = false
        if (registered) {
            context.unregisterReceiver(receiver)
            registered = false
        }
        clearInput()
    }

    fun clearInput() {
        hovering = false
        buttonHeld = false
    }

    fun onMotionEvent(event: MotionEvent) {
        if (!observing) return
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            clearInput()
            return
        }
        if ((0 until event.pointerCount).none {
            event.getToolType(it) == MotionEvent.TOOL_TYPE_STYLUS ||
                event.getToolType(it) == MotionEvent.TOOL_TYPE_ERASER
        }) return
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_EXIT -> {
                clearInput()
                return
            }
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> hovering = true
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> hovering = false
        }
        buttonHeld = event.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY)
    }
}
