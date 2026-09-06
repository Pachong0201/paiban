package com.gongwen.document.xml

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** document.xml 中的一个 body 顶层块（段落、表格、sectPr 等）。 */
class BodyBlock internal constructor(
    /** 顶层元素名，如 p / tbl / sectPr。 */
    val elementName: String,
    /** 在原始 XML 文本中的起始字符偏移（含）与结束字符偏移（不含）；新建块为 -1。 */
    internal val startOffset: Int,
    internal val endOffset: Int,
    /** 对应的 body 直接子元素索引（文档序）；新建块为 -1，插入时动态分配。 */
    internal val domIndex: Int,
) {
    /** 该块是否已被修改（修改后需用 DOM 重序列化）。 */
    var dirty: Boolean = false

    /** 是否为程序新建的块（无原始切片）。 */
    val isNew: Boolean get() = startOffset < 0
}

/**
 * document.xml 的块级编辑视图。
 *
 * 一次 StAX 扫描切出 body 直接子块（记录字符偏移 + 文档序索引），
 * 同时把整份文档解析为命名空间完整的 DOM。块的 DOM 元素 = body 的第 domIndex 个子元素。
 *
 * 导出：未 dirty 块输出原始文本切片（逐字符保真）；dirty 块从 DOM 序列化。
 * 新建块（无原始切片）只能来自 DOM，序列化同样走 DOM。
 *
 * 结构插入（新增/删除顶层块）会使 domIndex 与 blocks 对应复杂化：
 * 采用"插入后标记 structuralDirty，序列化时整个 body 从 DOM 输出"的保守策略，
 * 仅在发生结构变更的文档上付出整 body 重排代价，常规编辑（改一段）仍逐块保真。
 */
class DocumentXmlPart internal constructor(
    xmlBytes: ByteArray,
    private val blocks: MutableList<BodyBlock>,
    private val bodyContentStart: Int,
    private val bodyContentEnd: Int,
    private val dom: Document,
    private val bodyElement: Element,
) {
    /** 原始 XML 文本（字符偏移的基准）。 */
    private val text: String = String(xmlBytes, Charsets.UTF_8)

    /** 是否发生过结构变更（块增删）。 */
    private var structuralDirty: Boolean = false

    val blockCount: Int get() = blocks.size

    /** DOM 文档（供编辑层构造新元素）。 */
    internal val domDocument: Document get() = dom

    /** body 元素（供编辑层插入结构）。 */
    internal val bodyRoot: Element get() = bodyElement

    fun blockAt(index: Int): BodyBlock = blocks[index]

    val topBlocks: List<BodyBlock> get() = blocks

    fun rawBlockText(index: Int): String =
        text.substring(blocks[index].startOffset, blocks[index].endOffset)

    /** 块的 DOM 元素。若发生结构变更则按当前 DOM 顺序实时查找（domIndex 失效时回退线性扫描）。 */
    fun domElementOf(block: BodyBlock): Element {
        if (!structuralDirty) {
            return bodyElementChildByElementIndex(block.domIndex)
                ?: error("块 DOM 定位失败: ${block.elementName}")
        }
        // 结构已变更：按元素对象引用反查不可行（DOM 无回溯），
        // 因此结构变更路径一律由上层持有 Element 直接操作，此处按名称+序号尽力而为。
        return bodyElementChildByElementIndex(block.domIndex)
            ?: error("结构变更后 DOM 定位失败: ${block.elementName}")
    }

    private fun bodyElementChildByElementIndex(index: Int): Element? {
        var idx = 0
        val children = bodyElement.childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n is Element) {
                if (idx == index) return n
                idx++
            }
        }
        return null
    }

    fun markDirty(block: BodyBlock) {
        block.dirty = true
    }

    /** 新建块登记（DOM 已插入 bodyElement，此块无原始切片）。 */
    fun registerNewBlock(elementName: String): BodyBlock {
        val block = BodyBlock(elementName, -1, -1, -1)
        blocks.add(block)
        structuralDirty = true
        return block
    }

    /** 删除块（DOM 移除由上层执行）。 */
    fun removeBlock(block: BodyBlock) {
        blocks.remove(block)
        structuralDirty = true
    }

    /**
     * 结构变更后重建块索引：blocks 与 DOM 当前顺序对齐（新块 domIndex 按序分配）。
     * 调用方（DocumentModelEditor）在结构变更后应调用本方法再 resync 模型。
     */
    fun rescanFromDom() {
        blocks.clear()
        val children = bodyElement.childNodes
        var idx = 0
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n is Element) {
                blocks.add(BodyBlock(n.localName ?: "unknown", -1, -1, idx))
                idx++
            }
        }
        structuralDirty = true
    }

    /**
     * 在 body 内插入一个全新元素（DOM 已由上层构造并 insert）。
     * 简单起见插到 sectPr 之前（文档末尾正文区）；返回登记的新块。
     */
    fun appendBodyElement(element: Element): BodyBlock {
        val sectPr = bodyElementChildByElementName("sectPr")
        if (sectPr != null) {
            bodyElement.insertBefore(element, sectPr)
        } else {
            bodyElement.appendChild(element)
        }
        // blocks 保持与 DOM 一致顺序：sectPr 之前的位置插入
        val block = BodyBlock(element.localName ?: "unknown", -1, -1, -1)
        val sectIdx = blocks.indexOfFirst { it.elementName == "sectPr" }
        if (sectIdx >= 0) blocks.add(sectIdx, block) else blocks.add(block)
        structuralDirty = true
        return block
    }

    private fun bodyElementChildByElementName(name: String): Element? {
        val children = bodyElement.childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n is Element && n.localName == name) return n
        }
        return null
    }

    /** 序列化为完整 document.xml 字节。 */
    fun toByteArray(): ByteArray {
        if (structuralDirty) {
            // 结构变更：body 整体从 DOM 输出（语义保真优先）
            val sb = StringBuilder(text.length + 512)
            sb.append(text, 0, bodyContentStart)
            val children = bodyElement.childNodes
            for (i in 0 until children.length) {
                val n = children.item(i)
                if (n is Element) sb.append(serializeElement(n))
            }
            sb.append(text, bodyContentEnd, text.length)
            return sb.toString().toByteArray(Charsets.UTF_8)
        }
        val sb = StringBuilder(text.length + 512)
        sb.append(text, 0, bodyContentStart)
        for (block in blocks) {
            if (block.dirty || block.isNew) {
                sb.append(serializeElement(domElementOf(block)))
            } else {
                sb.append(text, block.startOffset, block.endOffset)
            }
        }
        sb.append(text, bodyContentEnd, text.length)
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun parse(xmlBytes: ByteArray): DocumentXmlPart {
            val text = String(xmlBytes, Charsets.UTF_8)
            val openTag = Regex("<w:body(?:\\s[^>]*)?>").find(text)
                ?: error("document.xml 缺少 <w:body>")
            val closeTag = Regex("</w:body>").find(text)
                ?: error("document.xml 缺少 </w:body>")
            val bodyContentStart = openTag.range.last + 1
            val bodyContentEnd = closeTag.range.first

            // 1) StAX 切块。Xerces characterOffset 为字符偏移（UTF-16 单元），与 text 下标同单位。
            val blocks = ArrayList<BodyBlock>()
            val factory = secureFactory()
            val reader = factory.createXMLStreamReader(ByteArrayInputStream(xmlBytes))
            try {
                var depth = 0
                var bodyDepth = -1
                var blockStart = -1
                var domIndex = 0
                while (reader.hasNext()) {
                    when (reader.next()) {
                        XMLStreamConstants.START_ELEMENT -> {
                            depth++
                            if (reader.localName == "body") {
                                bodyDepth = depth
                            } else if (depth == bodyDepth + 1) {
                                // characterOffset 指向元素名起点，回溯到 '<' 获得完整开标签起点
                                blockStart = text.lastIndexOf('<', reader.location.characterOffset - 1)
                            }
                        }
                        XMLStreamConstants.END_ELEMENT -> {
                            if (depth == bodyDepth + 1 && blockStart >= 0) {
                                // END offset 指向元素名起点；块结束应含 '</w:xxx>'，偏移到 '>' 之后
                                val nameStart = reader.location.characterOffset - 1
                                val endOffset = text.indexOf('>', nameStart) + 1
                                blocks.add(
                                    BodyBlock(
                                        reader.localName,
                                        blockStart,
                                        endOffset,
                                        domIndex,
                                    )
                                )
                                blockStart = -1
                                domIndex++
                            }
                            depth--
                        }
                    }
                }
            } finally {
                reader.close()
            }

            // 2) 整文档 DOM（命名空间完整，供 dirty 块编辑与序列化）
            val dom = parseStandalone(text)
            val bodyElement = dom.getElementsByTagNameNS(W_NS, "body").item(0) as? Element
                ?: error("DOM 中缺少 w:body")
            return DocumentXmlPart(xmlBytes, blocks, bodyContentStart, bodyContentEnd, dom, bodyElement)
        }

        private fun secureFactory(): XMLInputFactory {
            val f = XMLInputFactory.newFactory()
            f.setProperty(XMLInputFactory.SUPPORT_DTD, false)
            f.setProperty("javax.xml.stream.isSupportingExternalEntities", false)
            return f
        }

        fun parseStandalone(xml: String): Document {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        }

        private fun serializeElement(element: Element): String {
            val t = TransformerFactory.newInstance().newTransformer()
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            t.setOutputProperty(OutputKeys.INDENT, "no")
            val sw = java.io.StringWriter()
            t.transform(DOMSource(element), StreamResult(sw))
            return sw.toString()
        }

        private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    }
}
