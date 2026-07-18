# Harness 执行引擎

本目录（`harness/`）是抖音团长内部 SaaS V2 工程的核心自动化执行与监控基座。
主要用于标准化 Agent 的执行流程、状态流转、质量验证及报告输出。

## 目录结构说明
- `rules/`：长期规则、执行规范、质量门禁。
- `tasks/`：当前可执行任务卡，按领域或主题拆分。
- `probes/`：只读探针说明、接口核验模板、验证方法。
- `reports/`：受控报告入口；稳定当前摘要位于 `reports/current/`。
- `scripts/`：PowerShell / Bash / Python 等自动化执行脚本。
- `manifests/`：清理、归档、删除操作的证据清单。
- `archive/`：历史归档，保留核心结果索引。
- `templates/`：任务模板、报告模板、审计模板。
- `engineering/`：Matt Pocock engineering skills 的项目配置。
- `src/`：Node / TypeScript Harness 核心源码。
- `contracts/`：JSON Schema 与机器可读策略。
- `state/`：稳定发布、架构或验证基线，按需创建；禁止放入运行时产物。
- `tests/`：Harness 自身测试。

上述 13 个目录构成一级目录白名单。新增目录只在有真实职责和内容时创建，本任务不创建空目录；原始运行产物继续写入 `runtime/qa/out/<run-id>/`。

## 新增文件守则
1. **活跃预算**：直接文件/子目录 40 预警、50 硬上限；非脚本文本 160 行预警、200 行硬上限。
2. **报告预算**：`reports/` 根目标不超过 20；当前摘要覆盖写入 `reports/current/latest-<topic>.md`。
3. **增量门禁**：历史债务不阻断无关任务，但新增或恶化必须失败；同时输出任务门禁与仓库健康度。
4. **流水分离**：原始日志和长输出写入 `runtime/qa/out/<run-id>/`；归档/删除必须有 manifest。

> 详情请查阅 `rules/harness-structure-policy.md` 和 `INDEX.md`。

固定入口要求显式提供稳定 `ReportKey` 与当前任务的 `OwnedFiles`；脚本只暂存这些路径及本轮自动生成的报告/归档目标。

## DDD 收口验收

DDD 重构收口使用固定脚本聚合检查工作区、白名单、证据矩阵、架构测试和通用安全门禁：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-ddd-acceptance.ps1 -MaxRedlineDebt 11
```

常用变体：

- `-DocsOnly`：跳过 Maven，只做文档、矩阵、白名单和通用 docs 安全检查。
- `-RequireRedlineZero`：要求 architecture redline 白名单有效项为 0。
- `-FailOnUnexpectedDirty`：遇到非 docs/harness 且非登记历史 dirty 的文件时失败。

报告默认写入 `harness/reports/latest-ddd-acceptance-report.md`。详细口径见 `docs/ddd-validation-guide.md`。
