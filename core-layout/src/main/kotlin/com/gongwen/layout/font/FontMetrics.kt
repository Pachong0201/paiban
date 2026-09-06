package com.gongwen.layout.font

/** 字体度量提供者：按声明字体/字号返回字符宽度。Preview/PDF/测试共用接口。 */
interface FontMetricsProvider {
    /** 返回该字符在指定字号（half-points）下的宽度，单位 twips。 */
    fun advance(char: Char, fontSizeHalfPoints: Int, bold: Boolean, fontName: String?): Float

    /** 行高（twips）。exact 行距规则会覆盖。 */
    fun lineHeight(fontSizeHalfPoints: Int, fontName: String?): Float
}

/**
 * 确定性 CJK 度量（脱离真实字体的测试/回退实现）：
 * - 全角字符（CJK 标点、汉字）= 1em
 * - ASCII = 0.5em
 * - 行高默认 1.3 * font size（exact 行距覆盖时不使用）
 * 真实 Android/PDF 渲染端各自注入 Paint/font 实现。
 */
class CjkApproxMetrics : FontMetricsProvider {

    override fun advance(char: Char, fontSizeHalfPoints: Int, bold: Boolean, fontName: String?): Float {
        val em = fontSizeHalfPoints / 2f * 20f / 20f // half-points → points → 需 twips
        // 字号 half-points → point = /2 → twips = *20
        val emTwips = fontSizeHalfPoints * 10f
        return if (isFullWidth(char)) emTwips else emTwips / 2f
    }

    override fun lineHeight(fontSizeHalfPoints: Int, fontName: String?): Float =
        fontSizeHalfPoints * 10f * 1.3f

    companion object {
        /** 全角字符判定。 */
        fun isFullWidth(c: Char): Boolean {
            if (c.code in 0x1100..0x115F) return true // Hangul Jamo
            if (c.code in 0x2E80..0xA4CF) return true // CJK Radicals..Yi
            if (c.code in 0xAC00..0xD7A3) return true // Hangul
            if (c.code in 0xF900..0xFAFF) return true
            if (c.code in 0xFE30..0xFE4F) return true // CJK 兼容
            if (c.code in 0xFF00..0xFF60) return true // 全角
            if (c.code in 0xFFE0..0xFFE6) return true
            if (c.code == 0x3000) return true        // 全角空格
            if (c.code in 0x20000..0x2FFFD) return true
            if (c.code in 0x30000..0x3FFFD) return true
            // 常见中文标点
            if (c in "，。、；：？！“”‘’（）《》〈〉【】—…·～￥％") return true
            return false
        }
    }
}
