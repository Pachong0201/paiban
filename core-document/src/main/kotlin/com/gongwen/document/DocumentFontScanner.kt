package com.gongwen.document

import com.gongwen.document.model.Paragraph

/** 文档使用字体汇总。 */
data class FontUsage(
    val fontName: String,
    val usageCount: Int,
)

/**
 * 扫描文档声明的字体（不查渲染可用性；可用性由设备侧 FontManager 判定）。
 * 规格 §22：报告"本文件使用 N 种字体，其中 M 种本机缺失"。
 */
object DocumentFontScanner {

    fun scan(paragraphs: List<Paragraph>): List<FontUsage> {
        val counts = HashMap<String, Int>()
        fun count(name: String?) {
            if (name.isNullOrBlank()) return
            counts[name] = (counts[name] ?: 0) + 1
        }
        for (p in paragraphs) {
            for (run in p.runs) {
                count(run.properties.eastAsiaFont)
                count(run.properties.asciiFont)
                count(run.properties.hAnsiFont)
                count(run.properties.csFont)
            }
        }
        return counts.entries
            .sortedByDescending { it.value }
            .map { FontUsage(it.key, it.value) }
    }

    /** 常见 Android 内置可显示 CJK 的字体集合（用于缺失提示）。 */
    val SYSTEM_CJK_FONTS: Set<String> = setOf(
        "sans-serif", "serif", "monospace",
        "Noto Sans CJK SC", "Noto Serif CJK SC", "Noto Sans SC", "Noto Serif SC",
        "Droid Sans Fallback",
    )
}
