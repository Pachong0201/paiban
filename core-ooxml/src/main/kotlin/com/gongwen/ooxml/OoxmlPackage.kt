package com.gongwen.ooxml

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 解压与解包的安全限额。DOCX 属不可信输入。 */
data class PackageLimits(
    /** 单个 part 未压缩字节上限。 */
    val maxPartSizeBytes: Long = 512L * 1024 * 1024,
    /** 所有 part 解压后总字节上限。 */
    val maxTotalSizeBytes: Long = 1024L * 1024 * 1024,
    /** ZIP entry 数量上限（防 zip bomb 的 entry 洪泛）。 */
    val maxEntryCount: Int = 100_000,
    /** 压缩比告警阈值（超过记为可疑但不拒绝）。 */
    val suspiciousCompressionRatio: Double = 200.0,
)

/**
 * OOXML ZIP Package 容器（OPC）。
 *
 * 打开时解压全部 part 到内存并做安全校验；保存时按 [docx-package 约定]
 * 先写 [Content_Types].xml，并保留未修改 part 的原始字节与条目元数据
 * （压缩级别、方法由本实现统一处理，语义保真由 PackageSnapshot 门禁保障）。
 */
class OoxmlPackage private constructor(
    private val partsByName: LinkedHashMap<String, OoxmlPart>,
    private val limits: PackageLimits,
    private val suspiciousCompression: Boolean,
    val sourceDescriptor: String,
    private val registry: ContentTypeRegistry,
) {
    val isSuspiciousCompression: Boolean get() = suspiciousCompression

    /**
     * [Content_Types].xml 是否需要重建：
     * 仅当 part 集合发生增删（或某 part 内容类型被改成扩展名无法由原 Default 覆盖）时为 true。
     * part 集合未变时序列化器必须写回原始字节（P0-2）。
     */
    internal var contentTypesDirty: Boolean = false

    val contentTypeRegistry: ContentTypeRegistry get() = registry

    fun part(name: String): OoxmlPart? = partsByName[name]

    fun requirePart(name: String): OoxmlPart =
        part(name) ?: throw CorruptPackageException("缺少必需 part: $name")

    fun parts(): List<OoxmlPart> = partsByName.values.toList()

    fun partNames(): List<String> = partsByName.keys.toList()

    val partCount: Int get() = partsByName.size

    /** 新增或替换 part。name 使用规范化的 OPC 路径（"/word/document.xml"）。 */
    fun putPart(name: String, contentType: String, bytes: ByteArray) {
        val canonical = normalizePartName(name)
        val existing = partsByName[canonical]
        if (existing != null) {
            if (existing.contentType != contentType) {
                existing.contentType = contentType
                contentTypesDirty = true
            }
            existing.replaceBytes(bytes)
        } else {
            partsByName[canonical] = OoxmlPart(canonical, contentType, bytes)
            contentTypesDirty = true
        }
    }

    /** 删除 part（同时应清理其 rels 与引用，调用方负责一致性）。 */
    fun removePart(name: String) {
        val removed = partsByName.remove(normalizePartName(name))
        if (removed != null) contentTypesDirty = true
    }

    companion object {
        /** 打开并校验一个 DOCX 字节流。不修改输入流。 */
        fun open(input: InputStream, limits: PackageLimits = PackageLimits()): OoxmlPackage =
            openInternal(input.readBytes(), limits, "<input>")

        /** 打开并校验一个 DOCX 字节数组。 */
        fun open(bytes: ByteArray, limits: PackageLimits = PackageLimits()): OoxmlPackage =
            openInternal(bytes, limits, "<bytes>")

        private fun openInternal(raw: ByteArray, limits: PackageLimits, descriptor: String): OoxmlPackage {
            var total = 0L
            var count = 0
            var suspicious = false
            val parts = LinkedHashMap<String, OoxmlPart>()
            try {
                ZipInputStream(raw.inputStream()).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        count++
                        if (count > limits.maxEntryCount) {
                            throw PackageLimitExceededException(
                                "ZIP entry 数量超过上限 ${limits.maxEntryCount}（可能为 zip bomb）"
                            )
                        }
                        val name = entry.name
                        if (entry.isDirectory) {
                            entry = zip.nextEntry
                            continue
                        }
                        if (name.isEmpty() || name.startsWith("/") || name.contains("..") ||
                            name.contains('\\') || name.endsWith("/")
                        ) {
                            throw CorruptPackageException("非法 part 路径: $name")
                        }
                        // entry.size 未设置时为 -1；按已知大小预分配缓冲，否则给默认值
                        val known = if (entry.size > 0) entry.size.coerceAtMost((1 shl 20).toLong()).toInt() else 8192
                        val buf = ByteArrayOutputStream(known)
                        val chunk = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val n = zip.read(chunk)
                            if (n < 0) break
                            written += n
                            if (written > limits.maxPartSizeBytes) {
                                throw PackageLimitExceededException(
                                    "part $name 超过单文件上限 ${limits.maxPartSizeBytes} bytes"
                                )
                            }
                            buf.write(chunk, 0, n)
                        }
                        total += written
                        if (total > limits.maxTotalSizeBytes) {
                            throw PackageLimitExceededException(
                                "解压总大小超过上限 ${limits.maxTotalSizeBytes} bytes（可能为 zip bomb）"
                            )
                        }
                        if (entry.compressedSize > 0 && written > 0) {
                            val ratio = written.toDouble() / entry.compressedSize
                            if (ratio > limits.suspiciousCompressionRatio) suspicious = true
                        }
                        val canonical = normalizePartName(name)
                        parts[canonical] = OoxmlPart(canonical, "", buf.toByteArray())
                        entry = zip.nextEntry
                    }
                }
            } catch (e: PackageLimitExceededException) {
                throw e
            } catch (e: IOException) {
                throw CorruptPackageException("无法读取 DOCX ZIP 包: ${e.message}", e)
            }
            if (parts.isEmpty()) {
                throw CorruptPackageException("文件不是有效的 DOCX（ZIP 中无任何条目）")
            }
            val contentTypesPart = parts["/[Content_Types].xml"]
                ?: throw CorruptPackageException("缺少 [Content_Types].xml")
            val registry = ContentTypeRegistry.parse(contentTypesPart)
            val pkg = OoxmlPackage(parts, limits, suspicious, descriptor, registry)
            for (p in pkg.parts()) {
                if (p.name == "/[Content_Types].xml") continue
                p.contentType = registry.contentTypeFor(p.name)
                    ?: throw CorruptPackageException("part 缺少内容类型声明: ${p.name}")
            }
            return pkg
        }

        /** OPC 内部 part 名统一以 "/" 开头。 */
        fun normalizePartName(name: String): String =
            if (name.startsWith("/")) name else "/$name"
    }
}
