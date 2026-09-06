package com.gongwen.paiban.data

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.gongwen.document.WorkingDocument
import com.gongwen.layout.LayoutEngine
import com.gongwen.layout.model.LayoutBox.TextLine
import com.gongwen.layout.model.PageGeometry
import java.io.File

/**
 * PDF 导出：使用与分页预览相同的 [LayoutEngine]（规格 §49/§50 禁止两套排版），
 * 平台 PdfDocument 逐页绘制。字体由 Android Paint 渲染（嵌入子集由系统处理）。
 *
 * 已知限制：近似度量为 CJK 等宽，真实 Paint 仅用于绘制（度量仍来自引擎）；
 * 与 Word 的精确一致性依赖关键字体可用性 —— 在导出前应提示替代字体风险。
 */
object PdfExporter {

    fun export(context: Context, doc: WorkingDocument, target: Uri): Boolean {
        val geometry = PageGeometry()
        val layout = LayoutEngine(geometry = geometry).layout(doc.paragraphs)
        val pdf = PdfDocument()
        val widthPt = mmToPt(210)
        val heightPt = mmToPt(297)

        try {
            for ((i, page) in layout.pages.withIndex()) {
                val pdfPage = pdf.startPage(
                    PdfDocument.PageInfo.Builder(widthPt.toInt(), heightPt.toInt(), i + 1).create()
                )
                val canvas = pdfPage.canvas
                val scalePtPerTwip = 72f / 20f  // 1 twip = 1/20 pt
                val marginLeftPt = geometry.marginLeftTwips * scalePtPerTwip
                val marginTopPt = geometry.marginTopTwips * scalePtPerTwip

                // 把引擎输出的行按行绘制（按首 glyph 字号）
                for (line in page.contentBoxes.filterIsInstance<TextLine>()) {
                    if (line.glyphs.isEmpty()) continue
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.BLACK
                        textSize = line.glyphs[0].fontSizeHalfPoints / 2f  // half-points→pt
                        isFakeBoldText = line.glyphs[0].bold
                        typeface = android.graphics.Typeface.create("serif", if (line.glyphs[0].bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    }
                    val sb = StringBuilder()
                    line.glyphs.forEach { sb.append(it.char) }
                    val x = marginLeftPt + line.x * scalePtPerTwip
                    // y 基线：行顶 + 字号 ascent 修正（近似用 textSize）
                    val baseline = marginTopPt + line.y * scalePtPerTwip + paint.textSize * 0.8f
                    canvas.drawText(sb.toString(), x, baseline, paint)
                }
                // 页码（页脚）
                val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK
                    textSize = 9f
                }
                val footerY = (geometry.marginTopTwips + geometry.contentHeightTwips + 700) * scalePtPerTwip
                val label = "— ${page.pageIndex + 1} —"
                canvas.drawText(
                    label,
                    mmToPt(210) / 2f - numPaint.measureText(label) / 2f,
                    footerY,
                    numPaint,
                )
                pdf.finishPage(pdfPage)
            }

            val resolver = context.contentResolver
            resolver.openOutputStream(target, "wt")?.use { out ->
                pdf.writeTo(out)
            } ?: return false
            return true
        } catch (e: Exception) {
            return false
        } finally {
            pdf.close()
        }
    }

    private fun mmToPt(mm: Int): Float = mm * 72f / 25.4f
}
