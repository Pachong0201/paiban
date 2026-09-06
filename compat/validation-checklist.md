# Word / WPS 实机验收清单

> 状态：**BLOCKED_EXTERNAL_VALIDATION**
> 本 Agent 环境（WSL2，无 Windows Office/WPS）无法运行 Microsoft Word Desktop 或 WPS Desktop，
> 因此 Gate J（Word）与 Gate K（WPS）**未执行**，不得宣称 PASS。
> 以下为待人工验证的文件清单与检查步骤。验证完成后更新本文件状态与结果。

## 环境要求

- Windows 10/11 + Microsoft Word Desktop（2016+，建议 365）
- Windows + WPS Office Desktop（个人版/专业版均可）
- Android 设备（API 29+）安装 `app-release-unsigned.apk` 或签名包

## 待验证样本（Gongwen Golden）

1. 用本 App「新建文档 → 粘贴公文正文 → 一键公文排版 → 导出 DOCX」产出的文件；
2. 打开现成 DOCX → 一键排版 → 另存 的文件；
3. 含表格（插入表格→编辑单元格）后导出的文件；
4. 复杂第三方 DOCX（含图片/页眉页脚/批注等）经本 App 打开→另存（未编辑）的文件 —— 验证"不损坏"。

## Word 检查步骤

1. 双击打开，确认**无修复/损坏警告弹窗**；
2. 文件 → 信息：确认无"文档修复"提示；
3. 核对：页数、首页标题是否单行、正文首行缩进 2 字符、行距是否 28 磅观感；
4. 标题（二号小标宋）与正文（三号仿宋）字体显示是否与设置一致；
5. 表格边框完整、跨页时无异常；图片位置无漂移；
6. 页码位置（页脚中部或正下）与 App 预览一致；
7. 修改一段文字后保存重开：无二次损坏提示。

## WPS 检查步骤

同上 1-7；额外：
8. 兼容模式下打开无告警；
9. 打印预览分页与 Word 基本一致（允许 WPS 与 Word 间 ±1 行差异，需记录）。

## 分页一致性核对（Gate 分页）

对"关键字体可用"的样本：
- App 分页预览页数 = PDF 页数；
- PDF 页数 vs Word 实际打印页数；
- 记录差异段落位置与原因（字体度量/行距/页边距/换行规则）。

## 结果记录格式

| 样本 | Word 打开 | Word 修复警告 | WPS 打开 | 页数(Word/WPS/App) | 结论 |
|---|---|---|---|---|---|
| … | OK/FAIL | 无/有 | OK/FAIL | x/y/z | PASS/FAIL |

全部 PASS 后：更新 docs/architecture.md 与最终验收报告相应 Gate 为 PASS。
