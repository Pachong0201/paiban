package com.gongwen.ooxml

/** OOXML / OPC 命名空间常量。 */
object OoxmlNamespaces {
    const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    const val WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
    const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    const val PIC = "http://schemas.openxmlformats.org/drawingml/2006/picture"
    const val RELS = "http://schemas.openxmlformats.org/package/2006/relationships"
    const val CT = "http://schemas.openxmlformats.org/package/2006/content-types"
}

/** Office 内容类型（Content Types）。 */
object OoxmlContentTypes {
    const val DOCX_MAIN_DOCUMENT =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
    const val XML = "application/xml"
    const val RELATIONSHIPS = "application/vnd.openxmlformats-package.relationships+xml"
    const val CONTENT_TYPES = "application/vnd.openxmlformats-package.content-types+xml"
    const val STYLES =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"
    const val NUMBERING =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"
    const val SETTINGS =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"
    const val FONT_TABLE =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml"
    const val THEME =
        "application/vnd.openxmlformats-officedocument.theme+xml"
    const val HEADER =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"
    const val FOOTER =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"
    const val FOOTNOTES =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml"
    const val ENDNOTES =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml"
    const val COMMENTS =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml"
    const val CUSTOM_XML =
        "application/vnd.openxmlformats-officedocument.customXmlProperties+xml"
    const val CUSTOM_XML_ITEM = "application/xml"
    const val CORE_PROPERTIES = "application/vnd.openxmlformats-package.core-properties+xml"
    const val APP_PROPERTIES = "application/vnd.openxmlformats-officedocument.extended-properties+xml"
    const val PNG = "image/png"
    const val JPEG = "image/jpeg"
    const val GIF = "image/gif"
    const val BMP = "image/bmp"
    const val EMF = "image/x-emf"
    const val WMF = "image/x-wmf"
    const val TIFF = "image/tiff"
    const val OLE_OBJECT =
        "application/vnd.openxmlformats-officedocument.oleObject"
    const val PACKAGE = "application/vnd.openxmlformats-officedocument.package"
    const val MACRO_ENABLED_DOCUMENT =
        "application/vnd.ms-word.document.macroEnabled.main+xml"
}
