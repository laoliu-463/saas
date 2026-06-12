# Harness GC Plan

| 当前路径 | 类型 | 处理动作 | 目标路径 | 原因 | 风险 |
| -------- | ---- | -------- | -------- | ---- | ---- |
| `reports/archive/` 及内部所有日期子目录 | 目录/报告 | ARCHIVE/DELETE | `archive/reports-history/` (打包或精简归档) | 过期报告超量积压，违反目录及文件数量约束，大量历史证据文件无保留价值。 | 可能误删有追溯价值的历史联调记录，需打包保存或精简。 |
| `archive/retired-content/` | 目录 | DELETE | N/A | 无效的、过期的已退役内容堆积，不再具备执行参考意义。 | 无 |
| `templates/prompts/agents/` | 目录/文件 | DELETE | N/A | 目录文件数超限且多为旧版多 Agent 协作遗留临时文件或重复提示词。 | 若有仍在使用模板需提取，建议全部清除重新收敛。 |
| `reports/current/` | 目录/文件 | MERGE/DELETE | `reports/` (收敛为不超过 10 个最新文件) | 文件过多、信息离散。 | 无 |
| `reports/latest-harness-inventory.md` | 报告 | KEEP | `reports/latest-harness-inventory.md` | 本次盘点报告，符合规则。 | 无 |
| `reports/` 历史流水报告 | 文件/目录 | ARCHIVE | `archive/reports-20260612-ddd-event-003-retired.zip` | DDD-EVENT-003 提交后 reports 根目录 88 个文件，违反每目录 10 文件限制。 | 历史证据需从 zip 追溯。 |
| `rules/` 现有非标准文件 | 文件 | MERGE/DELETE | `rules/` (仅保留 3 个 policy 文件及核心规则) | 统一执行标准，去除多余文件。 | 无 |
| 超长历史文件 (如 `multi-agent-ddd-prompts-full.md`) | 报告 | DELETE/ARCHIVE | N/A | 单文件超200行严重违规，且已过时。 | 无 |
