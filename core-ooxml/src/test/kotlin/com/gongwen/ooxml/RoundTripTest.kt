package com.gongwen.ooxml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * P0 RoundTrip 门禁测试（规格 §42、§54）：
 * 1. open → saveAs → reopen：语义 package 零丢失；
 * 2. 编辑单个 part 后导出：无关部分（二进制媒体、未知 part、其余 XML）零变化。
 */
class RoundTripTest {

    @Test
    fun openSaveReopen_preservesAllPartsAndBinary() {
        val original = TestDocxBuilder.build()
        val pkg = OoxmlPackage.open(original)

        // 打开时应能识别主要 part
        assertNotNull(pkg.requirePart("/word/document.xml"))
        assertNotNull(pkg.requirePart("/word/media/image1.png"))
        assertNotNull(pkg.requirePart("/word/customBlob.bin"))
        assertEquals("image/png", pkg.requirePart("/word/media/image1.png").contentType)

        val snapshotBefore = PackageSnapshot.capture(pkg)

        val saved = OoxmlPackageSerializer.serialize(pkg)
        val reopened = OoxmlPackage.open(saved)
        val snapshotAfter = PackageSnapshot.capture(reopened)

        val diffs = snapshotBefore.diff(snapshotAfter)
        assertTrue("round-trip 应零语义差异，实际: ${diffs.joinToString("; ")}", diffs.isEmpty())
    }

    @Test
    fun editOnePart_leavesOthersByteIdentical() {
        val original = TestDocxBuilder.build()
        val pkg = OoxmlPackage.open(original)

        // 语义编辑：仅修改 document.xml（追加一段文字）
        val doc = pkg.requirePart("/word/document.xml")
        val editedXml = String(doc.byteArray(), Charsets.UTF_8)
            .replace("hello", "hello world")
        doc.replaceBytes(editedXml.toByteArray(Charsets.UTF_8))

        val saved = OoxmlPackageSerializer.serialize(pkg)
        val reopened = OoxmlPackage.open(saved)

        // 被编辑 part：内容按预期变化
        val newDoc = String(reopened.requirePart("/word/document.xml").byteArray(), Charsets.UTF_8)
        assertTrue(newDoc.contains("hello world"))

        // 无关 part：二进制与未知 part 必须逐字节一致
        assertTrue(
            TestDocxBuilder.FAKE_PNG.contentEquals(
                reopened.requirePart("/word/media/image1.png").byteArray()
            )
        )
        assertTrue(
            byteArrayOf(1, 2, 3, 4, 5, 0x7f.toByte()).contentEquals(
                reopened.requirePart("/word/customBlob.bin").byteArray()
            )
        )
        // styles.xml 等未修改 XML part 必须逐字节一致
        val stylesBefore = pkg.requirePart("/word/styles.xml").byteArray()
        val stylesAfter = reopened.requirePart("/word/styles.xml").byteArray()
        assertTrue("未修改 XML part 必须字节一致", stylesBefore.contentEquals(stylesAfter))
    }

    @Test
    fun snapshotDetectsChangedPart() {
        val original = TestDocxBuilder.build()
        val pkg1 = OoxmlPackage.open(original)
        val before = PackageSnapshot.capture(pkg1)

        pkg1.requirePart("/word/document.xml").replaceBytes(
            String(pkg1.requirePart("/word/document.xml").byteArray(), Charsets.UTF_8)
                .replace("hello", "CHANGED")
                .toByteArray(Charsets.UTF_8)
        )
        val after = PackageSnapshot.capture(pkg1)

        val diffs = before.diff(after)
        assertTrue(diffs.any { it.contains("/word/document.xml") })
        assertEquals(1, diffs.size)
    }

    @Test
    fun binaryHashIsContentBased() {
        val original = TestDocxBuilder.build()
        val pkg = OoxmlPackage.open(original)
        val snap = PackageSnapshot.capture(pkg)
        val fp = snap.partFingerprints["/word/media/image1.png"]
        assertTrue(fp is PackageSnapshot.PartFingerprint.Binary)
        fp as PackageSnapshot.PartFingerprint.Binary
        assertEquals(64, fp.sha256.length)
        assertEquals(TestDocxBuilder.FAKE_PNG.size.toLong(), fp.size)
    }
}
