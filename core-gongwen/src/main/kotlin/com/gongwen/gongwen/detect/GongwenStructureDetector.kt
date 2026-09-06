package com.gongwen.gongwen.detect

import com.gongwen.document.model.Paragraph
import com.gongwen.document.model.ParagraphRecognition
import com.gongwen.document.model.SemanticRole

/** 段落级识别输出。 */
data class DetectionResult(
    val paragraphIndex: Int,
    val role: SemanticRole,
    val confidence: Double,
    val reasonCodes: List<String>,
)

/** 识别过程的中文/阿拉伯数字工具。 */
internal object ChineseNumbers {
    private val DIGITS = mapOf(
        '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        '十' to 10, '百' to 100, '千' to 1000,
    )

    /** 解析中文数字串；失败返回 null。支持 一/二/…/十/十一/二十/一百二十三 等。 */
    fun parse(s: String): Int? {
        if (s.isEmpty()) return null
        if (s.all { it.isDigit() }) return s.toIntOrNull()
        if (s.none { it in DIGITS }) return null
        // 逐字累加（简化：只支持<=9999 常规序数）
        var total = 0
        var section = 0
        var number = 0
        for (c in s) {
            val v = DIGITS[c] ?: return null
            when {
                v == 10 || v == 100 || v == 1000 -> {
                    if (section == 0) section = 1
                    section *= v
                }
                else -> number += v
            }
        }
        total = section + number
        if (s.length == 1 && s[0] == '十') total = 10
        if (s.startsWith("十")) total = 10 + number
        return total
    }

    /** 把 1..99 转为中文数字（标题序数用）。 */
    fun toChinese(n: Int): String {
        if (n <= 0) return n.toString()
        if (n < 10) return listOf('零', '一', '二', '三', '四', '五', '六', '七', '八', '九')[n].toString()
        if (n < 20) return "十" + if (n % 10 == 0) "" else listOf('零', '一', '二', '三', '四', '五', '六', '七', '八', '九')[n % 10].toString()
        val tens = n / 10
        val ones = n % 10
        return listOf('零', '一', '二', '三', '四', '五', '六', '七', '八', '九')[tens].toString() +
            "十" + if (ones == 0) "" else listOf('零', '一', '二', '三', '四', '五', '六', '七', '八', '九')[ones].toString()
    }
}

/** 标题模式枚举。 */
internal enum class HeadingPattern(val level: Int) {
    H1_CN(1),      // 一、二、三、…
    H2_CN(2),      // （一）（二）…
    H3_ARABIC(3),  // 1. 2.
    H4_CN_PAREN(4),// （1）（2）
}

/**
 * 公文结构检测器。
 *
 * 设计：每段独立特征评分 + 文档级阶段定位（标题区、主送、正文、落款区），
 * 输出 (role, confidence, reasonCodes)。确定性规则，无 LLM。
 *
 * 阈值：>= AUTO_APPLY 自动应用；>= SUSPECT 标记疑似；< SUSPECT 保持正文。
 */
class GongwenStructureDetector {

    /** 完整检测：输入正文段落列表，返回每段识别结果。 */
    fun detect(paragraphs: List<Paragraph>): List<DetectionResult> {
        val n = paragraphs.size
        val results = ArrayList<DetectionResult>(n)

        // 1) 文档级阶段估计
        val titleCandidates = ArrayList<Int>()     // 靠前的居中长句候选
        var firstBodyLike = -1
        var recipientIdx = -1
        var attachmentDescIdx = -1
        var signatureIdx = -1
        var dateIdx = -1
        var colophonStart = n

        // 收集主标题候选：前 10 段中居中、无标点结尾、长度 5..60、含公文模式或紧邻主送
        for ((i, p) in paragraphs.withIndex()) {
            if (i > 12) break
            val t = p.text.trim()
            if (t.isEmpty()) continue
            if (looksLikeTitle(p, t, i, paragraphs)) {
                titleCandidates.add(i)
            }
            if (looksLikeRecipient(t) && recipientIdx < 0 && i > 0) {
                recipientIdx = i
            }
        }
        // 主送机关应在标题/空段之后
        if (recipientIdx >= 0) {
            firstBodyLike = recipientIdx + 1
        }

        // 落款与日期：从尾部向上找
        for (i in n - 1 downTo 0) {
            val t = paragraphs[i].text.trim()
            if (t.isEmpty()) continue
            if (dateIdx < 0 && looksLikeDate(t)) {
                dateIdx = i
            } else if (signatureIdx < 0 && looksLikeSignature(t)) {
                signatureIdx = i
                break
            }
        }
        // 版记起点：日期后的抄送/印发段
        val tailStart = if (dateIdx >= 0) dateIdx + 1 else n
        for (i in tailStart until n) {
            val t = paragraphs[i].text.trim()
            if (t.startsWith("抄送") || t.startsWith("印发") || t.startsWith("共印")) {
                colophonStart = i
                break
            }
        }

        // 2) 逐段识别
        for (i in 0 until n) {
            val p = paragraphs[i]
            val t = p.text.trim()
            val evaluated = when {
                t.isEmpty() -> null
                i == recipientIdx -> DetectionResult(i, SemanticRole.RECIPIENT, 0.95, listOf("position", "colon-end"))
                i == dateIdx -> DetectionResult(i, SemanticRole.DATE, 0.92, listOf("date-pattern", "near-tail"))
                i == signatureIdx -> DetectionResult(i, SemanticRole.SIGNATURE, 0.9, listOf("org-suffix", "near-date"))
                i >= colophonStart -> evaluateColophon(t)
                t.startsWith("附件") || t.startsWith("附注") -> evaluateAttachment(t)
                i in titleCandidates -> evaluateTitle(p, t, i, paragraphs)
                else -> evaluateBodyOrHeading(p, t, i, paragraphs)
            }
            val result = if (evaluated == null) {
                DetectionResult(i, SemanticRole.OTHER, 0.2, listOf("empty"))
            } else {
                evaluated.copy(paragraphIndex = i)
            }
            results.add(result)
        }

        // 3) 后处理：疑似标题（高置信但需确认）排序靠前提示；确保主标题唯一
        return results
    }

    // ---- 特征函数 ----

    private fun looksLikeRecipient(t: String): Boolean {
        if (!t.endsWith("：") && !t.endsWith(":")) return false
        if (t.length < 2 || t.length > 60) return false
        // 机构名常见词
        val hasOrg = t.contains("单位") || t.contains("机关") || t.contains("办公室") ||
            t.contains("委员会") || t.contains("党委") || t.contains("政府") ||
            t.contains("局") || t.contains("部") || t.contains("厅") || t.contains("公司")
        // 全角冒号前无句号/逗号
        return hasOrg
    }

    private fun looksLikeTitle(p: Paragraph, t: String, idx: Int, all: List<Paragraph>): Boolean {
        var score = 0.0
        // 主送机关/带冒号机构称谓排除
        if (t.endsWith("：") || t.endsWith(":")) {
            if (looksLikeRecipient(t)) return false
            if (t.length <= 40) score -= 0.5
        }
        // 位置：前 6 段内
        if (idx <= 2) score += 0.15 else if (idx <= 6) score += 0.08
        // 居中
        if (p.properties.alignment == "center") score += 0.3
        // 长度适中
        if (t.length in 5..60) score += 0.2 else if (t.length in 3..80) score += 0.08
        // 不以句号/逗号结尾
        if (!t.endsWith("。") && !t.endsWith("，") && !t.endsWith(",")) score += 0.1
        // 公文标题模式
        if (Regex("关于.*的(通知|通报|报告|请示|批复|函|意见|决定|公告|通告|纪要|方案|规划|总结|讲话|规定|办法|细则|制度|函)").containsMatchIn(t)) score += 0.5
        // 字号偏大或字体为小标宋
        val fp = p.runs.firstOrNull()?.properties?.fontSizeHalfPoints
        if (fp != null && fp >= 36) score += 0.35
        if (p.runs.any { it.properties.eastAsiaFont?.contains("小标宋") == true }) score += 0.3
        // 前/后有空段
        val prevEmpty = idx == 0 || all[idx - 1].text.isBlank()
        val nextEmpty = idx == all.size - 1 || all[idx + 1].text.isBlank()
        if (prevEmpty) score += 0.05
        if (nextEmpty) score += 0.1
        // 下一段是主送机关 → 强标题证据
        if (idx + 1 < all.size && looksLikeRecipient(all[idx + 1].text.trim())) score += 0.4
        // 正文特征减分：段中有多个分句
        if (t.count { it == '，' || it == '。' } >= 2) score -= 0.4
        return score >= 0.5
    }

    private fun evaluateTitle(p: Paragraph, t: String, idx: Int, all: List<Paragraph>): DetectionResult {
        val codes = mutableListOf("position-head", "center", "no-end-punct", "title-pattern")
        var conf = 0.75
        if (p.properties.alignment == "center") { conf += 0.1; codes.add("align-center") }
        if (Regex("关于.*的(通知|通报|报告|请示|批复|函|意见|决定|公告|通告|纪要|方案|规划|总结|讲话|规定|办法|细则|制度|函)").containsMatchIn(t)) {
            conf += 0.1
        } else {
            codes.add("generic-title")
        }
        if (p.runs.firstOrNull()?.properties?.fontSizeHalfPoints?.let { it >= 36 } == true) conf += 0.05
        return DetectionResult(idx, SemanticRole.TITLE, conf.coerceAtMost(0.98), codes)
    }

    private fun headingMatch(t: String): Pair<HeadingPattern, Int>? {
        // 必须去掉首行缩进空白后匹配，且序号后是顿号/点/括号，且序号后非空
        // 容忍序号与分隔符之间的空白（"一 、" "一 . " 等不规范变体）
        // 一、 / 二、 / 十、；容忍"一." "一 . " 等点号变体（手写习惯）
        val m1 = Regex("^([零一二两三四五六七八九十百千]+)\\s*[、.．]\\s*(.+)$").find(t)
        if (m1 != null) {
            val num = ChineseNumbers.parse(m1.groupValues[1])
            if (num != null && num in 1..99 && m1.groupValues[2].isNotBlank()) {
                return HeadingPattern.H1_CN to num
            }
        }
        // （一）（二） / (一)(二) / 全角或半角括号
        val m2 = Regex("^[（(]\\s*([零一二两三四五六七八九十百千]+)\\s*[）)](.*)$").find(t)
        if (m2 != null) {
            val num = ChineseNumbers.parse(m2.groupValues[1])
            if (num != null && num in 1..99) return HeadingPattern.H2_CN to num
        }
        // 1. 2. 或 1、1．1、
        val m3 = Regex("^([0-9]{1,3})\\s*[.、．]\\s*(.+)$").find(t)
        if (m3 != null) {
            val num = m3.groupValues[1].toIntOrNull()
            if (num != null && num >= 1 && m3.groupValues[2].isNotBlank()) {
                return HeadingPattern.H3_ARABIC to num
            }
        }
        // （1）（2）
        val m4 = Regex("^[（(]\\s*([0-9]{1,3})\\s*[）)](.*)$").find(t)
        if (m4 != null) {
            val num = m4.groupValues[1].toIntOrNull()
            if (num != null && num >= 1) return HeadingPattern.H4_CN_PAREN to num
        }
        return null
    }

    /** 一级/二级标题应有前序标题或位于正文段落密集区前；否则低置信。 */
    private fun evaluateBodyOrHeading(
        p: Paragraph, t: String, idx: Int, all: List<Paragraph>,
    ): DetectionResult {
        val m = headingMatch(t) ?: return bodyParagraphScore(p, t, idx)
        val pattern = m.first
        val num = m.second
        // 特征：长度（标题短）、无句号、缩进存在、字号
        var conf = 0.6
        val codes = mutableListOf("heading-pattern-${pattern.level}")

        if (t.length <= 60) conf += 0.1 else conf -= 0.2
        if (!t.endsWith("。")) conf += 0.1 else conf -= 0.3
        if (num <= 10) conf += 0.05

        // 上下文：若前面有同级标题，置信更高；若前后都是长正文句，可能是正文列举
        val prevIsSameOrUpper = idx > 0 &&
            (headingMatch(all[idx - 1].text.trim())?.first?.level ?: 99) <= pattern.level
        val nextIsLongBody = idx + 1 < all.size && all[idx + 1].text.trim().length > 60
        val prevIsLongBody = idx > 0 && all[idx - 1].text.trim().length > 60

        if (prevIsSameOrUpper) conf += 0.15
        // 负样本抑制：前一段是长正文且此段只是列举开头 → 大幅降置信
        val inlineEnumeration = t.count { it == '，' } >= 2 || t.length > 40
        if (inlineEnumeration) {
            conf -= 0.5
            codes.add("suspected-inline-enum")
        }
        if (prevIsLongBody && pattern.level == 1 && !nextIsLongBody) {
            // 长正文后紧跟"一、xxx"且之后不是长文——可能是列举第一项；低置信待确认
            conf -= 0.15
            codes.add("after-long-body")
        }
        if (conf >= 0.6 && pattern.level == 1 && idx == 0) {
            // 文档首段是"一、"：很奇怪，降
            conf -= 0.2
            codes.add("doc-starts-with-h1")
        }
        val role = when (pattern.level) {
            1 -> SemanticRole.HEADING1
            2 -> SemanticRole.HEADING2
            3 -> SemanticRole.HEADING3
            else -> SemanticRole.HEADING4
        }
        return DetectionResult(idx, role, conf.coerceIn(0.0, 1.0), codes)
    }

    private fun bodyParagraphScore(p: Paragraph, t: String, idx: Int): DetectionResult {
        // 普通正文：置信 1.0（无特殊特征即正文）
        return DetectionResult(idx, SemanticRole.BODY, 0.98, listOf("plain-body"))
    }

    private fun evaluateAttachment(t: String): DetectionResult {
        val isAnnot = t.startsWith("附注")
        return if (isAnnot) {
            DetectionResult(0, SemanticRole.ANNOTATION, 0.9, listOf("annotation-prefix"))
        } else {
            DetectionResult(0, SemanticRole.ATTACHMENT_DESC, 0.9, listOf("attachment-prefix"))
        }
    }

    private fun evaluateColophon(t: String): DetectionResult {
        return if (t.startsWith("抄送")) {
            DetectionResult(0, SemanticRole.COLOPHON, 0.9, listOf("colophon-cc"))
        } else if (t.startsWith("印发") || t.startsWith("共印")) {
            DetectionResult(0, SemanticRole.COLOPHON, 0.9, listOf("colophon-print"))
        } else {
            DetectionResult(0, SemanticRole.COLOPHON, 0.8, listOf("colophon-tail"))
        }
    }

    private fun looksLikeDate(t: String): Boolean {
        // 阿拉伯：2024年1月1日 / 2024-01-01 / 2024.1.1
        if (Regex("^[0-9]{4}\\s*[年.\\-/.／]\\s*[0-9]{1,2}\\s*[月.\\-/.／]\\s*[0-9]{1,2}\\s*日?$").containsMatchIn(t)) return true
        // 中文数字日期
        if (Regex("^[0-9零一二三四五六七八九十]{4}年[0-9零一二三四五六七八九十]{1,2}月[0-9零一二三四五六七八九十]{1,2}日$").containsMatchIn(t)) return true
        return false
    }

    private fun looksLikeSignature(t: String): Boolean {
        // 机构署名：不以标点结尾、长度中、含单位/办公室/委员会/公司/局/厅/部 等
        if (t.endsWith("。") || t.endsWith("，") || t.endsWith("：")) return false
        if (t.length > 50) return false
        val hasOrg = t.contains("单位") || t.contains("办公室") || t.contains("委员会") ||
            t.contains("公司") || t.contains("局") || t.contains("厅") || t.contains("部") ||
            t.contains("政府") || t.contains("党委") || t.contains("处") || t.contains("中心")
        return hasOrg
    }
}
