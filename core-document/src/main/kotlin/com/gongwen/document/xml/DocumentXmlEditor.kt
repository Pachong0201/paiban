package com.gongwen.document.xml

import com.gongwen.document.model.Paragraph
import com.gongwen.document.model.ParagraphProperties
import com.gongwen.document.model.Run
import com.gongwen.document.model.RunProperties
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val XML_NS = "http://www.w3.org/XML/1998/namespace"

/**
 * 对 document.xml 的局部编辑执行器。
 *
 * 所有写操作都作用在段落 DOM 上，并把所属顶层 [BodyBlock] 标记 dirty；
 * 序列化时仅 dirty 块被重写，其余保持原始字节 —— P0-2 最小编辑的落地。
 *
 * - 顶层段落：[Paragraph.ooxmlAnchor] = 该段的 w:p DOM，[blockAnchor] = 自身 BodyBlock
 * - 表格内段落：ooxmlAnchor = cell 内 w:p DOM，blockAnchor = 表格 BodyBlock
 */
class DocumentXmlEditor(private val xmlPart: DocumentXmlPart) {

    private fun domParagraphOf(p: Paragraph): Element =
        p.ooxmlAnchor as? Element ?: error("段落 ${p.stableId} 缺少 DOM 锚点")

    private fun blockOf(p: Paragraph): BodyBlock =
        p.blockAnchor as? BodyBlock ?: error("段落 ${p.stableId} 缺少块锚点")

    private fun mark(p: Paragraph) = xmlPart.markDirty(blockOf(p))

    // ===== 段落格式 =====

    /** 重写段落 pPr 为 [props] 描述的内容；未指定的属性保持 null 即不写。 */
    fun applyParagraphFormat(p: Paragraph, props: ParagraphProperties) {
        rewritePPr(domParagraphOf(p), props)
        mark(p)
    }

    private fun rewritePPr(pEl: Element, props: ParagraphProperties) {
        val doc = pEl.ownerDocument
        var pPr = child(pEl, "pPr")
        if (pPr == null) {
            pPr = doc.createElementNS(W_NS, "w:pPr")
            pEl.insertBefore(pPr, pEl.firstChild)
        }
        val knownNames = setOf(
            "pStyle", "jc", "ind", "spacing", "keepNext", "keepLines",
            "pageBreakBefore", "outlineLvl", "numPr", "shd", "tabs", "rPr", "bidi",
            "adjustRightInd", "divId", "snapToGrid", "contextualSpacing", "widowControl",
            "wordWrap", "overflowPunct", "autoSpaceDE", "autoSpaceDN", "textAlignment",
        )
        val unknown = childElements(pPr).filter { it.localName !in knownNames }
        while (pPr.firstChild != null) pPr.removeChild(pPr.firstChild)

        props.styleId?.let { setValElement(doc, pPr, "pStyle", it) }
        props.alignment?.let { setValElement(doc, pPr, "jc", it) }
        if (props.firstLineIndentTwips != null || props.leftIndentTwips != null ||
            props.rightIndentTwips != null || props.hangingIndentTwips != null
        ) {
            val ind = doc.createElementNS(W_NS, "w:ind")
            props.firstLineIndentTwips?.let { ind.setAttributeNS(W_NS, "w:firstLine", it.toString()) }
            props.leftIndentTwips?.let { ind.setAttributeNS(W_NS, "w:left", it.toString()) }
            props.rightIndentTwips?.let { ind.setAttributeNS(W_NS, "w:right", it.toString()) }
            props.hangingIndentTwips?.let { ind.setAttributeNS(W_NS, "w:hanging", it.toString()) }
            pPr.appendChild(ind)
        }
        if (props.lineSpacingRule != null || props.lineSpacingValue != null ||
            props.spaceBeforeTwips != null || props.spaceAfterTwips != null
        ) {
            val sp = doc.createElementNS(W_NS, "w:spacing")
            props.spaceBeforeTwips?.let { sp.setAttributeNS(W_NS, "w:before", it.toString()) }
            props.spaceAfterTwips?.let { sp.setAttributeNS(W_NS, "w:after", it.toString()) }
            props.lineSpacingRule?.let { sp.setAttributeNS(W_NS, "w:lineRule", it) }
            props.lineSpacingValue?.let { sp.setAttributeNS(W_NS, "w:line", it.toString()) }
            pPr.appendChild(sp)
        }
        if (props.keepWithNext) pPr.appendChild(doc.createElementNS(W_NS, "w:keepNext"))
        if (props.keepLines) pPr.appendChild(doc.createElementNS(W_NS, "w:keepLines"))
        if (props.pageBreakBefore) pPr.appendChild(doc.createElementNS(W_NS, "w:pageBreakBefore"))
        props.outlineLevel?.let { setValElement(doc, pPr, "outlineLvl", it.toString()) }

        unknown.forEach { pPr.appendChild(it) }
    }

    /** 应用 run 属性：重写 rPr。调用方负责保证 run 在已 dirty 或即将 dirty 的块内。 */
    fun applyRunFormat(run: Run, props: RunProperties, p: Paragraph? = null) {
        val rEl = run.ooxmlAnchor as? Element
            ?: error("run ${run.stableId} 缺少 DOM 锚点")
        rewriteRPr(rEl, props)
        if (p != null) mark(p)
    }

    private fun rewriteRPr(rEl: Element, props: RunProperties) {
        val doc = rEl.ownerDocument
        var rPr = child(rEl, "rPr")
        if (rPr == null) {
            rPr = doc.createElementNS(W_NS, "w:rPr")
            rEl.insertBefore(rPr, rEl.firstChild)
        }
        val knownNames = setOf(
            "rFonts", "sz", "szCs", "b", "bCs", "i", "iCs", "strike", "dstrike",
            "u", "color", "spacing", "highlight", "lang", "vertAlign", "position",
            "w", "kern", "shadow", "emboss", "imprint", "smallCaps", "caps",
        )
        val unknown = childElements(rPr).filter { it.localName !in knownNames }
        while (rPr.firstChild != null) rPr.removeChild(rPr.firstChild)

        if (props.eastAsiaFont != null || props.asciiFont != null ||
            props.hAnsiFont != null || props.csFont != null
        ) {
            val fonts = doc.createElementNS(W_NS, "w:rFonts")
            props.asciiFont?.let { fonts.setAttributeNS(W_NS, "w:ascii", it) }
            props.hAnsiFont?.let { fonts.setAttributeNS(W_NS, "w:hAnsi", it) }
            props.eastAsiaFont?.let { fonts.setAttributeNS(W_NS, "w:eastAsia", it) }
            props.csFont?.let { fonts.setAttributeNS(W_NS, "w:cs", it) }
            rPr.appendChild(fonts)
        }
        props.fontSizeHalfPoints?.let {
            setValElement(doc, rPr, "sz", it.toString())
        }
        props.fontSizeCsHalfPoints?.let {
            setValElement(doc, rPr, "szCs", it.toString())
        }
        when (props.bold) {
            true -> rPr.appendChild(doc.createElementNS(W_NS, "w:b"))
            false -> setValElement(doc, rPr, "b", "0")
            null -> {}
        }
        when (props.italic) {
            true -> rPr.appendChild(doc.createElementNS(W_NS, "w:i"))
            false -> setValElement(doc, rPr, "i", "0")
            null -> {}
        }
        when (props.strike) {
            true -> rPr.appendChild(doc.createElementNS(W_NS, "w:strike"))
            false -> setValElement(doc, rPr, "strike", "0")
            null -> {}
        }
        props.underline?.let { setValElement(doc, rPr, "u", it) }
        props.color?.let { setValElement(doc, rPr, "color", it) }
        props.characterSpacingTwips?.let { setValElement(doc, rPr, "spacing", it.toString()) }
        props.highlight?.let { setValElement(doc, rPr, "highlight", it) }

        unknown.forEach { rPr.appendChild(it) }
    }

    // ===== 文本 =====

    /** 用单 run 替换段落全部文字（保留第一个 run 的格式），其余 run 删除。 */
    fun replaceParagraphText(p: Paragraph, newText: String) {
        val pEl = domParagraphOf(p)
        replaceTextInElement(pEl, newText)
        mark(p)
    }

    private fun replaceTextInElement(pEl: Element, newText: String) {
        val doc = pEl.ownerDocument
        val runs = collectRuns(pEl)
        var rPrToKeep: Element? = null
        if (runs.isNotEmpty()) {
            val first = runs[0]
            rPrToKeep = child(first, "rPr")?.cloneNode(true) as? Element
        }
        runs.forEach { it.parentNode?.removeChild(it) }
        val r = doc.createElementNS(W_NS, "w:r")
        rPrToKeep?.let { r.appendChild(doc.importNode(it, true)) }
        val t = doc.createElementNS(W_NS, "w:t")
        t.setAttributeNS(XML_NS, "xml:space", "preserve")
        t.textContent = newText
        r.appendChild(t)
        // 追加到段落末尾（原 run 均已移除；段落内其他节点如 bookmark 顺序保留在 r 之前）
        pEl.appendChild(r)
    }

    private fun collectRuns(pEl: Element): List<Element> {
        val out = ArrayList<Element>()
        val list = pEl.getElementsByTagNameNS(W_NS, "r")
        for (i in 0 until list.length) out.add(list.item(i) as Element)
        return out
    }

    // ===== 通用辅助 =====

    private fun child(parent: Element, localName: String): Element? =
        childElements(parent).firstOrNull { it.localName == localName }

    private fun childElements(parent: Element): List<Element> {
        val out = ArrayList<Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n is Element) out.add(n)
        }
        return out
    }

    private fun setValElement(doc: Document, parent: Element, localName: String, value: String) {
        val el = doc.createElementNS(W_NS, "w:$localName")
        el.setAttributeNS(W_NS, "w:val", value)
        parent.appendChild(el)
    }
}
