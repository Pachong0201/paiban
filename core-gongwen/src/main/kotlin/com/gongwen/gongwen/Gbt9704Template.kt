package com.gongwen.gongwen

import com.gongwen.template.model.DocumentTemplate
import com.gongwen.template.model.FindingSeverity
import com.gongwen.template.model.PageSettings
import com.gongwen.template.model.ParagraphStyle
import com.gongwen.template.model.Role
import com.gongwen.template.model.RoleStyle
import com.gongwen.template.model.TemplateMetadata
import com.gongwen.template.model.TextStyle
import com.gongwen.template.model.ValidationRule

/**
 * GB/T 9704—2012《党政机关公文格式》内置模板。
 *
 * 版式要点（按标准，Word 无网格环境常用值）：
 * - 版心：上 3.7cm、下 3.5cm、左 2.8cm、右 2.6cm
 * - 正文：3 号仿宋_GB2312，一般每面排 22 行、每行排 28 字
 * - 标题：2 号小标宋体，可分一行或多行居中排布
 * - 一级标题：黑体；二级标题：楷体_GB2312；三四级：仿宋加粗/仿宋
 * - 行距 28~30 磅左右固定值（Word 惯例）
 *
 * 字号（half-points）：二号=44、三号=32、小标宋用于标题常见 22pt(44)。
 * 距离换算：1cm≈566.93 twips。
 */
object Gbt9704Template {

    const val TEMPLATE_ID = "gbt-9704-2012"

    private val page = PageSettings(
        pageWidthTwips = 11906,
        pageHeightTwips = 16838,
        orientation = "portrait",
        marginTopTwips = 2098,     // 3.7cm
        marginBottomTwips = 1984,  // 3.5cm
        marginLeftTwips = 1587,    // 2.8cm
        marginRightTwips = 1474,   // 2.6cm
        headerDistanceTwips = 851, // 1.5cm 页眉距
        footerDistanceTwips = 992, // 1.75cm 页脚距
        textColumns = 1,
    )

    // 正文行距按 Word 生成公文的惯例 28 磅固定 = 560 twips
    private val bodyText = TextStyle(
        eastAsiaFont = "仿宋_GB2312",
        latinFont = "Times New Roman",
        fontSizeHalfPoints = 32,   // 三号
    )

    private val bodyParagraph = ParagraphStyle(
        alignment = "both",
        firstLineIndentTwips = 640, // 2 字符（三号 16pt*2*20=640 twips 用固定值）
        lineSpacingRule = "exact",
        lineSpacingValue = 560,     // 28 磅
    )

    val instance: DocumentTemplate = DocumentTemplate(
        metadata = TemplateMetadata(
            templateId = TEMPLATE_ID,
            name = "GB/T 9704—2012 党政机关公文格式",
            version = "1.0.0",
            schemaVersion = 1,
            builtin = true,
            baseTemplate = null,
            author = "内置",
            description = "国家标准党政机关公文格式（A4 版心 3.7/3.5/2.8/2.6cm，正文三号仿宋）",
        ),
        page = page,
        roleStyles = mapOf(
            Role.TITLE to RoleStyle(
                paragraph = ParagraphStyle(
                    alignment = "center",
                    firstLineIndentTwips = 0,
                    lineSpacingRule = "exact",
                    lineSpacingValue = 680,   // 34 磅，标题常见行距
                    spaceAfterTwips = 300,
                    keepWithNext = true,
                    keepLines = true,
                ),
                text = TextStyle(
                    eastAsiaFont = "方正小标宋简体",
                    latinFont = "Times New Roman",
                    fontSizeHalfPoints = 44,  // 二号
                ),
            ),
            Role.RECIPIENT to RoleStyle(
                paragraph = ParagraphStyle(
                    alignment = "left",
                    firstLineIndentTwips = 0,
                    lineSpacingRule = "exact",
                    lineSpacingValue = 560,
                ),
                text = bodyText.copy(),
            ),
            Role.BODY to RoleStyle(paragraph = bodyParagraph, text = bodyText.copy()),
            Role.HEADING1 to RoleStyle(
                paragraph = ParagraphStyle(
                    alignment = "both",
                    firstLineIndentTwips = 640,
                    lineSpacingRule = "exact",
                    lineSpacingValue = 560,
                ),
                text = TextStyle(
                    eastAsiaFont = "黑体",
                    latinFont = "Times New Roman",
                    fontSizeHalfPoints = 32,  // 三号黑体
                ),
            ),
            Role.HEADING2 to RoleStyle(
                paragraph = ParagraphStyle(
                    alignment = "both",
                    firstLineIndentTwips = 640,
                    lineSpacingRule = "exact",
                    lineSpacingValue = 560,
                ),
                text = TextStyle(
                    eastAsiaFont = "楷体_GB2312",
                    latinFont = "Times New Roman",
                    fontSizeHalfPoints = 32,  // 三号楷体
                ),
            ),
            Role.HEADING3 to RoleStyle(
                paragraph = ParagraphStyle(
                    alignment = "both",
                    firstLineIndentTwips = 640,
                    lineSpacingRule = "exact",
                    lineSpacingValue = 560,
                ),
                text = TextStyle(
                    eastAsiaFont = "仿宋_GB2312",
                    latinFont = "Times New Roman",
                    fontSizeHalfPoints = 32,
                    bold = true,
                ),
            ),
            Role.HEADING4 to RoleStyle(
                paragraph = ParagraphStyle(
                    alignment = "both",
                    firstLineIndentTwips = 640,
                    lineSpacingRule = "exact",
                    lineSpacingValue = 560,
                ),
                text = bodyText.copy(),
            ),
            Role.QUOTATION to RoleStyle(
                paragraph = bodyParagraph.copy(),
                text = bodyText.copy(),
            ),
            Role.ATTACHMENT_DESC to RoleStyle(
                paragraph = ParagraphStyle(
                    alignment = "left",
                    firstLineIndentTwips = 640,
                    lineSpacingRule = "exact",
                    lineSpacingValue = 560,
                    spaceBeforeTwips = 200,
                ),
                text = bodyText.copy(),
            ),
            Role.SIGNATURE to RoleStyle(
                paragraph = ParagraphStyle(
                    alignment = "right",
                    rightIndentTwips = 400,  // 署名右侧空 2 字
                    lineSpacingRule = "exact",
                    lineSpacingValue = 560,
                    spaceBeforeTwips = 200,
                ),
                text = bodyText.copy(),
            ),
            Role.DATE to RoleStyle(
                paragraph = ParagraphStyle(
                    alignment = "right",
                    rightIndentTwips = 240,  // 日期与署名有相对位置惯例
                    lineSpacingRule = "exact",
                    lineSpacingValue = 560,
                ),
                text = bodyText.copy(),
            ),
            Role.ANNOTATION to RoleStyle(
                paragraph = ParagraphStyle(
                    alignment = "left",
                    firstLineIndentTwips = 0,
                    leftIndentTwips = 640,
                    lineSpacingRule = "exact",
                    lineSpacingValue = 560,
                ),
                text = bodyText.copy(),
            ),
            Role.COLOPHON to RoleStyle(
                paragraph = ParagraphStyle(
                    alignment = "left",
                    firstLineIndentTwips = 0,
                    lineSpacingRule = "auto",
                    lineSpacingValue = 360,  // 单倍
                ),
                text = TextStyle(
                    eastAsiaFont = "仿宋_GB2312",
                    latinFont = "Times New Roman",
                    fontSizeHalfPoints = 28,  // 小四号（版记常用）
                ),
            ),
            Role.SEAL_LINE to RoleStyle(
                paragraph = ParagraphStyle(
                    alignment = "right",
                    lineSpacingRule = "exact",
                    lineSpacingValue = 560,
                ),
                text = bodyText.copy(),
            ),
            Role.TABLE_TEXT to RoleStyle(
                paragraph = ParagraphStyle(alignment = "center"),
                text = TextStyle(
                    eastAsiaFont = "仿宋_GB2312",
                    latinFont = "Times New Roman",
                    fontSizeHalfPoints = 24,  // 小四 12pt
                ),
            ),
        ),
        defaultText = bodyText.copy(),
        rules = listOf(
            ValidationRule("gbt.page-size", null, "页面应为 A4（210×297mm）", FindingSeverity.ERROR),
            ValidationRule("gbt.title-font", Role.TITLE, "标题应为二号小标宋", FindingSeverity.ERROR),
            ValidationRule("gbt.title-align", Role.TITLE, "标题应居中", FindingSeverity.ERROR),
            ValidationRule("gbt.body-font", Role.BODY, "正文应为三号仿宋_GB2312", FindingSeverity.ERROR),
            ValidationRule("gbt.body-indent", Role.BODY, "正文首行缩进 2 字符", FindingSeverity.ERROR),
            ValidationRule("gbt.body-line", Role.BODY, "正文行距约 28 磅", FindingSeverity.WARNING),
            ValidationRule("gbt.h1-font", Role.HEADING1, "一级标题应为三号黑体", FindingSeverity.ERROR),
            ValidationRule("gbt.h2-font", Role.HEADING2, "二级标题应为三号楷体_GB2312", FindingSeverity.WARNING),
            ValidationRule("gbt.recipient-align", Role.RECIPIENT, "主送机关应顶格", FindingSeverity.WARNING),
        ),
    )
}
