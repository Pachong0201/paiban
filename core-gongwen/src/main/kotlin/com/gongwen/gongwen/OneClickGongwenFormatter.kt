package com.gongwen.gongwen

import com.gongwen.document.DocumentModelEditor
import com.gongwen.document.WorkingDocument
import com.gongwen.document.model.Paragraph
import com.gongwen.document.model.ParagraphRecognition
import com.gongwen.document.model.SemanticRole
import com.gongwen.gongwen.detect.GongwenStructureDetector
import com.gongwen.gongwen.format.FormatApplyResult
import com.gongwen.gongwen.format.GongwenFormatter
import com.gongwen.template.model.DocumentTemplate

/** 一键公文排版门面：识别 → 应用。可整体作为单个 Undo 事务。 */
class OneClickGongwenFormatter(
    private val templates: Map<String, DocumentTemplate>,
    private val templateId: String = Gbt9704Template.TEMPLATE_ID,
) {
    private val detector = GongwenStructureDetector()

    /**
     * 执行识别并把结果写入段落模型（不落 XML）。
     * @return 疑似项（0.60–0.85）供 UI 确认。
     */
    fun detectAndAnnotate(doc: WorkingDocument): List<DetectedParagraphInfo> {
        val paragraphs = doc.paragraphs
        val results = detector.detect(paragraphs)
        val suspect = ArrayList<DetectedParagraphInfo>()
        for (r in results) {
            if (r.paragraphIndex < paragraphs.size) {
                val p = paragraphs[r.paragraphIndex]
                p.recognition = ParagraphRecognition(r.role, r.confidence, r.reasonCodes)
                if (r.confidence in DetectionConfidence.SUSPECT_LO..DetectionConfidence.SUSPECT_HI) {
                    suspect.add(DetectedParagraphInfo(r.paragraphIndex, p.text, r.role, r.confidence, r.reasonCodes))
                }
            }
        }
        return suspect
    }

    /** 一键排版：按识别结果应用模板格式。 */
    fun format(doc: WorkingDocument): FormatApplyResult {
        val formatter = GongwenFormatter(DocumentModelEditor(doc), templates, templateId)
        return formatter.apply(doc.paragraphs) { p -> p.effectiveRole }
    }
}

/** 疑似识别项（供 UI 显示"发现 N 处可能标题，请确认"）。 */
data class DetectedParagraphInfo(
    val paragraphIndex: Int,
    val text: String,
    val role: SemanticRole,
    val confidence: Double,
    val reasonCodes: List<String>,
)

/** 检测置信度阈值（自动应用 / 疑似下界）。 */
object DetectionConfidence {
    const val AUTO_APPLY = 0.85
    const val SUSPECT_LO = 0.60
    const val SUSPECT_HI = 0.85
}
