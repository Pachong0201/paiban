package com.gongwen.paiban.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 首页：核心入口 + 最近文件。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewDocument: () -> Unit = {},
    onOpenDocument: () -> Unit = {},
    onQuickFormat: () -> Unit = {},
    onCheck: () -> Unit = {},
    onTemplates: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("公文排版") })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                EntryCard(Icons.AutoMirrored.Filled.InsertDriveFile, "新建文档", "创建空白 DOCX 工作副本") { onNewDocument() }
            }
            item {
                EntryCard(Icons.Filled.FolderOpen, "打开文件", "从系统文件选择器打开 DOCX") { onOpenDocument() }
            }
            item {
                EntryCard(Icons.Filled.AutoAwesome, "一键公文排版", "自动识别结构并按模板排版") { onQuickFormat() }
            }
            item {
                EntryCard(Icons.Filled.Checklist, "格式检查", "按当前模板检查格式问题") { onCheck() }
            }
            item {
                EntryCard(Icons.Filled.GridView, "模板库", "国家/单位模板管理与导入导出") { onTemplates() }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("最近文件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("（暂无）", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EntryCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
