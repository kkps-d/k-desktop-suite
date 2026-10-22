package com.kkps.kdesktopsuite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kkps.kdesktopsuite.ui.KDesktopSuiteApp
import com.kkps.kdesktopsuite.ui.theme.KDesktopSuiteTheme
import com.kkps.kdesktopsuite.workspace.WorkspaceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val workspace: WorkspaceViewModel = viewModel()
            KDesktopSuiteTheme {
                KDesktopSuiteApp(
                    documentText = workspace::textFor,
                    onDocumentTextChange = workspace::updateText,
                )
            }
        }
    }
}
