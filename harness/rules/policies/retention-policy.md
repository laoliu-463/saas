# Harness 保留政策（retention-policy）

> 与 `structure-policy.md` 配套；定义每类文件的"保留多久 / 何时归档 / 何时删除"。
> 仅适用 harness/ 内部；不覆盖业务证据归档（见 `docs/验收/`）。

## 1. 三类生命周期

| 类别 | 定义 | 默认保留 | 到期处理 |
|---|---|---|---|
| 长期 | 政策、合同、规则、模板 | **永久** | 仅当被新版本覆盖时归档旧版到 `archive/YYYYMMDD/` |
| 当前 | 当前任务、当前 evidence、当前探针 | **直到任务结案 + 7 天** | 7 天后迁 `archive/YYYYMMDD/` |
| 临时 | 空目录、`.gitkeep`、一次性占位 | **0 天** | 立即删除 |

## 2. 目录级保留规则

| 目录 | 保留级别 | 说明 |
|---|---|---|
| `rules/` | 长期 | 政策更新时旧版整体迁 `archive/YYYYMMDD/policies/` |
| `tasks/` | 当前 | 任务结案后 7 天迁 `archive/YYYYMMDD/tasks/` |
| `probes/` | 长期 | 探针升级时旧版入 `archive/`，新版本替换 |
| `reports/` | 当前 | 仅 `latest-*` 与 `current/*` 留根；其余一律 `archive/` |
| `reports/archive/YYYYMMDD/` | 长期 | 历史可追溯，不二次清理 |
| `scripts/` | 长期 | 弃用脚本迁 `archive/`，不在根删除 |
| `manifests/` | 长期 | 每次 GC 计划落地后整体留根，不二次清理 |
| `archive/` | 长期 | 永久保留，最长可按年度分桶 |
| `templates/` | 长期 | 模板被替代时旧版入 `archive/` |

## 3. 文件级保留规则

| 文件类型 | 保留 | 到期动作 |
|---|---|---|
| `evidence-YYYYMMDD-HHMMSS.md` | 任务期内多份 | 任务结案后**只保留最后 1 份**，其余删 |
| `content-retire-YYYYMMDD-HHMMSS.md` | 1 份 / 任务 | 任务结案后 7 天内必迁 `archive/` |
| `retro-YYYYMMDD-HHMMSS.md` | 1 份 / 任务 | 同上 |
| `*-handover.md` | 1 份 / 任务 | 任务结案后 7 天内迁 `archive/tasks/` |
| `*-status-before.md` | 1 份 / GC 任务 | 治理基线，**永久**留 `reports/current/` |
| `*-final.md` | 1 份 / GC 任务 | 同上 |
| `latest-*.md` | 1 份 / 主题 | 旧版入 `archive/<日期>/`，新版替换 |
| `*.task.md` | 1 份 / 任务 | 任务结案后 7 天内迁 `archive/<日期>/tasks/` |
| `*.probe.md` | 长期 | 升级时旧版入 `archive/` |

## 4. 命名 / 目录收紧

- 日期桶 `archive/YYYYMMDD/` 内部允许放 ≤10 个直接子分类（如 `tasks/`、`evidence/`、`retros/`），分类也必须满足"≤10 文件 / ≤10 子目录"硬约束。
- 同一日期桶内如果某个分类文件数即将 >10，**按主题合并**到单文件，而不是再开子分类。
- 不允许把"过去 N 天的 evidence"继续平铺在 `reports/` 根。

## 5. 删除豁免清单（DELETE FORBIDDEN）

下列内容即使暂时"看起来没用"也**不得删除**，只可归档：

- `reports/current/*-status-before.md` — 治理基线证据
- `reports/current/*-final.md` — 最近一次治理终态
- `archive/` 任何已存在文件 — 历史追溯
- `manifests/gc/*` — GC 操作历史
- `tasks/*` 当前未结案的任务卡
- `probes/*` 当前生效的探针
- 任何被 `INDEX.md` 引用为权威的文件

## 6. 校验

执行 `harness/scripts/check-harness-limits.ps1` 时同步校验：
- `reports/` 根除 `latest-*` 和 `current/` 之外，不应出现日期命名的 evidence / retro / content-retire 文件。
- `archive/YYYYMMDD/` 任何子目录文件数应 ≤10。
- `tasks/`、`probes/`、`templates/` 的过期文件应已被迁出（仅凭文件 mtime 触发提醒，不自动删除）。
