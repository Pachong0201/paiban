package com.gongwen.document

import com.gongwen.document.model.ParagraphProperties
import com.gongwen.document.model.RunProperties
import com.gongwen.ooxml.OoxmlPackage
import com.gongwen.ooxml.PackageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 模型解析、编辑、导出 + reopen 验证的集成测试。 */
class DocumentModelTest {

    private fun openDoc(): WorkingDocument {
        val bytes = DocFixture.build()
        return DocumentService.open(bytes)
    }

    @Test
    fun parse_buildsParagraphModelWithText() {
        val doc = openDoc()
        // 5 正文段 + 表格（表内还有段落）
        assertTrue(doc.paragraphs.size == 5)
        assertEquals("关于测试的通知", doc.paragraphs[0].text)
        assertEquals("各有关单位：", doc.paragraphs[1].text)
        assertEquals("正文第一段内容。正文第二句。", doc.paragraphs[2].text)
        assertEquals("一、提高认识", doc.paragraphs[3].text)
        // 表格结构
        assertEquals(1, doc.tables.size)
        assertEquals(1, doc.tables[0].rows.size)
        assertEquals(2, doc.tables[0].rows[0].cells.size)
        assertEquals("单位", doc.tables[0].rows[0].cells[0].paragraphs[0].text)
        assertTrue(doc.tables[0].rows[0].cells[0].paragraphs[0].inTable)
    }

    @Test
    fun parse_readsRunAndParagraphProperties() {
        val doc = openDoc()
        val title = doc.paragraphs[0]
        assertEquals("center", title.properties.alignment)
        assertEquals(560, title.properties.lineSpacingValue)
        assertEquals("exact", title.properties.lineSpacingRule)
        val run = title.runs[0]
        assertEquals("方正小标宋简体", run.properties.eastAsiaFont)
        assertEquals(44, run.properties.fontSizeHalfPoints)
        // 正文段首行缩进 480 twips = 2 字符（三号）
        assertEquals(480, doc.paragraphs[2].properties.firstLineIndentTwips)
    }

    @Test
    fun setParagraphFormat_writesXmlAndMarksDirty() {
        val doc = openDoc()
        val editor = DocumentModelEditor(doc)
        val p = doc.paragraphs[3] // 一、提高认识
        val props = ParagraphProperties().apply {
            alignment = "left"
            firstLineIndentTwips = 0
        }
        editor.setParagraphFormat(p, props)

        val xml = String(doc.toXmlBytes(), Charsets.UTF_8)
        assertTrue("jc 应写入", xml.contains("w:val=\"left\""))
        // 未编辑段落文字仍存在
        assertTrue(xml.contains("关于测试的通知"))
        assertTrue(xml.contains("正文第一段内容。"))
        assertTrue(xml.contains("正文第二句。"))
    }

    @Test
    fun setParagraphText_replacesTextOnly() {
        val doc = openDoc()
        val editor = DocumentModelEditor(doc)
        val p = doc.paragraphs[2]
        editor.setParagraphText(p, "修改后的正文。")

        assertEquals("修改后的正文。", p.text)
        val xml = String(doc.toXmlBytes(), Charsets.UTF_8)
        assertTrue(xml.contains("修改后的正文。"))
        assertTrue("原文本应被替换", !xml.contains("正文第一段内容。"))
        // 其他段落不动
        assertTrue(xml.contains("关于测试的通知"))
    }

    @Test
    fun setRunFormat_boldAndFont() {
        val doc = openDoc()
        val editor = DocumentModelEditor(doc)
        val p = doc.paragraphs[2]
        val run = p.runs[0]
        val props = RunProperties().apply {
            bold = true
            eastAsiaFont = "黑体"
        }
        editor.setRunFormat(p, run, props)

        val xml = String(doc.toXmlBytes(), Charsets.UTF_8)
        assertTrue("应写 <w:b/>", xml.contains("<w:b/>") || xml.contains("<w:b>"))
        assertTrue("应写黑体", xml.contains("黑体"))
    }

    @Test
    fun export_reopenVerifies_andUnrelatedPartsUnchanged() {
        val bytes = DocFixture.build()
        val pkg = OoxmlPackage.open(bytes)
        val doc = DocumentService.open(pkg)
        val before = PackageSnapshot.capture(pkg)

        val editor = DocumentModelEditor(doc)
        editor.setParagraphText(doc.paragraphs[2], "导出验证文本。")

        val result = DocumentExporter(pkg, doc).exportAndVerify()
        assertTrue("导出应成功: ${(result as? ExportResult.Failure)?.reason}", result is ExportResult.Success)
        result as ExportResult.Success

        // reopen 后能读取修改
        val reopened = DocumentService.open(result.bytes)
        assertTrue(reopened.paragraphs.any { it.text == "导出验证文本。" })

        // 除 document.xml 外零语义变化（PackageSnapshot diff 应仅含 document.xml）
        val after = PackageSnapshot.capture(reopenedPkg(result.bytes))
        val diffs = before.diff(after).filterNot { it.contains("/word/document.xml") }
        assertTrue("无关 part 不应变化: $diffs", diffs.isEmpty())
        // 表格结构在 reopen 后保留
        assertEquals(1, reopened.tables.size)
        assertEquals("单位", reopened.tables[0].rows[0].cells[0].paragraphs[0].text)
    }

    private fun reopenedPkg(bytes: ByteArray) = OoxmlPackage.open(bytes)

    @Test
    fun open_pythonDocxRealFile_parsesWithoutCrash() {
        val f = java.io.File("testdata/corpus/roundtrip/python-docx-default.docx")
        assertTrue(f.exists())
        val doc = DocumentService.open(f.readBytes())
        // python-docx 模板包含正文段落
        assertNotNull(doc.paragraphs)
    }
}
