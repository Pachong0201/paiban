# Android 公文排版 App（Gongwen Paiban）

面向党政机关公文及单位内部材料的**本地轻量文档编辑器 + 高可靠 DOCX 引擎 + 公文自动排版引擎 + 自定义模板系统 + 格式检查系统**。Android 10+（API 29），100% 离线，不需要网络、账号、服务器。

> 状态：MVP 核心链路可构建、可测试。Word/WPS 实机验收 = BLOCKED_EXTERNAL_VALIDATION（见 `compat/validation-checklist.md`）。

## 核心能力

- **DOCX 原样保真**：以自研 OOXML Package 层为唯一事实源，打开→另存→重开零语义丢失；未修改 part 逐字节保留（P0 门禁测试覆盖）。
- **打开/新建/编辑/导出**：SAF 打开任意来源 DOCX → 逐段编辑 → 导出新 DOCX（导出前自动 reopen 验证，损坏即失败）。
- **公文结构自动识别**：确定性规则 + 特征评分 + 置信度（无 LLM）。主标题/主送机关/一至四级标题/附件说明/署名/日期/版记等，含变体容忍与负样本抑制，支持人工纠正角色。
- **一键公文排版**：识别 → GB/T 9704—2012（或单位模板）→ 应用 → 单事务 Undo。段落格式锁定（formatLock）段跳过。
- **模板系统**：GB/T 内置 + 单位模板继承/覆盖（`.twtemplate` ZIP），可视化编辑关键样式，导入/导出，从文档抽取。
- **格式检查**：按"当前有效模板"检查字体/字号/对齐/缩进/行距，Error/Warning 分级，一键修复。
- **分页预览与 PDF**：Preview 与 PDF 共用同一 LayoutEngine（A4/版心/页码）。
- **字体管理**：声明字体与渲染字体分离；缺失字体检测提示；用户导入 TTF/OTF；导出 DOCX 不篡改字体声明。
- **崩溃恢复**：自动快照（App 私有空间），异常退出后启动自动恢复。
- **安全**：无 INTERNET 权限；zip-bomb/zip-slip/XXE 防护；宏/嵌入对象不执行；损坏文件安全拒绝。

## 构建

```bash
# 环境：JDK 17、Android SDK（compileSdk 34，minSdk 29）
sdk.dir=/path/to/Android/Sdk   # 写入 local.properties

./gradlew :app:assembleDebug    # Debug APK
./gradlew :app:assembleRelease  # Release APK（无 INTERNET 权限）
./gradlew :core-ooxml:test :core-document:test :core-template:test \
          :core-gongwen:test :core-layout:test   # 全部桌面单测
```

## 模块

| 模块 | 说明 |
|---|---|
| `core-ooxml` | OOXML ZIP Package：parts/relationships/content-types、安全解包、PackageSnapshot 语义指纹 |
| `core-document` | DocumentModel（Paragraph/Run/Table）、块级切片 XML 编辑（未动字节保真）、表格创建/单元格编辑、导出+reopen 验证、字体扫描 |
| `core-template` | 模板模型、继承/覆盖解析、零依赖 JSON codec、`.twtemplate` 打包 |
| `core-gongwen` | GB/T 9704 模板、结构检测器（置信度/疑似）、一键排版执行器、DocumentValidator |
| `core-layout` | LayoutEngine（CJK 断行/行距/分页/页码）——预览与 PDF 共用 |
| `app` | Compose UI：首页/编辑（段落+表格）/排版/检查/模板库/分页预览；SAF；PDF 导出；字体管理器；崩溃恢复 |

## 文档

- `docs/architecture.md` — 分层架构与 P0 不变式
- `docs/ooxml-strategy.md` — OOXML 保真策略与 round-trip 门禁
- `docs/template-schema.md` — `.twtemplate` 格式
- `docs/test-plan.md` — 测试计划与语料
- `compat/validation-checklist.md` — Word/WPS 实机验收清单（BLOCKED）

## 测试

```
48 个桌面 JVM 单测：0 failure
core-ooxml 12 · core-document 16 · core-template 6 · core-gongwen 8 · core-layout 6
```

覆盖：round-trip 门禁（程序 fixture + python-docx 模板 + 真实中文公文 17-18 parts）、
块级编辑保真、表格 CRUD、模板继承/环检测、识别盲测（正/变体/负样本）、一键排版+锁定、
validator 前后对比、异常输入、布局分页与性能（5/20/50/100 页级 JVM 1-5ms）。

未含：Android instrumentation（本环境无设备/模拟器）；Word/WPS 实机验收（外部环境）。
