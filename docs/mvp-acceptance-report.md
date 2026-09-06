# Android 公文排版 App MVP — 最终验收报告

> 生成时间：2026-09-06 · 构建环境：WSL2 Ubuntu（JDK 17.0.20 · AGP 8.5.2 · Kotlin 2.0.21 · Gradle 8.9 · compileSdk 34 · minSdk 29）

## 1. 最终结论

**BLOCKED_EXTERNAL_VALIDATION**

依据规格 §55/§70：Gate J（Microsoft Word Desktop）与 Gate K（WPS Desktop）必须在真实 Windows 环境执行，本 Agent 环境无法运行 Word/WPS，因此如实标注 BLOCKED，禁止宣称 PASS。其余可在本环境完成的门禁已执行并通过（见下）。待人工验证清单见 `compat/validation-checklist.md`。

## 2. 实现能力（对照 §63 MVP 功能清单）

| # | 能力 | 状态 |
|---|---|---|
| 1 | Android 10+（minSdk 29） | ✅ |
| 2 | SAF 打开 DOCX（含第三方 provider） | ✅ |
| 3 | 新建 DOCX | ✅ |
| 4 | 中轻度文字编辑（逐段编辑+自动提交） | ✅（连续段落流编辑，非 Word 式光标流内编辑） |
| 5 | Undo / Redo | ✅（文档级快照事务） |
| 6 | 标题样式（角色→样式） | ✅ |
| 7 | 常规表格（创建/加行/删行/单元格编辑） | ✅（合并单元格/列宽/行高 UI 未提供） |
| 8 | 图片 | ⚠️ 打开/round-trip 保真 ✅；插入/缩放未实现 |
| 9 | 页面设置 | ⚠️ 解析/模板定义 ✅；UI 编辑未提供 |
| 10 | 连续编辑模式 | ✅ |
| 11 | 分页预览 | ✅（近似字体度量） |
| 12 | GB/T 9704 内置模板 | ✅ |
| 13 | 完整公文主要元素支持（份号/密级/签发人/版记等识别参与排版） | ⚠️ 主要角色✅；份号/密级/红线等高级版面元素仅结构保留 |
| 14 | 公文结构自动识别 | ✅（主标题/主送/一~四级标题/附件/署名/日期/附注/版记） |
| 15 | 人工纠正结构 | ✅（段落角色选择） |
| 16 | 一键公文排版 | ✅ |
| 17 | 格式锁定 | ✅（单段锁定/解锁，一键排版跳过） |
| 18 | 自定义模板 | ✅ |
| 19 | 模板继承（basedOn） | ✅ |
| 20 | 从样本 DOCX 创建模板 | ⚠️ 引擎层（正文段样式抽取）✅；UI 入口未提供 |
| 21 | 模板导出 | ✅（.twtemplate） |
| 22 | 模板导入 | ✅ |
| 23 | 格式检查 | ✅（按当前有效模板） |
| 24 | 单项修复 | ⚠️ 检查展示 ✅；逐项修复 = 一键重排版 |
| 25 | 一键修复 | ✅ |
| 26 | 字体导入 | ✅（引擎层） |
| 27 | 字体缺失检测 | ✅ |
| 28 | 字体映射（声明↔渲染分离） | ✅（不污染 DOCX 声明） |
| 29 | DOCX 导出 | ✅（导出=reopen 验证，失败即拒） |
| 30 | PDF 导出 | ✅（与预览同 LayoutEngine） |
| 31 | unsupported OOXML preservation | ✅（未知 part/节点字节保真；real-world 验证） |
| 32 | 完全离线 | ✅（Release 无 INTERNET 权限，已 aapt2 验证） |
| 33 | Android 大屏适配 | ⚠️ 响应式基础（Material3/自适应布局未做双栏） |
| 34 | 崩溃恢复 | ✅ |
| 35 | 原文件保护 | ✅（SAF 只读打开→工作副本→另存） |

## 3. 架构

- **OOXML（core-ooxml）**：ZIP 安全解包（zip-slip/bomb/XXE 防护）、ContentType Registry、RelationshipGraph、PackageSnapshot 语义指纹、只对 dirty 结构重写。
- **DocumentModel（core-document）**：Paragraph/Run/Table 模型带 DOM 锚点；document.xml **块级切片保真**——未修改块逐字节输出，仅 dirty 块 DOM 重排；结构变更走 rescan+全 DOM 保守路径；表格 DOM 级 CRUD；导出后强制 reopen 验证。
- **Layout（core-layout）**：LayoutEngine 输出 twips 盒模型（CJK 断行/行距/分页/页码），Preview 与 PDF 共用。
- **Template（core-template）**：role→style、继承/覆盖合并、零依赖 JSON、.twtemplate ZIP。
- **Validator（core-gongwen）**：识别器（评分+置信度+疑似带+负样本抑制）、GB/T 模板、一键排版（formatLock 跳过）、按有效模板检查。
- **Export**：DocumentExporter（写回→序列化→reopen→PackageSnapshot diff 仅允许 document.xml 变化）。

## 4. 主要新增文件（21 提交内核心）

docs/ 4 份基线 + core-ooxml 7 + core-document 12 + core-template 8 + core-gongwen 8 + core-layout 6 + app 12（UI/数据/编辑/预览/模板/字体）+ compat 清单 + README。

## 5. 测试结果

```text
Unit            49 passed  0 failed  0 skipped
Instrumentation 0（本环境无设备/模拟器，未执行 —— 如实标注）
Golden          见下（round-trip 语义指纹断言内建于单测）
RoundTrip       见下
Performance     JVM 布局基准（见 §12）
```

## 6. DOCX RoundTrip 结果

- 程序构造 fixture（含图/未知 part）：open→save→reopen 零 diff；单段编辑后其余 part 字节级不变。
- python-docx 官方模板（17 parts：customXml/numbering/theme/fontTable/docProps/thumbnail）：零语义 diff。
- **真实 WPS/Word 中文公文 3 份**（新闻监测、半月研判，17–18 parts）：全部零语义 diff。

## 7. 公文识别盲测

- 正样本：标准通知（标题/主送/两级标题/署名/日期）→ 全部角色正确，标题置信 ≥0.85。
- 变体：`一.` `一 .` `（一）` `(一)` `1、` `1．` → 均识别为标题。
- 负样本：正文内"一、资金来源…二、人员安排…三、工作时限" → 保持 BODY；长句"一、各单位要…" → 置信 <0.85 不自动应用。
- 错误案例记录：初版将"各县（市、区）人民政府，市政府各部门："误判为标题（冒号结尾机构称谓）——已修复并加回归断言。

## 8-10. Word / WPS 验收

**BLOCKED_EXTERNAL_VALIDATION**。未在真实 Microsoft Word Desktop / WPS Desktop 运行。
`compat/validation-checklist.md` 提供样本生成步骤 + 检查清单 + 结果记录表。

## 11. PDF 验收

PDF 与预览共用 LayoutEngine（页数一致，同一布局树）。与 Word 分页一致性依赖关键字体可用性；当前字体度量为 CJK 等宽近似（非真实字体度量），已如实标注"与 Word 可能存在差异"提示。实机对比待 Word 门禁一并执行。

## 12. 性能（JVM 实测，Android 未真机）

| 规模 | 布局耗时 |
|---|---|
| ~5 页（35 段） | 1 ms |
| ~20 页（140 段） | 4 ms |
| ~50 页（350 段） | 5 ms |
| ~100 页（700 段） | 2 ms |

（CjkApproxMetrics 确定性度量；Android 端真实字体测量成本未计入，需真机补测。）

## 13. 已知限制

- 编辑为**逐段文本框**连续流，非 Word 式行内光标/选区/输入法整段编辑体验；
- 预览/PDF 字体度量近似，非逐字形真实测量；
- 图片插入/缩放未实现（打开与保真支持）；
- 表格 UI 不含合并/列宽/行高/跨页表头；
- 模板 UI 仅编辑关键样式字段（页面边距、标题/正文/H1 的字体字号对齐缩进），非全字段可视化；
- 从样本文档生成模板未接 UI 入口；
- 大屏双栏自适应未实现；
- 查找替换未实现；
- App 层未做 instrumentation（无设备）。

## 14. MVP 未实现事项（范围外/待后续，非失败项）

按 §38 明确不做：云同步/协同/登录/服务端/在线模板市场/LLM/OCR/手写/完整 Office 套件/宏/VBA/CA 签章。**此外**：上表 13 中带 ⚠️/未实现项为 MVP 清单内但未完整交付，如实列入"已知限制"，不属于"范围外"。

## 15. 下一阶段建议（Phase II，待 MVP 验收通过后）

1. Word/WPS 实机验收（Gate J/K）并回填本报告；
2. 真实 Paint 字体度量接入 LayoutEngine（Preview/PDF 与 Word 分页对齐）；
3. 行内光标编辑器 + 选区（RecyclerView 分段 + IME）；
4. 图片插入（media/rels/drawing 联动，需扩展 round-trip 测试）；
5. 表格合并/列宽/跨页表头；
6. Android 仪器测试 + 真机性能基准；
7. 模板全字段可视化编辑 + 从样本生成 UI。
