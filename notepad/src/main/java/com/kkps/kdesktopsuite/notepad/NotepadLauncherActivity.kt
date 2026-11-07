package com.kkps.kdesktopsuite.notepad

import com.kkps.kdesktopsuite.ui.AppIconLauncherActivity

class NotepadLauncherActivity : AppIconLauncherActivity() {
    override val editorActivity = NotepadActivity::class.java
}
