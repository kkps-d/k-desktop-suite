package com.kkps.kdesktopsuite.paint

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.MotionEvent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

internal val LocalPaintPenStatus = staticCompositionLocalOf<PaintPenStatus?> { null }

@Composable
internal fun rememberPaintPenStatus(): PaintPenStatus =
    LocalPaintPenStatus.current ?: (LocalActivity.current as? PaintActivity)?.penStatus ?: remember { PaintPenStatus() }

internal enum class FingerInput {
    Automatic, Draw, Navigate;

    fun navigationOnly(supported: Boolean, attached: Boolean?, usedInSession: Boolean): Boolean = supported && when (this) {
        Automatic -> attached?.not() ?: usedInSession
        Draw -> false
        Navigate -> true
    }
}

/** Observes native input without consuming or changing its dispatch. */
internal class PaintPenStatus {
    var supported by mutableStateOf(false)
        private set
    var usedInSession by mutableStateOf(false)
    var attached: Boolean? by mutableStateOf(null)
        private set
    var hovering by mutableStateOf(false)
        private set
    var buttonHeld by mutableStateOf(false)
        private set
    var touching by mutableStateOf(false)
        private set
    var cancelGeneration by mutableIntStateOf(0)
        private set
    private var observing = false
    private var registered = false
    private var inputManager: InputManager? = null
    private val observedDevices = mutableSetOf<Int>()
    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refreshDevices()
        override fun onInputDeviceChanged(deviceId: Int) = refreshDevices()
        override fun onInputDeviceRemoved(deviceId: Int) {
            observedDevices.remove(deviceId)
            refreshDevices()
        }
    }

    fun navigationOnly(preferences: PaintPreferences): Boolean =
        preferences.fingerInput.navigationOnly(supported,attached,usedInSession)

    private fun refreshDevices() {
        val manager = inputManager ?: return
        val devices = manager.inputDeviceIds.asSequence().mapNotNull(manager::getInputDevice).toList()
        updateDevices(devices.map { it.id }.toSet(), devices.filter {
            !it.isVirtual && it.supportsSource(InputDevice.SOURCE_STYLUS)
        }.map { it.id }.toSet())
    }

    internal fun updateDevices(present: Set<Int>, styluses: Set<Int>) {
        observedDevices.retainAll(present)
        supported = styluses.isNotEmpty() || observedDevices.isNotEmpty()
        if (!supported) clearInput()
    }
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
        inputManager = context.getSystemService(InputManager::class.java)
        inputManager?.registerInputDeviceListener(deviceListener, Handler(Looper.getMainLooper()))
        refreshDevices()
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
        inputManager?.unregisterInputDeviceListener(deviceListener)
        inputManager = null
        if (registered) {
            context.unregisterReceiver(receiver)
            registered = false
        }
        clearInput()
    }

    fun clearInput(cancel: Boolean = true) {
        hovering = false
        buttonHeld = false
        touching = false
        if (cancel) cancelGeneration++
    }

    fun onMotionEvent(event: MotionEvent) {
        if (!observing) return
        if (event.actionMasked == MotionEvent.ACTION_CANCEL ||
            Build.VERSION.SDK_INT >= 33 && event.flags and MotionEvent.FLAG_CANCELED != 0) {
            clearInput()
            return
        }
        if ((0 until event.pointerCount).none {
            event.getToolType(it) == MotionEvent.TOOL_TYPE_STYLUS ||
                event.getToolType(it) == MotionEvent.TOOL_TYPE_ERASER
        }) return
        observedDevices.add(event.deviceId)
        supported = true
        if (event.actionMasked in listOf(MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE,
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE)) usedInSession = true
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_EXIT -> {
                clearInput(cancel = false)
                return
            }
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> { hovering = true; touching = false }
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                hovering = false
                touching = (0 until event.pointerCount).any {
                    (event.getToolType(it) == MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(it) == MotionEvent.TOOL_TYPE_ERASER) &&
                        !((event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_POINTER_UP) &&
                            it == event.actionIndex)
                }
            }
        }
        buttonHeld = event.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY)
    }
}
