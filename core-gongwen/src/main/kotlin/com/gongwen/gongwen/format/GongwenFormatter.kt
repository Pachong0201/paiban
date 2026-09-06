package com.gongwen.gongwen.format

import com.gongwen.document.DocumentModelEditor
import com.gongwen.document.model.Paragraph
import com.gongwen.document.model.ParagraphProperties
import com.gongwen.document.model.RunProperties
import com.gongwen.document.model.SemanticRole
import com.gongwen.template.TemplateResolver
import com.gongwen.template.model.DocumentTemplate
import com.gongwen.template.model.Role

/** 排版一次应用的结果汇总。 */
data class FormatApplyResult(
    val appliedCount: Int,
    val skippedLocked: Int,
    val skippedNoTemplate: Int,
)

/** 语义角色 → 模板角色。 */
internal fun semanticToTemplateRole(role: SemanticRole): Role? = when (role) {
    SemanticRole.TITLE -> Role.TITLE
    SemanticRole.RECIPIENT -> Role.RECIPIENT
    SemanticRole.BODY -> Role.BODY
    SemanticRole.HEADING1 -> Role.HEADING1
    SemanticRole.HEADING2 -> Role.HEADING2
    SemanticRole.HEADING3 -> Role.HEADING3
    SemanticRole.HEADING4 -> Role.HEADING4
    SemanticRole.ATTACHMENT_DESC -> Role.ATTACHMENT_DESC
    SemanticRole.SIGNATURE -> Role.SIGNATURE
    SemanticRole.DATE -> Role.DATE
    SemanticRole.ANNOTATION -> Role.ANNOTATION
    SemanticRole.COLOPHON -> Role.COLOPHON
    SemanticRole.QUOTATION -> Role.QUOTATION
    SemanticRole.OTHER, SemanticRole.UNKNOWN -> null
}

/**
 * 一键排版执行器：按段落有效角色应用模板格式。
 *
 * - formatLock 的段落整体跳过（规格 §14）；
 * - 表格内段落跳过（表格文字由表格样式控制）；
 * - [templates] 为全部可用模板，按 templateId 解析有效模板（继承合并）。
 */
class GongwenFormatter(
    private val editor: DocumentModelEditor,
    private val templates: Map<String, DocumentTemplate>,
    private val templateId: String,
) {
    private val effective: DocumentTemplate by lazy {
        TemplateResolver.resolveEffective(templateId, templates)
    }

    fun apply(paragraphs: List<Paragraph>, detectRole: (Paragraph) -> SemanticRole): FormatApplyResult {
        var applied = 0
        var locked = 0
        var noTmpl = 0

        for (p in paragraphs) {
            if (p.formatLock != com.gongwen.document.model.FormatLockScope.NONE) {
                locked++
                continue
            }
            if (p.inTable) continue
            val role = detectRole(p)
            val tplRole = semanticToTemplateRole(role)
            if (tplRole == null) {
                noTmpl++
                continue
            }
            val style = effective.styleFor(tplRole)
            val pStyle = style.paragraph
            val tStyle = style.text

            val props = ParagraphProperties().apply {
                alignment = pStyle.alignment
                firstLineIndentTwips = pStyle.firstLineIndentTwips
                leftIndentTwips = pStyle.leftIndentTwips
                rightIndentTwips = pStyle.rightIndentTwips
                lineSpacingRule = pStyle.lineSpacingRule
                lineSpacingValue = pStyle.lineSpacingValue
                spaceBeforeTwips = pStyle.spaceBeforeTwips
                spaceAfterTwips = pStyle.spaceAfterTwips
                keepWithNext = pStyle.keepWithNext ?: false
                keepLines = pStyle.keepLines ?: false
            }
            editor.setParagraphFormat(p, props)

            for (r in p.runs) {
                val rp = RunProperties().apply {
                    eastAsiaFont = tStyle.eastAsiaFont
                    asciiFont = tStyle.latinFont
                    hAnsiFont = tStyle.latinFont
                    csFont = tStyle.latinFont
                    fontSizeHalfPoints = tStyle.fontSizeHalfPoints
                    bold = tStyle.bold
                }
                editor.setRunFormat(p, r, rp)
            }
            applied++
        }
        return FormatApplyResult(applied, locked, noTmpl)
    }
}
