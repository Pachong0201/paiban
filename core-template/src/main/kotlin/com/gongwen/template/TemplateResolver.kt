package com.gongwen.template

import com.gongwen.template.model.DocumentTemplate
import com.gongwen.template.model.ParagraphStyle
import com.gongwen.template.model.Role
import com.gongwen.template.model.RoleStyle
import com.gongwen.template.model.TextStyle

/**
 * 模板继承解析：单位模板 basedOn 国家模板 → 深度合并出有效模板（Current Effective Template）。
 *
 * 规则（docs/template-schema.md §6）：
 * - 字段级覆盖：单位模板声明了某 role 的某字段则覆盖，否则继承 base；
 * - Role 未在单位模板中出现则整体继承；
 * - rules 按 id 覆盖。
 */
object TemplateResolver {

    /** 解析继承链：templates 中每个 templateId → DocumentTemplate。baseTemplate 递归解析。 */
    fun resolveEffective(
        templateId: String,
        templates: Map<String, DocumentTemplate>,
    ): DocumentTemplate {
        val template = templates[templateId]
            ?: error("未知模板: $templateId")
        val baseId = template.metadata.baseTemplate ?: return template

        val base = resolveEffective(baseId, templates)
        return merge(base, template)
    }

    /** base 为底层（如 GB/T），child 为覆盖层（单位模板）。返回合并结果（不改动原对象）。 */
    fun merge(base: DocumentTemplate, child: DocumentTemplate): DocumentTemplate {
        // metadata：用 child 的，继承 base 缺失字段
        val meta = child.metadata.copy(
            baseTemplate = child.metadata.baseTemplate ?: base.metadata.templateId,
        )
        // page：child 的非默认字段覆盖 base
        val page = mergePage(base.page, child.page)
        // styles：role 级合并
        val allRoles = (base.roleStyles.keys + child.roleStyles.keys)
        val styles = HashMap<Role, RoleStyle>()
        for (role in allRoles) {
            val b = base.roleStyles[role] ?: RoleStyle()
            val c = child.roleStyles[role] ?: RoleStyle()
            styles[role] = mergeRoleStyle(b, c)
        }
        // rules：child 按 id 覆盖 base，其余保留
        val rulesById = LinkedHashMap<String, com.gongwen.template.model.ValidationRule>()
        base.rules.forEach { rulesById[it.id] = it }
        child.rules.forEach { rulesById[it.id] = it }
        return DocumentTemplate(
            metadata = meta,
            page = page,
            roleStyles = styles,
            defaultText = mergeText(base.defaultText, child.defaultText),
            rules = rulesById.values.toList(),
        )
    }

    private fun mergePage(base: com.gongwen.template.model.PageSettings,
                         child: com.gongwen.template.model.PageSettings
    ): com.gongwen.template.model.PageSettings {
        // 由于 data class 无 null 语义，约定：child 与默认值不同才覆盖；相同字段继承 base。
        // 更稳妥：单位模板 page 通常全量声明，若与默认全同则整体继承 base。
        val childAllDefault = child == com.gongwen.template.model.PageSettings()
        return if (childAllDefault) base else child
    }

    private fun mergeRoleStyle(base: RoleStyle, child: RoleStyle): RoleStyle {
        return RoleStyle(
            paragraph = mergeParagraph(base.paragraph, child.paragraph),
            text = mergeText(base.text, child.text),
        )
    }

    private fun mergeParagraph(base: ParagraphStyle, child: ParagraphStyle): ParagraphStyle {
        // 字段级 null 合并：child 非 null 覆盖，否则继承 base。
        return ParagraphStyle(
            alignment = child.alignment ?: base.alignment,
            firstLineIndentTwips = child.firstLineIndentTwips ?: base.firstLineIndentTwips,
            leftIndentTwips = child.leftIndentTwips ?: base.leftIndentTwips,
            rightIndentTwips = child.rightIndentTwips ?: base.rightIndentTwips,
            hangingIndentTwips = child.hangingIndentTwips ?: base.hangingIndentTwips,
            lineSpacingRule = child.lineSpacingRule ?: base.lineSpacingRule,
            lineSpacingValue = child.lineSpacingValue ?: base.lineSpacingValue,
            spaceBeforeTwips = child.spaceBeforeTwips ?: base.spaceBeforeTwips,
            spaceAfterTwips = child.spaceAfterTwips ?: base.spaceAfterTwips,
            keepWithNext = child.keepWithNext ?: base.keepWithNext,
            keepLines = child.keepLines ?: base.keepLines,
            pageBreakBefore = child.pageBreakBefore ?: base.pageBreakBefore,
        )
    }

    private fun mergeText(base: TextStyle, child: TextStyle): TextStyle {
        return TextStyle(
            eastAsiaFont = child.eastAsiaFont ?: base.eastAsiaFont,
            latinFont = child.latinFont ?: base.latinFont,
            fontSizeHalfPoints = child.fontSizeHalfPoints ?: base.fontSizeHalfPoints,
            bold = child.bold ?: base.bold,
            italic = child.italic ?: base.italic,
            underline = child.underline ?: base.underline,
            characterSpacingTwips = child.characterSpacingTwips ?: base.characterSpacingTwips,
            textColor = child.textColor ?: base.textColor,
        )
    }

    /** 校验模板森林无环、basedOn 存在。返回错误列表（空=通过）。 */
    fun validateInheritance(templates: Map<String, DocumentTemplate>): List<String> {
        val errors = ArrayList<String>()
        for ((id, t) in templates) {
            var current = t.metadata.baseTemplate
            var hops = 0
            var chainError = false
            while (current != null && !chainError) {
                if (current == id) {
                    errors.add("模板 $id 继承成环")
                    chainError = true
                } else if (hops++ > templates.size) {
                    errors.add("模板 $id 继承链过长（疑似环）")
                    chainError = true
                } else {
                    val next = templates[current]
                    if (next == null) {
                        errors.add("模板 $id 的 baseTemplate '$current' 不存在")
                        chainError = true
                    } else {
                        current = next.metadata.baseTemplate
                    }
                }
            }
        }
        return errors
    }
}
