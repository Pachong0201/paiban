package com.gongwen.template

import com.gongwen.template.model.DocumentTemplate
import com.gongwen.template.model.ParagraphStyle
import com.gongwen.template.model.Role
import com.gongwen.template.model.RoleStyle
import com.gongwen.template.model.TemplateMetadata
import com.gongwen.template.model.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TwTemplatePackageTest {

    @Test
    fun packUnpack_roundTrips() {
        val t = DocumentTemplate(
            metadata = TemplateMetadata(
                templateId = "unit-b",
                name = "XX单位正式公文",
                version = "1.0.0",
                baseTemplate = "gbt-9704-2012",
            ),
            roleStyles = mapOf(
                Role.BODY to RoleStyle(
                    paragraph = ParagraphStyle(firstLineIndentTwips = 480),
                    text = TextStyle(eastAsiaFont = "仿宋", fontSizeHalfPoints = 32),
                ),
                Role.TITLE to RoleStyle(
                    paragraph = ParagraphStyle(alignment = "center"),
                    text = TextStyle(eastAsiaFont = "方正小标宋简体", fontSizeHalfPoints = 44),
                ),
            ),
        )
        val bytes = TwTemplatePackage.pack(t)
        val back = TwTemplatePackage.unpack(bytes)

        assertEquals("unit-b", back.metadata.templateId)
        assertEquals("gbt-9704-2012", back.metadata.baseTemplate)
        assertEquals("仿宋", back.styleFor(Role.BODY).text.eastAsiaFont)
        assertEquals(480, back.styleFor(Role.BODY).paragraph.firstLineIndentTwips)
        assertEquals("center", back.styleFor(Role.TITLE).paragraph.alignment)
        assertEquals(44, back.styleFor(Role.TITLE).text.fontSizeHalfPoints)
        assertTrue(back.styleFor(Role.HEADING1).text.eastAsiaFont == null) // 未定义
    }
}
