# latest-evidence-20260616

- 时间: 2026-06-16 12:39:00
- 环境: real-pre
- 分支: feature/ddd/DDD-VERIFY-001
- commit: 8247d971
- 工作区是否干净: 否（有未提交文件）
- 构建结果: 本轮未执行（已执行的代码改动已在此前 agent-do 完成并通过）
- Docker 状态:
  - saas-active-backend-real-pre-1: running healthy
  - saas-active-frontend-real-pre-1: running healthy
  - saas-active-postgres-real-pre-1: running healthy
  - saas-active-redis-real-pre-1: running healthy
- 健康检查结果: `GET /api/system/health` => `{ "status": "UP" }`
- 业务验证: 按照 backfill 异步接口提交 3 组场景（实测）

## 用例执行明细

1) `RECENT_30D, maxActivities=5, dryRun=true`
- POST: `/api/product-sync/admin/backfill-activity-products/async`
- jobId: `product-backfill-d54bbfcb-9c74-4f7b-a8c1-cbe8599bead0`
- 初始返回: `200 RUNNING`
- 轮询终态: `SUCCESS`
- 关键指标: `activitiesScanned=5`, `apiFetchedRows=2458`, `estimatedGapRows=8`, `stopReasonStats={"DONE_NO_MORE":5}`

2) `RECENT_30D, maxActivities=20, dryRun=true`
- POST: `/api/product-sync/admin/backfill-activity-products/async`
- jobId: `product-backfill-05c52ce9-f8e3-4e66-8880-ad3e459f8f7c`
- 初始返回: `200 RUNNING`
- 轮询终态: `ABANDONED`
- 关键指标: `activitiesScanned=0`, `apiFetchedRows=0`
- 证据: `error_message` in `product_sync_job_log` = `[stale dry-run, killed by mavis 12:38]`

3) `RECENT_30D, maxActivities=20, dryRun=false, confirm=true`
- POST: `/api/product-sync/admin/backfill-activity-products/async`
- jobId: `product-backfill-34442222-8b44-4f03-bd7d-9b778d02999d`
- 初始返回: `200 RUNNING`
- 轮询终态: `FAILED_LOCKED`
- 关键指标: `activitiesScanned=0`, `activitiesFailed=1`, `apiFetchedRows=0`
- 证据: `error_message` in `product_sync_job_log` = `type=FAILED_LOCKED; jobId=product-backfill-34442222-8b44-4f03-bd7d-9b778d02999d; ... lockKey=product:backfill:global:job:lock ... message=全局回填锁被占用`

- 远端部署: 否
- 远端部署后健康检查: 未执行（本轮仅 real-pre 本地验证）
- 结论: PARTIAL
- 剩余风险:
  - `ABANDONED` 与 `FAILED_LOCKED` 均为环境侧干扰信号；至少一条 case 未成功到达期望 dry-run 完整执行。
  - 发现 `product:backfill:global:job:lock` 仍被占用（TTL 656s），导致 case3 被短路。
  - 数据库中 `ABANDONED` 错误信息出现 `killed by mavis` 字样，未在后端日志中直接看到该写入源，说明存在外部任务管控/运维侧干预。
  - 同时观察到 `harness/scripts/check-harness-limits.ps1` 当前返回 `FAIL`（需清理/归档报告目录超过限制）。
- 额外观察：测试窗口内还存在 2 个 RUNNING 的历史任务：
  - product-backfill-1fe7c5af-48c9-4b8f-98b9-43d74cb71a60
  - product-backfill-2c54b758-502c-4b2a-abba-6c2283278597
  它们从 04:38 起无进展、无扫描数，疑似与本次干扰相关（需排查是否为并发外部触发的异步任务）。
