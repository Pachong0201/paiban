package com.gongwen.ooxml

import java.security.MessageDigest

/**
 * 打开时的语义级 package 指纹，用于 round-trip 门禁对比（不要求字节级一致）。
 *
 * 记录：
 * - part 集合（name + content type + size）；
 * - 每个二进制 part 的 SHA-256；
 * - 每个 XML part 的结构指纹（元素/属性名序列 + 文本聚合 + 注释数 + PI 数）。
 */
class PackageSnapshot private constructor(
    val partFingerprints: Map<String, PartFingerprint>,
) {
    sealed class PartFingerprint {
        data class Xml(
            val contentType: String,
            val elementPathHash: Long,
            val attrNameCount: Long,
            val textHash: Long,
            val commentCount: Int,
            val piCount: Int,
            val rawTextHash: Long,
        ) : PartFingerprint()

        data class Binary(
            val contentType: String,
            val sha256: String,
            val size: Long,
        ) : PartFingerprint()
    }

    /** 与另一个快照比较，返回差异描述；空列表表示语义一致。 */
    fun diff(other: PackageSnapshot): List<String> {
        val issues = ArrayList<String>()
        val a = partFingerprints
        val b = other.partFingerprints
        val allNames = (a.keys + b.keys).sorted()
        for (name in allNames) {
            val fa = a[name]
            val fb = b[name]
            when {
                fa == null -> issues.add("part 消失: $name")
                fb == null -> issues.add("新增 part: $name")
                fa != fb -> issues.add("part 内容变化: $name")
            }
        }
        return issues
    }

    companion object {
        fun capture(pkg: OoxmlPackage): PackageSnapshot {
            val map = LinkedHashMap<String, PartFingerprint>()
            for (p in pkg.parts()) {
                map[p.name] = if (p.name == "/[Content_Types].xml") {
                    fingerprintContentTypes(p)
                } else if (p.isXml) {
                    fingerprintXml(p)
                } else {
                    val bytes = p.byteArray()
                    val digest = MessageDigest.getInstance("SHA-256")
                    PartFingerprint.Binary(
                        contentType = p.contentType,
                        sha256 = digest.digest(bytes).joinToString("") { "%02x".format(it) },
                        size = bytes.size.toLong(),
                    )
                }
            }
            return PackageSnapshot(map)
        }

        /**
         * [Content_Types].xml 的语义指纹：仅比较 Default/Override 的映射集合。
         * 序列化器会重建该文件（声明顺序与空白可能变化），只要映射集合等价即视为保真。
         */
        private fun fingerprintContentTypes(p: OoxmlPart): PartFingerprint.Xml {
            val defaults = sortedMapOf<String, String>()
            val overrides = sortedMapOf<String, String>()
            val reader = ContentTypeRegistry.secureXmlInputFactory()
                .createXMLStreamReader(p.byteArray().inputStream())
            try {
                while (reader.hasNext()) {
                    if (reader.next() == javax.xml.stream.XMLStreamConstants.START_ELEMENT) {
                        when (reader.localName) {
                            "Default" -> {
                                val ext = reader.getAttributeValue(null, "Extension")
                                val ct = reader.getAttributeValue(null, "ContentType")
                                if (ext != null && ct != null) defaults[ext.lowercase()] = ct
                            }
                            "Override" -> {
                                val pn = reader.getAttributeValue(null, "PartName")
                                val ct = reader.getAttributeValue(null, "ContentType")
                                if (pn != null && ct != null) overrides[pn] = ct
                            }
                        }
                    }
                }
            } finally {
                reader.close()
            }
            var hash = 0L
            for ((k, v) in defaults) hash = hash * 31 + "$k=$v".hashCode()
            for ((k, v) in overrides) hash = hash * 31 + "$k=$v".hashCode()
            return PartFingerprint.Xml(
                contentType = p.contentType,
                elementPathHash = hash,
                attrNameCount = (defaults.size + overrides.size).toLong(),
                textHash = hash,
                commentCount = 0,
                piCount = 0,
                rawTextHash = hash,
            )
        }

        private fun fingerprintXml(p: OoxmlPart): PartFingerprint.Xml {
            val raw = p.byteArray()
            var elementPathHash = 0L
            var attrNameCount = 0L
            var textHash = 0L
            var commentCount = 0
            var piCount = 0
            val path = ArrayDeque<String>()
            val factory = ContentTypeRegistry.secureXmlInputFactory()
            val reader = factory.createXMLStreamReader(raw.inputStream())
            try {
                while (reader.hasNext()) {
                    when (reader.next()) {
                        javax.xml.stream.XMLStreamConstants.START_ELEMENT -> {
                            path.addLast(reader.localName)
                            elementPathHash = elementPathHash * 31 + reader.localName.hashCode()
                            for (i in 0 until reader.attributeCount) {
                                val an = reader.getAttributeLocalName(i)
                                attrNameCount++
                                elementPathHash = elementPathHash * 31 + an.hashCode()
                            }
                        }
                        javax.xml.stream.XMLStreamConstants.END_ELEMENT -> if (path.isNotEmpty()) path.removeLast()
                        javax.xml.stream.XMLStreamConstants.CHARACTERS,
                        javax.xml.stream.XMLStreamConstants.CDATA,
                        -> {
                            if (reader.isWhiteSpace.not()) {
                                val t = reader.text
                                textHash = textHash * 31 + t.hashCode()
                            }
                        }
                        javax.xml.stream.XMLStreamConstants.COMMENT -> commentCount++
                        javax.xml.stream.XMLStreamConstants.PROCESSING_INSTRUCTION -> piCount++
                    }
                }
            } finally {
                reader.close()
            }
            return PartFingerprint.Xml(
                contentType = p.contentType,
                elementPathHash = elementPathHash,
                attrNameCount = attrNameCount,
                textHash = textHash,
                commentCount = commentCount,
                piCount = piCount,
                rawTextHash = raw.contentHashCode().toLong(),
            )
        }
    }
}
