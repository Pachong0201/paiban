package com.gongwen.ooxml

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 将 [OoxmlPackage] 序列化为 DOCX ZIP 字节流。
 *
 * - 每个 part 的内容写回其原始字节（未修改即字节级保真，修改过的由上层保证已是新字节）；
 * - [Content_Types].xml 由 registry 状态重建，保证新增 part 也有内容类型声明；
 * - 条目顺序：先写 [Content_Types].xml（OPC 要求可选但 Word 友好），再按 part 名稳定排序。
 */
object OoxmlPackageSerializer {

    fun serialize(pkg: OoxmlPackage): ByteArray {
        val out = ByteArrayOutputStream()
        serialize(pkg, out)
        return out.toByteArray()
    }

    fun serialize(pkg: OoxmlPackage, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.setLevel(ZipOutputStream.DEFLATED)
            val names = pkg.partNames().toMutableList()
            val ctPart = pkg.part("/[Content_Types].xml")
            if (ctPart != null) {
                names.remove("/[Content_Types].xml")
                // P0-2：part 集合未增删时 [Content_Types].xml 必须写回原始字节；
                // 仅当包发生结构性变化（新增/删除 part 或内容类型变更）时才重建。
                val bytes = if (pkg.contentTypesDirty) {
                    buildContentTypesXml(pkg)
                } else {
                    ctPart.byteArray()
                }
                writeEntry(zip, "[Content_Types].xml", bytes)
            }
            for (name in names.sorted()) {
                val part = pkg.requirePart(name)
                writeEntry(zip, name.substring(1), part.byteArray())
            }
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name)
        entry.size = bytes.size.toLong()
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    /** 依据当前 part 集合重建 [Content_Types].xml。 */
    fun buildContentTypesXml(pkg: OoxmlPackage): ByteArray {
        val sb = StringBuilder(4096)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        // Default 段（按扩展名归并）
        val defaultTypes = LinkedHashMap<String, String>()
        for (p in pkg.parts()) {
            if (p.name == "/[Content_Types].xml") continue
            val ext = p.name.substringAfterLast('.', "")
            if (ext.isNotEmpty() && p.contentType.endsWith("+xml").not() &&
                p.contentType !in setOf(OoxmlContentTypes.RELATIONSHIPS, OoxmlContentTypes.CONTENT_TYPES)
            ) {
                // 除 XML 系外的二进制扩展名走 Default
                defaultTypes.putIfAbsent(ext, p.contentType)
            }
        }
        // 额外标准 Default
        defaultTypes.putIfAbsent("rels", OoxmlContentTypes.RELATIONSHIPS)
        defaultTypes.putIfAbsent("xml", OoxmlContentTypes.XML)
        for ((ext, ct) in defaultTypes) {
            sb.append("<Default Extension=\"").append(ext).append("\" ContentType=\"")
                .append(ct).append("\"/>")
        }
        // Override 段：所有 XML 系 part（内容类型需要精确声明，包括 styles/rels 之外的 xml）
        for (p in pkg.parts()) {
            if (p.name == "/[Content_Types].xml") continue
            val ext = p.name.substringAfterLast('.', "")
            if (ext == "rels") continue // rels 由 Default 覆盖
            if (p.contentType.endsWith("+xml") || p.contentType == OoxmlContentTypes.XML) {
                sb.append("<Override PartName=\"").append(p.name).append("\" ContentType=\"")
                    .append(p.contentType).append("\"/>")
            }
        }
        sb.append("</Types>")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
}
