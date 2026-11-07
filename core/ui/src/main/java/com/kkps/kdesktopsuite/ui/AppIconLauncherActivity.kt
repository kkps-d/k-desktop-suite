package com.kkps.kdesktopsuite.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle

/** Launcher aliases finish here; editor tasks never belong to a switchable component. */
abstract class AppIconLauncherActivity : Activity() {
    protected abstract val editorActivity: Class<out Activity>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(intent).apply {
            component = ComponentName(this@AppIconLauncherActivity, editorActivity)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            removeFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        })
        finish()
    }
}
