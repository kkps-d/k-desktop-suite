package com.kkps.kdesktopsuite.paint

import com.kkps.kdesktopsuite.ui.AppIconLauncherActivity

class PaintLauncherActivity : AppIconLauncherActivity() {
    override val editorActivity = PaintActivity::class.java
}
