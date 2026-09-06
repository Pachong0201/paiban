package com.gongwen.paiban.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 编辑器底部页签。 */
private enum class EditorTab(val label: String) {
    EDIT("编辑"),
    STYLE("样式"),
    FORMAT("排版"),
    CHECK("检查"),
    MORE("更多"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onExportDocx: () -> Unit,
) {
    val state by viewModel.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(EditorTab.EDIT) }

    LaunchedEffect(state.info) {
        state.info?.let {
            snackbar.showSnackbar(it)
            viewModel.clearInfo()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearInfo()
        }
    }

    if (!state.isOpen) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.isLoading) CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.displayName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "撤销")
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = state.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, "重做")
                    }
                    IconButton(onClick = onExportDocx) { Icon(Icons.Filled.MoreHoriz, "更多") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                EditorTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    EditorTab.EDIT -> Icons.Filled.Edit
                                    EditorTab.STYLE -> Icons.Filled.Palette
                                    EditorTab.FORMAT -> Icons.Filled.AutoAwesome
                                    EditorTab.CHECK -> Icons.Filled.Checklist
                                    EditorTab.MORE -> Icons.Filled.MoreHoriz
                                },
                                contentDescription = t.label,
                            )
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (tab) {
            EditorTab.EDIT -> ParagraphList(state, viewModel, Modifier.padding(padding))
            EditorTab.STYLE -> StyleTab(state, viewModel, Modifier.padding(padding))
            EditorTab.FORMAT -> FormatTab(viewModel, Modifier.padding(padding))
            EditorTab.CHECK -> CheckTab(state, Modifier.padding(padding))
            EditorTab.MORE -> MoreTab(state, Modifier.padding(padding))
        }
    }
}

@Composable
private fun ParagraphList(
    state: EditorUiState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        itemsIndexed(state.paragraphTexts) { index, para ->
            if (para.inTable) return@itemsIndexed
            var text by remember { mutableStateOf(para.text) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        para.roleLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        "第 ${para.index + 1} 段",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                    ),
                    minLines = 1,
                    maxLines = 8,
                )
                Row {
                    TextButton(onClick = { viewModel.setParagraphText(index, text) }) { Text("应用") }
                }
            }
        }
    }
}

@Composable
private fun StyleTab(state: EditorUiState, viewModel: EditorViewModel, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp)) {
        Text("段落样式（MVP 提供角色识别与一键排版）", style = MaterialTheme.typography.titleMedium)
        Text(
            "长按选择段落 → 在“排版”页重新识别；格式锁定与样式面板将在后续版本提供精细控制。",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun FormatTab(viewModel: EditorViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("公文排版", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { viewModel.runOneClickFormat() }, modifier = Modifier.fillMaxWidth()) {
            Text("一键公文排版（识别 → 应用 GB/T 9704）")
        }
        Text(
            "排版前请确认文档为公文正文（含标题/主送/正文/落款）。疑似结构将提示人工确认。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CheckTab(state: EditorUiState, modifier: Modifier = Modifier) {
    val report = state.report
    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Text("格式检查（按当前模板）", style = MaterialTheme.typography.titleMedium)
        if (report == null) {
            Text("尚未执行检查。请先执行一键排版，再进入“排版/检查”。")
        } else {
            Text("严重 ${report.errorCount}　警告 ${report.warningCount}　提示 ${report.infoCount}")
            if (report.findings.isEmpty()) {
                Text("未发现问题。")
            } else {
                val first = report.findings.first()
                Text(
                    "示例：第 ${first.paragraphIndex + 1} 段 — ${first.message}\n${first.current}\n${first.expected}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MoreTab(state: EditorUiState, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp)) {
        Text("更多", style = MaterialTheme.typography.titleMedium)
        Text("模板库 / 字体管理 / 设置 将在此提供。当前：${state.templateName}")
    }
}
