package com.gongwen.gongwen.validate

import com.gongwen.document.model.Paragraph
import com.gongwen.document.model.Run
import com.gongwen.document.model.SemanticRole
import com.gongwen.template.TemplateResolver
import com.gongwen.template.model.DocumentTemplate
import com.gongwen.template.model.FindingSeverity
import com.gongwen.template.model.Role

/** 一条验证发现。 */
data class ValidationFinding(
    val ruleId: String,
    val severity: FindingSeverity,
    val paragraphIndex: Int,
    val message: String,
    val current: String,
    val expected: String,
)

/** 可自动修复标记。 */
enum class FixAction { NONE, AUTO }

data class FindingFix(
    val finding: ValidationFinding,
    val action: FixAction,
)

/** 验证汇总。 */
data class ValidationReport(
    val findings: List<ValidationFinding>,
    val errorCount: Int,
    val warningCount: Int,
    val infoCount: Int,
) {
    val autoFixable: List<ValidationFinding> get() = findings
}

/** 按"当前有效模板"执行格式检查（规格 §25）。 */
class DocumentValidator(
    private val templates: Map<String, DocumentTemplate>,
    private val templateId: String,
) {
    private val effective: DocumentTemplate by lazy {
        TemplateResolver.resolveEffective(templateId, templates)
    }

    /** 段落索引 → 段落（含表格内段落，按文档序）。 */
    fun validate(paragraphs: List<Paragraph>): ValidationReport {
        val findings = ArrayList<ValidationFinding>()
        for ((i, p) in paragraphs.withIndex()) {
            if (p.inTable) continue
            val role = p.effectiveRole
            if (role == SemanticRole.OTHER || role == SemanticRole.UNKNOWN) continue
            val tplRole = roleToTemplate(role) ?: continue
            val style = effective.styleFor(tplRole)

            // ---- 段落级 ----
            style.paragraph.alignment?.let { expect ->
                val actual = p.properties.alignment
                if (actual != expect) {
                    findings.add(
                        ValidationFinding(
                            "para.align", FindingSeverity.ERROR, i,
                            "第 ${i + 1} 段对齐方式不符合「${expect}」",
                            "当前：${actual ?: "未设置"}",
                            "要求：$expect",
                        )
                    )
                }
            }
            style.paragraph.firstLineIndentTwips?.let { expect ->
                val actual = p.properties.firstLineIndentTwips
                if (actual != expect) {
                    findings.add(
                        ValidationFinding(
                            "para.firstLine", FindingSeverity.ERROR, i,
                            "第 ${i + 1} 段首行缩进不符合",
                            "当前：${actual ?: 0} twips",
                            "要求：$expect twips",
                        )
                    )
                }
            }
            style.paragraph.lineSpacingRule?.let { expectRule ->
                val actualRule = p.properties.lineSpacingRule
                if (actualRule != expectRule) {
                    findings.add(
                        ValidationFinding(
                            "para.lineRule", FindingSeverity.WARNING, i,
                            "行距规则不一致",
                            "当前：${actualRule ?: "未设置"}",
                            "要求：$expectRule",
                        )
                    )
                }
            }

            // ---- 文字级（取段首 run 对比）----
            val firstRun = p.runs.firstOrNull()
            style.text.eastAsiaFont?.let { expectFont ->
                val actual = firstRun?.properties?.eastAsiaFont
                if (actual != expectFont) {
                    findings.add(
                        ValidationFinding(
                            "text.eastAsia", FindingSeverity.ERROR, i,
                            "中文字体不符合「$expectFont」",
                            "当前：${actual ?: "未设置"}",
                            "要求：$expectFont",
                        )
                    )
                }
            }
            style.text.fontSizeHalfPoints?.let { expectSize ->
                val actual = firstRun?.properties?.fontSizeHalfPoints
                if (actual != expectSize) {
                    findings.add(
                        ValidationFinding(
                            "text.sz", FindingSeverity.ERROR, i,
                            "字号不符合",
                            "当前：${halfPointToLabel(actual)}",
                            "要求：${halfPointToLabel(expectSize)}",
                        )
                    )
                }
            }
        }
        val err = findings.count { it.severity == FindingSeverity.ERROR }
        val warn = findings.count { it.severity == FindingSeverity.WARNING }
        val info = findings.count { it.severity == FindingSeverity.INFO }
        return ValidationReport(findings, err, warn, info)
    }

    private fun halfPointToLabel(hp: Int?): String = when (hp) {
        null -> "未设置"
        44 -> "二号(22pt)"
        40 -> "小二(20pt)"
        36 -> "三号(18pt)"
        32 -> "三号(16pt)"
        28 -> "小四(14pt)"
        24 -> "小四(12pt)"
        else -> "$hp half-points"
    }

    private fun roleToTemplate(role: SemanticRole): Role? = when (role) {
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
}
