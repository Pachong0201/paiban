package com.gongwen.paiban

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gongwen.paiban.ui.HomeScreen
import com.gongwen.paiban.ui.editor.EditorScreen
import com.gongwen.paiban.ui.editor.EditorViewModel
import com.gongwen.paiban.ui.theme.PaibanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaibanTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val editorVm: EditorViewModel = viewModel()
    var screen by remember { mutableStateOf("home") }

    // SAF 打开
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            editorVm.openUri(uri)
            screen = "editor"
        }
    }
    // 导出 DOCX（另存为）
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri: Uri? ->
        if (uri != null) {
            editorVm.exportTo(uri) { }
        }
    }

    when (screen) {
        "home" -> HomeScreen(
            onNewDocument = {
                editorVm.newDocument()
                screen = "editor"
            },
            onOpenDocument = {
                openLauncher.launch(arrayOf(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/msword",
                ))
            },
            onQuickFormat = {
                editorVm.newDocument()
                editorVm.runOneClickFormat()
                screen = "editor"
            },
            onCheck = { screen = "editor" },
            onTemplates = { /* 模板库 Phase 4b */ },
        )
        "editor" -> EditorScreen(
            viewModel = editorVm,
            onBack = { screen = "home" },
            onExportDocx = {
                editorVm.saveAutoRecovery()
                exportLauncher.launch("排版输出_${System.currentTimeMillis()}.docx")
            },
        )
    }
}
