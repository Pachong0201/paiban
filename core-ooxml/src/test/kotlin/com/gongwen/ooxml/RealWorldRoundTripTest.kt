package com.gongwen.ooxml

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 真实世界 DOCX round-trip：python-docx 默认模板（含 customXml/numbering/
 * fontTable/stylesWithEffects/theme/docProps/thumbnail 等 17 个 part）。
 * 验证 open → saveAs → reopen 语义零丢失，且未修改 part 字节级保真。
 */
class RealWorldRoundTripTest {

    private val corpusDir: File = File("testdata/corpus/roundtrip")

    @Test
    fun pythonDocxDefault_roundTripsCleanly() {
        val file = File(corpusDir, "python-docx-default.docx")
        assertTrue("corpus 样本缺失: ${file.absolutePath}", file.exists())

        val originalBytes = file.readBytes()
        val pkg = OoxmlPackage.open(originalBytes)
        val before = PackageSnapshot.capture(pkg)

        val saved = OoxmlPackageSerializer.serialize(pkg)
        val reopened = OoxmlPackage.open(saved)
        val after = PackageSnapshot.capture(reopened)

        val diffs = before.diff(after)
        assertTrue("round-trip 出现语义差异: ${diffs.joinToString("; ")}", diffs.isEmpty())
    }

    @Test
    fun pythonDocxDefault_untouchedXmlPartsAreByteIdentical() {
        val file = File(corpusDir, "python-docx-default.docx")
        val pkg = OoxmlPackage.open(file.readBytes())

        for (part in pkg.parts()) {
            if (part.name == "/[Content_Types].xml") continue
            val byteBefore = part.byteArray()

            val saved = OoxmlPackageSerializer.serialize(pkg)
            val reopened = OoxmlPackage.open(saved)
            val byteAfter = reopened.requirePart(part.name).byteArray()

            assertTrue(
                "未修改 part ${part.name} 必须字节级保真",
                byteBefore.contentEquals(byteAfter)
            )
        }
    }
}
