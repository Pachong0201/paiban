package com.gongwen.document

import com.gongwen.document.xml.DocumentXmlPart
import com.gongwen.ooxml.CorruptPackageException
import com.gongwen.ooxml.OoxmlException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 异常与畸形输入测试（规格 §58）：禁止 crash，禁止产出损坏文件。 */
class AbnormalDocumentTest {

    @Test
    fun corruptedXml_throwsMeaningfulError() {
        // 文档声明 UTF-8 但包含非法字节（截断的多字节序列）或不成对标签
        val bad = """<?xml version="1.0" encoding="UTF-8"?><w:document><w:body><w:p><w:r><w:t>未闭合"""
        try {
            DocumentXmlPart.parse(bad.toByteArray())
            fail("坏 XML 应被拒绝")
        } catch (e: Exception) {
            // 任何明确异常都可接受，只要不是进程崩溃
            assertTrue(e.message?.isNotEmpty() == true)
        }
    }

    @Test
    fun emptyBodyDocument_parsesToZeroParagraphs() {
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body><w:sectPr><w:pgSz w:w="11906" w:h="16838"/></w:sectPr></w:body>
</w:document>"""
        val part = DocumentXmlPart.parse(xml.toByteArray())
        // 仅含 sectPr（1 个顶层块，非段落）
        assertEquals(1, part.blockCount)
        assertEquals("sectPr", part.topBlocks[0].elementName)
        // 序列化后可重开（不崩溃、可往返）
        val bytes = part.toByteArray()
        val reparsed = DocumentXmlPart.parse(bytes)
        assertEquals(1, reparsed.blockCount)
    }

    @Test
    fun onlyTablesDocument_parses() {
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body><w:tbl><w:tblPr><w:tblW w:w="5000"/></w:tblPr><w:tr><w:tc><w:p><w:r><w:t>表格文字</w:t></w:r></w:p></w:tc></w:tr></w:tbl><w:sectPr/></w:body>
</w:document>"""
        val part = DocumentXmlPart.parse(xml.toByteArray())
        val parsed = com.gongwen.document.xml.DocumentParser(part).parse()
        assertEquals(1, parsed.tables.size)
        assertEquals(0, parsed.paragraphs.size)
        assertEquals("表格文字", parsed.tables[0].rows[0].cells[0].paragraphs[0].text)
    }

    @Test
    fun truncatedZip_rejectedCleanly() {
        val full = DocFixture.build()
        // 截断到一半
        val truncated = full.copyOf(full.size / 2)
        try {
            com.gongwen.ooxml.OoxmlPackage.open(truncated)
            fail("截断文件应被拒绝")
        } catch (e: OoxmlException) {
            assertTrue(e is CorruptPackageException)
        }
    }

    @Test
    fun randomBytes_rejectedAsNotDocx() {
        val random = ByteArray(4096) { (it * 31).toByte() }
        try {
            com.gongwen.ooxml.OoxmlPackage.open(random)
            fail("非 zip 应被拒绝")
        } catch (e: OoxmlException) {
            assertTrue(e is CorruptPackageException)
        }
    }

    @Test
    fun noStylesXml_stillOpens() {
        // 移除 styles.xml 引用与 part（真实世界有畸形文件缺 styles）
        val base = DocFixture.build()
        val pkg = com.gongwen.ooxml.OoxmlPackage.open(base)
        pkg.removePart("/word/styles.xml")
        val bytes = com.gongwen.ooxml.OoxmlPackageSerializer.serialize(pkg)
        val reopened = com.gongwen.ooxml.OoxmlPackage.open(bytes)
        assertTrue(reopened.part("/word/styles.xml") == null)
        // 文档模型仍可构建（styles 缺失不致命）
        val doc = DocumentService.open(reopened)
        assertTrue(doc.paragraphs.isNotEmpty())
    }
}
