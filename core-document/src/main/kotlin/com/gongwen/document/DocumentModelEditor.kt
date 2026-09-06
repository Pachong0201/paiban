package com.gongwen.document

import com.gongwen.document.model.Paragraph
import com.gongwen.document.model.ParagraphProperties
import com.gongwen.document.model.Run
import com.gongwen.document.model.RunProperties
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
    val paragraphs: List<Paragraph> = parsed.paragraphs
    /** 表格。 */
    val tables = parsed.tables
    /** 全部块级对象（按顺序，含段落与表格）。 */
    val topLevelObjects = parsed.topLevelObjects

    internal val xmlEditor = DocumentXmlEditor(xmlPart)

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
}
