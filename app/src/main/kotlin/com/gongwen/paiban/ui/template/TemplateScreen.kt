package com.gongwen.paiban.ui.template

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 模板库页面：列出模板、选择当前模板、新建/导入/导出。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(
    viewModel: TemplateViewModel,
    onBack: () -> Unit,
    onImportPick: () -> Unit,
    onExportPick: () -> Unit,
) {
    val state by viewModel.ui.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<com.gongwen.template.model.DocumentTemplate?>(null) }
    val appContext = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模板库") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showCreate = true }) { Text("新建单位模板") }
                Button(onClick = onImportPick) { Text("导入") }
                Button(onClick = onExportPick) { Text("导出当前") }
            }
            Text(
                "单位模板继承 GB/T 9704，仅覆盖差异。选择模板后，“一键排版/格式检查”将按当前模板执行。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(state.templates, key = { it.templateId }) { item ->
                    Card(
                        onClick = {
                            viewModel.setActive(item.templateId)
                            com.gongwen.paiban.data.AppPrefs.setActiveTemplate(appContext, item.templateId)
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = item.isActive, onClick = null)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    buildString {
                                        append("v${item.version}")
                                        if (item.builtin) append(" · 内置") else append(" · 单位")
                                        item.baseTemplate?.let { append(" · 继承自 $it") }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!item.builtin) {
                                TextButton(onClick = {
                                    viewModel.getEffective(item.templateId)?.let { editingTemplate = it }
                                }) { Text("编辑") }
                                TextButton(onClick = { viewModel.deleteTemplate(item.templateId) }) { Text("删除") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateTemplateDialog(
            onConfirm = { name, id ->
                viewModel.createUnitTemplate(name, id)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }
    editingTemplate?.let { t ->
        TemplateEditDialog(
            template = t,
            onSave = { updated ->
                viewModel.updateTemplate(updated)
                editingTemplate = null
            },
            onDismiss = { editingTemplate = null },
        )
    }
}

@Composable
private fun CreateTemplateDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建单位模板（继承 GB/T 9704）") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("模板名称（如：XX单位正式公文）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("模板 ID（英文小写，如 xx-unit-gongwen）") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, id) }, enabled = id.isNotBlank()) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
