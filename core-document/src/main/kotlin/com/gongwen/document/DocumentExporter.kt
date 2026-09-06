package com.gongwen.document

import com.gongwen.ooxml.OoxmlPackage
import com.gongwen.ooxml.OoxmlPackageSerializer
import com.gongwen.ooxml.PackageSnapshot

/** DOCX 导出结果。 */
sealed class ExportResult {
    data class Success(val bytes: ByteArray, val partCount: Int) : ExportResult()
    data class Failure(val reason: String, val cause: Throwable? = null) : ExportResult()
}

/**
 * 导出流程（规格 §30）：
 * semantic mutations → OOXML minimal mutations → package validation → write temp →
 * reopen verification → atomic output。
 *
 * 本类完成"内存内写出 + reopen 验证"；目标文件写入与原子替换由上层（SAF/文件系统）负责。
 */
class DocumentExporter(private val pkg: OoxmlPackage, private val doc: WorkingDocument) {

    /** 把编辑后的 document.xml 写回包，序列化并重新打开验证。 */
    fun exportAndVerify(): ExportResult {
        try {
            val newDocXml = doc.toXmlBytes()
            pkg.requirePart("/word/document.xml").replaceBytes(newDocXml)

            val bytes = OoxmlPackageSerializer.serialize(pkg)
            // reopen 验证：解析失败即导出失败
            val reopened = OoxmlPackage.open(bytes)
            if (reopened.requirePart("/word/document.xml").byteArray().isEmpty()) {
                return ExportResult.Failure("reopen 验证失败：document.xml 为空")
            }
            // 语义自检：与导出前快照比，仅 document.xml 允许变化
            val snapshotBefore = PackageSnapshot.capture(pkg)
            val snapshotAfter = PackageSnapshot.capture(reopened)
            val diffs = snapshotBefore.diff(snapshotAfter)
                .filterNot { it.contains("/word/document.xml") }
            if (diffs.isNotEmpty()) {
                return ExportResult.Failure("reopen 验证发现意外变化: ${diffs.joinToString("; ")}")
            }
            return ExportResult.Success(bytes, reopened.partCount)
        } catch (e: Exception) {
            return ExportResult.Failure("导出失败: ${e.message}", e)
        }
    }
}
