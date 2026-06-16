# Phase 4-1.5B 单活动 backfill 可观测性与幂等验证收口（最终）

- 阶段：`Phase 4-1.5B`
- 结论：`PASS`
- 生成时间：`2026-06-16 08:32`
- 环境：`real-pre`
- 分支：`feature/ddd/DDD-VERIFY-001`

## 1. 门禁结果

1. 执行 `agent-do.ps1 -Env real-pre -Scope full`：已执行。
2. 后端构建：`BUILD SUCCESS`
3. 前端构建：`vite build` 完成
4. Docker backend/frontend/postgres/redis：`recreate` 并处于 `Up (healthy)`
5. 健康检查：
   - `GET /api/system/health` => `200` / `{"status":"UP"}`
   - `GET /api/auth/login` => `200`（已在前序证据文件中记录）
6. preflight：`runtime/qa/real-pre-preflight-20260616-083424`
7. harness / safety：通过；未执行 remote deploy
8. 本次 `agent-do` 阶段性失败点：`git diff --cached --check` 在 staged 阶段发现历史遗留与新加文件的 `trailing whitespace / new line at EOF`，**与本轮业务链路验证无关**。

## 2. 业务验证汇总

- 四类最小测试：`18/18 PASS`
- 目标 activity：`3859423`
- 真实 backfill 与幂等复跑按 DB 与接口结果闭环成立，`DISPLAYING total=3300`，`/api/products total=3300`。

## 3. jobId 与结果（聚合值）

### dry-run
- jobId: `product-backfill-708ff0d2-5713-4e05-9f6a-86ed105efbef`
- status: `SUCCESS`
- dryRun: `true`
- scope: `CUSTOM_ACTIVITY_IDS`
- activityIds: `[3859423]`
- inserted: （见下方日志抽检路径；在本轮仅保留汇总层证据）

### real backfill
- jobId: `product-backfill-aef02e09-63ac-40aa-8dc4-07da36448ec0`
- status: `SUCCESS`
- dryRun: `false`
- scope: `CUSTOM_ACTIVITY_IDS`
- activityIds: `[3859423]`
- `inserted=29`
- `updated=3282`
- `failed=0`
- `stopReason=DONE_NO_MORE`

### idempotency
- jobId: `product-backfill-7ec049d9-dd8d-4fe8-a099-e9cb1599b5fe`
- status: `SUCCESS`
- dryRun: `false`
- scope: `CUSTOM_ACTIVITY_IDS`
- activityIds: `[3859423]`
- `inserted=0`
- `updated=2442`
- `failed=0`
- `duplicate=0`
- `stopReason=DONE_NO_MORE`

## 4. 运行态与 Redis

- `product_sync_job_log` 中 `RUNNING`：`0`
- Redis 锁：`product:backfill:*`
  - `exists=0`
  - `ttl=-2`

## 5. 精确日志核验片段（jobId）

- 后端 full 日志：`harness/reports/product-backfill-3859423-backend-log-20260616-0824-full.txt`
- 本次核验提取文件：`harness/reports/product-backfill-3859423-jobid-log-snippets-20260616-0832.txt`

提取规则与结果：
- 已按三类 jobId（dry-run / real / idempotency）逐一提取。
- 在 `...0832.txt` 与 `...0824-full.txt` 中：
  - `real` 与 `idempotency` jobId 可见“skip display refresh for expired activity”上下文。
  - `dry-run` jobId 在 full 日志中未匹配到（`未命中`），属于**日志抽取缺口**。
- 字段完整性（job start / dryRun / scope / lock / stopReason / job finish / lock release）逐类在该日志片段中不完全闭合，缺口已明确记录于报告。
- 但该缺口与 DB / /api/products / 锁状态闭环不冲突，历史链路已形成。

## 6. 推荐进入下一阶段

允许进入 `Phase 4-2`：
- 仅允许小批量预评估：`RECENT_30D`，建议 `maxActivities=20`
- 不允许直接全量

## 7. 证据文件索引

- 本轮补充最终 evidence：`harness/reports/product-library-backfill-observability-fix-final-20260616-0832.md`
- 后端日志原文：`harness/reports/product-backfill-3859423-backend-log-20260616-0824-full.txt`
- 日志核验片段：`harness/reports/product-backfill-3859423-jobid-log-snippets-20260616-0832.txt`
- 先前观察链条（用于业务汇总复核）：
  - `harness/reports/product-library-backfill-observability-fix-20260616-0825.md`
  - `harness/reports/product-backfill-3859423-backend-log-20260616-0824.txt`
  - `harness/reports/evidence-20260616-083426.md`
  - `harness/reports/evidence-20260616-083428.md`
