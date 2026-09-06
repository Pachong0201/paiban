# Workflow

- Wants the agent to execute directly from a given spec rather than stopping after design documents or repeatedly re-confirming already-specified requirements ("不要重复询问已经明确的产品需求"; "不要只写设计文档然后停止"). Confidence: 0.9
- Gives the agent autonomy over implementation choices (class names, module splits, libraries, parsers, database, serialization, refactors); for non-critical ambiguity expects the agent to pick the safest, most maintainable option and continue instead of asking. Confidence: 0.9
- Prefers small, incremental, runnable commits per phase with tests passing on every commit; forbids accumulating one giant final commit. Confidence: 0.9
- Demands honest verification status: never claim PASS or compatibility that wasn't actually performed; when the environment cannot run required external validation (e.g., real Word/WPS), explicitly report BLOCKED_EXTERNAL_VALIDATION and provide artifacts/checklist for human verification rather than faking success. Confidence: 0.95
- Rejects unmeasured or invented numbers: performance/timing claims must come from actual measurement on a baseline device; test reports must give accurate counts, not fabricated figures. Confidence: 0.9
- Wants explicit acceptance gates and a structured final report (PASS/FAIL/BLOCKED, accurate counts, known limitations clearly separated from intentionally out-of-scope items); a vague "done" is not acceptable. Confidence: 0.85
- When existing code conflicts with the agreed architecture, prefers fixing the architecture over accommodating the wrong implementation ("优先修正架构，而不是兼容错误实现"). Confidence: 0.85
- Forbids weakening tests to pass: no bulk-accepting golden snapshots to mask regressions, no lowering thresholds to make gates pass. Confidence: 0.85
