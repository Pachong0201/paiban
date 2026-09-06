package com.gongwen.paiban.ui.preview

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gongwen.layout.LayoutEngine
import com.gongwen.layout.model.DocumentLayout
import com.gongwen.layout.model.LayoutBox.TextLine
import com.gongwen.layout.model.PageGeometry
import com.gongwen.paiban.data.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 页面预览：同一 LayoutEngine（与 PDF 共用）分页 + Paint 绘制。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    session: DocumentRepository.SessionDocument?,
    onBack: () -> Unit,
    onExportPdf: () -> Unit = {},
) {
    var layout by remember { mutableStateOf<DocumentLayout?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(session) {
        layout = withContext(Dispatchers.Default) {
            session?.let {
                LayoutEngine(geometry = PageGeometry()).layout(it.document.paragraphs)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分页预览") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    androidx.compose.material3.TextButton(onClick = onExportPdf) { Text("导出 PDF") }
                },
            )
        },
    ) { padding ->
        val pages = layout?.pages ?: emptyList()
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("共 ${pages.size} 页")
                Slider(
                    value = scale,
                    onValueChange = { scale = it },
                    valueRange = 0.3f..2f,
                    modifier = Modifier.weight(1f),
                )
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "预览使用系统字体近似度量，分页可能与 Word 存在差异",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                pages.forEach { page ->
                    PageCanvas(
                        pageIndex = page.pageIndex,
                        totalPages = pages.size,
                        lines = page.contentBoxes.filterIsInstance<TextLine>(),
                        footerNumber = page.footerPageNumber,
                        scale = scale,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageCanvas(
    pageIndex: Int,
    totalPages: Int,
    lines: List<TextLine>,
    footerNumber: String?,
    scale: Float,
) {
    val geo = PageGeometry()
    // 版心宽 twips → dp：1twip = 1/20 pt；pt→dp 由 LocalDensity(sp) 换算
    val density = LocalDensity.current
    val sp2dp = with(density) { 1.sp.toDp().value }
    // 页面宽 twips → 逻辑像素（160dpi 基准）：pt = twips/20；px(160) = pt*160/72
    val scaleTwipsToDp = sp2dp * 160f / 72f / 20f * scale
    val pageW = geo.widthTwips * scaleTwipsToDp
    val pageH = geo.heightTwips * scaleTwipsToDp
    val marginL = geo.marginLeftTwips * scaleTwipsToDp
    val marginT = geo.marginTopTwips * scaleTwipsToDp

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.width(pageW.dp).height(pageH.dp).background(Color.White)
        ) {
            drawRoundRect(
                color = Color(0xFF999999),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
                cornerRadius = CornerRadius(1f),
            )
            val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
            }
            val canvas = drawContext.canvas.nativeCanvas
            for (line in lines) {
                if (line.glyphs.isEmpty()) continue
                val sizePt = line.glyphs[0].fontSizeHalfPoints / 2f
                textPaint.textSize = sizePt * (scaleTwipsToDp * 20f * sp2dp) // twips→px 同系数
                textPaint.isFakeBoldText = line.glyphs[0].bold
                val sb = StringBuilder()
                line.glyphs.forEach { sb.append(it.char) }
                val baseY = marginT + line.y * scaleTwipsToDp
                canvas.drawText(sb.toString(), marginL + line.x * scaleTwipsToDp, baseY, textPaint)
            }
            // 页脚页码
            footerNumber?.let { num ->
                val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
                fp.textSize = 9f * sp2dp
                val footerY = (geo.marginTopTwips + geo.contentHeightTwips + 500) * scaleTwipsToDp
                canvas.drawText("— $num —", (pageW / 2f - fp.measureText("— $num —") / 2f), footerY, fp)
            }
        }
        Text("第 ${pageIndex + 1} 页 / 共 $totalPages 页", style = MaterialTheme.typography.labelSmall)
    }
}
