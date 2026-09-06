package com.gongwen.paiban.data

import android.content.Context
import android.net.Uri
import com.gongwen.document.DocumentExporter
import com.gongwen.document.DocumentService
import com.gongwen.document.WorkingDocument
import com.gongwen.ooxml.OoxmlPackage
import com.gongwen.template.TwTemplatePackage
import com.gongwen.template.model.DocumentTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** SAF 文档仓库：负责打开/保存/模板库/自动恢复快照。 */
class DocumentRepository(private val context: Context) {

    /** 会话文档。 */
    var current: SessionDocument? = null
        private set

    /** 替换会话文档（undo/redo 恢复模型时使用）。 */
    fun replaceSession(session: SessionDocument) {
        current = session
    }

    /** 最近文件列表（URI 字符串 + 显示名）。 */
    val recentFiles = LinkedHashMap<String, String>()

    class SessionDocument(
        val sourceUri: Uri?,
        val displayName: String,
        val ooxmlPackage: OoxmlPackage,
        val document: WorkingDocument,
    )

    /** 从 SAF Uri 打开 DOCX（原始流 → 工作副本）。 */
    suspend fun openFromUri(uri: Uri): SessionDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = queryDisplayName(uri) ?: "未命名.docx"
        val pkg = resolver.openInputStream(uri)?.use { OoxmlPackage.open(it) }
            ?: throw IllegalStateException("无法读取文件")
        val doc = DocumentService.open(pkg, uri.toString())
        rememberRecent(uri, name)
        SessionDocument(uri, name, pkg, doc).also { current = it }
    }

    /** 新建空白文档（仅内存工作副本，未落盘）。 */
    fun createNew(name: String = "未命名公文.docx"): SessionDocument {
        val pkg = OoxmlPackage.open(NewDocxFactory.blankDocument())
        val doc = DocumentService.open(pkg, "<new>")
        return SessionDocument(null, name, pkg, doc).also { current = it }
    }

    /** 保存到原 Uri（仅用于已有取回权限的文档；"覆盖保存"需用户显式确认，MVP 默认另存）。 */
    fun exportTo(target: Uri, session: SessionDocument): Boolean {
        val result = DocumentExporter(session.ooxmlPackage, session.document).exportAndVerify()
        if (result !is com.gongwen.document.ExportResult.Success) return false
        val resolver = context.contentResolver
        resolver.openOutputStream(target, "wt")?.use { out ->
            out.write(result.bytes)
        } ?: return false
        return true
    }

    /** 导出字节（供"导出为新文件"使用）。 */
    fun exportBytes(session: SessionDocument): ByteArray? {
        val result = DocumentExporter(session.ooxmlPackage, session.document).exportAndVerify()
        return (result as? com.gongwen.document.ExportResult.Success)?.bytes
    }

    // ---- 模板库 ----

    private val templatesDir: File
        get() = File(context.filesDir, "templates").apply { mkdirs() }

    fun listTemplates(): Map<String, DocumentTemplate> {
        val out = LinkedHashMap<String, DocumentTemplate>()
        // 内置 GB/T
        val gbt = com.gongwen.gongwen.Gbt9704Template.instance
        out[gbt.metadata.templateId] = gbt
        // 用户模板
        templatesDir.listFiles()?.filter { it.extension == "twtemplate" }?.forEach { f ->
            try {
                val t = TwTemplatePackage.unpack(f.readBytes())
                out[t.metadata.templateId] = t
            } catch (_: Exception) {
                // 损坏的用户模板跳过
            }
        }
        return out
    }

    fun saveUserTemplate(template: DocumentTemplate) {
        val bytes = TwTemplatePackage.pack(template)
        val f = File(templatesDir, "${template.metadata.templateId}.twtemplate")
        f.writeBytes(bytes)
    }

    fun importTemplate(bytes: ByteArray) {
        val t = TwTemplatePackage.unpack(bytes)
        saveUserTemplate(t)
    }

    // ---- 崩溃恢复 ----

    private val autoRecoveryDir: File
        get() = File(context.filesDir, "autorecovery").apply { mkdirs() }

    fun saveAutoRecovery(session: SessionDocument) {
        val bytes = exportBytes(session) ?: return
        val f = File(autoRecoveryDir, "recovery.docx")
        f.writeBytes(bytes)
        val meta = File(autoRecoveryDir, "recovery.meta")
        meta.writeText("${session.displayName}\n${session.sourceUri?.toString() ?: ""}")
    }

    fun hasAutoRecovery(): Boolean = File(autoRecoveryDir, "recovery.docx").exists()

    fun loadAutoRecovery(): SessionDocument? {
        val f = File(autoRecoveryDir, "recovery.docx")
        if (!f.exists()) return null
        return try {
            val pkg = OoxmlPackage.open(f.readBytes())
            val doc = DocumentService.open(pkg, "<autorecovery>")
            val meta = File(autoRecoveryDir, "recovery.meta").readText().lines()
            SessionDocument(null, meta.getOrElse(0) { "已恢复文档.docx" }, pkg, doc).also { current = it }
        } catch (_: Exception) {
            null
        }
    }

    fun clearAutoRecovery() {
        File(autoRecoveryDir, "recovery.docx").delete()
        File(autoRecoveryDir, "recovery.meta").delete()
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun rememberRecent(uri: Uri, name: String) {
        recentFiles[uri.toString()] = name
        while (recentFiles.size > 10) recentFiles.remove(recentFiles.keys.first())
    }
}
