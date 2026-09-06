# OOXML 策略文档

> 目标：**原始 OOXML 最大化 round-trip preservation**（P0-2），并在其上做**局部最小编辑**。
> 本策略决定哪些库可用、哪些库只能当辅助解析器、以及保存字节时应守住的底线。

## 1. 事实源

**App 自己的 OOXML Package Layer 是唯一事实源**（见 `core-ooxml`）。

第三方库（Apache POI / docx4j / pandoc 等）若被引入，仅可作**辅助解析器**（只读提示、结构探测），
不得作为最终序列化器——因为它们会重建整个 package、重排全部 XML，违反 P0-2。
当前 MVP **不引入**任何第三方 OOXML 库，全部手写，避免许可证与体积风险。

## 2. 为什么 round-trip 不能靠"Word 能打开"

"Word 能打开"只证明文件可读，不证明结构、分页、样式、关系被保留。
Round-trip 门禁以**语义级 package 对比**为准（见 §5），外加 Word/WPS 实机验收
（无法实机时必须 `BLOCKED_EXTERNAL_VALIDATION`，禁止声称 PASS）。

## 3. core-ooxml 分层结构

```
OoxmlPackage          ZIP package 容器；保存 Part 列表与原始条目元数据
OoxmlPart             part 抽象（name/contentType/内部原始字节）
├─ XmlPart            XML part；持有：原始字节(保真) + 惰性解析 DOM + dirty 标志
├─ BinaryPart         media/embedding 等二进制（原始字节直存）
OoxmlRelationship     one rels 条目（id/type/target/targetMode）
RelationshipGraph     从 [Content_Types].xml + *.rels 建立的完整图
ContentTypeRegistry   part name → content type 映射（含 default/override）
PackageSnapshot       打开时的语义指纹（用于 round-trip 对比）
```

### 3.1 写入策略（最小编辑核心）

- XML Part 以 DOM 方式解析，但**保存策略分级**：
  1. 从未修改的 XmlPart → 写回**原始字节**（`dirty=false`，不做任何格式化）；
  2. 修改过的 XmlPart → 以**修改子树局部替换**方式序列化：保留其他节点的原始文本片段
     （把未触碰的 XML 文本按节点切分缓存，重写时只重新输出 dirty 子树 + 合并前后文本片段），
     并保持与原始一致的命名空间前缀声明（不规范化前缀）；
  3. 属性顺序、空元素自闭合写法、空白文本节点尽量保留（DOM 记录原文本，序列化时复用）。
- 修改过的新增 Part（新图片、新 rels）→ 增量追加，不重排既有 rels 顺序。
- 二进制 Part 永不重编码。

### 3.2 未知内容策略

- 未知 Part：原样拷贝（字节级）。
- 未知 XML 节点/属性/命名空间：保留。
- customXml / comments / footnotes / endnotes / textbox / drawing 等结构：保留原始子树；
  仅当语义编辑确实触及其中内容时才局部修改。
- 不支持的复杂对象（SmartArt/图表/OLE/公式/宏/域…）：**Detect → Preserve → Warn**，
  绝不 Detect → Delete。编辑其所在段落的普通文字不得删除它们。

## 4. 对 document.xml 的编辑方式

`core-document` 的 `DocumentXmlWriter` 提供原子操作（作用于 w:p / w:r / w:tbl 等）：

- `replaceParagraphText(p, runs)`：仅重写该段 runs 层；
- `setParagraphProps(p, pPr)`：仅重写该段的 pPr 子树；
- `setRunProps(r, rPr)`、`insertParagraphAfter(anchor, newP)`、`deleteParagraph(p)`、表格行/列/单元格操作等。

每个操作必须携带 `ooxmlAnchor`（指向 XML 节点）在模型上，禁止用"按文本全文搜索"定位。

## 5. RoundTrip 语义指纹与门禁（P0 门禁）

`PackageSnapshot` 记录（不比较字节，比较语义）：

```
- Part 集合（name 集合 + content type + rels target 集合）——多/少即失败
- 每个 XmlPart 的规范形式节点指纹：元素名(含命名空间)序列 + 属性名序列 +
  文本内容聚合 hash + 注释/处理指令计数
- 每个 BinaryPart 的 SHA-256
- relationships 图（节点 + 边）
- 每个 XmlPart 未被标记 dirty 的部分必须逐字节可复现
```

**门禁测试（core-ooxml 单测）**：

```
source.docx → open → (不做编辑) saveAs → reopen
断言：Part 集合一致、BinaryPart hash 一致、未 dirty XmlPart 指纹一致

source.docx → open → 编辑单个段落文字 → export
断言：除该段落范围（及不可避免的关联 rels/内容类型增量）外，其余全部一致
     media/tables/其他 part/嵌入对象零变化
```

Golden Corpus 存放在 `compat/roundtrip-corpus/`（git 管理的小体积样本）与
`compat/compatibility_samples/`（大体积、第三方来源样本，可能 git-lfs/外置，见 test-plan）。

## 6. 兼容目标与基线

- Word Desktop = 第一兼容基准，WPS Desktop = 第二，Android 本地预览 = 第三。
- App 创建/规范化的文档要求四者版式一致；对来源复杂第三方 DOCX 要求 unsupported-but-preserved。

## 7. 命名空间简表（实现参考）

| 前缀 | URI |
|---|---|
| w | http://schemas.openxmlformats.org/wordprocessingml/2006/main |
| r | http://schemas.openxmlformats.org/officeDocument/2006/relationships |
| wp | http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing |
| a | http://schemas.openxmlformats.org/drawingml/2006/main |
| pic | http://schemas.openxmlformats.org/drawingml/2006/picture |
| rels | http://schemas.openxmlformats.org/package/2006/relationships |
| ct | http://schemas.openxmlformats.org/package/2006/content-types |

XML 解析：`javax.xml.stream` 的 StAX（事件流保留原始文本切片），不采用会丢注释/格式化的 DOM 全量重建。
安全配置：关闭 DTD/外部实体（XXE）、限制实体扩展。
