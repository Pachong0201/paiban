package com.gongwen.ooxml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 包打开/内容类型/关系图/安全防护的单元测试。 */
class OoxmlPackageTest {

    @Test
    fun open_parsesContentTypesAndParts() {
        val pkg = OoxmlPackage.open(TestDocxBuilder.build())
        assertEquals(7, pkg.partCount)
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml",
            pkg.requirePart("/word/document.xml").contentType
        )
        assertEquals("image/png", pkg.requirePart("/word/media/image1.png").contentType)
        assertEquals("application/octet-stream", pkg.requirePart("/word/customBlob.bin").contentType)
    }

    @Test
    fun open_rejectsZipSlipPaths() {
        // 构造带 ../ 路径的恶意 zip
        val bytes = TestDocxBuilder.build()
        val bad = evilZip()
        try {
            OoxmlPackage.open(bad)
            fail("应当拒绝 zip-slip 路径")
        } catch (e: CorruptPackageException) {
            assertTrue(e.message!!.contains("非法 part 路径"))
        }
    }

    private fun evilZip(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("../evil.txt"))
            zip.write("boom".toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    @Test
    fun relationshipGraph_resolvesTargets() {
        val pkg = OoxmlPackage.open(TestDocxBuilder.build())
        val graph = RelationshipGraph.parse(pkg)

        // 根 rels → document.xml
        assertEquals("/word/document.xml", graph.resolveTarget("", "rId1"))

        // document rels：styles + 图片 + 外部超链接
        assertEquals("/word/styles.xml", graph.resolveTarget("/word/document.xml", "rId1"))
        assertEquals("/word/media/image1.png", graph.resolveTarget("/word/document.xml", "rId100"))
        // 外部 target 返回 null
        assertNull(graph.resolveTarget("/word/document.xml", "rId10"))
    }

    @Test
    fun missingContentType_declaration_isRejected() {
        // 去掉 png 的 Default 声明，构造后不写 [Content_Types].xml 的 png 类型
        val raw = TestDocxBuilder.build()
        val pkg = OoxmlPackage.open(raw)
        // 构造一个没有声明 content type 的 part
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            for (name in listOf("[Content_Types].xml", "_rels/.rels", "word/document.xml",
                    "word/_rels/document.xml.rels", "word/styles.xml", "word/media/image1.png",
                    "word/customBlob.bin")) {
                val part = pkg.requirePart("/$name")
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(part.byteArray())
                zip.closeEntry()
            }
        }
        val stripped = String(pkg.requirePart("/[Content_Types].xml").byteArray(), Charsets.UTF_8)
            .replace("""<Default Extension="png" ContentType="image/png"/>""", "")
            .replace("""<Override PartName="/word/customBlob.bin" ContentType="application/octet-stream"/>""", "")
        // 直接测试 registry 行为：png 无法解析 → 抛错在 open 时发生
        val rebuilt = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(rebuilt).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("[Content_Types].xml"))
            zip.write(stripped.toByteArray())
            zip.closeEntry()
            for (name in listOf("_rels/.rels", "word/document.xml",
                    "word/_rels/document.xml.rels", "word/styles.xml", "word/media/image1.png",
                    "word/customBlob.bin")) {
                val part = pkg.requirePart("/$name")
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(part.byteArray())
                zip.closeEntry()
            }
        }
        try {
            OoxmlPackage.open(rebuilt.toByteArray())
            fail("缺少 content type 声明的 part 应被拒绝")
        } catch (e: CorruptPackageException) {
            assertTrue(e.message!!.contains("缺少内容类型声明"))
        }
    }

    @Test
    fun packageLimits_rejectBomb() {
        // 构造高 entry 数会触发限额
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            repeat(10_000) {
                zip.putNextEntry(java.util.zip.ZipEntry("word/filler$it.txt"))
                zip.write("x".repeat(1024).toByteArray())
                zip.closeEntry()
            }
        }
        val limits = PackageLimits(maxEntryCount = 5_000)
        try {
            OoxmlPackage.open(out.toByteArray(), limits)
            fail("超出 entry 上限应被拒绝")
        } catch (e: PackageLimitExceededException) {
            assertTrue(e.message!!.contains("entry"))
        }
    }

    @Test
    fun serializer_rebuildsContentTypesForNewParts() {
        val pkg = OoxmlPackage.open(TestDocxBuilder.build())
        pkg.putPart("/word/media/image2.jpg", "image/jpeg", byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()))

        val saved = OoxmlPackageSerializer.serialize(pkg)
        val reopened = OoxmlPackage.open(saved)
        assertNotNull(reopened.requirePart("/word/media/image2.jpg"))
        assertEquals("image/jpeg", reopened.requirePart("/word/media/image2.jpg").contentType)
        assertFalse(
            "新增 jpg 后 round-trip 中新增 part 被识别",
            reopened.requirePart("/word/media/image2.jpg").byteArray().isEmpty()
        )
    }
}
