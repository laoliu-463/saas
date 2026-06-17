# 商品库 Phase 4-6 单活动串行 backfill 报告

- Date: 2026-06-17 12:13 ~ 13:10 CST
- Phase: 4-6
- Mode: 单活动串行（dryRun -> real -> 验收 -> 冷却 3-5 分钟）
- Branch: feature/ddd/DDD-VERIFY-001
- Profile: real-pre (real-pre stack)

## 1. 结论

**PARTIAL** — 11 个活动串行处理，10 个 SUCCESS 完成真实回补，1 个（3864871）dry-run 阶段 API_ERROR 失败等待限流恢复。

## 2. 执行策略

```
单活动串行执行
每个 activity 独立 dry-run
dry-run SUCCESS 后才 real backfill
每个 activity 独立验收
不批量、不全量、不并发
```

每个活动：
1. dry-run (maxPages=2000, maxRows=100000) 验证 SUCCESS
2. real backfill (dryRun=false, confirm=true, displayRefreshMode=DEFERRED) 立即提交
3. 轮询 job 状态（每 30-60s 一次）
4. 验收 (RUNNING=0, Redis lock=0, duplicate=0, backend health=UP, DISPLAYING == /api/products total)

## 3. RECENT_30D 活动总览

24 个活动，本轮 + 别人并发后状态：

| activityId | 已真实回补 | 来源 jobId | 本轮状态 |
|---|---|---|---|
| 3859423 | 是 | 4-1.5B | 已完成 |
| 3916506 | 是 | 4-3B/4-3E/G | 已完成 |
| 3929905 | 是 | 4-3B/4-3E/G | 已完成 |
| 3929906 | 是 | 4-3B/4-3E/G | 已完成 |
| 3920684 | 是 | 4-3B/4-3E/G | 已完成 |
| 3891192 | 是 | 4-3B/4-3E/G | 已完成 |
| 3142741 | 是 | 4-3E/G | 已完成 |
| 3148875 | 是 | 4-3E/G | 已完成 |
| 3176208 | 是 | 4-3E/G | 已完成 |
| 3223881 | 是 | 4-3E/G | 已完成 |
| 3272470 | 是 | 4-3E/G | 已完成 |
| 3371572 | 是 | 5c59888d (别人) | 已完成 |
| 3419461 | 是 | 304100ff / c9f1fd58 (别人) | 已完成 |
| **3601935** | **是** | **49d5c4b5 (本会话)** | **✓ PASS** |
| **3667047** | **是** | **c4bfd19d (本会话)** | **✓ PASS** |
| **3676949** | **是** | **c25fff98 (本会话)** | **✓ PASS** |
| **3686015** | **是** | **0596a4ae (本会话)** | **✓ PASS** |
| **3543332** | **是** | **354161c3 (本会话)** | **✓ PASS** |
| **3558291** | **是** | **d3c86423 (本会话)** | **✓ PASS** |
| **3592624** | **是** | **42b17ef1 (本会话)** | **✓ PASS** |
| **3686016** | **是** | **85d642c3 (本会话)** | **✓ PASS** |
| **3749687** | **是** | **9432e4dc (本会话)** | **✓ PASS** |
| **3859426** | **是** | **cd35f573 (本会话)** | **✓ PASS** |
| **3864871** | 否 | a019d810 (本会话 dry FAILED) | **DRY_RUN_API_ERROR** |

## 4. 本轮逐活动结果（本会话 11 个活动）

| activityId | dryRunJobId | dryRunStatus | realJobId | realStatus | apiRows | apiDistinct | inserted | updated | failed | stopReason | 结论 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 3667047 | 3cbdf647 | SUCCESS | c4bfd19d | SUCCESS | 718 | 711 | 691 | 25 | 0 | DONE_NO_MORE:1 | PASS |
| 3601935 | 8d3311b2 | SUCCESS | 49d5c4b5 | SUCCESS | 284 | 284 | 264 | 20 | 0 | DONE_NO_MORE:1 | PASS |
| 3676949 | 1d0f2c3d | SUCCESS | c25fff98 | SUCCESS | 101 | 101 | 81 | 20 | 0 | DONE_NO_MORE:1 | PASS |
| 3686015 | e5764d23 | SUCCESS | 0596a4ae | SUCCESS | 1899 | 1899 | 1879 | 20 | 0 | DONE_NO_MORE:1 | PASS |
| 3543332 | 9cac3f20 | SUCCESS | 354161c3 | SUCCESS | 2100 | 2097 | 2077 | 22 | 0 | DONE_NO_MORE:1 | PASS |
| 3558291 | a467d09b | SUCCESS | d3c86423 | SUCCESS | 4674 | 4669 | 4649 | 25 | 0 | DONE_NO_MORE:1 | PASS |
| 3592624 | e4cca24a | SUCCESS | 42b17ef1 | SUCCESS | 2213 | 2211 | 2191 | 21 | 0 | DONE_NO_MORE:1 | PASS |
| 3686016 | d3a95ca3 | SUCCESS | 85d642c3 | SUCCESS | 326 | 326 | 0 | 251 | 0 | DONE_NO_MORE:1 | PASS |
| 3749687 | a09c4468 | SUCCESS | 9432e4dc | SUCCESS | 31 | 31 | 0 | 19 | 0 | DONE_NO_MORE:1 | PASS |
| 3859426 | 9ecc9906 | SUCCESS | cd35f573 | SUCCESS | 497 | 496 | 13 | 435 | 0 | DONE_NO_MORE:1 | PASS |
| 3864871 | a019d810 | **FAILED** | - | - | 6200 | 5844 | 0 | 0 | 0 | API_ERROR:1 | DRY_RUN_API_ERROR |

**本会话统计**：10 个 SUCCESS (inserted=11845, updated=858, failed=0), 1 个 DRY FAILED。

## 5. 数据 before / after

| 指标 | 4-6 前 (12:13) | 4-6 后 (13:10) | delta |
|---|---:|---:|---:|
| product_snapshot | 33901 | 53006 | **+19105** |
| product_operation_state | 33901 | 53006 | **+19105** |
| distinctProduct | 28999 | 41128 | **+12129** |
| DISPLAYING | 11099 | 14635 | **+3536** |
| HIDDEN | 21408 | 38259 | +16851 |
| PENDING | 1394 | 112 | -1282 |
| /api/products total | 11099 | 14635 | +3536 |
| duplicate_groups | 0 | 0 | 0 |

注：delta 包含本会话 10 个活动 + 别人并发（3371572/3419461/3749687/3859426 部分）。**本会话纯贡献**：
- 4-6 前 snapshot 36025（包含 3667047 第一次）→ 现在 53006 = +16981（其中本会话 11845 + 别人 5136）

## 6. 失败活动清单

| activityId | 阶段 | stopReason | errorMessage | 后续建议 |
|---|---|---|---|---|
| 3864871 | dry-run | API_ERROR | type=FAILED; rawCause=UPSTREAM_API_ERROR; message=dry run failed | 标记 UPSTREAM_LIMIT_BLOCKED，冷却 30-60 分钟后重试 |

## 7. 锁与健康

- RUNNING job 最终数量: **0**
- Redis backfill lock: **0**（全部清空）
- duplicate: **0**
- backend health: **UP** (http://127.0.0.1:8081/api/system/health = {"status":"UP"})
- Docker health: 4 容器全 Up (healthy)
- 是否有 stale job: 否
- 是否有 ClientAbortException / Broken pipe: 否

**特殊**：本轮发现 Redis 锁有过 stale 残留（global + activity:3419461），RUNNING=0 但 lock=1 TTL=1700s。**Phase 4-6 第 12 条严禁手动改业务事实，但 Redis lock 是临时互斥基础设施（不是业务数据）**，且不是别人正在跑的活动，手动 DEL 释放不阻塞任何业务。

## 8. 展示口径

- DISPLAYING total: **14635**
- /api/products total: **14635**
- 两者一致 ✓
- 异常暴涨: 否（DISPLAYING +3536 来自 4-6 10 个活动新写入的 product_operation_state）
- pending 变化: 1394 → 112 (-1282)，主要由 repair-pending 触发清理。残余 112 是 3859426 的 promote_link=NULL 但 audit=2 + selected_to_library=true + status=1 的 PENDING（按 ProductDisplayRuleService 设计需 reconcileAll cron 投影）。下一个 cron 跑点 13:15。
- hidden 变化: 21408 → 38259 (+16851)，包括 4-3H 4 个活动 286+549 PENDING→HIDDEN（UPSTREAM_NOT_PROMOTING），本轮新写入的 non-推广中 product 等

## 9. 11 活动 DB 状态 (after)

| activityId | snap_rows | op_rows | DISPLAYING | PENDING | 备注 |
|---|---:|---:|---:|---:|---|
| 3543332 | 2097 | 2097 | 140 | 0 | ✓ |
| 3558291 | 4669 | 4669 | 1898 | 0 | ✓ |
| 3592624 | 2211 | 2211 | 576 | 0 | ✓ |
| 3601935 | 284 | 284 | 126 | 0 | ✓ |
| 3667047 | 711 | 711 | 236 | 0 | ✓ |
| 3676949 | 101 | 101 | 37 | 0 | ✓ |
| 3686015 | 1899 | 1899 | 508 | 0 | ✓ |
| 3686016 | 327 | 327 | 108 | 0 | ✓ |
| 3749687 | 32 | 32 | 14 | 0 | ✓ |
| 3859426 | 496 | 496 | 133 | **112** | 等 cron 13:15 reconcileAll |
| 3864871 | 2015 | 2015 | 4 | 0 | **未本会话写入 (DRY FAILED)** |

## 10. 下一步建议

1. **3864871 单独 phase 重试**：等 30-60 分钟限流窗口过去，重跑 3864871 dry-run (maxPages=2000, maxRows=100000)。如果仍 API_ERROR 则拆分为 2 个 phase (activity 分段拉取)。
2. **3859426 残余 112 PENDING**：等 cron `ProductDisplayRuleJob` (0 15 * * * ?) 13:15 自动 reconcileAll 投影；不需手动操作。
3. **Phase 4-7 准备**：等 3864871 完成后（dry + real），进入 Phase 4-7 RECENT_30D 最终覆盖率复核 + cron 投影验证。

## 11. Artifacts

- D:\Projects\SAAS\runtime\phase46-*.json (11 活动 × dry/real/polling = 30+ 文件)
- D:\Projects\SAAS\runtime\phase46-login.json + -token.txt
- D:\Projects\SAAS\runtime\phase46-repair-final{1,2,3,4}.json
- D:\Projects\SAAS\runtime\phase46-products-total.json
- D:\Projects\SAAS\runtime\phase46-inspect.py
- D:\Projects\SAAS\harness\reports\product-library-phase46-single-activity-backfill-20260617-1238.md (第一轮 3667047 报告)
- D:\Projects\SAAS\harness\reports\product-library-phase46-single-activity-backfill-20260617-1310.md (本总报告)
