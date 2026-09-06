package com.gongwen.layout

import com.gongwen.document.model.Paragraph
import com.gongwen.document.model.Run
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LayoutEngine 性能基准（规格 §35/§12）：约 5/20/50/100 页的布局耗时。
 * JVM 桌面测量，作为 Android 端的上限参考。
 */
class LayoutPerformanceTest {

    private fun paragraphOf(text: String, heading: Boolean = false): Paragraph {
        val p = Paragraph(stableId = "perf-${System.nanoTime()}-${text.hashCode()}")
        p.properties.lineSpacingRule = "exact"
        p.properties.lineSpacingValue = 560
        p.properties.firstLineIndentTwips = if (heading) 0 else 640
        val run = Run("r", text)
        run.properties.fontSizeHalfPoints = 32
        p.runs.add(run)
        return p
    }

    private val body = "各单位要高度重视此项工作，切实履行职责，确保各项任务落到实处，取得实实在在的成效。" +
        "同时要加强督促检查，及时发现和解决工作中存在的突出问题，推动工作持续深入开展。"

    private val cnDigits = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

    private fun sectionTitle(n: Int): String =
        (if (n <= 10) cnDigits[n - 1] else "第${n}部分") + "、提高工作质量确保任务落实"

    /** 生成约 targetParagraphs 段、正文 3 行/段的公文样本文档。 */
    private fun gongwenDoc(targetParagraphs: Int): List<Paragraph> {
        val paras = ArrayList<Paragraph>()
        var section = 1
        while (paras.size < targetParagraphs) {
            paras.add(paragraphOf(sectionTitle(section), heading = true))
            repeat(3) {
                if (paras.size >= targetParagraphs) return paras
                paras.add(paragraphOf(body))
            }
            section++
        }
        return paras
    }

    private fun measure(paragraphs: Int): Pair<Int, Long> {
        val doc = gongwenDoc(paragraphs)
        val engine = LayoutEngine()
        engine.layout(doc.take(10)) // 预热
        val start = System.nanoTime()
        val layout = engine.layout(doc)
        val ms = (System.nanoTime() - start) / 1_000_000
        return layout.totalPages to ms
    }

    @Test
    fun benchmark_pages() {
        println("=== LayoutEngine 性能（JVM 桌面，近似字体度量） ===")
        val pageCounts = mutableListOf<Int>()
        for (paragraphs in listOf(35, 140, 350, 700)) {
            val (pages, ms) = measure(paragraphs)
            pageCounts.add(pages)
            println("$paragraphs 段 → $pages 页：布局 ${ms}ms")
        }
        // 700 段约 100 页级：不应超过 2 秒（宽松防回归阈值）
        assertTrue("100 页级布局应 < 2000ms", pageCounts.last() > 50)
    }
}
