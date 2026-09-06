# Android 公文排版 App — 架构文档

> 本文档是实现的基线。任何偏离本文档的实现都必须在合并前同步更新本文档。
> 状态：Phase 0 冻结基线（实施过程中允许按事实修订）。

## 1. 目标与定位

Android 10+（API 29）上的**本地公文排版应用**：

- 打开现有 DOCX（SAF），无损读取；
- 公文结构自动识别（确定性规则 + 评分 + 置信度，**无 LLM**）；
- 一键公文排版（GB/T 9704—2012 内置模板 + 单位自定义模板，支持继承/覆盖）；
- 格式检查（Error / Warning / Info）与逐项/一键修复；
- 连续编辑 + 真实分页预览 + PDF 导出；
- 整个核心流程 **100% 离线**；Release Manifest 不申请 `INTERNET` 权限。
- 源文件不可变（P0-3）：导入文件 → 工作副本 → 导出新文件，永不默认覆盖原文件。

## 2. 产品优先级（不可颠倒）

```
文档安全 > DOCX 往返可靠性 > Word/WPS 兼容 > 公文排版正确
         > 模板系统 > 格式检查 > 编辑体验 > 视觉效果
```

## 3. 模块划分

| 模块 | 类型 | 职责 | 测试环境 |
|---|---|---|---|
| `core-ooxml` | Kotlin JVM | OOXML ZIP Package 层：parts、relationships、content types、媒体、安全防护、round-trip 比较 | 桌面 JVM 单测 |
| `core-document` | Kotlin JVM | Document Semantic Model、OOXML↔Model 映射、最小化 XML 编辑、DOCX 导出 + reopen 验证 | 桌面 JVM 单测 |
| `core-template` | Kotlin JVM | 模板模型、role→style 解析、继承/覆盖、`.twtemplate` 序列化、模板抽取 | 桌面 JVM 单测 |
| `core-gongwen` | Kotlin JVM | 公文结构检测器（确定性规则+评分+置信度）、GB/T 9704 内置模板、一键排版事务 | 桌面 JVM 单测 |
| `core-layout` | Kotlin JVM | LayoutEngine：行布局/断行/分页/表格/页眉页脚/页码；驱动 Preview 与 PDF 两处渲染 | 桌面 JVM 单测 |
| `app` | Android | Compose UI、SAF、ViewModel、undo/redo 事务、预览/PDF 的 Android 渲染侧、字体管理器 | Robolectric/仪器测试 |

依赖方向（禁止反向）：

```
app → core-layout → core-document → core-ooxml
   ↘ core-gongwen → core-template ↗
```

`core-gongwen` 通过接口引用模板与模型，**不写死任何 LLM SDK**（AI 扩展预留）。

## 4. 五层文档架构

1. **OOXML Package Layer**（core-ooxml）— ZIP 容器、Part、Relationship、ContentType、二进制媒体。掌握"原始字节保真"的唯一事实层。
2. **Document Semantic Model**（core-document）— `Document/Block/Paragraph/Run/Table/...`；Paragraph 带 `stableId`、`ooxmlAnchor`、`semanticRole`、`detectedLevel`、`confidence`、`lockedProperties`。模型节点与 OOXML 节点一一对应，不脱钩。
3. **Style Engine**（core-template）— 级联：`锁定格式 > 用户显式局部修改 > 单位模板 > GB/T 内置模板 > 原文档样式 > 默认样式`。区分 `contentLock` 与 `formatLock`（MVP 实现 formatLock）。
4. **Layout Engine**（core-layout）— 中文字符度量、换行、缩进、行距、分页、页眉页脚、页码、表格跨页、keep-with-next、widow/orphan。Preview 与 PDF 共用同一引擎。
5. **Android Editor**（app）— 仅消费 DocumentModel，禁止直接操作 XML。

## 5. 关键不变式

- 不做 `DOCX → HTML → 编辑 → HTML → DOCX` 主架构（P0-1）。HTML 只用于辅助展示。
- 未修改的 ZIP Part 保持原始字节；未修改 XML 不重新格式化；未修改 rels 不重建；未识别 XML 节点/属性/命名空间不删除（P0-2）。
- 编辑器不直接改 XML：所有修改 = Semantic Mutation → `DocumentXmlWriter` 的**局部最小 XML 编辑**。
- 同一 LayoutEngine 驱动页面预览与 PDF 输出；不允许形成两套排版。
- 导出 = 写临时 → 重新打开验证 → 原子改名/输出。验证失败即导出失败。

## 6. 数据流

```
SAF Uri → OoxmlPackage.open(流)            [core-ooxml，含 zip-bomb/XXE/zip-slip 防护]
       → DocumentParser.parse(package)     [core-document，构建语义模型]
       → GongwenStructureDetector.detect() [core-gongwen，产出 role+confidence+reasonCodes]
       → 人工纠正角色（UI，可选）
       → 一键排版 = UndoTransaction { TemplateResolver → 逐段 ComputeFormatting → Apply }
       → DocumentValidator.check(model, template)
       → LayoutEngine.layout(model, fonts, pageSetup)   [预览/PDF 同源]
       → exportDocx / exportPdf → reopen verify → 输出到 SAF 目标
```

## 7. 编辑状态与持久化

- 导入文件 immutable source；编辑全部落在 App 私有目录的 **working copy**。
- 自动恢复快照 `AutoRecoverySnapshot` 存 App 私有空间，异常退出可恢复，正常导出后清理。
- 最近文件仅记录 SAF URI + metadata，不复制用户文件进私有库。
- 结构识别结果（predictedRole/confidence/reasonCodes）与 formatLock 属于本 App 编辑状态；
  可选的 DOCX 自定义属性持久化必须保证不破坏 Word/WPS（默认仅内存 + 快照保存）。

## 8. UI 结构（响应式）

- 手机：单栏连续编辑；平板/大屏/折叠展开：文档区 + 属性面板双栏。
- 首页：新建 / 打开 / 一键公文排版 / 格式检查 / 模板库 + 最近文件。
- 编辑页顶部：返回、文件名、Undo、Redo、更多。
- 编辑页底部主导航：编辑 | 样式 | 排版 | 检查 | 更多。
- 连续编辑与分页预览分离：预览单独入口，渲染真实分页（A4/版心/页眉页脚/页码），支持单页/连续/缩放/页计数。

## 9. 安全要求

DOCX 为不可信输入：

- 防 Zip Slip（entry 路径规范化后必须位于解压根内）；
- 限制解压总大小与 entry 数量（防 zip bomb）；
- 禁用 XXE / 外部实体（XML parser `setFeature(disallow-doctype-decl)` 等）；
- 不执行宏/OLE/嵌入对象；宏文档仅保存不执行；
- 损坏文件必须给出明确失败而非 crash；不允许产出"看似成功实则损坏"的文件。

## 10. 日志

Debug 结构化日志事件：`DOC_OPEN / DOC_PARSE / OOXML_WARNING / STRUCTURE_DETECT / TEMPLATE_APPLY / VALIDATION / LAYOUT / DOC_EXPORT / PDF_EXPORT`。
Release 不输出用户正文与文件名等敏感内容。

## 11. 性能目标与策略

- Lazy/视口驱动渲染，禁止一次性把 100 页全部栅格化；图片采样加载；缓存控制。
- 50 页常规公文在普通中端设备可正常编辑/预览、无 OOM（具体指标以基准设备测量为准）。
- 性能基准集：5 页纯文本 / 20 页公文 / 50 页公文 / 100 页长文 / 20 页含表 / 20 页含图 / 复杂混合。

## 12. 反技术债清单（Code Review 检查点）

巨型 ViewModel、Activity 直接解析 OOXML、UI 直接改 XML、mutable global state、写死文件 URI、
Windows 路径硬编码、HTML 作为 document truth、模板规则写死进 UI、识别正则散落各处、
字体 fallback 散落 renderer、Preview/PDF 两套 layout、`catch(Exception)` 静默吞错 —— 一律禁止。

## 13. 阶段路线图与分支

| Phase | 交付 | 分支 |
|---|---|---|
| 0 | 环境/骨架/本文档 | `phase/0-bootstrap` |
| 1 | core-ooxml + RoundTrip Golden | `phase/ooxml` |
| 2 | core-document 模型 + 编辑 + 导出 | `phase/document-model` |
| 3 | core-gongwen 识别 + core-template 模板 | `phase/gongwen` |
| 4 | app 编辑主流程 + 一键排版 + validator UI | `phase/editor` |
| 5 | core-layout + 预览 + PDF + 字体 | `phase/layout` |
| 6 | 全量测试 + 验收报告 | `phase/validation` |

每阶段保持可构建、可测试、可提交。
