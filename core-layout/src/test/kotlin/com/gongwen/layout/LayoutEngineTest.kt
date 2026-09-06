package com.gongwen.layout

import com.gongwen.document.model.Paragraph
import com.gongwen.document.model.Run
import com.gongwen.layout.model.LayoutBox.TextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutEngineTest {

    private fun p(text: String, sizeHp: Int = 32, indent: Int? = null, lineRule: String? = null, lineVal: Int? = null): Paragraph {
        val para = Paragraph(stableId = "p-${text.hashCode()}")
        para.properties.lineSpacingRule = lineRule
        para.properties.lineSpacingValue = lineVal
        para.properties.firstLineIndentTwips = indent
        val run = Run("r", text)
        run.properties.fontSizeHalfPoints = sizeHp
        run.properties.eastAsiaFont = "仿宋_GB2312"
        para.runs.add(run)
        return para
    }

    private fun chars(n: Int): String = "测".repeat(n)

    @Test
    fun oneShortParagraph_singlePage() {
        val engine = LayoutEngine()
        val layout = engine.layout(listOf(p("这是测试正文。", lineRule = "exact", lineVal = 560)))
        assertEquals(1, layout.totalPages)
        assertTrue(layout.pages[0].contentBoxes.isNotEmpty())
    }

    @Test
    fun longDocument_paginatesByContentHeight() {
        val engine = LayoutEngine()
        // 3 号(32hp) 全角=320 twips/字，版心宽 11906-1587-1474=8845 → 每行 27 字
        // 每段 27 字 = 1 行；行高 560；版心高 12756 → 每页 22 行
        val paras = (1..50).map { p(chars(27), lineRule = "exact", lineVal = 560) }
        val layout = engine.layout(paras)
        assertEquals(3, layout.totalPages)
        layout.pages.forEachIndexed { i, page ->
            assertTrue("页 $i 行数应 ≤22，实际 ${page.contentBoxes.size}", page.contentBoxes.size <= 22)
        }
    }

    @Test
    fun longParagraph_wrapsIntoMultipleLines() {
        val engine = LayoutEngine()
        // 100 字 → 4 行
        val para = p(chars(100), lineRule = "exact", lineVal = 560)
        val layout = engine.layout(listOf(para))
        assertEquals(1, layout.totalPages)
        val lines = layout.pages[0].contentBoxes.filterIsInstance<TextLine>()
        assertTrue("100 字应换行多行", lines.size >= 4)
        // 每行字数检查：首行与后续行
        val firstLineGlyphs = lines[0].glyphs.size
        assertTrue("首行≤27 字", firstLineGlyphs <= 27)
    }

    @Test
    fun firstLineIndent_reducesFirstLineCapacity() {
        val engine = LayoutEngine()
        // 缩进 640 twips=2字，首行只能 25 字
        val para = p(chars(60), indent = 640, lineRule = "exact", lineVal = 560)
        val layout = engine.layout(listOf(para))
        val lines = layout.pages[0].contentBoxes.filterIsInstance<TextLine>()
        assertTrue(lines.isNotEmpty())
        assertEquals(25, lines[0].glyphs.size)
        assertEquals(27, lines[1].glyphs.size)
    }

    @Test
    fun pageNumbering_assigned() {
        val engine = LayoutEngine()
        val paras = (1..50).map { p(chars(27), lineRule = "exact", lineVal = 560) }
        val layout = engine.layout(paras)
        assertEquals("1", layout.pages[0].footerPageNumber)
        assertEquals("3", layout.pages[2].footerPageNumber)
    }
}
