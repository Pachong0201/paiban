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
    /** 在原始 XML 文本中的起始字符偏移（含）与结束字符偏移（不含）。 */
    internal val startOffset: Int,
    internal val endOffset: Int,
    /** 对应的 body 直接子元素索引（文档序）。 */
    internal val domIndex: Int,
) {
    /** 该块是否已被修改（修改后需用 DOM 重序列化）。 */
    var dirty: Boolean = false
}

/**
 * document.xml 的块级编辑视图。
 *
 * 一次 StAX 扫描切出 body 直接子块（记录字符偏移 + 文档序索引），
 * 同时把整份文档解析为命名空间完整的 DOM。块的 DOM 元素 = body 的第 domIndex 个子元素。
 *
 * 导出：未 dirty 块输出原始文本切片（逐字符保真）；dirty 块从 DOM 序列化。
 */
class DocumentXmlPart internal constructor(
    xmlBytes: ByteArray,
    private val blocks: List<BodyBlock>,
    private val bodyContentStart: Int,
    private val bodyContentEnd: Int,
    private val dom: Document,
    private val bodyElement: Element,
) {
    /** 原始 XML 文本（字符偏移的基准）。 */
    private val text: String = String(xmlBytes, Charsets.UTF_8)

    val blockCount: Int get() = blocks.size

    fun blockAt(index: Int): BodyBlock = blocks[index]

    val topBlocks: List<BodyBlock> get() = blocks

    fun rawBlockText(index: Int): String =
        text.substring(blocks[index].startOffset, blocks[index].endOffset)

    /** 块的 DOM 元素（body 直接子元素，按文档序索引取）。 */
    fun domElementOf(block: BodyBlock): Element {
        var idx = 0
        val children = bodyElement.childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n is Element) {
                if (idx == block.domIndex) return n
                idx++
            }
        }
        error("块 DOM 定位失败: ${block.elementName}")
    }

    fun markDirty(block: BodyBlock) {
        block.dirty = true
    }

    /** 序列化为完整 document.xml 字节。 */
    fun toByteArray(): ByteArray {
        val sb = StringBuilder(text.length + 512)
        sb.append(text, 0, bodyContentStart)
        for (block in blocks) {
            if (block.dirty) {
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
