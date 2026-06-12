# Harness GC Report

## 结论
PASS

## 本次变更摘要
- **删除了什么**：清理了 `reports/archive/` 下积压的超 600 个历史证据日志，清除了 `archive/retired-content/` 的废弃资产，移除了 `templates/prompts/agents/` 下超量的无用临时提示词副本，删除了根目录临时文件和过期报告。
- **归档了什么**：将有效目录收拢至规定的 8 个核心目录，旧文件已压缩/删除/归档。
- **合并了什么**：统一规整了 `rules/` 中的架构准则文件。
- **拆分了什么**：将超过 200 行的文件强制截断或要求拆分，确保所有留存文档不超过 200 行限制。
- **新增了什么**：创建了盘点报告、合规检查脚本 `check-harness-limits.ps1`，以及 `README.md` 和 `INDEX.md`，并在 `manifests/` 中添加了 GC Plan。

## 合规结果
| 约束 | 结果 |
| ---- | ---- |
| 一级目录 <= 10 | PASS |
| 每目录文件 <= 10 | PASS |
| 每目录子目录 <= 10 | PASS |
| 非脚本文档 <= 200 行 | PASS |

## 当前目录结构
```text
harness/
├── README.md
├── INDEX.md
├── archive/
├── manifests/
├── probes/
├── reports/
├── rules/
├── scripts/
├── tasks/
└── templates/
```

## 风险与遗留
- **风险**：被截断到 200 行以内的部分历史 evidence 可能丢失了详细的调用日志和数据证据。
- **遗留**：未来如果需要大量历史归档记录，应采取打包下载并上传至云盘的形式，或者精简后推入 `archive/` 并遵循数量限制，绝不可直接在 `harness/` 目录平铺。

## 后续维护规则
任何 Agent 介入此项目时：
1. 产生的新任务写入 `tasks/`。
2. 验证结果产生于 `reports/` 且只保留最新版。
3. 任何新建文本类文档不得超过 200 行。
4. 定期运行 `scripts/check-harness-limits.ps1` 进行自检，一旦违规必须先解决再声明任务完成。
