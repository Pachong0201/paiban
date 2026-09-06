package com.gongwen.template.codec

import com.gongwen.template.model.DocumentTemplate
import com.gongwen.template.model.FindingSeverity
import com.gongwen.template.model.PageSettings
import com.gongwen.template.model.ParagraphStyle
import com.gongwen.template.model.Role
import com.gongwen.template.model.RoleStyle
import com.gongwen.template.model.TemplateMetadata
import com.gongwen.template.model.TextStyle
import com.gongwen.template.model.ValidationRule

/**
 * 极简 JSON 编解码（零第三方依赖）。
 * 生成端与解析端均在本文件；底层为递归下降解析器（可测试、可推理）。
 * 字段约定见 docs/template-schema.md。
 */
object TemplateJson {

    // ==================== 序列化 ====================

    private fun StringBuilder.comma() = append(',')

    private fun StringBuilder.str(key: String, value: String) {
        append(encode(key)).append(':').append(encode(value))
    }

    private fun StringBuilder.raw(key: String, value: Any) {
        append(encode(key)).append(':').append(value)
    }

    private fun StringBuilder.bool(key: String, value: Boolean) {
        append(encode(key)).append(':').append(value)
    }

    fun encode(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u0000' -> append("\\u0000")
                else -> append(c)
            }
        }
        append('"')
    }

    fun metadataToJson(m: TemplateMetadata): String = buildString {
        append('{')
        str("templateId", m.templateId); comma()
        str("name", m.name); comma()
        str("version", m.version); comma()
        raw("schemaVersion", m.schemaVersion); comma()
        bool("builtin", m.builtin)
        m.baseTemplate?.let { comma(); str("baseTemplate", it) }
        m.author?.let { comma(); str("author", it) }
        m.createdAt?.let { comma(); str("createdAt", it) }
        m.updatedAt?.let { comma(); str("updatedAt", it) }
        m.description?.let { comma(); str("description", it) }
        append('}')
    }

    fun pageToJson(p: PageSettings): String = buildString {
        append('{')
        raw("pageWidthTwips", p.pageWidthTwips); comma()
        raw("pageHeightTwips", p.pageHeightTwips); comma()
        str("orientation", p.orientation); comma()
        raw("marginTopTwips", p.marginTopTwips); comma()
        raw("marginBottomTwips", p.marginBottomTwips); comma()
        raw("marginLeftTwips", p.marginLeftTwips); comma()
        raw("marginRightTwips", p.marginRightTwips); comma()
        raw("headerDistanceTwips", p.headerDistanceTwips); comma()
        raw("footerDistanceTwips", p.footerDistanceTwips); comma()
        raw("textColumns", p.textColumns)
        append('}')
    }

    fun paragraphStyleToJson(p: ParagraphStyle): String = buildString {
        append('{')
        var first = true
        fun f(key: String, v: Any?) {
            if (v == null) return
            if (!first) comma()
            first = false
            when (v) {
                is String -> str(key, v)
                is Boolean -> bool(key, v)
                is Int -> raw(key, v)
            }
        }
        f("alignment", p.alignment)
        f("firstLineIndentTwips", p.firstLineIndentTwips)
        f("leftIndentTwips", p.leftIndentTwips)
        f("rightIndentTwips", p.rightIndentTwips)
        f("hangingIndentTwips", p.hangingIndentTwips)
        f("lineSpacingRule", p.lineSpacingRule)
        f("lineSpacingValue", p.lineSpacingValue)
        f("spaceBeforeTwips", p.spaceBeforeTwips)
        f("spaceAfterTwips", p.spaceAfterTwips)
        f("keepWithNext", p.keepWithNext)
        f("keepLines", p.keepLines)
        f("pageBreakBefore", p.pageBreakBefore)
        append('}')
    }

    fun textStyleToJson(t: TextStyle): String = buildString {
        append('{')
        var first = true
        fun f(key: String, v: Any?) {
            if (v == null) return
            if (!first) comma()
            first = false
            when (v) {
                is String -> str(key, v)
                is Boolean -> bool(key, v)
                is Int -> raw(key, v)
            }
        }
        f("eastAsiaFont", t.eastAsiaFont)
        f("latinFont", t.latinFont)
        f("fontSizeHalfPoints", t.fontSizeHalfPoints)
        f("bold", t.bold)
        f("italic", t.italic)
        f("underline", t.underline)
        f("characterSpacingTwips", t.characterSpacingTwips)
        f("textColor", t.textColor)
        append('}')
    }

    private fun roleStyleToJson(s: RoleStyle): String = buildString {
        append('{')
        append(encode("paragraph")).append(':').append(paragraphStyleToJson(s.paragraph)).append(',')
        append(encode("text")).append(':').append(textStyleToJson(s.text))
        append('}')
    }

    fun roleStylesToJson(styles: Map<Role, RoleStyle>): String = buildString {
        append('{')
        var first = true
        for ((role, style) in styles.entries.sortedBy { it.key.name }) {
            if (!first) comma()
            first = false
            append(encode(role.name.lowercase())).append(':').append(roleStyleToJson(style))
        }
        append('}')
    }

    fun rulesToJson(rules: List<ValidationRule>): String = buildString {
        append('[')
        for ((i, r) in rules.withIndex()) {
            if (i > 0) comma()
            append('{')
            str("id", r.id); comma()
            str("description", r.description)
            r.role?.let { comma(); str("role", it.name.lowercase()) }
            comma(); str("severity", r.severity.name)
            append('}')
        }
        append(']')
    }

    fun templateToJson(t: DocumentTemplate): String = buildString {
        append('{')
        append(encode("metadata")).append(':').append(metadataToJson(t.metadata)).append(',')
        append(encode("page")).append(':').append(pageToJson(t.page)).append(',')
        append(encode("styles")).append(':').append(roleStylesToJson(t.roleStyles)).append(',')
        append(encode("defaultText")).append(':').append(textStyleToJson(t.defaultText)).append(',')
        append(encode("rules")).append(':').append(rulesToJson(t.rules))
        append('}')
    }

    // ==================== 解析 ====================

    /** 解析完整 JSON 文档为通用节点树。 */
    fun parse(json: String): JNode {
        val p = JParser(json)
        val node = p.parseValue()
        p.skipWs()
        if (!p.atEnd()) throw IllegalArgumentException("JSON 尾部有多余内容 @${p.pos}")
        return node
    }

    private fun JNode.objOrNull(): JObject? = this as? JObject
    private fun JNode.arrOrNull(): JArray? = this as? JArray

    fun metadataFromJson(json: String): TemplateMetadata {
        val o = parse(json).objOrNull() ?: error("manifest 应为对象")
        return TemplateMetadata(
            templateId = o.str("templateId") ?: error("缺少 templateId"),
            name = o.str("name") ?: error("缺少 name"),
            version = o.str("version") ?: "1.0.0",
            schemaVersion = o.int("schemaVersion") ?: 1,
            builtin = o.bool("builtin") ?: false,
            baseTemplate = o.str("baseTemplate"),
            author = o.str("author"),
            createdAt = o.str("createdAt"),
            updatedAt = o.str("updatedAt"),
            description = o.str("description"),
        )
    }

    fun pageFromJson(json: String): PageSettings {
        val o = parse(json).objOrNull() ?: error("page 应为对象")
        return PageSettings(
            pageWidthTwips = o.int("pageWidthTwips") ?: 11906,
            pageHeightTwips = o.int("pageHeightTwips") ?: 16838,
            orientation = o.str("orientation") ?: "portrait",
            marginTopTwips = o.int("marginTopTwips") ?: 1440,
            marginBottomTwips = o.int("marginBottomTwips") ?: 1440,
            marginLeftTwips = o.int("marginLeftTwips") ?: 1654,
            marginRightTwips = o.int("marginRightTwips") ?: 1440,
            headerDistanceTwips = o.int("headerDistanceTwips") ?: 851,
            footerDistanceTwips = o.int("footerDistanceTwips") ?: 992,
            textColumns = o.int("textColumns") ?: 1,
        )
    }

    fun roleStylesFromJson(json: String): Map<Role, RoleStyle> {
        val o = parse(json).objOrNull() ?: return emptyMap()
        val out = HashMap<Role, RoleStyle>()
        for (key in o.keys()) {
            val role = Role.entries.firstOrNull { it.name.lowercase() == key } ?: continue
            val so = o.obj(key) ?: continue
            out[role] = RoleStyle(
                paragraph = paragraphStyleFromObj(so.obj("paragraph")),
                text = textStyleFromObj(so.obj("text")),
            )
        }
        return out
    }

    fun paragraphStyleFromObj(o: JObject?): ParagraphStyle {
        if (o == null) return ParagraphStyle()
        return ParagraphStyle(
            alignment = o.str("alignment"),
            firstLineIndentTwips = o.int("firstLineIndentTwips"),
            leftIndentTwips = o.int("leftIndentTwips"),
            rightIndentTwips = o.int("rightIndentTwips"),
            hangingIndentTwips = o.int("hangingIndentTwips"),
            lineSpacingRule = o.str("lineSpacingRule"),
            lineSpacingValue = o.int("lineSpacingValue"),
            spaceBeforeTwips = o.int("spaceBeforeTwips"),
            spaceAfterTwips = o.int("spaceAfterTwips"),
            keepWithNext = o.bool("keepWithNext"),
            keepLines = o.bool("keepLines"),
            pageBreakBefore = o.bool("pageBreakBefore"),
        )
    }

    fun textStyleFromObj(o: JObject?): TextStyle {
        if (o == null) return TextStyle()
        return TextStyle(
            eastAsiaFont = o.str("eastAsiaFont"),
            latinFont = o.str("latinFont"),
            fontSizeHalfPoints = o.int("fontSizeHalfPoints"),
            bold = o.bool("bold"),
            italic = o.bool("italic"),
            underline = o.str("underline"),
            characterSpacingTwips = o.int("characterSpacingTwips"),
            textColor = o.str("textColor"),
        )
    }

    /** 从独立 JSON 文本解析文字样式。 */
    fun textStyleFromJson(json: String): TextStyle =
        textStyleFromObj(parse(json) as? JObject)

    fun rulesFromJson(json: String): List<ValidationRule> {
        val arr = parse(json).arrOrNull() ?: return emptyList()
        val out = ArrayList<ValidationRule>()
        for (node in arr.items) {
            val o = node as? JObject ?: continue
            out.add(
                ValidationRule(
                    id = o.str("id") ?: "rule-${out.size}",
                    role = o.str("role")?.let { Role.entries.firstOrNull { r -> r.name.lowercase() == it } },
                    description = o.str("description") ?: "",
                    severity = when (o.str("severity")) {
                        "WARNING" -> FindingSeverity.WARNING
                        "INFO" -> FindingSeverity.INFO
                        else -> FindingSeverity.ERROR
                    },
                )
            )
        }
        return out
    }

    fun templateFromJson(json: String): DocumentTemplate {
        val o = parse(json).objOrNull() ?: error("模板应为 JSON 对象")
        val metadata = metadataFromJson(encodeToRaw(o, "metadata"))
        val page = o.obj("page")?.let { pageFromJson(it.raw) } ?: PageSettings()
        val styles = o.obj("styles")?.let { roleStylesFromJson(it.raw) } ?: emptyMap()
        val defaultText = o.obj("defaultText")?.let { textStyleFromObj(it) } ?: TextStyle()
        val rules = o.arr("rules")?.let { rulesFromJson(it.raw) } ?: emptyList()
        return DocumentTemplate(metadata, page, styles, defaultText, rules)
    }

    private fun encodeToRaw(o: JObject, key: String): String {
        val node = o.get(key) ?: error("缺少 $key")
        return node.raw
    }
}

// ==================== 通用 JSON 节点 ====================

sealed class JNode {
    abstract val raw: String
}

class JObject private constructor(val entries: Map<String, JNode>, override val raw: String) : JNode() {
    fun str(key: String): String? = (entries[key] as? JString)?.value
    fun int(key: String): Int? = (entries[key] as? JNumber)?.value?.toIntOrNull()
    fun bool(key: String): Boolean? = (entries[key] as? JBool)?.value
    fun obj(key: String): JObject? = entries[key] as? JObject
    fun arr(key: String): JArray? = entries[key] as? JArray
    fun get(key: String): JNode? = entries[key]
    fun keys(): Set<String> = entries.keys

    companion object {
        fun of(entries: Map<String, JNode>, raw: String) = JObject(entries, raw)
    }
}

class JArray(val items: List<JNode>, override val raw: String) : JNode()
class JString(val value: String, override val raw: String) : JNode()
class JNumber(val value: String, override val raw: String) : JNode()
class JBool(val value: Boolean, override val raw: String) : JNode()
class JNull(override val raw: String) : JNode()

/** 递归下降 JSON 解析器。 */
internal class JParser(private val s: String) {
    var pos = 0

    fun atEnd() = pos >= s.length

    fun skipWs() {
        while (pos < s.length && s[pos].isWhitespace()) pos++
    }

    fun parseValue(): JNode {
        skipWs()
        if (atEnd()) throw IllegalArgumentException("JSON 意外结束")
        return when (s[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> {
                val raw = parseString()
                JString(unescape(raw), raw)
            }
            't', 'f' -> {
                val raw = parseLiteral()
                JBool(raw == "true", raw)
            }
            'n' -> {
                val raw = parseLiteral()
                JNull(raw)
            }
            else -> {
                if (s[pos] == '-' || s[pos].isDigit()) {
                    val start = pos
                    while (pos < s.length && (s[pos].isDigit() || s[pos] in "-+.eE")) pos++
                    val raw = s.substring(start, pos)
                    JNumber(raw, raw)
                } else {
                    throw IllegalArgumentException("非法 JSON 字符 '${s[pos]}' @$pos")
                }
            }
        }
    }

    private fun parseObject(): JObject {
        val start = pos
        pos++ // {
        val map = LinkedHashMap<String, JNode>()
        skipWs()
        if (!atEnd() && s[pos] == '}') {
            pos++
            return JObject.of(map, s.substring(start, pos))
        }
        while (true) {
            skipWs()
            val keyRaw = parseString()
            val key = unescape(keyRaw)
            skipWs()
            expect(':')
            val value = parseValue()
            map[key] = value
            skipWs()
            if (atEnd()) throw IllegalArgumentException("JSON 对象未闭合")
            when (s[pos]) {
                ',' -> pos++
                '}' -> {
                    pos++
                    return JObject.of(map, s.substring(start, pos))
                }
                else -> throw IllegalArgumentException("JSON 对象缺少分隔符 @$pos")
            }
        }
    }

    private fun parseArray(): JArray {
        val start = pos
        pos++ // [
        val items = ArrayList<JNode>()
        skipWs()
        if (!atEnd() && s[pos] == ']') {
            pos++
            return JArray(items, s.substring(start, pos))
        }
        while (true) {
            items.add(parseValue())
            skipWs()
            if (atEnd()) throw IllegalArgumentException("JSON 数组未闭合")
            when (s[pos]) {
                ',' -> pos++
                ']' -> {
                    pos++
                    return JArray(items, s.substring(start, pos))
                }
                else -> throw IllegalArgumentException("JSON 数组缺少分隔符 @$pos")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val start = pos
        while (pos < s.length) {
            when (s[pos]) {
                '\\' -> pos += 2
                '"' -> {
                    val raw = s.substring(start, pos)
                    pos++
                    return raw
                }
                else -> pos++
            }
        }
        throw IllegalArgumentException("字符串未闭合")
    }

    private fun parseLiteral(): String {
        val start = pos
        while (pos < s.length && s[pos].isLetter()) pos++
        return s.substring(start, pos)
    }

    private fun expect(c: Char) {
        if (atEnd() || s[pos] != c) throw IllegalArgumentException("期望 '$c' @$pos")
        pos++
    }

    private fun unescape(raw: String): String {
        if ('\\' !in raw) return raw
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val n = raw[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'u' -> {
                        val hex = raw.substring(i + 2, (i + 6).coerceAtMost(raw.length))
                        sb.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                        i += 6
                        continue
                    }
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
