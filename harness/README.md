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
1. **禁止越权**：任何目录子级不得超过 10 个子目录/10 个文件。
2. **禁止超长**：非脚本文档不得超过 200 行。
3. **禁止堆积**：临时产生的流水日志阅后即焚，有效结论写入 `reports/`。

> 详情请查阅 `rules/harness-structure-policy.md` 和 `INDEX.md`。
