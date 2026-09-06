package com.gongwen.paiban.ui.template

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.gongwen.gongwen.Gbt9704Template
import com.gongwen.paiban.data.DocumentRepository
import com.gongwen.template.TemplateResolver
import com.gongwen.template.model.DocumentTemplate
import com.gongwen.template.model.TemplateMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/** 模板库 UI 状态。 */
data class TemplateUiState(
    val templates: List<TemplateListItem> = emptyList(),
    val activeId: String = Gbt9704Template.TEMPLATE_ID,
)

data class TemplateListItem(
    val templateId: String,
    val name: String,
    val version: String,
    val builtin: Boolean,
    val baseTemplate: String?,
    val isActive: Boolean,
)

/** 模板库 ViewModel：列表/切换/删除/导入导出/新建（基于当前模板继承）。 */
class TemplateViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DocumentRepository(app)
    private val _ui = MutableStateFlow(TemplateUiState())
    val ui: StateFlow<TemplateUiState> = _ui.asStateFlow()

    private var sessionActiveTemplate: String =
        com.gongwen.paiban.data.AppPrefs.activeTemplateId(app)

    init {
        refresh()
    }

    fun refresh() {
        val templates = repo.listTemplates()
        val items = templates.values.map { t ->
            TemplateListItem(
                templateId = t.metadata.templateId,
                name = t.metadata.name,
                version = t.metadata.version,
                builtin = t.metadata.builtin,
                baseTemplate = t.metadata.baseTemplate,
                isActive = t.metadata.templateId == sessionActiveTemplate,
            )
        }.sortedBy { it.builtin.not() } // 内置排前
        _ui.update { it.copy(templates = items) }
    }

    fun setActive(templateId: String) {
        sessionActiveTemplate = templateId
        refresh()
    }

    /** 新建继承自 base 的单位模板。 */
    fun createUnitTemplate(name: String, templateId: String, baseId: String = Gbt9704Template.TEMPLATE_ID): Boolean {
        if (templateId.isBlank()) return false
        val base = repo.listTemplates()[baseId] ?: return false
        // 继承 base 全部样式，作为起点
        val effective = TemplateResolver.resolveEffective(baseId, repo.listTemplates())
        val meta = TemplateMetadata(
            templateId = templateId,
            name = name.ifBlank { "未命名单位模板" },
            version = "1.0.0",
            schemaVersion = 1,
            builtin = false,
            baseTemplate = baseId,
            author = "公文排版",
        )
        // 仅存储差异会丢失有效样式——MVP 存全量样式（安全）但保留 basedOn
        val t = effective.copy(metadata = meta)
        repo.saveUserTemplate(t)
        refresh()
        return true
    }

    fun deleteTemplate(templateId: String): Boolean {
        if (templateId == Gbt9704Template.TEMPLATE_ID) return false
        val f = File(appContext().filesDir, "templates/$templateId.twtemplate")
        val ok = f.delete()
        if (ok) refresh()
        return ok
    }

    fun importFromBytes(bytes: ByteArray): Boolean = try {
        repo.importTemplate(bytes)
        refresh()
        true
    } catch (_: Exception) {
        false
    }

    fun exportTemplateBytes(templateId: String): ByteArray? {
        val t = repo.listTemplates()[templateId] ?: return null
        return com.gongwen.template.TwTemplatePackage.pack(t)
    }

    /** 从当前文档生成单位模板（抽取页面与正文样式），另存进库。 */
    fun createFromSessionDocument(session: DocumentRepository.SessionDocument?): Boolean {
        if (session == null) return false
        val base = repo.listTemplates()[Gbt9704Template.TEMPLATE_ID] ?: return false
        val doc = session.document

        // 从 sectPr/正文推导（MVP：抽取第 1 个正文段作为样本，或默认继承 base）
        val bodyPara = doc.paragraphs.firstOrNull { it.effectiveRole == com.gongwen.document.model.SemanticRole.BODY }
        val template = if (bodyPara != null) {
            // 用文档正文段实际格式覆盖 BODY 角色（抽取模板核心）
            val p = bodyPara.properties
            val r = bodyPara.runs.firstOrNull()?.properties
            val bodyStyle = com.gongwen.template.model.RoleStyle(
                paragraph = com.gongwen.template.model.ParagraphStyle(
                    alignment = p.alignment,
                    firstLineIndentTwips = p.firstLineIndentTwips,
                    lineSpacingRule = p.lineSpacingRule,
                    lineSpacingValue = p.lineSpacingValue,
                ),
                text = com.gongwen.template.model.TextStyle(
                    eastAsiaFont = r?.eastAsiaFont,
                    latinFont = r?.asciiFont,
                    fontSizeHalfPoints = r?.fontSizeHalfPoints,
                ),
            )
            // 继承国家模板全部角色，仅覆盖 BODY
            val styles = HashMap(base.roleStyles)
            styles[com.gongwen.template.model.Role.BODY] = bodyStyle
            base.copy(
                metadata = TemplateMetadata(
                    templateId = "unit-" + System.currentTimeMillis().toString().takeLast(6),
                    name = "从文档生成（待确认）",
                    baseTemplate = Gbt9704Template.TEMPLATE_ID,
                    builtin = false,
                ),
                roleStyles = styles,
            )
        } else {
            base.copy(
                metadata = TemplateMetadata(
                    templateId = "unit-" + System.currentTimeMillis().toString().takeLast(6),
                    name = "从文档生成（默认继承）",
                    baseTemplate = Gbt9704Template.TEMPLATE_ID,
                    builtin = false,
                ),
            )
        }
        repo.saveUserTemplate(template)
        refresh()
        return true
    }

    /** 更新模板（编辑后保存）。 */
    fun updateTemplate(updated: DocumentTemplate) {
        repo.saveUserTemplate(updated)
        refresh()
    }

    /** 取单个模板（含继承合并后的有效样式）。 */
    fun getEffective(templateId: String): DocumentTemplate? {
        val all = repo.listTemplates()
        return try {
            TemplateResolver.resolveEffective(templateId, all)
        } catch (_: Exception) {
            all[templateId]
        }
    }

    private fun appContext() = getApplication<Application>()
}
