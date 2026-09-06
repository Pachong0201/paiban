# 模板 Schema 文档

> 模板 = 一套独立可移植的"公文版式规则包"，文件扩展名 `.twtemplate`（内部为 ZIP）。
> 模板通过 **basedOn 继承**复用 GB/T 9704 国家模板，单位模板只声明差异。

## 1. 文件结构

```
*.twtemplate  (ZIP)
├── manifest.json     模板元数据（必须）
├── page.json         页面设置（必须）
├── styles.json       role → 样式定义（必须）
├── rules.json        检查规则 / 固定值规则（可选，缺省用国家默认）
├── preview.json      模板编辑器的实时样例文案（可选）
├── metadata.json     单位/作者等展示信息（可选）
└── assets/           预留（页眉 logo 图等，MVP 可空）
```

schemaVersion 当前 = `1`。解析器必须做 schema migration。

## 2. manifest.json

```json
{
  "templateId": "gbt-9704-2012",
  "name": "GB/T 9704—2012 党政机关公文格式",
  "version": "1.0.0",
  "schemaVersion": 1,
  "builtin": true,
  "baseTemplate": null,
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z",
  "author": ""
}
```

`builtin` 模板不可删除/覆盖；`baseTemplate` 为 null 表示根模板（内置只有 GB/T 一根）。

## 3. page.json

```json
{
  "pageSize": { "widthTwips": 11906, "heightTwips": 16838 },
  "orientation": "portrait",
  "marginTopTwips": 1440,
  "marginBottomTwips": 1440,
  "marginLeftTwips": 1654,
  "marginRightTwips": 1440,
  "headerDistanceTwips": 851,
  "footerDistanceTwips": 992,
  "textColumns": 1
}
```

单位统一 **twips**（1/20 磅；1cm ≈ 566.93 twips）。GB/T 版心：上 3.7cm、下 3.5cm、左 2.8cm、右 2.6cm（Word 无网格时常用近似），具体数值以模板为准、可被单位模板覆盖。

## 4. styles.json — role 样式定义

角色（Role）集合固定：

```
title recipient body heading1 heading2 heading3 heading4 quotation
attachmentDescription signature date annotation footer colophon
tableText sealLine  ← 印章说明行（盖章处）
redLine            ← 红色分隔线专用（结构元素，不一定有样式）
```

每个 role 的定义结构：

```json
{
  "title": {
    "paragraph": {
      "alignment": "center",
      "firstLineIndentTwips": 0,
      "leftIndentTwips": 0,
      "rightIndentTwips": 0,
      "lineSpacing": { "rule": "exact", "valueTwips": 680 },
      "spaceBeforeTwips": 0,
      "spaceAfterTwips": 400,
      "keepWithNext": true,
      "keepLinesTogether": true
    },
    "text": {
      "eastAsiaFont": "方正小标宋简体",
      "latinFont": "Times New Roman",
      "fontSizeHalfPoints": 44,
      "bold": false,
      "italic": false,
      "underline": "none",
      "characterSpacingTwips": 0,
      "textColor": "000000"
    }
  }
}
```

字号用 **half-points**（Word 内部单位；三号=32 half-points=16pt，二号=44=22pt，小标宋 title 建议二号/22pt）。
字体是**声明名**（document declared font），与设备实际渲染字体解耦（见字体策略 §6）。

## 5. rules.json — 检查规则

```json
{
  "requiredElements": ["sepHongXian", "title", "recipient", "body", "date"],
  "rules": [
    {
      "id": "gb/t-title-font",
      "scope": "role:title",
      "check": "font == { eastAsiaFont: 方正小标宋简体 } && fontSize == 44",
      "severity": "error"
    }
  ]
}
```

MVP 中 rules 由 role 样式 + 文档级默认（正文行距 28 磅固定、页码格式等）驱动；
`DocumentValidator` 的规则集由 **Current Effective Template**（继承解析后）唯一决定。

## 6. 继承 / override 语义

单位模板 `basedOn = "gbt-9704-2012"`：

- 解析时自底向上合并：`effective(role) = unit[role] ?: gbt[role]`（深度合并 paragraph/text 子结构）。
- 单位模板**不需要**复制国家完整规则，只需列差异。
- 同名 role 覆盖；未提及 role 自动继承。
- `rules.json` 合并：单位规则整体替换同 id 国家规则，新增规则追加。
- 无环校验：模板图必须是森林（除内置根外恰好一个 basedOn 且不得成环）。

## 7. 模板抽取（从样本文档 DOCX）

输入一份正确排版的 DOCX → `TemplateExtractor` 产出 `TemplateDraft`：

- page/section/margins、默认字体、标题/正文样式频率统计、编号模式（heading pattern）、
  页眉页脚/页码、表格样式。
- 推断结合：内容结构 + 样式频率 + 位置 + 编号模式。
- **禁止盲目把 Word Heading1 当一级标题**；置信不足的项进入 `pendingConfirmation`（"待确认"）。
- 默认不保存样本文档正文（避免单位敏感内容进入模板）。
- 用户确认后写回 `.twtemplate`。

## 8. 导入导出与冲突

- 导出/分享：将上述 JSON 打包为 `.twtemplate`。
- 导入：按 templateId 冲突策略 —— 新版本覆盖（比 version 大）、同名同版本提示、
  不同 id 直接新增；schemaVersion 高于当前解析器 → 拒绝并提示升级。
- 模板库以 JSON 文件存于 App 私有目录 `templates/`（或 Room，二选一；MVP 先文件存储 + 索引）。

## 9. 文件命名与定位

模板 JSON schema 常量定义在 `core-template` 的 `TemplateSchema`；
GB/T 内置模板常量位于 `core-gongwen`（`Gbt9704Template`），以代码内嵌 JSON 字符串形式随 APK 提供
（文本资源，不涉及字体文件，规避字体重分发许可问题）。
