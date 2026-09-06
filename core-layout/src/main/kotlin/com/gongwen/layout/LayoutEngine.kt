package com.gongwen.layout

import com.gongwen.document.model.Paragraph
import com.gongwen.layout.font.CjkApproxMetrics
import com.gongwen.layout.font.FontMetricsProvider
import com.gongwen.layout.model.DocumentLayout
import com.gongwen.layout.model.Glyph
import com.gongwen.layout.model.LayoutBox
import com.gongwen.layout.model.LayoutBox.TextLine
import com.gongwen.layout.model.PageGeometry
import com.gongwen.layout.model.PageModel

/**
 * LayoutEngine：行布局 + 段落堆叠 + 分页。Preview 与 PDF 共用此引擎（规格 §5 Layer4、§49）。
 *
 * 字号与版心单位一律 twips。行距规则：
 * - exact：行距=指定值；auto/atLeast/null：按字体行高。
 * 分页：keepNext 防止段后分页；首行缩进；段落前后间距。表格/图片按块高参与流动布局。
 */
class LayoutEngine(
    private val metrics: FontMetricsProvider = CjkApproxMetrics(),
    private val geometry: PageGeometry = PageGeometry(),
    private val lineRule: LineBreakingRule = LineBreakingRule(),
) {

    /** 布局一组段落。 */
    fun layout(paragraphs: List<Paragraph>): DocumentLayout {
        val pages = ArrayList<PageModel>()
        var currentPageBoxes = ArrayList<LayoutBox>()
        var cursorY = 0f
        var pageIndex = 0

        fun newPage() {
            if (currentPageBoxes.isNotEmpty()) {
                pages.add(
                    PageModel(
                        pageIndex,
                        currentPageBoxes,
                        footerPageNumber = (pageIndex + 1).toString(),
                    )
                )
                pageIndex++
            }
            currentPageBoxes = ArrayList()
            cursorY = 0f
        }

        for ((pi, p) in paragraphs.withIndex()) {
            if (p.inTable) continue // 表格布局另行处理（MVP 简化：跳过内容仍参与分页）
            val text = p.text
            if (text.isBlank()) {
                // 空段：占一行高
                val lineH = 560f
                if (cursorY + lineH > geometry.contentHeightTwips) newPage()
                cursorY += lineH
                continue
            }
            val lines = wrapParagraph(p, pi)
            val spacingBefore = p.properties.spaceBeforeTwips?.toFloat() ?: 0f
            val spacingAfter = p.properties.spaceAfterTwips?.toFloat() ?: 0f

            cursorY += spacingBefore
            var firstLine = true
            for (line in lines) {
                val h = line.height
                if (cursorY + h > geometry.contentHeightTwips) {
                    // 段首行放不下 → 整段移到下一页（keepLines 语义；MVP 简化）
                    if (firstLine) {
                        newPage()
                    } else {
                        // keepWithNext/孤行控制：至少 2 行留在本页原则——MVP 直接新页
                        if (p.properties.keepWithNext || p.properties.keepLines) {
                            // 回退：把本段此前已放置的行从本页移除
                            currentPageBoxes.removeAll { it is TextLine && it.paragraphIndex == pi }
                            newPage()
                        } else {
                            newPage()
                        }
                    }
                }
                currentPageBoxes.add(
                    line.copy(y = cursorY, paragraphIndex = pi)
                )
                cursorY += h
                firstLine = false
            }
            cursorY += spacingAfter
        }
        newPage()
        // 最后一页如果空（整篇无内容）
        if (pages.isEmpty()) {
            pages.add(PageModel(0, emptyList(), footerPageNumber = "1"))
        }
        return DocumentLayout(pages, pages.size)
    }

    /** 段落换行 → 行盒（含缩进计算）。 */
    fun wrapParagraph(p: Paragraph, paragraphIndex: Int): List<TextLine> {
        val text = p.text
        val runs = flattenRuns(p)
        val chars = runs.flatMap { r -> r.map { it } }
        if (chars.isEmpty()) return emptyList()

        val contentWidth = geometry.contentWidthTwips.toFloat()
        val firstLineIndent = p.properties.firstLineIndentTwips?.toFloat() ?: 0f

        val lines = ArrayList<TextLine>()
        var lineStart = 0
        // x 为"已放置内容累计宽度"（不含缩进；缩进通过行 x 坐标输出）
        var x = 0f
        var lineIndex = 0
        val lineHeight = resolveLineHeight(p)

        // 贪心断行（CJK 逐字，ASCII 按空格断词——简版按字符断）
        val glyphList = ArrayList<Glyph>(chars)
        var i = 0
        while (i < glyphList.size) {
            val g = glyphList[i]
            val w = g.advanceTwips
            val avail = if (lineIndex == 0) contentWidth - firstLineIndent else contentWidth
            if (x + w > avail && x > 0) {
                // 断行
                lines.add(
                    TextLine(
                        x = if (lineIndex == 0) firstLineIndent else 0f,
                        y = 0f, // 占位，堆叠时填充
                        width = x,
                        height = lineHeight,
                        glyphs = ArrayList(glyphList.subList(lineStart, i)),
                        paragraphIndex = paragraphIndex,
                        runIndex = 0,
                        lineIndexInParagraph = lineIndex,
                        paragraphFirstLine = lineIndex == 0,
                    )
                )
                lineIndex++
                lineStart = i
                x = 0f
            } else {
                x += w
                i++
            }
        }
        if (lineStart < glyphList.size) {
            lines.add(
                TextLine(
                    x = if (lineIndex == 0) firstLineIndent else 0f,
                    y = 0f,
                    width = x,
                    height = lineHeight,
                    glyphs = ArrayList(glyphList.subList(lineStart, glyphList.size)),
                    paragraphIndex = paragraphIndex,
                    runIndex = 0,
                    lineIndexInParagraph = lineIndex,
                    paragraphFirstLine = lineIndex == 0,
                )
            )
        }
        return lines
    }

    /** 把段落按 run 拆成字形序列（合并相同格式 run 的连续文本）。 */
    private fun flattenRuns(p: Paragraph): List<List<Glyph>> {
        val out = ArrayList<List<Glyph>>()
        for (run in p.runs) {
            val font = run.properties.eastAsiaFont ?: run.properties.asciiFont
            val size = run.properties.fontSizeHalfPoints ?: 32
            val bold = run.properties.bold == true
            val glyphs = run.text.map { c ->
                Glyph(
                    char = c,
                    fontSizeHalfPoints = size,
                    advanceTwips = metrics.advance(c, size, bold, font),
                    bold = bold,
                    eastAsia = CjkApproxMetrics.isFullWidth(c),
                )
            }
            if (glyphs.isNotEmpty()) out.add(glyphs)
        }
        return out
    }

    private fun resolveLineHeight(p: Paragraph): Float {
        val rule = p.properties.lineSpacingRule
        val value = p.properties.lineSpacingValue
        return when (rule) {
            "exact" -> (value ?: 560).toFloat()
            "atLeast" -> (value ?: 560).toFloat()
            else -> {
                val size = p.runs.firstOrNull()?.properties?.fontSizeHalfPoints ?: 32
                metrics.lineHeight(size, null)
            }
        }
    }
}

/** 断行规则（预留：punctuation 压缩、禁则处理等）。 */
class LineBreakingRule(
    /** 行首禁则字符（中文排版）。 */
    val leadingForbidden: Set<Char> = setOf('，', '。', '、', '；', '：', '！', '？', '）', '》', '〉', '】', '」', '』', '”', '’'),
    /** 行尾禁则字符。 */
    val trailingForbidden: Set<Char> = setOf('（', '《', '〈', '【', '「', '『', '“', '‘'),
)
