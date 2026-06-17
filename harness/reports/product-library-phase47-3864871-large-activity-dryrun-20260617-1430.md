# 商品库 Phase 4-7：3864871 超大活动专项 dry-run 报告

## 1. 结论

**PARTIAL**：`3864871` 在 `maxPagesPerActivity=3000`、`maxRowsPerActivity=60000` 下未到 `DONE_NO_MORE`，终态为 `MAX_ROWS_REACHED`。

本阶段未执行真实 backfill，未传 `dryRun=false`，未传 `confirm=true`，不得宣布 `3864871` 已补齐，也不得宣布 RECENT_30D 全量完成。

## 2. 背景

RECENT_30D 已完成 23/24 活动真实回补。唯一剩余活动为 `3864871`。

上一次 `maxPages=2000` 已拉取 `40000` rows / `35679` distinct，但 `MAX_PAGES_REACHED`。本阶段只做 dry-run，不真实写库。

## 3. 执行参数

| 参数 | 值 |
| --- | --- |
| activityId | 3864871 |
| scope | CUSTOM_ACTIVITY_IDS |
| dryRun | true |
| pageSize | 20 |
| maxActivities | 1 |
| maxPagesPerActivity | 3000 |
| maxRowsPerActivity | 60000 |
| confirm | false |

说明：前置检查时已存在同参数 RUNNING job，本轮未重复提交，改为接管轮询该 job。

## 4. 前置检查

| 项 | 结果 |
| --- | --- |
| Branch | `feature/ddd/DDD-VERIFY-001` |
| Commit | `2d088e00` |
| Docker | backend/frontend/postgres/redis 均 healthy |
| backend health | `UP` |
| auth login | code=200，认证成功，未记录凭证 |
| RUNNING job | 发现 1 个既有同参数 dry-run job |
| Redis lock | 前置查询未发现残留；运行中日志显示 active lock 曾阻止定时商品同步 |
| product_snapshot baseline | 53009 rows / 41129 distinct |
| product_operation_state baseline | 53009 rows / 41129 distinct |
| DISPLAYING baseline | 14636 |
| HIDDEN baseline | 37996 |
| PENDING baseline | 377 |
| /api/products total baseline | 14636 |
| duplicate_groups baseline | 0 |

## 5. dry-run 结果

| 指标 | 数值 |
| --- | ---: |
| jobId | `product-backfill-b5e983f8-456d-40cb-9940-e30512ac2ea2` |
| status | PARTIAL |
| activitiesScanned | 1 |
| activitiesSuccess | 0 |
| activitiesIncomplete | 1 |
| activitiesFailed | 0 |
| apiFetchedRows | 60000 |
| apiDistinctProductIds | 52032 |
| stopReasonStats | `{"MAX_ROWS_REACHED":1}` |
| errorMessage | `stopReason=MAX_ROWS_REACHED; message=dry run failed` |
| duration | 27m33s |
| lockWaitCount | 0 |
| deadlockRetryCount | 0 |

日志片段：`harness/reports/product-backfill-3864871-phase47-backend-log-20260617-1429.txt`。

## 6. before / after 验证

| 指标 | before | after | delta |
| --- | ---: | ---: | ---: |
| product_snapshot | 53009 | 53009 | 0 |
| product_operation_state | 53009 | 53009 | 0 |
| distinctProduct | 41129 | 41129 | 0 |
| DISPLAYING | 14636 | 14639 | +3 |
| HIDDEN | 37996 | 34628 | -3368 |
| PENDING | 377 | 3742 | +3365 |
| /api/products total | 14636 | 14639 | +3 |
| duplicate_groups | 0 | 0 | 0 |
| RUNNING job | 1 | 0 | -1 |
| Redis backfill lock | 0 | 0 | 0 |

状态分布变化由 06:15 的 `ProductDisplayRuleJob completed, processedProductIds=14639` 解释；本轮 dry-run 未增加 `product_snapshot` 或 `product_operation_state` 行数。

## 7. 结论解释

`3864871` 在 `maxRowsPerActivity=60000` 下仍未完整读取。由于 `60000 = 3000 * pageSize(20)`，该结果等价于 3000 页 / 60000 行边界仍不足。

不允许真实 backfill。不允许继续直接加到 5000。建议进入 Phase 4-8：分段式 cursor/page checkpoint 设计。

## 8. 风险

1. 本阶段未真实写库。
2. 未补齐 `3864871`。
3. 不能宣布 RECENT_30D 全量完成。
4. 如果继续盲目加页数，仍可能触发长任务、限流或状态不可观测问题。
5. 状态接口没有按页刷新 `apiFetchedRows`，中间进度主要依赖 backend 日志旁路观察。

## 9. 下一步建议

进入 Phase 4-8：为 `3864871` 设计分段拉取和 checkpoint，明确分段边界、幂等、锁、失败恢复和验证口径后再评估真实 backfill。
