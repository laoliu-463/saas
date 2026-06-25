# GC Plan — Harness 极致瘦身与结构治理（2026-06-11）

> 任务 ID：HARNESS-DOC-GC-OPTIMIZE-002
> 生成时间：2026-06-11
> 上游证据：`harness/reports/latest-harness-inventory.md`
> 关联政策：`harness/rules/structure-policy.md`、`harness/rules/retention-policy.md`、`harness/rules/report-style-policy.md`
> 范围：仅 harness/ 内部；不触碰源码、配置、env、DB migration、test、生产密钥、部署脚本

## 1. 目标一级目录（10 个）

```
harness/
├── README.md
├── INDEX.md
├── rules/       ← 治理标准（structure / retention / report-style）
├── tasks/       ← 当前任务卡
├── probes/      ← 探针规范
├── reports/     ← 当前有效报告（latest-*、current/*）
├── scripts/     ← PowerShell / Bash 工具
├── manifests/   ← GC / 治理清单
├── archive/     ← 历史日期桶
└── templates/   ← 任务 / 报告 / 审计模板
```

## 2. 目录映射表（OLD → NEW，动作 KEEP/MERGE/SPLIT/ARCHIVE/DELETE/RENAME）

### 2.1 一级目录收敛

| 旧目录 | 动作 | 目标 | 说明 |
|---|---|---|---|
| `agent-locks/` | MERGE | `manifests/locks/` | LOCK_INDEX.md + 各 .lock.md 合并为一个 index |
| `agents/` | DELETE | — | templates/ 为空目录，无任何文件 |
| `agents/templates/` | DELETE | — | 空目录 |
| `archive/` | RENAME | `archive/` | 保留，新增 manifests/ 旧 batch 标识 |
| `commands/` | MERGE | `scripts/` | 10 个 .ps1 迁 `scripts/commands/` |
| `core/` | MERGE | `rules/style/` | 04-doc-style-guide.md → `rules/style-guide.md` |
| `doc/` | MERGE | `rules/instructions/` + `rules/environment/` + `rules/feedback/` | 5 个子分类按内容主题收敛 |
| `environment/` | MERGE | `rules/environment/` | 7 个 md 合并到 1 个 index + 链接 |
| `evals/` | MERGE | `probes/` | 6 个 .evals 视为探针规范 |
| `feedback/` | MERGE | `templates/` + `rules/feedback/` | 模板类入 templates/，规则类入 rules/feedback/ |
| `handovers/` | MERGE | `tasks/` | handover 是任务卡的一种 |
| `instructions/` | MERGE | `rules/instructions/` | 13 个 md 收敛为 index + 域子文件 |
| `manifests/` | KEEP | `manifests/` | 保留，新增 gc/ 子分类 |
| `plans/` | MERGE | `rules/roadmap.md` | 3 份长期路线合并为 1 份（≤200 行） |
| `prompts/` | MERGE | `templates/prompts/` | 5 个 prompt + 15 个 agents + 4 batches 收敛 |
| `reports/` | KEEP | `reports/` | 根级收敛为 latest-* / current/，其余迁 archive/ |
| `runbooks/` | MERGE | `rules/runbooks/` | 16 个 md 收敛为 index + 主题子文件 |
| `scripts/` | KEEP | `scripts/` | 保留，新增 check-harness-limits.ps1 |
| `skills/` | MERGE | `rules/skills/` | 18 个 skill.md 收敛为 index + 主题子文件 |
| `state/` | MERGE | `rules/state/` + `reports/current/` | 长期状态入 rules/，当前快照入 reports/current/ |
| `tasks/` | KEEP | `tasks/` | 保留 2 份任务卡 |
| `tools/` | MERGE | `scripts/README.md` | README 合并到 scripts/ 入口 |

### 2.2 一级 Markdown 文件收敛

| 旧文件 | 动作 | 目标 | 说明 |
|---|---|---|---|
| `AGENT_CONTRACT.md` (260 行) | MERGE | `rules/agent-contract.md` | 拆为 rules/agent-contract.md（≤200 行） |
| `COMPLETION_GATES.md` | MERGE | `rules/completion-gates.md` | 与 `completion-gates-detail.md` / `completion-gates-git.md` 合并 |
| `completion-gates-detail.md` | MERGE | 同上 |  |
| `completion-gates-git.md` | MERGE | 同上 |  |
| `CURRENT_STATE.md` (350 行) | SPLIT | `rules/state/current-business-state.md` + `reports/current/business-state-snapshot.md` | 主文件 ≤200 行，详细入 current/ |
| `DOMAIN_MAP.md` | MERGE | `rules/domains-map.md` | 1 份域地图 |
| `FORBIDDEN_SCOPE.md` | MERGE | `rules/forbidden-scope.md` | 1 份禁止范围 |
| `HARNESS_CHANGELOG.md` (614 行) | SPLIT | `rules/changelog.md`（主索引 ≤200 行）+ `archive/20260610/harness-changelog-full.md` | 详细历史入 archive |
| `INDEX.md` | RENAME | `INDEX.md` | 内容重写为导航版 |
| `QUALITY_LEDGER.md` | MERGE | `rules/quality-ledger.md` | 收敛 |
| `README.md` | REWRITE | `README.md` | ≤120 行重写 |
| `SESSION_EXIT_GATE.md` | MERGE | `rules/session-exit-gate.md` | 收敛 |
| `TASK_ROUTING.md` | MERGE | `rules/task-routing.md` | 收敛 |

### 2.3 reports/ 根级文件收敛

| 旧文件 | 动作 | 目标 |
|---|---|---|
| `reports/.gitkeep` | DELETE | — |
| `reports/evidence-20260611-144558.md` (1003 行) | SPLIT | `reports/current/evidence-20260611-settlement.md`（摘要 ≤200 行）+ `archive/20260611/evidence-20260611-144558-full.md` |
| `reports/evidence-20260611-144556.md` (1001 行) | SPLIT | 同上模式（保留最新 1 份） |
| `reports/evidence-20260611-144351.md` (995 行) | DELETE | 同任务保留最后 1 份即可 |
| `reports/evidence-20260610-203014.md` (966 行) | DELETE | 同任务重复 |
| `reports/evidence-20260610-202445.md` (916 行) | DELETE | 同任务重复 |
| `reports/content-retire-20260610-202445.md` | ARCHIVE | `archive/20260610/content-retire-20260610-202445.md` |
| `reports/content-retire-20260611-144556.md` | ARCHIVE | `archive/20260611/content-retire-20260611-144556.md` |
| `reports/ddd-sample-005-fix-sample-agent-20260610.md` | KEEP | `reports/current/ddd-sample-005-fix.md` |
| `reports/harness-doc-gc-optimize-001-status-before.md` | KEEP | `reports/current/harness-doc-gc-001-status-before.md` |
| `reports/order-6468-db-extra-data-*.json` | MERGE | 与对应 .md 合并为 .md 末尾附录 |
| `reports/order-6468-live-raw-*.json` | MERGE | 同上 |
| `reports/order-6468-effective-fields-audit-*.md` | MERGE | `reports/current/order-6468-audit.md` |
| `reports/order-6468-nested-phase-id-aliases-audit-*.md` | MERGE | `reports/current/order-6468-audit.md`（合一份） |
| `reports/order-6468-settlement-as-primary-001.md` | MERGE | 同上 |
| `reports/evidence-20260611-145500-settlement-verify.md` | MERGE | `reports/current/settlement-verify.md` |

### 2.4 reports/archive/ 内部收敛

| 旧文件 / 旧子目录 | 动作 | 目标 |
|---|---|---|
| `reports/archive/20260603/` 94 文件 | KEEP | 保留原状（已入 archive，豁免文件数与行数） |
| `reports/archive/20260604/` 62 文件 | KEEP | 同上 |
| `reports/archive/20260605/` 20 文件 | KEEP | 同上 |
| `reports/archive/20260606/` 88 文件 | KEEP | 同上 |
| `reports/archive/20260607/` 14 文件 | KEEP | 同上 |
| `reports/archive/20260608/` 43 文件 | KEEP | 同上 |
| `reports/archive/20260609/` 34 文件 | KEEP | 同上 |
| `reports/archive/20260610/` 34 文件 | KEEP | 同上 |
| `reports/current/harness-doc-gc-optimize-001-final.md` | KEEP | 保留为最近一次 GC 终态 |
| `archive/retired-content/20260603-reports-archive/` 52 文件 | KEEP | 历史一次归档 |
| `archive/manifests/` 1 文件 | KEEP | 历史 manifest |

### 2.5 prompts/ 收敛

| 旧路径 | 动作 | 目标 |
|---|---|---|
| `prompts/agents/00-coordinator.md` | KEEP | `templates/prompts/agents/00-coordinator.md` |
| `prompts/agents/01-14-*.md` (14 份) | KEEP | 同上目录 |
| `prompts/batches/0-3-*.md` (4 份) | KEEP | `templates/prompts/batches/` |
| `prompts/*.prompt.md` (5 份) | KEEP | `templates/prompts/` |
| `prompts/agents/` 15 文件 / 1 目录 | KEEP | 上限 10 文件 — 拆分到 `agents/` 子目录 |

### 2.6 runbooks / skills / instructions 收敛

三类都按"主题分组"拆为子目录，每个子目录 ≤10 文件，根目录只保留 index.md：

- `rules/runbooks/index.md` + `rules/runbooks/<topic>.md` ≤10 个
- `rules/skills/index.md` + `rules/skills/<topic>.md` ≤10 个
- `rules/instructions/index.md` + `rules/instructions/<domain>.md` ≤10 个

### 2.7 commands 收敛

| 旧文件 | 动作 | 目标 |
|---|---|---|
| `commands/_lib.ps1` | KEEP | `scripts/commands/_lib.ps1` |
| `commands/agent-do.ps1` | KEEP | `scripts/commands/agent-do.ps1` |
| `commands/collect-evidence.ps1` | KEEP | `scripts/commands/collect-evidence.ps1` |
| `commands/deploy-remote.ps1` | KEEP | `scripts/commands/deploy-remote.ps1` |
| `commands/git-push-safe.ps1` | KEEP | `scripts/commands/git-push-safe.ps1` |
| `commands/new-retro.ps1` | KEEP | `scripts/commands/new-retro.ps1` |
| `commands/restart-compose.ps1` | KEEP | `scripts/commands/restart-compose.ps1` |
| `commands/retire-content.ps1` | KEEP | `scripts/commands/retire-content.ps1` |
| `commands/safety-check.ps1` | KEEP | `scripts/commands/safety-check.ps1` |
| `commands/verify-local.ps1` | KEEP | `scripts/commands/verify-local.ps1` |
| `scripts/check-doc-lines.ps1` | KEEP | `scripts/check-doc-lines.ps1` |
| `scripts/check-harness-limits.ps1` (新增) | CREATE | Step 7 落地 |

## 3. 实施顺序

1. **Step 4**：先建目标目录骨架（rules/tasks/probes/templates/manifests 已建好），再迁 files。
2. **Step 5**：压缩在线 2 个 >200 行文档（HARNESS_CHANGELOG、CURRENT_STATE）。
3. **Step 6**：写新 README.md（≤120 行）+ INDEX.md（≤200 行）。
4. **Step 7**：写 check-harness-limits.ps1。
5. **Step 8**：跑校验，输出 latest-harness-limits-check.md。
6. **Step 9**：输出最新 latest-harness-gc-report.md。

## 4. 风险与回滚

- 主要风险：批量 mv 后 git status 噪音 → 风险低，分批提交可缓解。
- 回滚：所有动作在 git 工作树执行；任意步骤可 `git checkout -- harness/` 整体回退。
- 不删除任何 archive/ 内容；任何"删除"动作都是物理 `rm` 临时占位文件，可由 git 找回。
