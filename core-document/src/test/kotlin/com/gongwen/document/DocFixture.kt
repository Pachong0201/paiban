package com.gongwen.document

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * core-document 测试 fixture：构造含多段、表格、图片引用的 DOCX。
 */
object DocFixture {

    private const val MAIN_CT =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"

    fun build(
        paragraphsXml: List<String> = listOf(
            """<w:p><w:pPr><w:jc w:val="center"/><w:spacing w:line="560" w:lineRule="exact"/></w:pPr><w:r><w:rPr><w:rFonts w:eastAsia="方正小标宋简体"/><w:sz w:val="44"/></w:rPr><w:t>关于测试的通知</w:t></w:r></w:p>""",
            """<w:p><w:pPr><w:jc w:val="both"/><w:ind w:firstLine="480"/><w:spacing w:line="560" w:lineRule="exact"/></w:pPr><w:r><w:rPr><w:rFonts w:eastAsia="仿宋_GB2312"/><w:sz w:val="32"/></w:rPr><w:t>各有关单位：</w:t></w:r></w:p>""",
            """<w:p><w:pPr><w:ind w:firstLine="480"/></w:pPr><w:r><w:t>正文第一段内容。</w:t></w:r><w:r><w:t>正文第二句。</w:t></w:r></w:p>""",
            """<w:p><w:pPr><w:ind w:firstLine="480"/></w:pPr><w:r><w:t>一、提高认识</w:t></w:r></w:p>""",
            """<w:p><w:pPr><w:ind w:firstLine="480"/></w:pPr><w:r><w:t>正文内容。</w:t></w:r></w:p>""",
        ),
        tableXml: String? = """<w:tbl><w:tblPr><w:tblW w:w="9600" w:type="dxa"/></w:tblPr><w:tr><w:tc><w:p><w:r><w:t>单位</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>数量</w:t></w:r></w:p></w:tc></w:tr></w:tbl>""",
        withImage: Boolean = true,
    ): ByteArray {
        val body = StringBuilder()
        paragraphsXml.forEach { body.append(it) }
        if (tableXml != null) body.append(tableXml)
        body.append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/></w:sectPr>""")

        val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body>$body</w:body>
</w:document>"""

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            put(zip, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Default Extension="png" ContentType="image/png"/>
<Override PartName="/word/document.xml" ContentType="$MAIN_CT"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
<Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>
</Types>""")
            put(zip, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""")
            put(zip, "word/document.xml", docXml)
            put(zip, "word/_rels/document.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
</Relationships>""")
            put(zip, "word/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Times New Roman" w:eastAsia="仿宋_GB2312"/><w:sz w:val="32"/></w:rPr></w:rPrDefault></w:docDefaults>
</w:styles>""")
            put(zip, "word/numbering.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"></w:numbering>""")
            if (withImage) {
                put(zip, "word/media/image1.png", java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
                ))
            }
        }
        return out.toByteArray()
    }

    private fun put(zip: ZipOutputStream, name: String, content: String) {
        put(zip, name, content.toByteArray(Charsets.UTF_8))
    }

    private fun put(zip: ZipOutputStream, name: String, content: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content)
        zip.closeEntry()
    }
}
