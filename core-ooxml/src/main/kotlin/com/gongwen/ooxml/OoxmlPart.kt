package com.gongwen.ooxml

/**
 * 单个 OOXML Part：统一以 [name]（OPC 路径，"/" 开头）、[contentType] 与原始字节为真相。
 * Part 不解析内容；任何解析/序列化由上层按需进行。
 */
class OoxmlPart internal constructor(
    val name: String,
    var contentType: String,
    internal var bytes: ByteArray,
) {
    val sizeBytes: Int get() = bytes.size

    /** 替换整个 part 内容（二进制 part 或整体重写后的 XML）。 */
    fun replaceBytes(newBytes: ByteArray) {
        bytes = newBytes
    }

    fun byteArray(): ByteArray = bytes

    /** 该 part 是否 XML（按 content type 或扩展名判断）。 */
    val isXml: Boolean
        get() {
            if (contentType == OoxmlContentTypes.XML) return true
            if (contentType.substringAfterLast('/').lowercase().endsWith("+xml")) return true
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext == "xml" || ext == "rels"
        }
}
