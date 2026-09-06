package com.gongwen.document

import com.gongwen.document.xml.DocumentXmlPart
import com.gongwen.document.xml.DocumentParser
import org.junit.Assert.assertEquals
import org.junit.Test

/** 表格创建与编辑集成测试（规格 §26 最小集）。 */
class TableEditingTest {

    /** 从工作文档序列化 XML 后重解析模型（等价重开验证）。 */
    private fun reopenDoc(doc: WorkingDocument): DocumentParser.ParsedDocument {
        val bytes = doc.toXmlBytes()
        val part = DocumentXmlPart.parse(bytes)
        return DocumentParser(part).parse()
    }

    @Test
    fun createTable_appendsToDocumentEnd() {
        val doc = DocumentService.open(DocFixture.build())
        val editor = DocumentModelEditor(doc)
        val before = doc.tables.size

        val table = editor.createTable(3, 4, headers = listOf("序号", "单位", "任务", "备注"))
        assertEquals(before + 1, doc.tables.size)
        assertEquals(3, table.rows.size)
        assertEquals(4, table.rows[0].cells.size)
        assertEquals("序号", table.rows[0].cells[0].paragraphs[0].text)

        // 序列化 → 重解析：表格仍在且完整
        val reparsed = reopenDoc(doc)
        assertEquals(before + 1, reparsed.tables.size)
        val t = reparsed.tables.last()
        assertEquals(3, t.rows.size)
        assertEquals(4, t.rows[0].cells.size)
        // 原正文段落仍保留
        assert(reparsed.paragraphs.any { it.text.contains("关于测试的通知") })
    }

    @Test
    fun appendRowAndSetCell_works() {
        val doc = DocumentService.open(DocFixture.build())
        val editor = DocumentModelEditor(doc)
        editor.createTable(2, 2, headers = listOf("姓名", "职务"))
        val table = doc.tables.last()

        editor.appendTableRow(table, listOf("张三", "主任"))
        val current = doc.tables.last()
        assertEquals(3, current.rows.size)
        assertEquals("张三", current.rows[2].cells[0].paragraphs[0].text)

        editor.setTableCellText(current, 0, 1, "负责人")
        assertEquals("负责人", doc.tables.last().rows[0].cells[1].paragraphs[0].text)

        val reparsed = reopenDoc(doc)
        val t = reparsed.tables.last()
        assertEquals(3, t.rows.size)
        assertEquals("负责人", t.rows[0].cells[1].paragraphs[0].text)
        assertEquals("张三", t.rows[2].cells[0].paragraphs[0].text)
    }

    @Test
    fun deleteRow_removesRow() {
        val doc = DocumentService.open(DocFixture.build())
        val editor = DocumentModelEditor(doc)
        editor.createTable(4, 2)
        assertEquals(4, doc.tables.last().rows.size)

        editor.deleteTableRow(doc.tables.last(), 1)
        val reparsed = reopenDoc(doc)
        assertEquals(3, reparsed.tables.last().rows.size)
    }
}
