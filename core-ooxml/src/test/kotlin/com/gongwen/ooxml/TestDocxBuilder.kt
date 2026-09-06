package com.gongwen.ooxml

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 测试辅助：手工构造最小合法 DOCX（不依赖任何第三方库），
 * 用于 round-trip 与解析测试。内容可控，方便断言。
 */
object TestDocxBuilder {

    fun minimalDocumentXml(bodyXml: String = "<w:p><w:r><w:t>hello</w:t></w:r></w:p>"): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    $bodyXml
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/>
    </w:sectPr>
  </w:body>
</w:document>
"""

    /** 构造包含自定义 body、可选 png 与未知 part 的 DOCX。 */
    fun build(
        bodyXml: String = minimalDocumentXml(),
        withImage: Boolean = true,
        unknownPartName: String? = "word/customBlob.bin",
        unknownPartBytes: ByteArray = byteArrayOf(1, 2, 3, 4, 5, 0x7f.toByte()),
        commentXml: String? = null,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            put(zip, "[Content_Types].xml", contentTypes(withImage, unknownPartName, commentXml != null))
            put(zip, "_rels/.rels", rootRels())
            put(zip, "word/document.xml", bodyXml)
            put(zip, "word/_rels/document.xml.rels", docRels(withImage, commentXml != null))
            put(zip, "word/styles.xml", minimalStyles())
            if (withImage) put(zip, "word/media/image1.png", FAKE_PNG)
            if (unknownPartName != null) put(zip, unknownPartName, unknownPartBytes)
            if (commentXml != null) {
                put(zip, "word/comments.xml", commentXml)
                put(zip, "word/_rels/comments.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>""")
            }
        }
        return out.toByteArray()
    }

    private fun put(zip: ZipOutputStream, name: String, content: String) {
        put(zip, name, content.toByteArray(Charsets.UTF_8))
    }

    private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    fun contentTypes(withImage: Boolean, unknownPart: String?, withComments: Boolean): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Default Extension="png" ContentType="image/png"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>""")
        if (unknownPart != null) {
            sb.append("""<Override PartName="/$unknownPart" ContentType="application/octet-stream"/>""")
        }
        if (withComments) {
            sb.append("""<Override PartName="/word/comments.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml"/>""")
        }
        sb.append("</Types>")
        return sb.toString()
    }

    fun rootRels(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    fun docRels(withImage: Boolean, withComments: Boolean): String {
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
<Relationship Id="rId10" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="https://example.com" TargetMode="External"/>""")
        if (withImage) {
            sb.append("""<Relationship Id="rId100" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>""")
        }
        if (withComments) {
            sb.append("""<Relationship Id="rId200" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments" Target="comments.xml"/>""")
        }
        sb.append("</Relationships>")
        return sb.toString()
    }

    fun minimalStyles(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults>
<w:rPrDefault><w:rPr><w:rFonts w:ascii="Times New Roman" w:eastAsia="宋体"/><w:sz w:val="24"/></w:rPr></w:rPrDefault>
</w:docDefaults>
</w:styles>"""

    /** 一段有效的 1x1 红色 PNG。 */
    val FAKE_PNG: ByteArray = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    )
}
