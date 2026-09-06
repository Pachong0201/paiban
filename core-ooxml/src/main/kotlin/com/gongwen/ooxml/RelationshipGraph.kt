package com.gongwen.ooxml

import java.io.ByteArrayInputStream
import javax.xml.stream.XMLStreamConstants

/** 一条 OPC relationship（来自某 part 的 .rels）。 */
data class OoxmlRelationship(
    val id: String,
    val type: String,
    val target: String,
    val targetMode: String?,
) {
    val isExternal: Boolean get() = targetMode == "External"
}

/** 一个 source part 的全部关系。 */
class RelationshipSet internal constructor(
    val sourcePart: String,
    val relationships: List<OoxmlRelationship>,
) {
    fun byId(id: String): OoxmlRelationship? = relationships.firstOrNull { it.id == id }
    fun byType(typeSuffix: String): List<OoxmlRelationship> =
        relationships.filter { it.type.endsWith("/$typeSuffix") || it.type.endsWith(typeSuffix) }
}

/**
 * 全包 relationship 图：source part（含包根 ""）→ RelationshipSet。
 */
class RelationshipGraph internal constructor(
    private val sets: Map<String, RelationshipSet>,
) {
    fun relationshipsOf(sourcePart: String): RelationshipSet? {
        val key = if (sourcePart.isEmpty() || sourcePart == "/") "" else OoxmlPackage.normalizePartName(sourcePart)
        return sets[key]
    }

    /** 解析 source part 的 rels 中 rId 指向的 target part 名；External 返回 null。 */
    fun resolveTarget(sourcePart: String, rId: String): String? {
        val set = relationshipsOf(sourcePart) ?: return null
        val rel = set.byId(rId) ?: return null
        if (rel.isExternal) return null
        return resolveRelative(sourcePart, rel.target)
    }

    /** 按 OPC 规则把相对 target 解析为绝对 part 路径。 */
    fun resolveRelative(sourcePart: String, target: String): String {
        val normalized = target.replace('\\', '/')
        if (normalized.startsWith("/")) return normalized
        val sourceDir = sourcePart.substringBeforeLast('/', "/")
        val segments = ArrayDeque<String>()
        val base = (sourceDir + "/" + normalized).split('/')
        for (seg in base) {
            when (seg) {
                "", "." -> {}
                ".." -> if (segments.isNotEmpty()) segments.removeLast()
                else -> segments.addLast(seg)
            }
        }
        return "/" + segments.joinToString("/")
    }

    /** source part 的直接 XML 子 part 集合（含通过 relationships 链可达的 header/footer 等）。 */
    fun directTargets(sourcePart: String): List<String> {
        val set = relationshipsOf(sourcePart) ?: return emptyList()
        return set.relationships.filterNot { it.isExternal }
            .map { resolveRelative(sourcePart, it.target) }
    }

    companion object {
        fun parse(pkg: OoxmlPackage): RelationshipGraph {
            val sets = HashMap<String, RelationshipSet>()
            for (part in pkg.parts()) {
                if (!part.name.endsWith(".rels")) continue
                val source = if (part.name == "/_rels/.rels") {
                    ""
                } else {
                    val noRels = part.name.removeSuffix(".rels")
                        .removePrefix("/")
                    // "/word/_rels/document.xml.rels" -> "/word/document.xml"
                    if (noRels.endsWith("/_rels/")) "" else {
                        val idx = noRels.lastIndexOf("/_rels/")
                        val dir = noRels.substring(0, idx)
                        val file = noRels.substring(idx + "/_rels/".length)
                        OoxmlPackage.normalizePartName("$dir/$file")
                    }
                }
                val rels = parseRels(part)
                if (rels.isNotEmpty() || source.isNotEmpty()) {
                    sets[source] = RelationshipSet(source, rels)
                }
            }
            return RelationshipGraph(sets)
        }

        private fun parseRels(part: OoxmlPart): List<OoxmlRelationship> {
            val out = ArrayList<OoxmlRelationship>()
            val factory = ContentTypeRegistry.secureXmlInputFactory()
            val reader = try {
                factory.createXMLStreamReader(ByteArrayInputStream(part.byteArray()))
            } catch (e: Exception) {
                throw CorruptPackageException("无法解析 ${part.name}: ${e.message}", e)
            }
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT &&
                        reader.localName == "Relationship"
                    ) {
                        val id = reader.getAttributeValue(null, "Id")
                        val type = reader.getAttributeValue(null, "Type")
                        val target = reader.getAttributeValue(null, "Target")
                        val mode = reader.getAttributeValue(null, "TargetMode")
                        if (id != null && type != null && target != null) {
                            out.add(OoxmlRelationship(id, type, target, mode))
                        }
                    }
                }
            } catch (e: Exception) {
                throw CorruptPackageException("无法解析 ${part.name}: ${e.message}", e)
            } finally {
                reader.close()
            }
            return out
        }
    }
}
