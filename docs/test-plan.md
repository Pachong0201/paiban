# 测试计划

> 测试按"桌面 JVM 快速单测优先、Android 仪器测试补 UI、Golden 防回归"组织。
> 所有测试命令在 CI/本地必须可重复、可离线（首次构建缓存后）。

## 1. 测试分层

| 层 | 运行位置 | 覆盖 |
|---|---|---|
| Unit | JVM（core-*） | OOXML 解析/关系/样式/numbering/模型/样式级联/检测器/validator/字体映射/导出/round-trip |
| Instrumentation | Android（app） | 打开文件、编辑、一键排版、Undo、检查、修复、模板增删改、DOCX/PDF 导出 |
| Golden | JVM + 仪器 | DOCX 语义 golden、OOXML 结构 golden、布局 golden、PDF 视觉 golden |
| RoundTrip | JVM（core-ooxml） | P0 门禁：开→存→开 语义无丢失；局部编辑不影响无关部分 |
| Performance | JVM（core-layout） | 5/20/50/100 页布局耗时与内存 |

## 2. 语料库（corpus）

```
testdata/corpus/
├── gongwen/           公文 golden 正样本（通知/通报/报告/请示/批复/函/纪要）
├── variants/          识别变体样本（"一."/"一 ."/"（一） "/"(一)"/"1、"/"1．" 与不规范空格）
├── negative/          高难负样本（正文内含"一、二、三"但非标题，如"资金来源、人员安排"）
├── complex/           复杂 Word（大表/合并单元格/多图/多节/页眉页脚/域/批注/修订/textbox/SmartArt/OLE/公式）
├── corrupt/           损坏/异常（坏 zip、zip bomb、超大图、无 styles.xml、关系断裂、空文档等）
└── roundtrip/         round-trip golden（Phase 1 建立）
```

`compat/`（仓库外或 git-lfs）：`compatibility_samples/` 供 Word/WPS 实机验收。
体积小、可再分发的样本进 git；来源受限的大样本放外置目录并在 README 记录来源与许可证。

## 3. RoundTrip 门禁（P0，禁止跳过）

见 `docs/ooxml-strategy.md` §5。所有 round-trip 断言必须在 PR 门禁中运行；
禁止"为通过而跳过"（规格书禁止 7）。

## 4. 识别盲测

`GongwenStructureDetector` 验收标准（阈值以实际 corpus 锁定后写入本文件并冻结）：

- 正样本：结构规范公文，主要角色识别正确率 ≥ 阈值（初定 95%，样本建立后复核）；
- 变体样本：容忍格式噪声（不同编号写法的标题仍被识别为标题，含置信度标注）；
- 负样本：正文中"一、…二、…三、…"列表**不得**整体误判为多级标题（允许单句低置信提示，<0.60 保持正文）；
- 疑似带：0.60–0.84 标记"疑似"；≥0.85 自动应用；<0.60 保持正文（实际阈值在 corpus 上标定后更新并冻结）。

输出 `PredictedRole { role, confidence, reasonCodes[] }`，供 UI 人工纠正与 validator 使用。

## 5. Golden 测试规则

- 新 golden 必须有 diff 说明；**禁止批量接受 snapshot 掩盖回归**。
- DOCX semantic golden：编辑操作后模型序列化对比。
- OOXML structure golden：PackageSnapshot 指纹对比。
- PDF visual golden：core-layout 输出位图/布局树对比（桌面 JVM 先行；像素级对比仅对固定字体样本）。

## 6. 兼容性门禁（无法实机时如实标注）

- Gate J（Word）/ Gate K（WPS）必须真实运行于 Windows Desktop；
  本 Agent 环境若无法运行 → 结论 `BLOCKED_EXTERNAL_VALIDATION`，并输出
  `compat/validation-checklist.md`（待人工验证文件清单 + 检查步骤），不得宣称 PASS。

## 7. 异常与安全测试

损坏 DOCX、zip bomb（超限拒绝）、超大图采样、无 styles.xml、relationship 断裂、空文档、
纯表格、纯图片、100+ 页、URI 权限丢失、存储不足、导出中断 —— 全部要求：不 crash、
不产出损坏文件、给出可理解错误。

## 8. 性能基准（Phase 5 建立）

- 以 5/20/50/100 页纯文字公文 + 表格/图片样本，在中端 Android 设备与 JVM 分别测量；
- 指标：解析耗时、首帧布局耗时、滚动流畅度、峰值内存、导出耗时；
- 目标（基准建立后修订）：50 页常规公文正常编辑/预览不 OOM。

## 9. 命令约定

```bash
./gradlew :core-ooxml:test :core-document:test :core-gongwen:test \
          :core-template:test :core-layout:test        # 全部 JVM 单测
./gradlew :app:assembleDebug                            # Debug APK
./gradlew :app:assembleRelease                          # Release APK（Gate A）
./gradlew :app:connectedDebugAndroidTest                # 仪器测试（需设备/模拟器）
```

## 10. 交付时测试统计口径

Unit / Instrumentation / Golden / RoundTrip / Performance 分别给出准确数量
（passed / failed / skipped + skip 理由）。最终验收只能：
`PASS` / `FAIL` / `BLOCKED_EXTERNAL_VALIDATION`。
