package com.gongwen.document.model

/** 文档结构角色（公文体裁无关的通用语义槽；公文专用角色在 core-gongwen 层）。 */
enum class SemanticRole {
    BODY,              // 普通正文
    TITLE,             // 主标题
    HEADING1,
    HEADING2,
    HEADING3,
    HEADING4,
    RECIPIENT,         // 主送机关
    ATTACHMENT_DESC,   // 附件说明
    SIGNATURE,         // 发文机关署名
    DATE,              // 成文日期
    ANNOTATION,        // 附注
    COLOPHON,          // 版记（抄送/印发机关等）
    QUOTATION,         // 引文
    OTHER,
    UNKNOWN,
}

/** 识别置信度：>= AUTO_APPLY 自动应用；>= SUSPECT 标记疑似；低于 SUSPECT 保持正文。 */
object DetectionConfidence {
    const val AUTO_APPLY = 0.85
    const val SUSPECT = 0.60
}

/** 段落识别结果（预测角色 + 置信度 + 理由码）。 */
data class ParagraphRecognition(
    val role: SemanticRole,
    val confidence: Double,
    val reasonCodes: List<String> = emptyList(),
)

/** 格式锁定范围。MVP 实现 formatLock（排版时保留原格式）。 */
enum class FormatLockScope {
    NONE,
    PARAGRAPH,   // 段级
    RUN,         // run 级
    FULL,        // 整段含字符
}

/** 段落模型。*/
class Paragraph(
    /** 稳定 ID：解析期分配，跨编辑保存。 */
    val stableId: String,
    /** OOXML 锚点：指向 document.xml DOM 中的 w:p 元素。 */
    internal var ooxmlAnchor: Any? = null,
    /** 段落属性（对齐/缩进/行距/字体…）由解析填充。 */
    var properties: ParagraphProperties = ParagraphProperties(),
    /** 子 run 列表（文本、格式），与 OOXML runs 对应。 */
    val runs: MutableList<Run> = mutableListOf(),
    /** 语义角色与识别信息。 */
    var recognition: ParagraphRecognition = ParagraphRecognition(SemanticRole.UNKNOWN, 0.0),
    /** 用户显式修正的角色（覆盖自动识别）。 */
    var userRole: SemanticRole? = null,
    /** 格式锁定。 */
    var formatLock: FormatLockScope = FormatLockScope.NONE,
    /** 该段是否位于表格单元格内。 */
    var inTable: Boolean = false,
) {
    /** 所属 body 顶层块（编辑脏标记用），顶层段即自身块，表格段为其表格块。 */
    internal var blockAnchor: Any? = null

    val effectiveRole: SemanticRole get() = userRole ?: recognition.role

    val text: String
        get() = runs.joinToString("") { it.text }

    val isHeading: Boolean
        get() = effectiveRole in setOf(
            SemanticRole.HEADING1, SemanticRole.HEADING2,
            SemanticRole.HEADING3, SemanticRole.HEADING4,
        )
}

/** Run（同格式文本片段）。 */
class Run(
    val stableId: String,
    var text: String,
    var properties: RunProperties = RunProperties(),
    internal var ooxmlAnchor: Any? = null,
)

/** 段落属性（与 OOXML w:pPr 对应）。 */
class ParagraphProperties {
    var alignment: String? = null          // left/center/right/both/justify
    var firstLineIndentTwips: Int? = null  // w:ind w:firstLine
    var leftIndentTwips: Int? = null
    var rightIndentTwips: Int? = null
    var hangingIndentTwips: Int? = null    // w:ind w:hanging
    var lineSpacingRule: String? = null    // auto/exact/atLeast
    var lineSpacingValue: Int? = null      // 行距值：auto=240ths，exact/atLeast=twips
    var spaceBeforeTwips: Int? = null
    var spaceAfterTwips: Int? = null
    var keepWithNext: Boolean = false
    var keepLines: Boolean = false
    var pageBreakBefore: Boolean = false
    var outlineLevel: Int? = null          // w:outlineLvl
    /** OOXML 引用样式 id（如 Heading1、正文）。 */
    var styleId: String? = null
    /** 该段 pPr 中程序不认识但存在的子节点数（提示用）。 */
    var unknownChildren: Int = 0

    fun copyFrom(other: ParagraphProperties) {
        alignment = other.alignment
        firstLineIndentTwips = other.firstLineIndentTwips
        leftIndentTwips = other.leftIndentTwips
        rightIndentTwips = other.rightIndentTwips
        hangingIndentTwips = other.hangingIndentTwips
        lineSpacingRule = other.lineSpacingRule
        lineSpacingValue = other.lineSpacingValue
        spaceBeforeTwips = other.spaceBeforeTwips
        spaceAfterTwips = other.spaceAfterTwips
        keepWithNext = other.keepWithNext
        keepLines = other.keepLines
        pageBreakBefore = other.pageBreakBefore
        outlineLevel = other.outlineLevel
        styleId = other.styleId
        unknownChildren = other.unknownChildren
    }
}

/** Run 属性（与 OOXML w:rPr 对应）。 */
class RunProperties {
    var eastAsiaFont: String? = null       // w:rFonts w:eastAsia
    var asciiFont: String? = null          // w:rFonts w:ascii
    var hAnsiFont: String? = null
    var csFont: String? = null
    var fontSizeHalfPoints: Int? = null    // w:sz（half-points）
    var fontSizeCsHalfPoints: Int? = null  // w:szCs
    var bold: Boolean? = null
    var italic: Boolean? = null
    var underline: String? = null          // none/single/…
    var strike: Boolean? = null
    var color: String? = null              // 十六进制 RRGGBB
    var characterSpacingTwips: Int? = null // w:spacing（twentieths of a point）
    var highlight: String? = null
    /** rPr 中未知子节点数。 */
    var unknownChildren: Int = 0

    fun copyFrom(other: RunProperties) {
        eastAsiaFont = other.eastAsiaFont
        asciiFont = other.asciiFont
        hAnsiFont = other.hAnsiFont
        csFont = other.csFont
        fontSizeHalfPoints = other.fontSizeHalfPoints
        fontSizeCsHalfPoints = other.fontSizeCsHalfPoints
        bold = other.bold
        italic = other.italic
        underline = other.underline
        strike = other.strike
        color = other.color
        characterSpacingTwips = other.characterSpacingTwips
        highlight = other.highlight
        unknownChildren = other.unknownChildren
    }
}

/** 表格模型（结构级；单元格内的段落复用 Paragraph）。 */
class Table(
    val stableId: String,
    internal var ooxmlAnchor: Any? = null,
    val rows: MutableList<TableRow> = mutableListOf(),
) {
    /** 所属 body 顶层块（编辑脏标记用）。 */
    internal var blockAnchor: Any? = null

    val columnCount: Int
        get() = rows.maxOfOrNull { it.cells.size } ?: 0
}

class TableRow(
    val stableId: String,
    val cells: MutableList<TableCell> = mutableListOf(),
)

class TableCell(
    val stableId: String,
    val paragraphs: MutableList<Paragraph> = mutableListOf(),
    var gridSpan: Int = 1,
    var vMerge: String? = null, // none/restart/continue
    internal var ooxmlAnchor: Any? = null,
)
