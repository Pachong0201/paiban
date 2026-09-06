package com.gongwen.paiban.ui.template

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gongwen.template.model.DocumentTemplate
import com.gongwen.template.model.PageSettings
import com.gongwen.template.model.ParagraphStyle
import com.gongwen.template.model.Role
import com.gongwen.template.model.RoleStyle
import com.gongwen.template.model.TextStyle

/** 模板样式编辑对话框：页面设置 + 标题/正文/一级标题样式。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditDialog(
    template: DocumentTemplate,
    onSave: (DocumentTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    // 编辑的是"本模板自身"样式（非继承合并值）——base 样式保持继承
    val bodyStyle = template.styleFor(Role.BODY)
    val titleStyle = template.styleFor(Role.TITLE)
    val h1Style = template.styleFor(Role.HEADING1)

    var mt by remember { mutableStateOf(template.page.marginTopTwips.toString()) }
    var mb by remember { mutableStateOf(template.page.marginBottomTwips.toString()) }
    var ml by remember { mutableStateOf(template.page.marginLeftTwips.toString()) }
    var mr by remember { mutableStateOf(template.page.marginRightTwips.toString()) }

    var bodyFont by remember { mutableStateOf(bodyStyle.text.eastAsiaFont ?: "") }
    var bodySize by remember { mutableStateOf((bodyStyle.text.fontSizeHalfPoints ?: 32).toString()) }
    var bodyIndent by remember { mutableStateOf((bodyStyle.paragraph.firstLineIndentTwips ?: 640).toString()) }
    var bodyAlign by remember { mutableStateOf(bodyStyle.paragraph.alignment ?: "both") }

    var titleFont by remember { mutableStateOf(titleStyle.text.eastAsiaFont ?: "") }
    var titleSize by remember { mutableStateOf((titleStyle.text.fontSizeHalfPoints ?: 44).toString()) }
    var titleAlign by remember { mutableStateOf(titleStyle.paragraph.alignment ?: "center") }

    var h1Font by remember { mutableStateOf(h1Style.text.eastAsiaFont ?: "") }
    var h1Size by remember { mutableStateOf((h1Style.text.fontSizeHalfPoints ?: 32).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑模板样式：${template.metadata.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("—— 页面（twips，1cm≈567）——")
                NumberField("上边距", mt) { mt = it }
                NumberField("下边距", mb) { mb = it }
                NumberField("左边距", ml) { ml = it }
                NumberField("右边距", mr) { mr = it }
                Text("—— 正文 ——", Modifier.padding(top = 8.dp))
                OutlinedTextField(bodyFont, { bodyFont = it }, Modifier.fillMaxWidth(), label = { Text("中文字体") })
                Row {
                    NumberField("字号(half-pt)", bodySize, Modifier.weight(1f)) { bodySize = it }
                    NumberField("缩进", bodyIndent, Modifier.weight(1f)) { bodyIndent = it }
                }
                AlignPicker("对齐", bodyAlign) { bodyAlign = it }
                Text("—— 标题 ——", Modifier.padding(top = 8.dp))
                OutlinedTextField(titleFont, { titleFont = it }, Modifier.fillMaxWidth(), label = { Text("中文字体") })
                NumberField("字号(half-pt)", titleSize) { titleSize = it }
                AlignPicker("对齐", titleAlign) { titleAlign = it }
                Text("—— 一级标题 ——", Modifier.padding(top = 8.dp))
                OutlinedTextField(h1Font, { h1Font = it }, Modifier.fillMaxWidth(), label = { Text("中文字体") })
                NumberField("字号(half-pt)", h1Size) { h1Size = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val page = template.page.copy(
                    marginTopTwips = mt.toIntOrNull() ?: template.page.marginTopTwips,
                    marginBottomTwips = mb.toIntOrNull() ?: template.page.marginBottomTwips,
                    marginLeftTwips = ml.toIntOrNull() ?: template.page.marginLeftTwips,
                    marginRightTwips = mr.toIntOrNull() ?: template.page.marginRightTwips,
                )
                val styles = HashMap(template.roleStyles)
                styles[Role.BODY] = RoleStyle(
                    paragraph = ParagraphStyle(
                        alignment = bodyAlign,
                        firstLineIndentTwips = bodyIndent.toIntOrNull() ?: 640,
                    ),
                    text = TextStyle(
                        eastAsiaFont = bodyFont.ifBlank { null },
                        latinFont = bodyStyle.text.latinFont,
                        fontSizeHalfPoints = bodySize.toIntOrNull() ?: 32,
                    ),
                )
                styles[Role.TITLE] = RoleStyle(
                    paragraph = ParagraphStyle(alignment = titleAlign),
                    text = TextStyle(
                        eastAsiaFont = titleFont.ifBlank { null },
                        latinFont = titleStyle.text.latinFont,
                        fontSizeHalfPoints = titleSize.toIntOrNull() ?: 44,
                    ),
                )
                styles[Role.HEADING1] = RoleStyle(
                    paragraph = ParagraphStyle(),
                    text = TextStyle(
                        eastAsiaFont = h1Font.ifBlank { null },
                        latinFont = h1Style.text.latinFont,
                        fontSizeHalfPoints = h1Size.toIntOrNull() ?: 32,
                    ),
                )
                onSave(template.copy(page = page, roleStyles = styles))
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlignPicker(label: String, value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("left" to "左对齐", "center" to "居中", "right" to "右对齐", "both" to "两端对齐")
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == value }?.second ?: value,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (v, l) ->
                DropdownMenuItem(text = { Text(l) }, onClick = {
                    onChange(v)
                    expanded = false
                })
            }
        }
    }
}
