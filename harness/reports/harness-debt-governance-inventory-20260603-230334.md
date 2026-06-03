# Harness Debt Governance — Inventory Report

- **任务**：HARNESS-DEBT-GOVERNANCE-ITERATION
- **生成时间**：2026-06-03 23:03:34
- **当前分支**：feature/auth-system
- **当前 HEAD**：7573a625 docs(harness): GIT-BATCH-C TALENT-ADDRESS remote deploy verification
- **工作区状态**：clean（无 staged / modified / untracked）

---

## 1. 盘点口径

本次盘点以仓库根 `harness/` 子树为对象；不修改业务代码、不修改 Docker / Compose / env。
盘点命令：`find harness -type f`、`git status --short`、`git log -1`、`git remote -v`、
`find . -maxdepth N -type d` 配合已知 generated 目录白名单。

## 2. 仓库顶层结构（Harness Engineering 视角）

```text
SAAS/
├── AGENTS.md                       (强制执行协议入口)
├── CLAUDE.md                       (项目地图)
├── backend/                        (业务代码 — 本任务不修改)
├── frontend/                       (业务代码 — 本任务不修改)
├── docker-compose*.yml             (部署配置 — 本任务不修改)
├── runtime/                        (运行时产物 — 见 §6)
├── tests/                          (E2E 与 integration — 本任务不修改)
├── docs/                           (事实主源 — 本任务不修改)
└── harness/                        (本任务治理对象)
    ├── AGENT_CONTRACT.md
    ├── COMPLETION_GATES.md
    ├── CURRENT_STATE.md
    ├── DOMAIN_MAP.md
    ├── FORBIDDEN_SCOPE.md
    ├── HARNESS_CHANGELOG.md
    ├── QUALITY_LEDGER.md
    ├── README.md
    ├── SESSION_EXIT_GATE.md
    ├── TASK_ROUTING.md
    ├── archive/                     (54)
    ├── commands/                    (10 ps1)
    ├── doc/                         (24, 聚合文档入口)
    ├── environment/                 (6)
    ├── evals/                       (6)
    ├── feedback/                    (4)
    ├── instructions/                (12, 8 领域 + 4 通用)
    ├── plans/                       (2)
    ├── prompts/                     (5)
    ├── reports/                     (73, 含 .gitkeep)
    ├── runbooks/                    (13)
    ├── skills/                      (15)
    ├── state/                       (10)
    └── tools/                       (1 README, 5 索引文档)
```

## 3. Harness 文件统计

| 维度 | 数量 | 备注 |
| --- | --- | --- |
| Harness 文件总数 | 245 | 含子目录所有 `.md` / `.ps1` / `.json` / `.keep` |
| `.md` 文件 | 231 | 含状态、报告、模板、runbook、skill、plan、eval、instruction |
| PowerShell 脚本 | 10 | 固定命令入口（`commands/*.ps1`） |
| `reports/` 文件 | 72 | 加 1 个 `.gitkeep` = 73 |
| `archive/` 文件 | 54 | 全部位于 `archive/retired-content/` 之下 |
| `state/` 文件 | 10 | 状态子系统文件 |
| `skills/` 文件 | 15 | 能力包 |
| `instructions/` 文件 | 12 | 8 领域 + 4 通用 |
| `commands/` 文件 | 10 | 全部为 PowerShell |
| `runbooks/` 文件 | 13 | 13 个 runbook |
| `evals/` 文件 | 6 | v1-business-closure / p0-regression / rbac-scope / order-attribution / product-library / sample-auto-complete |
| `environment/` 文件 | 6 | test-env / real-pre-env / remote-real-pre-env / docker-compose-map / local-dev-env / README |
| `feedback/` 文件 | 4 | evidence / retro / feedback-loop / garbage-collection-policy |
| `doc/` 文件 | 24 | Harness 聚合文档入口（旧 doc/**，与新 harness 五子系统并存） |
| `plans/` 文件 | 2 | DDD 路线图与任务矩阵 |
| `prompts/` 文件 | 5 | domain-alignment / full-review / p0-fix / real-pre-debug / release-check |

## 4. Git 状态

```text
branch: feature/auth-system
HEAD:   7573a625 docs(harness): GIT-BATCH-C TALENT-ADDRESS remote deploy verification
status: clean
remotes:
  gitee  https://gitee.com/cao-jianing463/saas.git
  origin https://github.com/laoliu-463/saas.git
```

未发现未登记 dirty、未 staged 文件、未 push commit。本次盘点时为完全干净状态，
与上一次会话结束的 `DONE_CLEAN` 一致。

## 5. dirty 分类

| 分类 | 数量 | 说明 |
| --- | --- | --- |
| business_code | 0 | 无 |
| frontend_code | 0 | 无 |
| backend_code | 0 | 无 |
| sql_migration | 0 | 无 |
| docker_config | 0 | 无 |
| harness_docs | 0 | 无 |
| reports_only | 0 | 无 |
| generated_artifacts | 0 | 见 §6（已被 .gitignore 排除） |
| previous_partial | 0 | 无 |
| unknown | 0 | 无 |

工作区干净。RISK-008（P0-P1 register 中登记的 Batch B 8 文件 dirty）已在 commit `7573a625` 之后被消化（HEAD 已包含 TALENT-ADDRESS-SAMPLE-DEFAULT 的 writeBackClaimAddress / 测试 / 前端 modal 提交）。

## 6. 仓库内已知生成 / 临时产物

下列目录虽然存在但被 `.gitignore` 排除或与 harness 无关，**不在 dirty 范围**：

| 路径 | 状态 | 备注 |
| --- | --- | --- |
| `backend/target/` | gitignore | Maven 构建产物 |
| `frontend/dist/` | gitignore | Vite 构建产物 |
| `frontend/node_modules/` | gitignore | npm 依赖 |
| `.opencode/node_modules/` | gitignore | opencode 插件 |
| `runtime/qa/out/` | gitignore | QA 截图 / 报告 |
| `frontend/runtime/` | gitignore | 运行时缓存 |
| `frontend/test-results/` | gitignore | vitest 报告 |
| `playwright-report/` | gitignore | Playwright 报告 |
| `test-results/` | gitignore | Playwright 报告 |
| `tests/e2e/runtime/` | gitignore | E2E 缓存 |

发现 **未在 gitignore** 的临时文件需要登记（不属于 dirty 但是**潜在债务**）：

| 路径 | 类别 | 处理建议 |
| --- | --- | --- |
| `backend/mvn-package.log` | ad-hoc log | DEBT-013：下次 P3 任务清理 |
| `backend/mvn-productsuite.log` | ad-hoc log | DEBT-014：下次 P3 任务清理 |
| `backend/mvn-services.log` | ad-hoc log | DEBT-015：下次 P3 任务清理 |
| `backend/mvn-shopscore.log` | ad-hoc log | DEBT-016：下次 P3 任务清理 |
| `backend/debug-02e7cc.log` | debug log | DEBT-017：下次 P3 任务清理 |
| `.code-review-graph/hook-update.log` | 工具日志 | DEBT-018：纳入 .gitignore 候选 |
| `.cursor/debug-02e7cc.log` | IDE 调试日志 | DEBT-019：纳入 .gitignore 候选 |
| `frontend/build.log` | ad-hoc log | DEBT-020：下次 P3 任务清理 |
| `frontend/frontend-3002.err.log` | ad-hoc log | DEBT-021：下次 P3 任务清理 |
| `frontend/frontend-3002.out.log` | ad-hoc log | DEBT-022：下次 P3 任务清理 |

> 这些文件**当前未被 git 跟踪**，因此不构成 dirty；但作为"运行产物未清理"的债务登记。

## 7. Harness 五子系统覆盖情况

> 沿用 `harness/doc/00-HARNESS-README.md` 与 `harness/instructions/definition-of-done.md` 的五子系统分层：
> Instruction / Tool / State / Feedback / Environment。

| 子系统 | 现状 | 主要文件 | 缺口 |
| --- | --- | --- | --- |
| **Instruction** | 完整 | `AGENTS.md`、`harness/AGENT_CONTRACT.md`、`harness/TASK_ROUTING.md`、`harness/FORBIDDEN_SCOPE.md`、`harness/COMPLETION_GATES.md`、`harness/SESSION_EXIT_GATE.md`、`harness/instructions/*.md` | (1) `harness/instructions/safety-rules.md` 与 `harness/FORBIDDEN_SCOPE.md` 存在内容重叠；(2) 缺任务生命周期模板 runbook；(3) 缺债务治理 runbook |
| **Tool** | 完整 | `harness/commands/*.ps1`、`harness/tools/README.md`、`harness/doc/02-tools/*.md` | (1) 命令矩阵在 `TASK_ROUTING.md` 中已表格化，但缺独立的"scope → command"决策表 runbook；(2) `agent-do.ps1` 的 docs 路径在多份报告里被指为 missing 实际是子流程；建议新增 `harness/runbooks/scope-command-matrix.md` |
| **State** | 完整 | `harness/CURRENT_STATE.md`、`harness/HARNESS_CHANGELOG.md`、`harness/state/DOMAIN_STATUS.md`、`harness/state/KNOWN_ISSUES.md`、`harness/state/p0-p1-register.md`、`harness/state/DECISIONS.md`、`harness/state/DEPLOYMENT_STATE.md`、`harness/state/VALIDATION_STATE.md`、`harness/state/current-business-state.md`、`harness/state/known-risks.md`、`harness/state/real-pre-evidence-index.md`、`harness/state/TASK_HISTORY.md` | (1) 12 个状态文件存在命名 / 重叠 / 分工问题：`current-business-state.md` 与 `CURRENT_STATE.md` 有重叠；`p0-p1-register.md` 与 `KNOWN_ISSUES.md` 有重叠；`real-pre-evidence-index.md` 与 `docs/05-real-pre证据索引.md` 可能存在外链；`DEPLOYMENT_STATE.md` 内容未在本次盘点看到；(2) 缺"债务治理 state 更新模板" |
| **Feedback** | 完整 | `harness/feedback/evidence-report-template.md`、`harness/feedback/retro-summary-template.md`、`harness/feedback/feedback-loop.md`、`harness/feedback/garbage-collection-policy.md` | (1) 旧内容生命周期在多文件分散说明，需要一个 runbook 集中；(2) 缺 evidence / retro 模板的最小化版本（当前 11 节模板对纯 docs 任务过重） |
| **Environment** | 完整 | `harness/environment/README.md`、`test-env.md`、`real-pre-env.md`、`remote-real-pre-env.md`、`docker-compose-map.md`、`local-dev-env.md` | (1) 本地端口 / 健康检查 URL 分散在多份环境文档；(2) 缺一个"环境速查表" |

## 8. 重复 / 重叠文件识别

| 重叠对 | 现象 | 建议 |
| --- | --- | --- |
| `CURRENT_STATE.md` vs `state/current-business-state.md` | 两者均描述"当前项目状态" | 保持现状：前者做变更日志型，后者做业务快照型；在 `CURRENT_STATE.md` 顶部加 1 行指针 |
| `state/KNOWN_ISSUES.md` vs `state/p0-p1-register.md` | 已知问题与 P0/P1 寄存器 | 保持现状：前者做 issue 卡片，后者做 P0/P1 列表；在 `KNOWN_ISSUES.md` 加互引 |
| `state/known-risks.md` vs `state/KNOWN_ISSUES.md` vs `state/p0-p1-register.md` | 三处描述风险 | 确认 `known-risks.md` 是否仅做"风险分类"而 P0/P1 register 实际承担风险列表；建议在 `state/KNOWN_ISSUES.md` 顶部写明三文件分工 |
| `harness/doc/**` vs 根 `harness/**` | 聚合文档与平铺文档并存 | 保留：聚合 doc 仅作入口 / 阅读路径索引，主源仍在平铺目录；不强行合并 |
| `harness/instructions/safety-rules.md` vs `harness/FORBIDDEN_SCOPE.md` | 安全规则重复 | 列入 DEBT-002：待精简 |

## 9. reports / archive 体量

| 维度 | 数量 | 体量评估 |
| --- | --- | --- |
| `reports/` 报告 | 72 | 持续累计，**未触发归档阈值**（建议 > 100 时启动） |
| `archive/retired-content/` 归档 | 54 | 仅 2 个批次（`20260602-153913`、`20260603-reports-archive`）；归档节奏健康 |
| 最近一次归档 | 20260603 | 由 `content-retire-20260603-213739.md` 触发；归档策略有效 |

## 10. 五大子系统结论

| 子系统 | 评级 | 一句话 |
| --- | --- | --- |
| Instruction | A- | 完整，AGENT_CONTRACT / TASK_ROUTING / FORBIDDEN_SCOPE / COMPLETION_GATES / SESSION_EXIT_GATE 五件套已闭环；缺任务生命周期 runbook |
| Tool | A | 10 个 ps1 + `tools/README.md` 完整命令矩阵；缺 scope→command 决策表 |
| State | B+ | 10 个 state 文件有重叠 / 分工不清；需在 `KNOWN_ISSUES.md` / `CURRENT_STATE.md` 顶部明确分工指针 |
| Feedback | A- | 模板完整、GC 策略清晰；缺最小化模板（docs-only 场景） |
| Environment | A | 6 份环境说明完整；缺速查表 |

## 11. 验证与归档

- 本次盘点未修改任何文件。
- `git status --short` 输出为空，符合盘点口径。
- 本报告归档位置：`harness/reports/harness-debt-governance-inventory-20260603-230334.md`。

## 12. 剩余风险

- 12 个 ad-hoc log 文件**未被跟踪**但**未被 .gitignore 排除**；下次任务期间如 `git add` 时被波及将构成 dirty。建议作为 P3 任务清理。
- `state/DEPLOYMENT_STATE.md` 与 `state/VALIDATION_STATE.md` 内容本次未读取，建议下一任务补读以确认它们与 `CURRENT_STATE.md` 的分工。
- `harness/doc/**` 与根 `harness/**` 聚合目录并存，目前以"主源在根目录"为隐式约定；建议在 `harness/README.md` 写一行显式声明。
