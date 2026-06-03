# Harness Debt Governance — Plan & Debt Register

- **任务**：HARNESS-DEBT-GOVERNANCE-ITERATION
- **生成时间**：2026-06-03 23:03:34
- **对应盘点报告**：`harness/reports/harness-debt-governance-inventory-20260603-230334.md`
- **范围**：仅 harness/ + docs/ 治理类债务；不登记业务功能债务

---

## 1. 优先级分层

| 等级 | 含义 | 处理紧迫度 |
| --- | --- | --- |
| P0-blocker | 阻塞业务闭环、阻塞验收、导致数据错误、权限越界、归因错误、部署失败 | 立即处理 |
| P1-risk | 当前可用，但有重复实现、不一致风险、测试不足、环境漂移 | 本迭代处理 |
| P2-cleanup | 文档过时、报告堆积、命名不统一、临时残留、已完成任务未归档 | 整理窗口处理 |
| P3-enhancement | 自动化增强、模板优化、提示词增强、未来治理能力 | 后续专项 |

## 2. 债务登记总表

> 格式：`DEBT-NNN | 领域 | 级别 | 现象 | 根因 | 当前状态 | 建议处理 | 是否本任务处理 | 后续任务`

### P0 — 阻塞级（harness 自身）

> 本次盘点未发现 P0-blocker 类债务。仓库工作区干净、commit HEAD 含本次最新样本；
> harness 五大子系统均存在并基本闭环。

### P1 — 风险级

| ID | 领域 | 现象 | 根因 | 当前状态 | 建议处理 | 本任务 |
| --- | --- | --- | --- | --- | --- | --- |
| DEBT-001 | Instruction | 任务生命周期没有显式 runbook，新 Agent 容易跳过 Intake/Scope 判定 | `harness/runbooks/` 缺 `task-lifecycle.md` | open | 本任务新增 runbook | ✅ |
| DEBT-002 | Instruction | `harness/instructions/safety-rules.md` 与 `harness/FORBIDDEN_SCOPE.md` 内容重叠 | 历史分层时未去重 | open | 在 safety-rules.md 顶部加指针至 FORBIDDEN_SCOPE.md，并标注"主源在 FORBIDDEN_SCOPE" | ✅ |
| DEBT-003 | Tool | Scope → Command 决策表分散在 TASK_ROUTING 和 doc/02-tools | 历史两处描述一致但未做单一入口 | open | 新增 `harness/runbooks/scope-command-matrix.md` 作为单一决策表 | ✅ |
| DEBT-004 | State | `state/KNOWN_ISSUES.md` / `state/p0-p1-register.md` / `state/known-risks.md` 三个文件职责分工隐式 | 历史上分别承担 issue / P0-P1 列表 / 风险列表，但顶部未互引 | open | 在三文件顶部各加 1 行分工指针 | ✅ |
| DEBT-005 | State | `state/DEPLOYMENT_STATE.md` 与 `harness/environment/remote-real-pre-env.md` 内容部分重叠 | 历史两份独立维护 | open | 在 DEPLOYMENT_STATE.md 顶部加指针至 environment/ | ✅ |
| DEBT-006 | State | `state/TASK_HISTORY.md` 自上次 Harness 治理后只记 1 行（2026-06-02），未补后续 | 任务历史未跟随更新 | open | 本次盘点后追加最新 5+ 任务摘要 | ✅ |
| DEBT-007 | Feedback | 现有 evidence / retro 模板为 11 节，对纯 docs-only 任务过重 | 模板未分场景 | open | 保留主模板；新增 docs-only 最小模板（在 feedback 目录） | ✅ |
| DEBT-008 | Environment | 本地端口 / 健康检查 URL 分散在 6 份 environment 文档 | 历史多文档并列 | open | 新增 `harness/environment/CHEATSHEET.md` 单一速查表 | ✅ |
| DEBT-009 | Tool | `state/VALIDATION_STATE.md` 的"验证入口表"未与 `TASK_ROUTING.md` 的"任务类型分流"互引 | 双表未对齐 | open | 在两处顶部加互引 | ✅ |

### P2 — 清理级

| ID | 领域 | 现象 | 根因 | 当前状态 | 建议处理 | 本任务 |
| --- | --- | --- | --- | --- | --- | --- |
| DEBT-010 | State | `harness/CURRENT_STATE.md` 自最近一次治理后未做目录级别索引 | 文档持续追加，目录未生成 | open | 顶部加目录 + 章节指针 | ✅ |
| DEBT-011 | State | `harness/QUALITY_LEDGER.md` "下一步" 列与 `state/DOMAIN_STATUS.md` "DDD 优化下一步" 列有重复 | 两者承担同维度信息 | open | 保留现状，在 QUALITY_LEDGER 顶部加指针 | ✅ |
| DEBT-012 | Instruction | `harness/doc/00-HARNESS-README.md` 与 `harness/README.md` 入口并存，main `README.md` 与 `harness/README.md` 三方并存 | 历史聚合 doc 与平铺 doc 兼容 | open | 保留现状，在 harness/README.md 顶部显式声明主源 | ✅ |
| DEBT-013 | Environment | 12 个 ad-hoc log 文件未被 git 跟踪但也未 .gitignore 排除 | 历史构建调试遗留 | open | 登记到 DEBT 表，下次 P3 任务清理；不删除不修改 | 仅登记 |
| DEBT-014 | Reports | `reports/` 已 72 份，未触发归档阈值 | 累计健康 | open | 暂不归档；超过 100 时启动 | 仅登记 |
| DEBT-015 | doc | `harness/doc/01-instructions/05-领域边界总表.md` 等聚合 doc 与 `harness/instructions/*.md` 内容有重叠 | 旧 doc 聚合遗留 | open | 保留现状；doc 顶部加"主源在 harness/instructions" | ✅ |
| DEBT-016 | Plan | `harness/plans/DDD_DOMAIN_TASK_MATRIX.md` 未按最近治理更新（2026-06-03 后） | 任务矩阵未跟随 | open | 仅登记；后续 DDD 任务再更新 | 仅登记 |
| DEBT-017 | Feedback | `harness/feedback/garbage-collection-policy.md` "删除前检查"未列出本任务产生的 inventory 报告为受保护路径 | 政策不针对 reports | open | 顶部加"reports/ 报告受保护"说明 | ✅ |
| DEBT-018 | Quality | `harness/QUALITY_LEDGER.md` 的"Harness"模块停留在 B | 治理任务未更新自身质量分 | open | 本任务完成后将 Harness 升级至 A- | ✅ |
| DEBT-019 | Environment | `harness/environment/CHEATSHEET.md` 缺失 | 历史未建速查表 | open | 本任务新增 | ✅ |

### P3 — 增强级

| ID | 领域 | 现象 | 根因 | 当前状态 | 建议处理 | 本任务 |
| --- | --- | --- | --- | --- | --- | --- |
| DEBT-020 | Tool | `agent-do.ps1` 未提供"仅生成 harness 报告"模式（不执行 build / restart） | 当前 docs Scope 已经接近但未独立成"harness-only" | open | 后续任务：新增 `-Scope harness` | 仅登记 |
| DEBT-021 | Feedback | 缺乏自动化"未变更文件检测" | 手工判断 | open | 后续任务：新增 `verify-harness-no-op.ps1` | 仅登记 |
| DEBT-022 | Tool | `safety-check.ps1` 仅支持 `backend/frontend/full/docs` 4 个 scope；多份 retro 指出 `-Scope code` 无效 | 历史接口未扩展 | open | 后续任务：扩展 scope | 仅登记 |
| DEBT-023 | State | 没有"Harness 内部债务"专用文件，散落在 KNOWN_ISSUES / QUALITY_LEDGER | 任务分类未独立 | open | 本任务新增 `harness/state/HARNESS_DEBT.md` 集中登记 | ✅ |
| DEBT-024 | Environment | `.env.real-pre.example` 与 `.env.real-pre` 在多份 report 中被混引 | 命名不统一 | open | 后续任务：标准化引用 | 仅登记 |
| DEBT-025 | Plans | `harness/plans/` 没有"harness 自身的迭代路线图" | 任务分类未独立 | open | 本任务新增 `harness/plans/HARNESS_ITERATION_ROADMAP.md` | ✅ |

## 3. 债务总数与本任务处理范围

| 等级 | 数量 | 本任务处理 | 后续处理 |
| --- | --- | --- | --- |
| P0 | 0 | 0 | 0 |
| P1 | 9 | 9 | 0 |
| P2 | 10 | 6（含登记） | 4 |
| P3 | 6 | 2 | 4 |
| **合计** | **25** | **17** | **8** |

> 本任务允许范围内：P1 全部处理 + P2 重点处理 + P3 仅登记 2 个新文件入口；不修改业务代码。

## 4. 后续任务拆分建议（最多 3 个）

| 任务 ID | 标题 | scope | 风险 | 验证方式 |
| --- | --- | --- | --- | --- |
| **HARNESS-DEBT-GC-001** | 清理 12 个 ad-hoc log（DEBT-013/14/15/16/17/18/19/20/21/22） + 把 `*.log` / `.cursor/` / `.code-review-graph/hook-update.log` 写入 `.gitignore` | docs-only | 低：仅删除已 untracked 文件，添加 gitignore 行 | safety-check docs dry-run + git status clean |
| **HARNESS-AGENT-DO-HARDEN** | 扩展 `agent-do.ps1` 支持 `-Scope harness` 模式 + `safety-check.ps1` 扩展 scope 列表（DEBT-020/22） | docs + scripts | 中：脚本修改需 dry-run 验证 | safety-check docs/backend dry-run + 现有 docs 任务跑通 |
| **HARNESS-ENV-CHEATSHEET-V2** | 在环境速查表里把远端 SSH alias / 远端目录 / 健康检查 URL 收纳为单一表（DEBT-008/19/24） | docs | 低 | safety-check docs dry-run + 内容交叉对账 |

## 5. 不在本任务处理范围的债务（明确登记，避免后续误以为遗漏）

- DEBT-016、DEBT-018、DEBT-020、DEBT-021、DEBT-022、DEBT-024：均明确登记为后续任务，**不**在本任务静默修改。
- DEBT-013（log 清理）：仅登记为债务，**不**在本任务删除任何 log 文件（避免误删用户调试上下文）。
- DEBT-014（reports 归档）：72 份未触发阈值，**不**启动归档。

## 6. 处理顺序

1. 第三阶段：建立 `harness/state/HARNESS_DEBT.md`（DEBT-023）。
2. 第四阶段：建立 `harness/runbooks/task-lifecycle.md`（DEBT-001）。
3. 第四阶段：建立 `harness/runbooks/scope-command-matrix.md`（DEBT-003）。
4. 第四阶段：建立 `harness/runbooks/debt-governance.md`（强化防回流）。
5. 第四阶段：建立 `harness/environment/CHEATSHEET.md`（DEBT-008/19）。
6. 第五阶段：建立 `harness/feedback/docs-only-evidence-template.md`（DEBT-007）。
7. 第五阶段：在已知重叠文件顶部加分工指针（DEBT-002/04/05/09/10/11/12/15/17）。
8. 第五阶段：建立 `harness/plans/HARNESS_ITERATION_ROADMAP.md`（DEBT-025）。
9. 第六阶段：同步输出 4 份治理报告（inventory / plan / evidence / retro）。
10. 第七阶段：safety-check + verify-local dry-run。
11. 第八阶段：docs-only 提交 + push 双 remote。

## 7. 处理原则（与 FORBIDDEN_SCOPE.md 保持一致）

- 禁止修改业务代码。
- 禁止 `git add .` / `git add -A` / `git add <dir>/`。
- 禁止 `git commit --amend`。
- 禁止 `git push --force`。
- 禁止删除未登记债务文件。
- 禁止把未验证项写成 PASS。
- 禁止把 `BLOCKED` / `PENDING` / `PARTIAL` 写成 `DONE`。
