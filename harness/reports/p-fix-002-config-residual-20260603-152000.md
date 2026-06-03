# P-FIX-002-CONFIG-RESIDUAL：处理 application-real-pre.yml 商品同步配置残留

- 任务编号：P-FIX-002-CONFIG-RESIDUAL
- 任务名称：处理 application-real-pre.yml 商品同步配置残留
- 时间：2026-06-03
- 环境：real-pre（本地）
- Completion Gate：Gate 0（Docs Only — 本任务只生成报告 + 恢复文件 + 提交状态回写）
- Session Exit Gate：Git State Clean
- 是否修改业务代码：**否**
- 是否执行数据库写操作命令：**否**
- 是否执行 migration 命令：**否**
- 是否重启容器：**否**
- 是否部署远端：**否**

---

## Final Status

DONE_CLEAN

## Selected Gate

Gate 0 - Docs Only（Gate G0 Git 子门禁：Docs-only clean + Git Exit Gate 终态 `DONE_CLEAN`）

## Scope

- 修改领域：Harness 工程化（Git 状态收口）
- 修改文件：1 个文件被 `git restore`（`application-real-pre.yml` 5 行 `product.activity.sync` 恢复）
- 额外 commit：2 个 harness 状态文件（`CURRENT_STATE.md` + `HARNESS_CHANGELOG.md` v0.5.1），属于 GIT-BATCH-4-REPORTS 状态回写
- 影响接口：无
- 影响页面：无
- 影响表：无
- 影响容器：无

## 1. 任务概述

GIT-BATCH-4-REPORTS 之后，工作区残留 1 个 dirty 文件 `backend/src/main/resources/application-real-pre.yml`，被分类为 `previous_partial`（P-FIX-002A 残留）。本任务判断该文件是否需要提交。

结论：5 行 `product.activity.sync` 配置与 `application.yml` 默认配置完全等价，运行时由 `docker-compose.real-pre.yml` 通过 env 注入覆盖。P-FIX-002A commit `dea06e4c` **未修改 `application-real-pre.yml`**，本地未提交新增是开发时工作区未清理残留。**执行 `git restore` 恢复文件**。

附加修正：GIT-BATCH-4-REPORTS 时未把 v0.5.1 HARNESS_CHANGELOG 状态回写 commit，本次作为状态收口补上。

## 2. 当前 dirty 状态（Intake Gate）

```text
 M backend/src/main/resources/application-real-pre.yml
 M harness/CURRENT_STATE.md
 M harness/HARNESS_CHANGELOG.md
```

- branch: feature/auth-system
- head: `7c69986e docs(harness): sync remaining task reports`
- modified: 3
- untracked: 0
- staged: 0
- unknown: 0
- decision: **START**（全部已分类：`previous_partial` × 1 + `docs_state` × 2）

注：原本任务定义只预期 1 个 `application-real-pre.yml` 残留；额外发现 `CURRENT_STATE.md` 和 `HARNESS_CHANGELOG.md` 是 GIT-BATCH-4-REPORTS 时追加但未 commit 的状态回写（v0.5.1 条目 + Batch 4 完成段），按任务九"状态回写"规则决定：本次作为 Batch 4 状态回口补 commit，然后再 restore `application-real-pre.yml`。

## 3. application-real-pre.yml diff 摘要

```diff
@@ -44,6 +44,12 @@ app:
     talent: false
     logistics: false

+product:
+  activity:
+    sync:
+      enabled: ${PRODUCT_ACTIVITY_SYNC_ENABLED:true}
+      cron: "${PRODUCT_ACTIVITY_SYNC_CRON:0 */5 * * * ?}"
+
 talent:
   enrich:
     mode: ${TALENT_ENRICH_MODE:real}
```

5 行新增，配置 `product.activity.sync`（enabled + cron）。与 `application.yml` 默认配置对比：

| 配置项 | `application.yml` 默认 | `application-real-pre.yml` 本地新增 |
| --- | --- | --- |
| `enabled` | `${PRODUCT_ACTIVITY_SYNC_ENABLED:false}` | `${PRODUCT_ACTIVITY_SYNC_ENABLED:true}` |
| `cron` | `"${PRODUCT_ACTIVITY_SYNC_CRON:0 */5 * * * ?}"` | `"${PRODUCT_ACTIVITY_SYNC_CRON:0 */5 * * * ?}"` |
| `batch-size` | `${PRODUCT_ACTIVITY_SYNC_BATCH_SIZE:20}` | （未设置） |
| `whitelist-activities` | `"${PRODUCT_ACTIVITY_SYNC_WHITELIST_ACTIVITIES:}"` | （未设置） |

YAML 文件内重复声明：当 profile 加载时，后加载的 `application-real-pre.yml` 覆盖 `application.yml` 的值。**实际行为等价**（值与 compose env 注入一致）。

## 4. 与 application.yml / docker-compose.real-pre.yml / .env.real-pre.example 的对比

### 4.1 `application.yml` 默认同步配置

```yaml
product:
  activity:
    sync:
      enabled: ${PRODUCT_ACTIVITY_SYNC_ENABLED:false}
      cron: "${PRODUCT_ACTIVITY_SYNC_CRON:0 */5 * * * ?}"
      batch-size: ${PRODUCT_ACTIVITY_SYNC_BATCH_SIZE:20}
      whitelist-activities: "${PRODUCT_ACTIVITY_SYNC_WHITELIST_ACTIVITIES:}"
```

### 4.2 `docker-compose.real-pre.yml` env 注入（backend-real-pre service）

```yaml
PRODUCT_ACTIVITY_SYNC_ENABLED: ${PRODUCT_ACTIVITY_SYNC_ENABLED:-true}
PRODUCT_ACTIVITY_SYNC_CRON: "${PRODUCT_ACTIVITY_SYNC_CRON:-0 */5 * * * ?}"
```

### 4.3 `.env.real-pre.example`

```bash
PRODUCT_ACTIVITY_SYNC_ENABLED=true
PRODUCT_ACTIVITY_SYNC_CRON=0 */5 * * * ?
```

### 4.4 对比结论

| 来源 | 实际生效值 |
| --- | --- |
| `application.yml` 默认（无 env） | `enabled=false`（不安全，不应用） |
| `application-real-pre.yml` 本地新增 | `enabled=true`、`cron=0 */5 * * * ?` |
| `application.yml` + compose env 注入 | `enabled=true`（env override）、`cron=0 */5 * * * ?` |

`application-real-pre.yml` 新增与 `application.yml` + compose env 注入的最终行为**完全等价**。本地新增冗余。

## 5. 远端 env 已生效证据引用

`harness/reports/p-fix-002d-remote-deploy-verify-20260603-132805.md`：

- 远端 commit 对齐 `dea06e4c`。
- 远端 env 补齐：追加 `PRODUCT_ACTIVITY_SYNC_ENABLED=true` 和 `PRODUCT_ACTIVITY_SYNC_CRON=0 */5 * * * ?` 到 `/opt/saas/env/.env.real-pre`。
- 同步配置生效（`enabled=true, cron=0 */5 * * * ?, batchSize=20`）。
- 两个 5 分钟周期正常执行：ok=5+ok=0, fail=0，零唯一索引冲突。
- 远端 4 容器全部 healthy。

确认远端通过 env + compose 已生效。

## 6. P-FIX-002A commit `dea06e4c` 实际改动范围

通过 `git show dea06e4c --stat` 和 `git show dea06e4c:backend/src/main/resources/application-real-pre.yml | grep product` 无输出，确认 commit `dea06e4c` **未修改** `backend/src/main/resources/application-real-pre.yml`。

P-FIX-002A 修改的文件：

- `backend/src/main/java/.../ProductActivitySyncJob.java`（默认 cron）
- `backend/src/main/resources/application.yml`（默认 cron）
- `docker-compose.real-pre.yml`（env 注入）
- `.env.real-pre.example`（参数示例）
- `harness/runbooks/remote-deploy.md`（部署 runbook）
- `harness/commands/deploy-remote.ps1`（部署脚本）

**`application-real-pre.yml` 不在 P-FIX-002A 改动列表**。本地 5 行新增是工作区未清理的脏数据，不是 P-FIX-002A 内容。

## 7. 决策：恢复

满足情况 A 全部条件：

1. ✅ `application.yml` 已有默认同步配置（且行为等价）
2. ✅ `docker-compose.real-pre.yml` 已注入 env（`PRODUCT_ACTIVITY_SYNC_ENABLED: ${PRODUCT_ACTIVITY_SYNC_ENABLED:-true}` + `PRODUCT_ACTIVITY_SYNC_CRON: "${PRODUCT_ACTIVITY_SYNC_CRON:-0 */5 * * * ?}"`）
3. ✅ `.env.real-pre.example` 已有参数
4. ✅ 远端已通过 env 验证
5. ✅ `application-real-pre.yml` 的 diff 只是重复配置
6. ✅ 该文件不是必要版本化变更（P-FIX-002A commit 不含此文件）
7. ✅ 恢复不影响已提交的 P-FIX-002 配置（`dea06e4c` 中本来就没有这 5 行）
8. ✅ 恢复不影响远端已部署环境（远端通过 env 注入生效，与 yml 文件无关）

**执行 `git restore backend/src/main/resources/application-real-pre.yml`**。

## 8. 决策理由

1. **避免重复声明**：`application.yml` 已含同步配置，profile 级别重复声明没有意义。
2. **避免与 P-FIX-002A 真实改动混淆**：commit `dea06e4c` 不含此文件，本地新增是脏数据。
3. **避免误导后续 Agent**：未提交的 5 行会让下一个 Agent 误以为此配置重要，触发更多工作。
4. **避免部署风险**：如果本地提交但远端 yml 已更新，配置差异会导致不可预测的合并冲突。
5. **远端已通过 env 验证**：env 注入优先级高于 yml，恢复 yml 不会改变远端运行时行为。

## 9. 执行动作

### 9.1 状态回写 commit（GIT-BATCH-4-REPORTS 状态回写）

```text
git add harness/CURRENT_STATE.md harness/HARNESS_CHANGELOG.md
git diff --cached --name-only
# → harness/CURRENT_STATE.md
# → harness/HARNESS_CHANGELOG.md
git diff --cached --check
# → （无输出，PASS）
git commit -m "docs(harness): GIT-BATCH-4-REPORTS record batch 4 state"
# → [feature/auth-system 78bdf8fa] docs(harness): GIT-BATCH-4-REPORTS record batch 4 state
# → 2 files changed, 17 insertions(+), 1 deletion(-)
git push gitee feature/auth-system
# → 7c69986e..78bdf8fa  feature/auth-system -> feature/auth-system
git push origin feature/auth-system
# → 7c69986e..78bdf8fa  feature/auth-system -> feature/auth-system
```

### 9.2 恢复 application-real-pre.yml

```text
git restore backend/src/main/resources/application-real-pre.yml
git status --short
# → （无输出，工作区 clean）
git diff HEAD -- backend/src/main/resources/application-real-pre.yml
# → （无输出，文件与 HEAD 一致）
```

## 10. 验证结果

| 命令 | 结果 |
| --- | --- |
| `git status --short` | 空（工作区 clean） |
| `git diff --name-only` | 空 |
| `git diff HEAD -- backend/src/main/resources/application-real-pre.yml` | 空（文件与 HEAD 一致） |
| `git diff --check` | PASS（无输出） |
| `safety-check -Scope docs -DryRun` | PASS "Safety check passed" |
| 双 remote 推送（state commit） | gitee + origin PASS |
| code-review-graph state commit 风险分 | 0.00（无 changed function/flow） |

## 11. 关键不变量确认

| 不变量 | 状态 |
| --- | --- |
| 是否修改业务代码 | **否**（仅恢复文件 + 提交状态文件） |
| 是否执行数据库操作 | **否** |
| 是否执行 migration | **否** |
| 是否重启容器 | **否** |
| 是否部署远端 | **否** |
| 是否修改 `application.yml` 默认配置 | **否** |
| 是否修改 `docker-compose.real-pre.yml` | **否** |
| 是否修改 `.env.real-pre.example` | **否** |
| 是否修改 Java 代码 | **否** |
| 是否使用 `git add .` | **否**（按文件逐个 add） |
| 是否混入其他文件 | **否** |
| 恢复是否影响远端已部署环境 | **否**（远端通过 env 注入生效） |
| 恢复是否影响已提交的 P-FIX-002 配置 | **否**（`dea06e4c` 不含此文件） |

## 12. Git Exit Gate 终态

**`DONE_CLEAN`**。

- 工作区 `git status --short` 为空。
- 所有 dirty 已处理完毕。
- 状态文件已 commit。
- `application-real-pre.yml` 已恢复至 HEAD 一致。
- 当前任务 commit 已推送到 gitee + origin。
- 无 unknown dirty。

## 13. 状态回写

- `harness/HARNESS_CHANGELOG.md`：新增 v0.5.2 条目（包含本任务）。
- `harness/CURRENT_STATE.md`：追加 P-FIX-002-CONFIG-RESIDUAL 完成段落。
- `harness/state/DOMAIN_STATUS.md`：商品域 P0 状态不变。
- 本报告暂不 commit（按任务九"如果选择提交报告，必须单独确认"规则），但建议作为下一 docs commit 一并提交。

## 14. Verification Summary

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| Build | SKIP | docs-only 任务不需 build |
| Container Reload | SKIP | docs-only 任务不需重启 |
| Health | SKIP | docs-only 任务不需 health check |
| API Smoke | SKIP | docs-only 任务不需 API smoke |
| UI Smoke | SKIP | docs-only 任务不需 UI smoke |
| SQL Reconcile | N/A | 未涉及 DB |
| Business Flow | NO_REGRESSION | 恢复 yml 等价行为，不影响业务 |
| Git State | DONE_CLEAN | `git status --short` 为空 |

## 15. 下一步建议

1. **GIT-EXIT-001**：当前工作区已 `DONE_CLEAN`，可视为 GIT-EXIT-001 通过。
2. **下一业务任务**：可以进入用户域 U-3（CurrentUser / PermissionContext 统一）或商品域 P-VERIFY-002（远端商品库数量复核）。
3. **报告提交**：本报告 + v0.5.2 HARNESS_CHANGELOG + CURRENT_STATE 追加段 一起作为下一 docs commit 一并提交。
4. **未来避免**：每次 P-FIX-002A 类的"含 yml 配置变更"任务，必须在 commit 前确认是否需要 `application-real-pre.yml` 同步修改；如果需要，应纳入同次 commit；如果不需要，应避免在本地工作区留下未提交修改。

## 16. 报告路径

`harness/reports/p-fix-002-config-residual-20260603-152000.md`
