package com.gongwen.gongwen

import com.gongwen.document.DocumentModelEditor
import com.gongwen.document.DocumentService
import com.gongwen.document.model.FormatLockScope
import com.gongwen.gongwen.validate.DocumentValidator
import com.gongwen.template.model.FindingSeverity
import com.gongwen.template.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 一键排版 + 验证器的集成测试（Gate E/F 的桌面侧）。 */
class OneClickFormattingTest {

    private val templates = mapOf(Gbt9704Template.TEMPLATE_ID to Gbt9704Template.instance)

    /** 构造一份"格式不规范但结构清晰"的通知（无格式→全默认正文）。 */
    private fun buildUnformattedDoc(): ByteArray {
        val paras = listOf(
            """<w:p><w:r><w:t>关于开展安全生产检查的通知</w:t></w:r></w:p>""",
            """<w:p><w:r><w:t>各乡镇人民政府，县直各部门：</w:t></w:r></w:p>""",
            """<w:p><w:r><w:t>为切实做好安全生产工作，现就有关事项通知如下。</w:t></w:r></w:p>""",
            """<w:p><w:r><w:t>一、加强组织领导</w:t></w:r></w:p>""",
            """<w:p><w:r><w:t>各乡镇要成立专门工作机构，明确责任分工。</w:t></w:r></w:p>""",
            """<w:p><w:r><w:t>二、严格督查考核</w:t></w:r></w:p>""",
            """<w:p><w:r><w:t>县安委办将适时组织督查。</w:t></w:r></w:p>""",
            """<w:p><w:r><w:t>XX县安全生产委员会</w:t></w:r></w:p>""",
            """<w:p><w:r><w:t>2024年6月30日</w:t></w:r></w:p>""",
        )
        val body = paras.joinToString("") + """<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/></w:sectPr>"""
        val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body</w:body></w:document>"""
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            fun put(name: String, s: String) {
                zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(s.toByteArray()); zip.closeEntry()
            }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            put("word/document.xml", docXml)
        }
        return out.toByteArray()
    }

    @Test
    fun oneClickFormat_appliesGbtStyles_andDetectsRoles() {
        val doc = DocumentService.open(buildUnformattedDoc())
        val formatter = OneClickGongwenFormatter(templates)

        // 第一步：识别
        val suspects = formatter.detectAndAnnotate(doc)
        // 第二步：应用
        val result = formatter.format(doc)

        assertTrue("至少应用 5 段（标题/主送/正文/2标题/署名/日期…）", result.appliedCount >= 7)

        val xml = String(doc.toXmlBytes(), Charsets.UTF_8)
        // 标题段应居中且小标宋
        val titleIdx = doc.paragraphs.indexOfFirst { it.text.contains("安全生产检查的通知") }
        val title = doc.paragraphs[titleIdx]
        assertEquals(Role.TITLE.name, "TITLE")
        assertEquals("center", title.properties.alignment)
        assertEquals(44, title.runs[0].properties.fontSizeHalfPoints)
        assertEquals("方正小标宋简体", title.runs[0].properties.eastAsiaFont)

        // 一级标题黑体
        val h1 = doc.paragraphs.first { it.text.startsWith("一、") }
        assertEquals("黑体", h1.runs[0].properties.eastAsiaFont)
        assertTrue(h1.runs[0].properties.fontSizeHalfPoints == 32)

        // 正文仿宋 缩进
        val body = doc.paragraphs.first { it.text.startsWith("为切实做好") }
        assertEquals("仿宋_GB2312", body.runs[0].properties.eastAsiaFont)
        assertEquals(640, body.properties.firstLineIndentTwips)

        // 署名右对齐 / 日期
        val sig = doc.paragraphs.first { it.text.contains("安全生产委员会") }
        assertEquals("right", sig.properties.alignment)

        // 落款日期
        val date = doc.paragraphs.first { it.text.contains("2024年6月30日") }
        assertTrue(date.effectiveRole.name == "DATE")
    }

    @Test
    fun formatLock_skipsLockedParagraph() {
        val doc = DocumentService.open(buildUnformattedDoc())
        val formatter = OneClickGongwenFormatter(templates)
        formatter.detectAndAnnotate(doc)

        // 锁定标题段：保持其原始非居中格式
        val title = doc.paragraphs.first { it.text.contains("安全生产检查的通知") }
        title.formatLock = FormatLockScope.PARAGRAPH
        val originalAlign = title.properties.alignment

        val result = formatter.format(doc)
        assertTrue(result.skippedLocked >= 1)

        // 锁定段 XML 未被改动（仍是原值，null 对齐）
        assertEquals(originalAlign, title.properties.alignment)
        val xml = String(doc.toXmlBytes(), Charsets.UTF_8)
        assertTrue(xml.contains("关于开展安全生产检查的通知"))
    }

    @Test
    fun validator_reportsFindingsAgainstEffectiveTemplate() {
        val doc = DocumentService.open(buildUnformattedDoc())
        val formatter = OneClickGongwenFormatter(templates)
        formatter.detectAndAnnotate(doc)

        // 未排版 → 验证器应报大量格式问题
        val v = DocumentValidator(templates, Gbt9704Template.TEMPLATE_ID)
        val report = v.validate(doc.paragraphs)
        assertTrue("未排版文档应发现错误", report.errorCount > 0)

        // 排版后 → 问题大幅减少
        formatter.format(doc)
        val report2 = v.validate(doc.paragraphs)
        assertTrue(
            "排版后错误应明显减少: before=${report.errorCount} after=${report2.errorCount}",
            report2.errorCount < report.errorCount
        )
    }
}
