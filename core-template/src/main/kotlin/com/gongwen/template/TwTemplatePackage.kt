package com.gongwen.template

import com.gongwen.template.codec.TemplateJson
import com.gongwen.template.model.DocumentTemplate
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** .twtemplate 打包/解包（ZIP 容器，结构见 docs/template-schema.md §1）。 */
object TwTemplatePackage {

    const val MANIFEST = "manifest.json"
    const val PAGE = "page.json"
    const val STYLES = "styles.json"
    const val RULES = "rules.json"
    const val DEFAULT_TEXT = "defaultText.json"

    /** 导出为 .twtemplate 字节。 */
    fun pack(template: DocumentTemplate): ByteArray {
        val out = ByteArrayOutputStream()
        pack(template, out)
        return out.toByteArray()
    }

    fun pack(template: DocumentTemplate, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            put(zip, MANIFEST, TemplateJson.metadataToJson(template.metadata))
            put(zip, PAGE, TemplateJson.pageToJson(template.page))
            put(zip, STYLES, TemplateJson.roleStylesToJson(template.roleStyles))
            put(zip, RULES, TemplateJson.rulesToJson(template.rules))
            put(zip, DEFAULT_TEXT, TemplateJson.textStyleToJson(template.defaultText))
        }
    }

    /** 从 .twtemplate 字节导入。 */
    fun unpack(bytes: ByteArray): DocumentTemplate {
        var manifest: String? = null
        var page: String? = null
        var styles: String? = null
        var rules: String? = null
        var defaultText: String? = null
        ZipInputStream(bytes.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val content = zip.readBytes()
                when (e.name) {
                    MANIFEST -> manifest = String(content, Charsets.UTF_8)
                    PAGE -> page = String(content, Charsets.UTF_8)
                    STYLES -> styles = String(content, Charsets.UTF_8)
                    RULES -> rules = String(content, Charsets.UTF_8)
                    DEFAULT_TEXT -> defaultText = String(content, Charsets.UTF_8)
                }
                e = zip.nextEntry
            }
        }
        val manifestJson = manifest ?: error(".twtemplate 缺少 manifest.json")
        val metadata = TemplateJson.metadataFromJson(manifestJson)
        return DocumentTemplate(
            metadata = metadata,
            page = page?.let { TemplateJson.pageFromJson(it) } ?: com.gongwen.template.model.PageSettings(),
            roleStyles = styles?.let { TemplateJson.roleStylesFromJson(it) } ?: emptyMap(),
            defaultText = defaultText?.let { TemplateJson.textStyleFromJson(it) }
                ?: com.gongwen.template.model.TextStyle(),
            rules = rules?.let { TemplateJson.rulesFromJson(it) } ?: emptyList(),
        )
    }

    private fun put(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
