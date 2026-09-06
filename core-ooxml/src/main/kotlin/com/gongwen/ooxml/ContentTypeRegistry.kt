package com.gongwen.ooxml

import java.io.ByteArrayInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/**
 * [Content_Types].xml 解析器：default（扩展名）与 override（精确 part）两级映射。
 * 只读；写入时由 [OoxmlPackageSerializer] 维护。
 */
class ContentTypeRegistry internal constructor(
    private val defaults: Map<String, String>,
    private val overrides: Map<String, String>,
) {
    fun contentTypeFor(partName: String): String? {
        val normalized = OoxmlPackage.normalizePartName(partName)
        overrides[normalized]?.let { return it }
        val ext = normalized.substringAfterLast('.', "")
        return defaults[ext]
    }

    companion object {
        fun parse(contentTypesPart: OoxmlPart): ContentTypeRegistry {
            val defaults = HashMap<String, String>()
            val overrides = HashMap<String, String>()
            val factory = secureXmlInputFactory()
            val reader = try {
                factory.createXMLStreamReader(ByteArrayInputStream(contentTypesPart.byteArray()))
            } catch (e: Exception) {
                throw CorruptPackageException("无法解析 [Content_Types].xml: ${e.message}", e)
            }
            try {
                while (reader.hasNext()) {
                    when (reader.next()) {
                        XMLStreamConstants.START_ELEMENT -> {
                            when (reader.localName) {
                                "Default" -> {
                                    val ext = reader.getAttributeValue(null, "Extension")
                                    val ct = reader.getAttributeValue(null, "ContentType")
                                    if (ext != null && ct != null) defaults[ext.lowercase()] = ct
                                }
                                "Override" -> {
                                    val pn = reader.getAttributeValue(null, "PartName")
                                    val ct = reader.getAttributeValue(null, "ContentType")
                                    if (pn != null && ct != null) {
                                        overrides[OoxmlPackage.normalizePartName(pn)] = ct
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                throw CorruptPackageException("无法解析 [Content_Types].xml: ${e.message}", e)
            } finally {
                reader.close()
            }
            return ContentTypeRegistry(defaults, overrides)
        }

        internal fun secureXmlInputFactory(): XMLInputFactory {
            val f = XMLInputFactory.newFactory()
            // XXE 防护：禁用 DTD 与外部实体
            f.setProperty(XMLInputFactory.SUPPORT_DTD, false)
            f.setProperty("javax.xml.stream.isSupportingExternalEntities", false)
            return f
        }
    }
}
