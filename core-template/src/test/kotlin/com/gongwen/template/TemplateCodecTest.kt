package com.gongwen.template

import com.gongwen.template.codec.TemplateJson
import com.gongwen.template.model.DocumentTemplate
import com.gongwen.template.model.FindingSeverity
import com.gongwen.template.model.PageSettings
import com.gongwen.template.model.ParagraphStyle
import com.gongwen.template.model.Role
import com.gongwen.template.model.RoleStyle
import com.gongwen.template.model.TemplateMetadata
import com.gongwen.template.model.TextStyle
import com.gongwen.template.model.ValidationRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateCodecTest {

    private fun sampleTemplate(id: String, base: String? = null): DocumentTemplate {
        val styles = mapOf(
            Role.TITLE to RoleStyle(
                paragraph = ParagraphStyle(alignment = "center", lineSpacingRule = "exact", lineSpacingValue = 680),
                text = TextStyle(eastAsiaFont = "方正小标宋简体", fontSizeHalfPoints = 44),
            ),
            Role.BODY to RoleStyle(
                paragraph = ParagraphStyle(firstLineIndentTwips = 480, alignment = "both"),
                text = TextStyle(eastAsiaFont = "仿宋_GB2312", fontSizeHalfPoints = 32),
            ),
            Role.HEADING1 to RoleStyle(
                paragraph = ParagraphStyle(firstLineIndentTwips = 480),
                text = TextStyle(eastAsiaFont = "黑体", fontSizeHalfPoints = 32),
            ),
        )
        return DocumentTemplate(
            metadata = TemplateMetadata(
                templateId = id,
                name = "测试模板 $id",
                baseTemplate = base,
                schemaVersion = 1,
            ),
            page = PageSettings(),
            roleStyles = styles,
            rules = listOf(
                ValidationRule("r1", Role.TITLE, "标题必须居中", FindingSeverity.ERROR),
            ),
        )
    }

    @Test
    fun templateJson_roundTrips() {
        val t = sampleTemplate("gbt-test")
        val json = TemplateJson.templateToJson(t)
        val back = TemplateJson.templateFromJson(json)

        assertEquals(t.metadata.templateId, back.metadata.templateId)
        assertEquals(t.metadata.name, back.metadata.name)
        assertEquals("方正小标宋简体", back.styleFor(Role.TITLE).text.eastAsiaFont)
        assertEquals(44, back.styleFor(Role.TITLE).text.fontSizeHalfPoints)
        assertEquals("center", back.styleFor(Role.TITLE).paragraph.alignment)
        assertEquals(480, back.styleFor(Role.BODY).paragraph.firstLineIndentTwips)
        assertEquals("仿宋_GB2312", back.styleFor(Role.BODY).text.eastAsiaFont)
        assertEquals("黑体", back.styleFor(Role.HEADING1).text.eastAsiaFont)
        assertEquals(1, back.rules.size)
        assertEquals("r1", back.rules[0].id)
        // page 往返
        assertEquals(back.page.pageWidthTwips, PageSettings().pageWidthTwips)
    }

    @Test
    fun json_handlesSpecialCharacters() {
        val t = sampleTemplate("x").copy(
            metadata = TemplateMetadata(
                templateId = "x", name = "含\"引号\"和\\反斜杠及\n换行", baseTemplate = "gbt",
            )
        )
        val json = TemplateJson.templateToJson(t)
        val back = TemplateJson.templateFromJson(json)
        assertEquals("含\"引号\"和\\反斜杠及\n换行", back.metadata.name)
    }

    @Test
    fun resolver_mergesChildOverBase() {
        val base = sampleTemplate("gbt")
        val child = DocumentTemplate(
            metadata = TemplateMetadata(templateId = "unit-a", name = "A单位", baseTemplate = "gbt"),
            page = PageSettings(marginLeftTwips = 1700),
            roleStyles = mapOf(
                // 只覆盖正文黑体、字号三号
                Role.BODY to RoleStyle(
                    paragraph = ParagraphStyle(firstLineIndentTwips = 480), // 显式声明同值
                    text = TextStyle(eastAsiaFont = "仿宋", fontSizeHalfPoints = 32),
                ),
            ),
        )
        val effective = TemplateResolver.resolveEffective("unit-a", mapOf("gbt" to base, "unit-a" to child))

        // 覆盖生效
        assertEquals("仿宋", effective.styleFor(Role.BODY).text.eastAsiaFont)
        // 未覆盖的继承
        assertEquals("方正小标宋简体", effective.styleFor(Role.TITLE).text.eastAsiaFont)
        assertEquals("center", effective.styleFor(Role.TITLE).paragraph.alignment)
        assertEquals(44, effective.styleFor(Role.TITLE).text.fontSizeHalfPoints)
        // 一级标题继承 base（黑体）
        assertEquals("黑体", effective.styleFor(Role.HEADING1).text.eastAsiaFont)
        // page 覆盖
        assertEquals(1700, effective.page.marginLeftTwips)
        // metadata base 保留
        assertEquals("gbt", effective.metadata.baseTemplate)
    }

    @Test
    fun resolver_detectsCycle() {
        val a = sampleTemplate("a").copy(metadata = TemplateMetadata("a", "A", baseTemplate = "b"))
        val b = sampleTemplate("b").copy(metadata = TemplateMetadata("b", "B", baseTemplate = "a"))
        val errors = TemplateResolver.validateInheritance(mapOf("a" to a, "b" to b))
        assertTrue("应检测到环", errors.any { it.contains("环") })
    }

    @Test
    fun resolver_missingBase_reported() {
        val a = sampleTemplate("a", base = "nope")
        val errors = TemplateResolver.validateInheritance(mapOf("a" to a))
        assertTrue(errors.any { it.contains("不存在") })
    }
}
