package com.gongwen.paiban.ui.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gongwen.document.DocumentModelEditor
import com.gongwen.document.model.Paragraph
import com.gongwen.gongwen.Gbt9704Template
import com.gongwen.gongwen.OneClickGongwenFormatter
import com.gongwen.gongwen.validate.DocumentValidator
import com.gongwen.gongwen.validate.ValidationReport
import com.gongwen.paiban.data.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 编辑器 UI 状态。 */
data class EditorUiState(
    val isOpen: Boolean = false,
    val displayName: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val paragraphTexts: List<ParagraphUi> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val report: ValidationReport? = null,
    val templateName: String = "GB/T 9704—2012",
    val fontSummary: String = "",
)

/** 段落 UI 呈现（含文本与角色标签）。 */
data class ParagraphUi(
    val index: Int,
    val stableId: String,
    val text: String,
    val roleLabel: String,
    val inTable: Boolean,
    val formatLocked: Boolean = false,
    val role: com.gongwen.document.model.SemanticRole = com.gongwen.document.model.SemanticRole.BODY,
)

/** 撤销快照：整个 document.xml 的字节级副本 + 模型重建由 DocumentService 负责。 */
class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DocumentRepository(app)
    private val _ui = MutableStateFlow(EditorUiState())
    val ui: StateFlow<EditorUiState> = _ui.asStateFlow()

    private val undoStack = ArrayDeque<ByteArray>()
    private val redoStack = ArrayDeque<ByteArray>()

    /** 一键排版（识别+应用）。 */
    private val formatter by lazy { OneClickGongwenFormatter(repo.listTemplates()) }

    fun openUri(uri: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            try {
                val session = repo.openFromUri(uri)
                updateFromSession(session.displayName)
            } catch (e: Exception) {
                _ui.update { it.copy(isLoading = false, error = e.message ?: "打开失败") }
            }
        }
    }

    fun newDocument() {
        val session = repo.createNew()
        updateFromSession(session.displayName)
    }

    private fun updateFromSession(name: String) {
        val session = repo.current ?: return
        val texts = session.document.paragraphs.mapIndexed { i, p ->
            ParagraphUi(
                index = i,
                stableId = p.stableId,
                text = p.text,
                roleLabel = roleLabel(p),
                inTable = p.inTable,
                formatLocked = p.formatLock != com.gongwen.document.model.FormatLockScope.NONE,
                role = p.effectiveRole,
            )
        }
        _ui.update {
            it.copy(
                isOpen = true,
                isLoading = false,
                displayName = name,
                paragraphTexts = texts,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                error = null,
                fontSummary = fontSummary(session),
            )
        }
    }

    private fun fontSummary(session: com.gongwen.paiban.data.DocumentRepository.SessionDocument): String {
        val fontManager = com.gongwen.paiban.data.FontManager(getApplication())
        val usages = com.gongwen.document.DocumentFontScanner.scan(session.document.paragraphs)
        if (usages.isEmpty()) return ""
        val missing = usages.filter { !fontManager.isAvailable(it.fontName) }
        val declared = usages.joinToString("、") { it.fontName }
        return if (missing.isEmpty()) {
            "本文件使用 ${usages.size} 种字体：$declared（均可用）"
        } else {
            "本文件使用 ${usages.size} 种字体：$declared。" +
                "其中 ${missing.size} 种本机缺失（${missing.joinToString("、") { it.fontName }}），预览存在字体替代。"
        }
    }

    private fun roleLabel(p: Paragraph): String = when (p.effectiveRole) {
        com.gongwen.document.model.SemanticRole.TITLE -> "标题"
        com.gongwen.document.model.SemanticRole.RECIPIENT -> "主送"
        com.gongwen.document.model.SemanticRole.BODY -> "正文"
        com.gongwen.document.model.SemanticRole.HEADING1 -> "一"
        com.gongwen.document.model.SemanticRole.HEADING2 -> "（一）"
        com.gongwen.document.model.SemanticRole.HEADING3 -> "1."
        com.gongwen.document.model.SemanticRole.HEADING4 -> "（1）"
        com.gongwen.document.model.SemanticRole.SIGNATURE -> "署名"
        com.gongwen.document.model.SemanticRole.DATE -> "日期"
        com.gongwen.document.model.SemanticRole.ATTACHMENT_DESC -> "附件"
        com.gongwen.document.model.SemanticRole.COLOPHON -> "版记"
        com.gongwen.document.model.SemanticRole.ANNOTATION -> "附注"
        else -> ""
    }

    private fun snapshot() {
        val session = repo.current ?: return
        undoStack.addLast(session.document.toXmlBytes())
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
    }

    fun setParagraphText(index: Int, newText: String) {
        val session = repo.current ?: return
        if (index !in session.document.paragraphs.indices) return
        if (session.document.paragraphs[index].text == newText) return
        snapshot()
        val editor = DocumentModelEditor(session.document)
        editor.setParagraphText(session.document.paragraphs[index], newText)
        updateFromSession(session.displayName)
    }

    /** 人工纠正段落角色（规格 §11）。 */
    fun setUserRole(index: Int, role: com.gongwen.document.model.SemanticRole) {
        val session = repo.current ?: return
        if (index !in session.document.paragraphs.indices) return
        session.document.paragraphs[index].userRole = role
        updateFromSession(session.displayName)
    }

    /** 长按锁定/解锁格式（规格 §14 formatLock）。 */
    fun toggleFormatLock(index: Int) {
        val session = repo.current ?: return
        if (index !in session.document.paragraphs.indices) return
        val p = session.document.paragraphs[index]
        p.formatLock = if (p.formatLock == com.gongwen.document.model.FormatLockScope.NONE) {
            com.gongwen.document.model.FormatLockScope.PARAGRAPH
        } else {
            com.gongwen.document.model.FormatLockScope.NONE
        }
        updateFromSession(session.displayName)
    }

    fun undo() {
        val session = repo.current ?: return
        if (undoStack.isEmpty()) return
        val state = undoStack.removeLast()
        redoStack.addLast(session.document.toXmlBytes())
        restoreXml(session, state)
    }

    fun redo() {
        val session = repo.current ?: return
        if (redoStack.isEmpty()) return
        val state = redoStack.removeLast()
        undoStack.addLast(session.document.toXmlBytes())
        restoreXml(session, state)
    }

    private fun restoreXml(session: DocumentRepository.SessionDocument, xmlBytes: ByteArray) {
        // 重建 document part 内容
        val part = session.ooxmlPackage.requirePart("/word/document.xml")
        part.replaceBytes(xmlBytes)
        // 重新解析模型（保留包内其他 part）
        val newDoc = com.gongwen.document.DocumentService.open(
            session.ooxmlPackage,
            session.sourceUri?.toString() ?: "<restored>",
        )
        repo.replaceSession(
            DocumentRepository.SessionDocument(
                session.sourceUri, session.displayName, session.ooxmlPackage, newDoc,
            )
        )
        updateFromSession(session.displayName)
    }

    /** 一键公文排版（识别 + 应用）。 */
    fun runOneClickFormat() {
        val session = repo.current ?: return
        snapshot()
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val suspects = formatter.detectAndAnnotate(session.document)
                formatter.format(session.document)
                val suspectMsg = if (suspects.isEmpty()) "" else "发现 ${suspects.size} 处疑似标题，请人工确认。"
                _ui.update { it.copy(info = "排版完成。$suspectMsg") }
                updateFromSession(session.displayName)
            }
        }
    }

    /** 格式检查（按当前模板）。 */
    fun runCheck() {
        val session = repo.current ?: return
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val validator = DocumentValidator(repo.listTemplates(), Gbt9704Template.TEMPLATE_ID)
                val report = validator.validate(session.document.paragraphs)
                _ui.update { it.copy(report = report) }
            }
        }
    }

    /** 导出 DOCX 到目标 Uri（已取得写权限）。 */
    fun exportTo(uri: Uri, onDone: (Boolean) -> Unit) {
        val session = repo.current ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repo.exportTo(uri, session) }
            if (ok) {
                repo.clearAutoRecovery()
                _ui.update { it.copy(info = "导出成功") }
            } else {
                _ui.update { it.copy(error = "导出失败：文件未通过重新打开验证") }
            }
            onDone(ok)
        }
    }

    fun saveAutoRecovery() {
        val session = repo.current ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.saveAutoRecovery(session) }
        }
    }

    fun clearInfo() {
        _ui.update { it.copy(info = null, error = null) }
    }

    /** 当前会话（预览等需要访问原始模型的场景）。 */
    fun currentSession(): com.gongwen.paiban.data.DocumentRepository.SessionDocument? = repo.current

    /** PDF 导出结果提示。 */
    fun notifyExportPdf(ok: Boolean) {
        _ui.update {
            if (ok) it.copy(info = "PDF 导出成功") else it.copy(error = "PDF 导出失败")
        }
    }
}
