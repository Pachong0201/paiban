package com.gongwen.layout.model

import com.gongwen.document.model.Paragraph
import com.gongwen.document.model.ParagraphProperties

/** 页面物理尺寸（twips）。 */
data class PageGeometry(
    val widthTwips: Int = 11906,
    val heightTwips: Int = 16838,
    val marginTopTwips: Int = 2098,
    val marginBottomTwips: Int = 1984,
    val marginLeftTwips: Int = 1587,
    val marginRightTwips: Int = 1474,
    val headerDistanceTwips: Int = 851,
    val footerDistanceTwips: Int = 992,
) {
    val contentWidthTwips: Int get() = widthTwips - marginLeftTwips - marginRightTwips
    val contentHeightTwips: Int get() = heightTwips - marginTopTwips - marginBottomTwips
}

/** 布局产出的抽象盒模型 —— Preview 与 PDF 共用。 */
sealed class LayoutBox {
    abstract val x: Float  // twips，相对版心左上
    abstract val y: Float
    abstract val width: Float
    abstract val height: Float

    data class TextLine(
        override val x: Float,
        override val y: Float,
        override val width: Float,
        override val height: Float,
        val glyphs: List<Glyph>,
        val paragraphIndex: Int,
        val runIndex: Int,   // 源 run（同格式片段）
        val lineIndexInParagraph: Int,
        val paragraphFirstLine: Boolean,
    ) : LayoutBox()

    data class TableBox(
        override val x: Float,
        override val y: Float,
        override val width: Float,
        override val height: Float,
        val rows: List<RowBox>,
    ) : LayoutBox()

    data class ImageBox(
        override val x: Float,
        override val y: Float,
        override val width: Float,
        override val height: Float,
        val contentRef: String, // 图片 part 名或资源键
    ) : LayoutBox()

    data class PageBreakMarker(override val x: Float, override val y: Float, override val width: Float, override val height: Float) : LayoutBox()

    /** 表格行。 */
    data class RowBox(
        val cells: List<CellBox>,
        val height: Float,
    )

    data class CellBox(
        val lines: List<TextLine>,
        val width: Float,
        val gridSpan: Int,
    )
}

/** 一个字符的字形单元（字体名声明/实际渲染字体在渲染期决定）。 */
data class Glyph(
    val char: Char,
    val fontSizeHalfPoints: Int,
    /** 渲染宽度 twips（由 FontMetricsProvider 计算并缓存）。 */
    val advanceTwips: Float,
    val bold: Boolean,
    val eastAsia: Boolean, // 东亚全角 vs 拉丁
)

/** 页面模型：含页眉/页脚/正文盒。 */
data class PageModel(
    val pageIndex: Int,               // 0-based
    val contentBoxes: List<LayoutBox>,
    val headerLines: List<LayoutBox.TextLine> = emptyList(),
    val footerLines: List<LayoutBox.TextLine> = emptyList(),
    val footerPageNumber: String? = null,
)

/** 整篇布局结果。 */
data class DocumentLayout(
    val pages: List<PageModel>,
    val totalPages: Int,
)
