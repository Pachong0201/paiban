package com.gongwen.document.xml

import com.gongwen.document.model.Paragraph
import com.gongwen.document.model.ParagraphProperties
import com.gongwen.document.model.Run
import com.gongwen.document.model.RunProperties
import com.gongwen.document.model.Table
import com.gongwen.document.model.TableCell
import com.gongwen.document.model.TableRow
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList

/** OOXML w 命名空间常量（本地避免依赖 core-ooxml 常量造成耦合，但仍指向同一 URI）。 */
private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

/**
 * 把 document.xml 的块切分为语义模型（段落/表格）。
 *
 * 只处理 body 顶层 w:p 与 w:tbl；w:sectPr 记录但不出模型。
 * 每个 [Paragraph]/[Table] 持有其 [BodyBlock] 索引（ooxmlAnchor），
 * 编辑后通过 anchor 标记块 dirty。
 */
class DocumentParser(private val xmlPart: DocumentXmlPart) {

    /** 解析结果：文档内所有块级对象（含表格内段落）。 */
    class ParsedDocument(
        val paragraphs: List<Paragraph>,
        val tables: List<Table>,
        val topLevelObjects: List<Any>,
    )

    private var idCounter = 0
    private fun nextId(prefix: String): String = "$prefix-${idCounter++}"

    fun parse(): ParsedDocument {
        idCounter = 0
        val paragraphs = ArrayList<Paragraph>()
        val tables = ArrayList<Table>()
        val topObjects = ArrayList<Any>()

        for ((index, block) in xmlPart.topBlocks.withIndex()) {
            when (block.elementName) {
                "p" -> {
                    val p = parseParagraph(block, index)
                    paragraphs.add(p)
                    topObjects.add(p)
                }
                "tbl" -> {
                    val t = parseTable(block, index)
                    tables.add(t)
                    topObjects.add(t)
                }
                else -> {
                    // sectPr 等结构块：不做模型，保留原样
                }
            }
        }
        return ParsedDocument(paragraphs, tables, topObjects)
    }

    private fun parseParagraph(block: BodyBlock, blockIndex: Int): Paragraph {
        val element = xmlPart.domElementOf(block)
        val p = Paragraph(stableId = nextId("p"))
        p.ooxmlAnchor = element
        p.blockAnchor = block
        val pPr = childElement(element, "pPr")
        if (pPr != null) {
            p.properties = parseParagraphProperties(pPr)
        }
        parseRuns(element, p)
        return p
    }

    private fun parseParagraphProperties(pPr: Element): ParagraphProperties {
        val props = ParagraphProperties()
        childElement(pPr, "pStyle")?.let { props.styleId = it.getAttributeNS(W_NS, "val").ifEmpty { null } }
        childElement(pPr, "jc")?.let { props.alignment = it.getAttributeNS(W_NS, "val").ifEmpty { null } }
        childElement(pPr, "ind")?.let { ind ->
            props.firstLineIndentTwips = intAttr(ind, "firstLine")
            props.leftIndentTwips = intAttr(ind, "left")
            props.rightIndentTwips = intAttr(ind, "right")
            props.hangingIndentTwips = intAttr(ind, "hanging")
        }
        childElement(pPr, "spacing")?.let { sp ->
            props.lineSpacingRule = attr(sp, "lineRule")
            props.lineSpacingValue = intAttr(sp, "line")
            props.spaceBeforeTwips = intAttr(sp, "before")
            props.spaceAfterTwips = intAttr(sp, "after")
        }
        childElement(pPr, "keepNext")?.let { props.keepWithNext = true }
        childElement(pPr, "keepLines")?.let { props.keepLines = true }
        childElement(pPr, "pageBreakBefore")?.let { props.pageBreakBefore = true }
        childElement(pPr, "outlineLvl")?.let { props.outlineLevel = intAttr(it, "val") }
        // 统计未知子元素（保留但提示）
        val known = setOf("pStyle", "jc", "ind", "spacing", "keepNext", "keepLines",
            "pageBreakBefore", "outlineLvl", "rPr", "tabs", "shd", "numPr")
        val unknown = childrenElements(pPr).count { it.localName !in known }
        props.unknownChildren = unknown
        return props
    }

    /** 解析 w:p 的直接子 run（w:r），并处理 w:hyperlink 内部 run、w:bookmarkStart 等跳过。 */
    private fun parseRuns(pElement: Element, p: Paragraph) {
        val nodes = childNodes(pElement)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node !is Element) continue
            when (node.localName) {
                "r" -> p.runs.add(parseRun(node))
                "hyperlink" -> {
                    val rNodes = childNodes(node)
                    for (j in 0 until rNodes.length) {
                        val rn = rNodes.item(j)
                        if (rn is Element && rn.localName == "r") p.runs.add(parseRun(rn))
                    }
                }
                // bookmarkStart/bookmarkEnd/commentRangeStart 等：跳过（保留在 XML 中）
                else -> {}
            }
        }
    }

    private fun parseRun(rElement: Element): Run {
        val run = Run(stableId = nextId("r"), text = "")
        run.ooxmlAnchor = rElement
        run.properties = parseRunProperties(childElement(rElement, "rPr"))
        val text = StringBuilder()
        val tNodes = childNodes(rElement)
        for (i in 0 until tNodes.length) {
            val tn = tNodes.item(i)
            if (tn is Element && tn.localName == "t") {
                val content = tn.textContent
                // xml:space="preserve" 已由 DOM 保留文本
                text.append(content)
            } else if (tn is Element && tn.localName == "tab") {
                text.append('\t')
            } else if (tn is Element && tn.localName == "br") {
                text.append('\n')
            } else if (tn is Element && tn.localName == "cr") {
                text.append('\n')
            }
        }
        run.text = text.toString()
        return run
    }

    private fun parseRunProperties(rPr: Element?): RunProperties {
        val props = RunProperties()
        if (rPr == null) return props
        childElement(rPr, "rFonts")?.let { fonts ->
            props.eastAsiaFont = attr(fonts, "eastAsia")
            props.asciiFont = attr(fonts, "ascii")
            props.hAnsiFont = attr(fonts, "hAnsi")
            props.csFont = attr(fonts, "cs")
        }
        childElement(rPr, "sz")?.let { props.fontSizeHalfPoints = intAttr(it, "val") }
        childElement(rPr, "szCs")?.let { props.fontSizeCsHalfPoints = intAttr(it, "val") }
        childElement(rPr, "b")?.let { props.bold = true }
        childElement(rPr, "bCs")?.let {}
        childElement(rPr, "i")?.let { props.italic = true }
        childElement(rPr, "strike")?.let { props.strike = true }
        childElement(rPr, "u")?.let { props.underline = attr(it, "val") }
        childElement(rPr, "color")?.let { props.color = attr(it, "val") }
        childElement(rPr, "spacing")?.let { props.characterSpacingTwips = intAttr(it, "val") }
        childElement(rPr, "highlight")?.let { props.highlight = attr(it, "val") }
        val known = setOf("rFonts", "sz", "szCs", "b", "bCs", "i", "iCs", "strike", "u",
            "color", "spacing", "highlight", "lang", "vertAlign", "position", "w", "kern")
        val unknown = childrenElements(rPr).count { it.localName !in known }
        props.unknownChildren = unknown
        return props
    }

    private fun parseTable(block: BodyBlock, blockIndex: Int): Table {
        val element = xmlPart.domElementOf(block)
        val table = Table(stableId = nextId("tbl"), ooxmlAnchor = block)
        table.blockAnchor = block
        val trNodes = childrenElements(element).filter { it.localName == "tr" }
        for (trEl in trNodes) {
            val row = TableRow(stableId = nextId("tr"))
            val tcNodes = childrenElements(trEl).filter { it.localName == "tc" }
            for (tcEl in tcNodes) {
                val cell = TableCell(stableId = nextId("tc"))
                cell.ooxmlAnchor = tcEl
                childElement(tcEl, "tcPr")?.let { tcPr ->
                    childElement(tcPr, "gridSpan")?.let { cell.gridSpan = intAttr(it, "val") ?: 1 }
                    childElement(tcPr, "vMerge")?.let { cell.vMerge = attr(it, "val") }
                }
                val pNodes = childrenElements(tcEl).filter { it.localName == "p" }
                for (pEl in pNodes) {
                    val p = Paragraph(stableId = nextId("p"))
                    p.ooxmlAnchor = pEl
                    p.blockAnchor = block
                    p.inTable = true
                    childElement(pEl, "pPr")?.let { p.properties = parseParagraphProperties(it) }
                    parseRuns(pEl, p)
                    cell.paragraphs.add(p)
                }
                row.cells.add(cell)
            }
            table.rows.add(row)
        }
        return table
    }

    // ---- 便捷 DOM 辅助 ----

    private fun childElement(parent: Element, localName: String): Element? =
        childrenElements(parent).firstOrNull { it.localName == localName }

    private fun childrenElements(parent: Element): List<Element> {
        val out = ArrayList<Element>()
        val nodes: NodeList = parent.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n is Element) out.add(n)
        }
        return out
    }

    private fun childNodes(parent: Element): NodeList = parent.childNodes

    private fun attr(el: Element, name: String): String? {
        val v = el.getAttributeNS(W_NS, name)
        return if (v.isEmpty()) null else v
    }

    private fun intAttr(el: Element, name: String): Int? = attr(el, name)?.toIntOrNull()
}
