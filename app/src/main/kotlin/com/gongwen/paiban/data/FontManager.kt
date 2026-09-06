package com.gongwen.paiban.data

import android.content.Context
import android.graphics.Typeface
import java.io.File

/**
 * 字体管理器（MVP 简化版）：
 * - 判定设备是否具备某声明字体的可渲染替代（Typeface 探测）；
 * - 用户导入 TTF/OTF 存私有目录；
 * - 维护 声明字体 → 渲染字体 映射（仅影响预览，不改 DOCX 声明）。
 */
class FontManager(private val context: Context) {

    private val fontsDir: File
        get() = File(context.filesDir, "fonts").apply { mkdirs() }

    private val mappingFile: File get() = File(context.filesDir, "font-mapping.properties")

    private val mappings: MutableMap<String, String> by lazy {
        val m = HashMap<String, String>()
        if (mappingFile.exists()) {
            mappingFile.readLines().forEach { line ->
                val idx = line.indexOf('=')
                if (idx > 0) m[line.substring(0, idx)] = line.substring(idx + 1)
            }
        }
        m
    }

    /** 设备能否渲染该字体名（内置或已导入）。 */
    fun isAvailable(declaredFont: String): Boolean {
        if (mappings.containsKey(declaredFont)) return true
        return try {
            val tf = Typeface.create(declaredFont, Typeface.NORMAL)
            tf != null && tf !== Typeface.DEFAULT || declaredFont in SYSTEM_FALLBACKS
        } catch (_: Exception) {
            declaredFont in SYSTEM_FALLBACKS
        }
    }

    /** 导入 TTF/OTF 到私有目录。 */
    fun importFontFile(bytes: ByteArray, fileName: String): String {
        val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val f = File(fontsDir, safe)
        f.writeBytes(bytes)
        return f.absolutePath
    }

    /** 设置映射：声明字体 → 渲染字体（渲染字体必须可用）。 */
    fun setMapping(declared: String, renderFont: String) {
        mappings[declared] = renderFont
        persist()
    }

    fun removeMapping(declared: String) {
        mappings.remove(declared)
        persist()
    }

    fun mappingOf(declared: String): String? = mappings[declared]

    private fun persist() {
        mappingFile.writeText(mappings.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }

    companion object {
        /** 系统内置字体名（Android 稳定存在）。 */
        val SYSTEM_FALLBACKS = setOf(
            "sans-serif", "serif", "monospace",
            "sans-serif-light", "sans-serif-medium", "serif-monospace",
        )
    }
}
