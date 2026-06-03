# HARNESS-DEBT-GC-001 — Cleanup Inventory (Pre-Cleanup)

> **任务**: HARNESS-DEBT-GC-001 — harness 安全清理、归档、瘦身
> **时间**: 2026-06-04 00:10:52 +08:00
> **分支**: feature/auth-system
> **HEAD**: c76986f5 docs(harness): HARNESS-DEBT-GOVERNANCE-ITERATION governance pass
> **Scope**: docs-only / harness-cleanup（DEBT-013 ad-hoc log + DEBT-014 reports 归档部分推进）
> **状态**: Phase 1 盘点完成

---

## 1. 基本信息（Git Intake）

| 字段 | 值 |
| --- | --- |
| `git status --short` 数量 | **0**（工作区 clean） |
| `git branch --show-current` | `feature/auth-system` |
| `git log -1 --oneline` | `c76986f5 docs(harness): HARNESS-DEBT-GOVERNANCE-ITERATION governance pass` |
| 工作区是否干净 | ✅ Clean |
| 是否需要 stash | 否 |
| 是否需要 clean -fd | 否 |

**Intake 结论**：工作区完全 clean，无需 stash/clean。Phase 4 之前产生的新 dirty 也全部会随本任务统一提交。

---

## 2. 清理前文件统计

| 路径 | 数量 | 总大小 | 备注 |
| --- | --- | --- | --- |
| `harness/reports/*.md` | **76** | 861 KB | 包含 evidence/retro/content-retire/domain/git-* 等 |
| `harness/archive/retired-content/` 目录数 | 2 (`20260602-153913` + `20260603-reports-archive`) | — | 已存在 |
| `harness/archive/retired-content/20260603-reports-archive/` 文件数 | 52 | — | 上游会话归档批次，未修改 |
| 根目录 `nul` | 1 (0 bytes) | — | Windows 设备文件残留；已 `.gitignore` 覆盖；不污染 git |
| 根目录 `*.log` | 0 | — | 干净 |
| 根目录 `snap*.txt` / `args.json` / `env.json` / `health.json` | 0 | — | 干净 |
| `out/` | 不存在 | — | 干净 |
| `tmp/` | 不存在 | — | 干净 |
| `test-results/playwright/` | 1 目录 | — | playwright 生成物；已 `.gitignore` |
| `playwright-report/` | 1 文件 + data 目录 (528 KB `index.html`) | 528 KB | playwright 生成物；已 `.gitignore` |
| `.codex-run/` | 不存在 | — | 干净 |
| `runtime/tmp/` | 不存在 | — | 干净 |
| `.nyc_output/` / `coverage/` | 不存在 | — | 干净 |
| `backend/target/` (含 80 MB `colonel-saas.jar`) | 已 `.gitignore` | — | 干净（构建产物） |
| `frontend/dist/` / `frontend/node_modules/` | 已 `.gitignore` | — | 干净 |

**gitignore 已覆盖**：`test-results/`、`playwright-report/`、`nul`、`backend/target/`、`frontend/dist/`、`frontend/node_modules/` — 全部正确。

---

## 3. 候选清理对象（分类前）

### 3.1 明确生成型临时物（已 .gitignore，但仍占据工作区空间）

| 路径 | 类型 | 建议 | 备注 |
| --- | --- | --- | --- |
| `nul` | Windows 设备文件残留（0 bytes） | **delete** | 已知 Windows CMD 错误重定向产物；可手动删 |
| `test-results/playwright/` | playwright 测试结果 | **delete** | 可再生；已 gitignore；不污染 git |
| `playwright-report/` | playwright 报告 (528 KB) | **delete** | 可再生；已 gitignore；不污染 git |

### 3.2 重复 / 早期版本报告（需逐对判断是否 keep）

| 旧路径 | 新路径 | 状态 |
| --- | --- | --- |
| `harness/reports/user-domain-u1-inventory-20260603-120000.md` (696 行) | `harness/reports/user-domain-u1-inventory-20260603-090000.md` (743 行) | 两个并存（U-1 任务被反复盘点两轮，第二轮更新到 120000） |
| `harness/reports/user-domain-u2-model-schema-alignment-20260603-093000.md` (668 行) | `harness/reports/user-domain-u2-model-schema-alignment-20260603-150000.md` (510 行) | 两轮盘点，第二轮更新到 150000 |
| `harness/reports/p-fix-001c-product-library-pagination-20260603-112740.md` (193 行) | `harness/reports/p-fix-001c-product-library-pagination-20260603-113616.md` (106 行) | 早期 vs 最终报告，最终版 113616 是主源 |

**关键发现**：所有 3 对"重复"报告都被其他主源/evidence 显式引用 — 必须 keep（不能归档/删除）。详细交叉引用见第 4 节。

### 3.3 content-retire 一次性 plan 报告

7 份 content-retire 报告（全部为 `Action: Plan`，无 archive/delete 操作）：

| 文件 | Action | Reason |
| --- | --- | --- |
| `content-retire-20260603-093347.md` | Plan | docs: user-domain U-2 model schema alignment |
| `content-retire-20260603-095000.md` | Plan | docs: user domain U-2.5-A dept_type unification plan |
| `content-retire-20260603-103207.md` | Delete | proactive cleanup 2026-06-03 |
| `content-retire-20260603-111343.md` | Plan | FUNC-001 product card hover UI |
| `content-retire-20260603-113617.md` | Plan | post-task content maintenance |
| `content-retire-20260603-213739.md` | Plan | fix: sample apply from product library |
| `content-retire-20260603-214340.md` | Plan | fix: quick sample manual talent fallback |

**关键发现**：所有 7 份 content-retire 都至少被 1 个其他 evidence/retro/git-batch 报告交叉引用 — 必须 keep。

### 3.4 其他分类内被引用报告（不可归档）

- 所有 `evidence-*.md`（除归档批次）— 最近 5 天的 evidence，含关键审计与运行态证据。
- 所有 `retro-*.md` — 任务后复盘，DEBT 治理和 Session Exit 记录。
- 所有 `git-*.md` — 批次提交 / dirty 分类 / 工作区治理报告。
- 所有 `p-fix-*.md` / `p0-*.md` / `p-diag-*.md` / `func-*.md` / `order-*.md` / `user-domain-*.md` / `talent-*.md` / `test-1-*.md` / `completion-gates-*.md` / `session-exit-*.md` / `sample-apply-*.md` / `sync-plan-*.md` — 任务主报告 + evidence + retro，被 `CURRENT_STATE.md` / `HARNESS_CHANGELOG.md` / `KNOWN_ISSUES.md` 显式引用。

---

## 4. 风险判断

### 4.1 为什么"重复"报告必须 keep

- **`u1-inventory 120000`**：被 `harness/state/DOMAIN_STATUS.md`、`harness/reports/user-domain-u2-model-schema-alignment-20260603-150000.md`、`harness/reports/user-domain-u2_5-*.md` 等 4 处引用。
- **`u2-model-schema-alignment 093000`**：被 `HARNESS_CHANGELOG.md` 和 2 个 evidence 引用。
- **`p-fix-001c 112740`**：被 evidence / batch / git-harness-001 等 8 处引用（早期草稿）。

> 即使是"被替代"的旧报告，**被其他报告交叉引用**意味着删除会破坏证据链。归档操作会修改路径导致引用 404。

### 4.2 唯一可安全清理的对象

- **`nul`**（0 bytes Windows 设备文件）：可手动删除，不影响任何代码、文档、报告。
- **`test-results/playwright/`** 和 **`playwright-report/`**：playwright 重新执行会再生；已 gitignore；删除不影响任何业务或证据链。
- **未提交的脏文件**：0（工作区 clean）— 不需要处理。

### 4.3 不能做的清理

- 任何 `.md` 报告：被 evidence 链引用；归档会破坏引用。
- 任何 `harness/state/*`、`harness/runbooks/*`、`harness/commands/*`、`harness/feedback/*`、`harness/skills/*`、`harness/instructions/*`、`harness/environment/*`、`harness/plans/*`、`harness/evals/*`、`harness/prompts/*`、`harness/tools/*`：Harness 主源，受保护。
- 任何 `docs/*`、`backend/*`、`frontend/*`、`docker-compose*`、`*.env*`、`backend/src/main/resources/db/*`：业务事实与受保护资产。
- `harness/archive/retired-content/20260603-reports-archive/`：已存在的归档批次，本任务不动。

---

## 5. 候选对象总表

| 类别 | 候选 | 决定 |
| --- | --- | --- |
| **keep** | 76 份 reports 全部 | 全部 keep — 几乎所有都被主源/证据链引用 |
| **archive** | 0 | 没有可以安全归档的 reports（即使"重复"也被交叉引用） |
| **delete** | `nul`, `test-results/playwright/`, `playwright-report/` | 3 项 — 纯生成物，删除不影响任何引用 |
| **forbidden** | `harness/state/*`, `harness/commands/*`, `harness/runbooks/*`, `harness/feedback/*`, `harness/skills/*`, `harness/instructions/*`, `harness/environment/*`, `harness/plans/*`, `harness/evals/*`, `harness/prompts/*`, `harness/tools/*`, `harness/CURRENT_STATE.md`, `harness/HARNESS_CHANGELOG.md`, `harness/QUALITY_LEDGER.md`, `harness/AGENT_CONTRACT.md`, `harness/SESSION_EXIT_GATE.md`, `harness/FORBIDDEN_SCOPE.md`, `harness/TASK_ROUTING.md`, `harness/DOMAIN_MAP.md`, `harness/COMPLETION_GATES.md`, `harness/README.md`, `harness/doc/*`, `docs/*`, `backend/*`, `frontend/*`, `docker-compose*`, `*.env*`, `backend/src/main/resources/db/*`, `harness/archive/retired-content/20260603-reports-archive/*` | 全部受保护 |
| **manual_review** | 无 | 0 |

---

## 6. DEBT 收敛预测

| DEBT | 标题 | 状态变更 | 预测 |
| --- | --- | --- | --- |
| DEBT-013 | 12 个 ad-hoc log 未 .gitignore 排除 | open → **fixed** | 实际盘点只有 `nul` 1 个 0-byte 设备文件 + `test-results/` + `playwright-report/`（已 gitignore）。本任务清理 + 验证 |
| DEBT-014 | reports/ 72 份未触发归档 | deferred → **partial** | 76 份报告中 0 份可安全归档（全部被证据链引用）。本任务确认 76/76 keep，归档必要性低；建议 **关闭 DEBT-014**（结论：reports 目录受 GC 政策保护，**没有可归档对象**，不需要轮转），或在 HARNESS_ITERATION_ROADMAP 中标记为 wontfix |

**新发现债务**：0
**新增 deferred**：0

---

## 7. 下一步

- Phase 2: 分类（已在第 5 节完成 keep/archive/delete 分类）
- Phase 3: 准备 manifest 走 `retire-content.ps1 -Action Delete -Manifest`
- Phase 4: 删除 `nul` + `test-results/` + `playwright-report/`
- Phase 5: 更新 harness 状态
- Phase 6: 生成 evidence/retro
- Phase 7: 验证
- Phase 8: docs-only commit + push gitee + push origin
- Phase 9: 最终 session 报告
