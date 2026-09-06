package com.gongwen.template.model

/** 模板 schema 版本。 */
const val TEMPLATE_SCHEMA_VERSION = 1

/** 排版角色（与 core-document 的 SemanticRole 对齐，但作为模板的样式槽）。 */
enum class Role {
    TITLE,
    RECIPIENT,
    BODY,
    HEADING1,
    HEADING2,
    HEADING3,
    HEADING4,
    QUOTATION,
    ATTACHMENT_DESC,
    SIGNATURE,
    DATE,
    ANNOTATION,
    FOOTER,
    COLOPHON,
    TABLE_TEXT,
    SEAL_LINE,
    PAGE_NUMBER,
    RED_LINE,
}

/** 页面设置（单位 twips；1cm≈566.93twips，1 磅=20twips）。 */
data class PageSettings(
    val pageWidthTwips: Int = 11906,    // A4 210mm
    val pageHeightTwips: Int = 16838,   // A4 297mm
    val orientation: String = "portrait",
    val marginTopTwips: Int = 1440,
    val marginBottomTwips: Int = 1440,
    val marginLeftTwips: Int = 1654,    // 2.8cm 约
    val marginRightTwips: Int = 1440,
    val headerDistanceTwips: Int = 851,
    val footerDistanceTwips: Int = 992,
    val textColumns: Int = 1,
)

/** 段落样式。 */
data class ParagraphStyle(
    val alignment: String? = null,          // left/center/right/both/justify
    val firstLineIndentTwips: Int? = null,
    val leftIndentTwips: Int? = null,
    val rightIndentTwips: Int? = null,
    val hangingIndentTwips: Int? = null,
    val lineSpacingRule: String? = null,    // auto/exact/atLeast
    val lineSpacingValue: Int? = null,
    val spaceBeforeTwips: Int? = null,
    val spaceAfterTwips: Int? = null,
    val keepWithNext: Boolean? = null,
    val keepLines: Boolean? = null,
    val pageBreakBefore: Boolean? = null,
)

/** 文字样式（声明字体，非渲染字体）。 */
data class TextStyle(
    val eastAsiaFont: String? = null,
    val latinFont: String? = null,
    val fontSizeHalfPoints: Int? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: String? = null,
    val characterSpacingTwips: Int? = null,
    val textColor: String? = null,
)

/** 一个角色完整样式（段落 + 文字）。 */
data class RoleStyle(
    val paragraph: ParagraphStyle = ParagraphStyle(),
    val text: TextStyle = TextStyle(),
)

/** 检查发现严重度。 */
enum class FindingSeverity { ERROR, WARNING, INFO }

/** 一条检查规则。 */
data class ValidationRule(
    val id: String,
    val role: Role? = null,
    val description: String,
    val severity: FindingSeverity = FindingSeverity.ERROR,
)

/** 模板元数据。 */
data class TemplateMetadata(
    val templateId: String,
    val name: String,
    val version: String = "1.0.0",
    val schemaVersion: Int = TEMPLATE_SCHEMA_VERSION,
    val builtin: Boolean = false,
    val baseTemplate: String? = null,
    val author: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val description: String? = null,
)

/** 完整文档模板。 */
data class DocumentTemplate(
    val metadata: TemplateMetadata,
    val page: PageSettings = PageSettings(),
    val roleStyles: Map<Role, RoleStyle> = emptyMap(),
    /** 文档级默认（如正文默认行距、基准字体）。 */
    val defaultText: TextStyle = TextStyle(),
    val rules: List<ValidationRule> = emptyList(),
) {
    /** 取某角色样式：未显式定义返回空 RoleStyle（由级联上层补默认）。 */
    fun styleFor(role: Role): RoleStyle = roleStyles[role] ?: RoleStyle()
}
