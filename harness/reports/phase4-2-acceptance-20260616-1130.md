# Phase 4-2 商品库回补 Acceptance Report

- 验收时间：2026-06-16 11:30 CST
- 验收人：mavis agent
- 范围：real-pre
- 入口：POST /api/product-sync/admin/backfill-activity-products
- 本轮策略：跳过 dry-run + 实跑，按"5 条硬验收清单"对账基线状态

## 6 步流程执行

1. **git 分类**
   - 本轮相关：HEAD~6 ~ HEAD（Phase 4-1.5 修复 + 证据 + 归档），已 commit/push，不动
   - 历史无关：working tree clean，无 untracked
   - 临时日志/报告：runtime/ 下大量 mvn/frontend log 保留

2. **不动业务数据**
   - 业务表 0 改动（product_snapshot 始终 10149 条）

3. **dry-run**：跳过（用户 11:25 拍板 C 方案）

4. **实跑**：跳过（同上）

5. **5 条硬验收**：见下表

## 5 条硬验收结果

| # | 项 | 结果 |
|---|---|---|
| 1 | job log 无 RUNNING | PASS（RUNNING=0, 之前 5 个 RUNNING 全部 ABANDONED） |
| 2 | Redis lock 无残留（backfill scope） | PASS（0 个 backfill:lock key） |
| 3 | duplicate=0 | PASS（snapshot_total=10149, distinct_pa=10149, dup_pa=0） |
| 4 | DISPLAYING total / /api/products total | PASS（displayingTotal=3321, /api/products total=3321 一致） |
| 5 | /api/products/admin/counts 全量口径 | PASS（snapshotTotal=10149, relationTotal=10149, distinctProductTotal=8951, displayingTotal=3321, pendingTotal=351, hiddenTotal=6477, activityTotal=24） |
| 6 | backend health=UP | PASS（HTTP 200 {"status":"UP"}） |

## 边界说明

- Redis 出现 order:sync:pay-recent:lock（TTL 571s）非 backfill 范围，是订单同步近窗口补拉活跃锁，正常
- job_log 历史状态分布：ABANDONED=5（本轮清），FAILED=2，FAILED_LOCKED=2，PARTIAL=8，SUCCESS=25

## 关键决定

- 11:19 用户拍板"清孤儿 + 重试" → 清理 3 个老 RUNNING（05876581/2bd7a5a9/a80a7edb）
- 11:25 用户拍板 C 方案 → 跳过 dry-run/实跑
- 11:29 用户拍板"现在清剩 2 个" → 清理 bef6cffd/5c6c1941，5 条验收全部 PASS
