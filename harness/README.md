# Harness 执行引擎

本目录（`harness/`）是抖音团长内部 SaaS V2 工程的核心自动化执行与监控基座。
主要用于标准化 Agent 的执行流程、状态流转、质量验证及报告输出。

## 目录结构说明
- `rules/`：长期规则、执行规范、质量门禁。
- `tasks/`：当前可执行任务卡，按领域或主题拆分。
- `probes/`：只读探针说明、接口核验模板、验证方法。
- `reports/`：当前仍有效的最新报告，旧报告已归档或清理。
- `scripts/`：PowerShell / Bash / Python 等自动化执行脚本。
- `manifests/`：清理、归档、删除操作的证据清单。
- `archive/`：历史归档，保留核心结果索引。
- `templates/`：任务模板、报告模板、审计模板。
- `engineering/`：Matt Pocock engineering skills 的项目配置。

## 新增文件守则
1. **禁止越权**：任何目录的直接子目录/直接文件均不得超过 50 个。
2. **禁止超长**：非脚本文档不得超过 200 行。
3. **禁止堆积**：临时产生的流水日志阅后即焚，有效结论写入 `reports/`。
4. **定期复查**：任务结束后必须运行限制检查；每周或每个迭代开始前做一次清理复查。

> 详情请查阅 `rules/harness-structure-policy.md` 和 `INDEX.md`。

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
