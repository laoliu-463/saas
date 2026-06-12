# Harness 结构政策（structure-policy）

> 长期生效规则；与 `harness/INDEX.md` 一致；不替代 `retention-policy.md` / `report-style-policy.md`。
> 本文件不依赖 harness 内任何子文档，删除 harness 不会破坏业务。

## 1. 硬约束（HARD LIMIT）

| 维度 | 上限 | 例外 |
|---|---|---|
| 一级目录数 | **10** | 无 |
| 任意目录的直接子目录数 | **10** | 无 |
| 任意目录的直接文件数 | **10** | `commands/`、`scripts/` 下的 .ps1/.sh/.py 视为"工具集"豁免文件数；`.gitkeep` 不计 |
| 非脚本 Markdown 文本文件行数 | **200** | `README.md` ≤ 120；archive/ 下历史文件豁免；脚本豁免 |
| 空目录 | 0 | 仅 `manifests/` 下的任务子目录允许临时为空直到首次写入 |

## 2. 一级目录白名单

仅允许以下 10 个一级目录，禁止新增其他一级目录：

1. `README.md`、`INDEX.md`（一级文件，目录白名单外）
2. `rules/` — 长期执行标准、保留政策、报告风格
3. `tasks/` — 当前可执行任务卡
4. `probes/` — 只读探针规范、接口校验模板
5. `reports/` — 当前有效报告（latest-*、current/*）
6. `scripts/` — PowerShell / Bash / Python 工具
7. `manifests/` — GC / 归档 / 删除清单
8. `archive/` — 历史日期桶（YYYYMMDD）
9. `templates/` — 任务 / 报告 / 审计模板

旧目录（`doc/`、`instructions/`、`prompts/`、`runbooks/`、`skills/`、`state/`、`evals/`、`environment/`、`plans/`、`feedback/`、`agents/`、`core/`、`tools/`、`agent-locks/`、`commands/`、`handovers/`）一律收敛为 `rules/` 或 `templates/` 的子级，不再以业务维度名分目录。

## 3. 文件名 / 命名规范

| 类别 | 命名 | 示例 |
|---|---|---|
| 治理规则 | `<topic>.md` | `structure-policy.md` |
| 当前最新证据报告 | `latest-<topic>.md` | `latest-harness-inventory.md` |
| 历史证据 | `archive/YYYYMMDD/<topic>-<seq>.md` | `archive/20260603/order-audit-001.md` |
| 任务卡 | `tasks/<topic>.task.md` | `tasks/sample-lifecycle.task.md` |
| 探针 | `probes/<target>.probe.md` | `probes/order-sync.probe.md` |
| 模板 | `templates/<role>-template.md` | `templates/evidence-report-template.md` |
| 清单 | `manifests/<action>-<seq>.md` | `manifests/gc/2026-06-11-harness-gc.md` |
| 脚本 | `<verb>-<noun>.ps1` | `check-harness-limits.ps1` |

禁止使用：
- 全大写无连字符的旧名：`CURRENT_STATE.md`、`HARNESS_CHANGELOG.md`、`DOMAIN_MAP.md`（必须拆解为 `state/current-business-state.md` 等）
- 以日期为唯一后缀的"每日快照"：`evidence-20260611-144556.md` 一律迁入 `archive/20260611/`

## 4. 收敛动作字典

| 动作 | 含义 | 触发条件 |
|---|---|---|
| KEEP | 不动 | 已合规 |
| MERGE | 合并内容到一个权威文件 | 同名 / 同主题 / 高重叠（>50%） |
| SPLIT | 按章节拆成主文件 + 子文件 | 在线文档 >200 行 |
| ARCHIVE | 迁入 `archive/YYYYMMDD/` | 一次性报告 / 任务已结案 |
| DELETE | 物理删除 | 空目录 / 临时占位 / 已被替代 |
| RENAME | 改文件名 | 命名不符合 §3 |

## 5. 禁止事项

- 禁止向 harness/ 写超过 200 行的新 Markdown 文档（脚本除外）。
- 禁止用空目录 / 假 README 制造表面合规。
- 禁止把过期 evidence 留在 `reports/` 根；必须迁 `archive/`。
- 禁止在 `reports/` 根以"日期"为文件后缀堆叠。
- 禁止把同一任务的 evidence / content-retire / retro 各保留多份在根。
- 禁止删除源码、配置、env、DB migration、test、生产密钥、部署脚本（这些本来就不在 harness 治理范围）。

## 6. 校验

由 `harness/scripts/check-harness-limits.ps1` 自动校验；CI/会话结束前运行一次。失败必须修复后才能提交。
