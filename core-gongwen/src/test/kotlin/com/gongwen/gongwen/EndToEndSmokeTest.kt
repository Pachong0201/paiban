package com.gongwen.gongwen

import com.gongwen.document.DocumentExporter
import com.gongwen.document.DocumentService
import com.gongwen.ooxml.OoxmlPackage
import com.gongwen.ooxml.PackageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 端到端 smoke：真实公文 → 识别 → 一键排版 → DOCX 导出（reopen 验证）→ 重开格式断言。
 * 布局分页由 core-layout 测试独立覆盖（模块分层约束）。
 */
class EndToEndSmokeTest {

    private fun buildRealisticGongwen(): ByteArray {
        val paras = listOf(
            """<w:p><w:pPr><w:jc w:val="left"/></w:pPr><w:r><w:t>XX省人民政府关于进一步加强安全生产工作的实施意见</w:t></w:r></w:p>""",
            """<w:p><w:pPr><w:jc w:val="left"/></w:pPr><w:r><w:t>各市、县（市、区）人民政府，省政府直属各单位：</w:t></w:r></w:p>""",
            """<w:p><w:pPr><w:jc w:val="left"/></w:pPr><w:r><w:t>为深入贯彻落实党中央、国务院关于安全生产工作的决策部署，进一步加强全省安全生产工作，切实保障人民群众生命财产安全，现提出如下实施意见。</w:t></w:r></w:p>""",
            """<w:p><w:pPr><w:jc w:val="left"/></w:pPr><w:r><w:t>一、总体要求</w:t></w:r></w:p>""",
            """<w:p><w:pPr><w:jc w:val="left"/></w:pPr><w:r><w:t>坚持人民至上、生命至上，牢固树立安全发展理念，压紧压实安全生产责任，坚决防范和遏制重特大事故发生。</w:t></w:r></w:p>""",
            """<w:p><w:pPr><w:jc w:val="left"/></w:pPr><w:r><w:t>（一）强化组织领导</w:t></w:r></w:p>""",
            """<w:p><w:pPr><w:jc w:val="left"/></w:pPr><w:r><w:t>各级人民政府要建立健全安全生产领导机制，主要负责人亲自抓、负总责。</w:t></w:r></w:p>""",
            """<w:p><w:pPr><w:jc w:val="left"/></w:pPr><w:r><w:t>1.完善考核体系。</w:t></w:r></w:p>""",
            """<w:p><w:pPr><w:jc w:val="left"/></w:pPr><w:r><w:t>将安全生产纳入高质量发展考核体系，实行一票否决。</w:t></w:r></w:p>""",
            """<w:p><w:pPr><w:jc w:val="left"/></w:pPr><w:r><w:t>XX省人民政府</w:t></w:r></w:p>""",
            """<w:p><w:pPr><w:jc w:val="left"/></w:pPr><w:r><w:t>2024年5月20日</w:t></w:r></w:p>""",
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
    fun fullPipeline_smoke() {
        // 1. 打开
        val original = buildRealisticGongwen()
        val pkg = OoxmlPackage.open(original)
        val before = PackageSnapshot.capture(pkg)
        val doc = DocumentService.open(pkg)

        // 2. 一键排版（识别+应用）
        val templates = mapOf(Gbt9704Template.TEMPLATE_ID to Gbt9704Template.instance)
        val formatter = OneClickGongwenFormatter(templates)
        val suspects = formatter.detectAndAnnotate(doc)
        val result = formatter.format(doc)
        assertTrue("排版应应用到主体段落", result.appliedCount >= 9)

        // 3. 导出 + reopen 验证
        val export = DocumentExporter(pkg, doc).exportAndVerify()
        assertTrue("导出应成功", export is com.gongwen.document.ExportResult.Success)
        export as com.gongwen.document.ExportResult.Success

        // 4. 重开
        val reopenedPkg = OoxmlPackage.open(export.bytes)
        val after = PackageSnapshot.capture(reopenedPkg)
        val diffs = before.diff(after).filterNot { it.contains("/word/document.xml") }
        assertTrue("导出仅允许 document.xml 变化: $diffs", diffs.isEmpty())
        val reopened = DocumentService.open(export.bytes)

        // 5. 格式断言
        val title = reopened.paragraphs.first { it.text.contains("实施意见") }
        assertEquals("center", title.properties.alignment)
        assertEquals("方正小标宋简体", title.runs[0].properties.eastAsiaFont)
        assertEquals(44, title.runs[0].properties.fontSizeHalfPoints)
        val h1 = reopened.paragraphs.first { it.text.startsWith("一、") }
        assertEquals("黑体", h1.runs[0].properties.eastAsiaFont)
        val body = reopened.paragraphs.first { it.text.startsWith("为深入贯彻落实") }
        assertEquals(640, body.properties.firstLineIndentTwips)
        assertEquals("仿宋_GB2312", body.runs[0].properties.eastAsiaFont)

        println("端到端 smoke OK: ${suspects.size} suspects, ${result.appliedCount} applied")
    }
}
