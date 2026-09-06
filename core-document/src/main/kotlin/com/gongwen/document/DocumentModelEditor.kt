package com.gongwen.document

import com.gongwen.document.model.Paragraph
import com.gongwen.document.model.ParagraphProperties
import com.gongwen.document.model.Run
import com.gongwen.document.model.RunProperties
import com.gongwen.document.model.Table
import com.gongwen.document.model.TableRow
import com.gongwen.document.model.TableCell
import com.gongwen.document.xml.BodyBlock
import com.gongwen.document.xml.DocumentParser
import com.gongwen.document.xml.DocumentXmlEditor
import com.gongwen.document.xml.DocumentXmlPart

/** 打开后处于编辑状态的文档。 */
class WorkingDocument internal constructor(
    val sourceDescriptor: String,
    val xmlPart: DocumentXmlPart,
    parsed: DocumentParser.ParsedDocument,
) {
    /** 正文段落（不含表格内段落）。 */
    var paragraphs: List<Paragraph> = parsed.paragraphs
        private set
    /** 表格。 */
    var tables: List<Table> = parsed.tables
        private set
    /** 全部块级对象（按顺序，含段落与表格）。 */
    var topLevelObjects: List<Any> = parsed.topLevelObjects
        private set

    internal val xmlEditor = DocumentXmlEditor(xmlPart)

    /** 结构变更后重新解析模型并同步引用。 */
    internal fun resyncFromXml() {
        xmlPart.rescanFromDom()
        val reparsed = DocumentParser(xmlPart).parse()
        paragraphs = reparsed.paragraphs
        tables = reparsed.tables
        topLevelObjects = reparsed.topLevelObjects
    }

    /** 该段所属顶层块（表格段为表格块）。 */
    internal fun blockOf(p: Paragraph): BodyBlock =
        p.blockAnchor as? BodyBlock ?: error("缺少块锚点")

    /** 序列化为完整 document.xml 字节。 */
    fun toXmlBytes(): ByteArray = xmlPart.toByteArray()
}

/**
 * 高层编辑 API（模型与 XML 保持同步，仅对触碰的块做 dirty 标记）。
 * UI 与排版引擎只通过此类操作文档。
 */
class DocumentModelEditor(private val doc: WorkingDocument) {

    /** 按需在段落上应用格式。null 字段表示"不清除也不设置"，沿用现有。 */
    fun setParagraphFormat(p: Paragraph, props: ParagraphProperties) {
        doc.xmlEditor.applyParagraphFormat(p, props)
        p.properties = deepCopy(props)
    }

    /** 设置 run 格式（模型+XML）。 */
    fun setRunFormat(p: Paragraph, run: Run, props: RunProperties) {
        doc.xmlEditor.applyRunFormat(run, props, p)
        run.properties = deepCopyRun(props)
    }

    /** 替换整段文本，模型与 XML 同步。 */
    fun setParagraphText(p: Paragraph, newText: String) {
        val firstRunProps = p.runs.firstOrNull()?.properties
        doc.xmlEditor.replaceParagraphText(p, newText)
        p.runs.clear()
        val r = Run(stableId = "u-${System.nanoTime()}", text = newText)
        if (firstRunProps != null) r.properties = deepCopyRun(firstRunProps)
        p.runs.add(r)
    }

    private fun deepCopy(src: ParagraphProperties): ParagraphProperties {
        val copy = ParagraphProperties()
        copy.copyFrom(src)
        return copy
    }

    private fun deepCopyRun(src: RunProperties): RunProperties {
        val copy = RunProperties()
        copy.copyFrom(src)
        return copy
    }

    // ===== 表格操作（MVP 最小集） =====

    private fun tableDom(table: Table): org.w3c.dom.Element {
        val block = table.ooxmlAnchor as? BodyBlock
            ?: error("表格 ${table.stableId} 缺少块锚点")
        return doc.xmlPart.domElementOf(block)
    }

    /** 创建 rows×cols 表格并追加到文档末尾。返回模型 Table。 */
    fun createTable(rows: Int, cols: Int, headers: List<String> = emptyList()): Table {
        doc.xmlEditor.createTable(rows, cols, headers)
        doc.resyncFromXml()
        return doc.tables.lastOrNull() ?: error("表格创建后解析失败")
    }

    /** 表格加一行（texts 每列文本）。 */
    fun appendTableRow(table: Table, texts: List<String> = emptyList()) {
        doc.xmlEditor.addTableRow(tableDom(table), texts)
        doc.resyncFromXml()
    }

    /** 设置单元格文本。 */
    fun setTableCellText(table: Table, row: Int, col: Int, text: String) {
        doc.xmlEditor.setCellText(tableDom(table), row, col, text)
        doc.resyncFromXml()
    }

    /** 删除表格行。 */
    fun deleteTableRow(table: Table, row: Int) {
        doc.xmlEditor.deleteTableRow(tableDom(table), row)
        doc.resyncFromXml()
    }
}
