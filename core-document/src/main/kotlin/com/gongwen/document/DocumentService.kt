package com.gongwen.document

import com.gongwen.ooxml.OoxmlPackage
import com.gongwen.document.xml.DocumentParser
import com.gongwen.document.xml.DocumentXmlPart

/** 文档打开门面：字节/包 → WorkingDocument。 */
object DocumentService {

    /** 从 DOCX 字节打开。 */
    fun open(bytes: ByteArray, sourceDescriptor: String = "<bytes>"): WorkingDocument {
        val pkg = OoxmlPackage.open(bytes)
        return open(pkg, sourceDescriptor)
    }

    /** 从已打开的 OoxmlPackage 打开（共享同一包实例以支持导出回写）。 */
    fun open(pkg: OoxmlPackage, sourceDescriptor: String = pkg.sourceDescriptor): WorkingDocument {
        val docPart = pkg.requirePart("/word/document.xml")
        val xmlPart = DocumentXmlPart.parse(docPart.byteArray())
        val parsed = DocumentParser(xmlPart).parse()
        return WorkingDocument(sourceDescriptor, xmlPart, parsed)
    }
}
