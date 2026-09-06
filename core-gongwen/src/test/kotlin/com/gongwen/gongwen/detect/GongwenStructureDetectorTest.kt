package com.gongwen.gongwen.detect

import com.gongwen.document.model.Paragraph
import com.gongwen.document.model.SemanticRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结构识别盲测（规格 §52）：正样本 / 变体 / 高难负样本。
 * 阈值：>=0.85 自动应用；0.60–0.84 疑似；<0.60 保持正文。
 */
class GongwenStructureDetectorTest {

    private fun p(text: String, align: String? = "both", sizeHp: Int? = 32): Paragraph {
        val para = Paragraph(stableId = "t-${text.hashCode()}")
        para.properties.alignment = align
        val run = com.gongwen.document.model.Run("r", text)
        run.properties.eastAsiaFont = "仿宋_GB2312"
        run.properties.fontSizeHalfPoints = sizeHp
        para.runs.add(run)
        return para
    }

    private fun detect(paras: List<Paragraph>): List<DetectionResult> =
        GongwenStructureDetector().detect(paras)

    // ---------- 正样本 ----------

    @Test
    fun fullGongwen_notice_detectsAllRoles() {
        val paras = listOf(
            p("", null),
            p("XX市人民政府关于进一步加强安全生产工作的通知", "center", 44),
            p("", null),
            p("各县（市、区）人民政府，市政府各部门：", "left"),
            p("为深入贯彻落实党中央、国务院关于安全生产工作的决策部署，切实保障人民群众生命财产安全，现就有关事项通知如下：", "both"),
            p("一、提高政治站位", "both", 32),
            p("各级各部门要充分认识做好安全生产工作的重要性，坚持人民至上、生命至上。", "both"),
            p("二、压实工作责任", "both", 32),
            p("要严格落实安全生产责任制，层层传导压力，确保各项措施落地见效。", "both"),
            p("（一）加强组织领导", "both", 32),
            p("各部门主要负责同志要亲自抓、负总责，分管负责同志要具体抓。", "both"),
            p("1.细化任务分工。", "both", 32),
            p("要将工作任务分解到岗、落实到人。", "both"),
            p("XX市人民政府", "right"),
            p("2024年3月15日", "right"),
        )
        val r = detect(paras)
        val byText = r.associateBy { paras[it.paragraphIndex].text }

        assertEquals(SemanticRole.TITLE, byText["XX市人民政府关于进一步加强安全生产工作的通知"]?.role)
        assertEquals(SemanticRole.RECIPIENT, byText["各县（市、区）人民政府，市政府各部门："]?.role)
        assertEquals(SemanticRole.HEADING1, byText["一、提高政治站位"]?.role)
        assertEquals(SemanticRole.HEADING2, byText["（一）加强组织领导"]?.role)
        assertEquals(SemanticRole.HEADING3, byText["1.细化任务分工。"]?.role)
        assertEquals(SemanticRole.SIGNATURE, byText["XX市人民政府"]?.role)
        assertEquals(SemanticRole.DATE, byText["2024年3月15日"]?.role)
        // 正文
        val body = byText["为深入贯彻落实党中央、国务院关于安全生产工作的决策部署，切实保障人民群众生命财产安全，现就有关事项通知如下："]
        assertEquals(SemanticRole.BODY, body?.role)
    }

    @Test
    fun titleConfidence_isAutoApplicable() {
        val paras = listOf(
            p("关于开展2024年度全市安全生产大检查的通知", "center", 44),
            p("各县（市、区）：", "left"),
            p("根据工作安排，现开展全市安全生产大检查。", "both"),
        )
        val r = detect(paras)
        val title = r[0]
        assertEquals(SemanticRole.TITLE, title.role)
        assertTrue("标题置信应 >=0.85（自动应用），实际 ${title.confidence}", title.confidence >= 0.85)
    }

    // ---------- 变体 ----------

    @Test
    fun variants_irregularHeadingMarks_stillDetected() {
        // 变体：空格/半角点/顿号变异
        val variants = listOf(
            "一 . 强化组织领导",   // 空格+半角点
            "1、完善工作机制",      // 顿号
            "1．细化任务",          // 全角点
            "（一）加强协调",       // 全角括号
            "(二)统筹推进",         // 半角括号
        )
        for (v in variants) {
            val paras = listOf(
                p("某单位关于改进工作的通知", "center", 44),
                p(v, "both"),
                p("这是正文内容，用于说明相关要求并给出具体安排。", "both"),
            )
            val r = detect(paras)
            val found = r.firstOrNull { paras[it.paragraphIndex].text == v }
            assertTrue("变体应识别: $v", found != null)
            assertTrue("变体角色应为标题类: $v", found!!.role in setOf(
                SemanticRole.HEADING1, SemanticRole.HEADING2, SemanticRole.HEADING3))
        }
    }

    // ---------- 负样本 ----------

    @Test
    fun negative_inlineEnumeration_isNotHeading() {
        // 规格 §52：正文内"一、…二、…三、"列举不是标题
        val paras = listOf(
            p("关于某专项工作的实施方案", "center", 44),
            p("各县（市、区）：", "left"),
            p("项目经费主要涉及一、资金来源渠道，二、人员安排方案，三、工作时限要求等方面内容，请各地结合实际抓好落实。", "both"),
            p("（联系人：张三，联系电话：12345678）", "both"),
        )
        val r = detect(paras)
        val listing = r.first { paras[it.paragraphIndex].text.contains("主要涉及") }
        assertEquals("正文列举不能被识别为标题", SemanticRole.BODY, listing.role)
    }

    @Test
    fun negative_sentenceStartsWithDash_isNotH1() {
        // "一、"开头但不是标题的正文——长文、含逗号
        val paras = listOf(
            p("关于开展调研的通知", "center", 44),
            p("各部门：", "left"),
            p("一、各单位要高度重视此次调研工作，认真组织、周密安排，确保调研取得实效，不得敷衍了事走过场。", "both"),
            p("二、调研期间要严格执行中央八项规定精神，轻车简从，减少陪同，不搞层层接待。", "both"),
        )
        val r = detect(paras)
        val h = r.first { paras[it.paragraphIndex].text.startsWith("一、各单位") }
        // 长句列举 → 置信低于 0.85 或保持正文
        assertTrue(
            "长句列举不应高置信标题: role=${h.role} conf=${h.confidence}",
            h.confidence < 0.85 || h.role == SemanticRole.BODY
        )
    }
}
