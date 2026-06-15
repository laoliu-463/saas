# 商品库 Phase 4-1 单活动真实 backfill 验证报告

## 1. 结论

- 状态：FAIL
- 环境：real-pre，本地 Docker Compose
- 分支：feature/ddd/DDD-VERIFY-001
- 执行时间：2026-06-15 13:42 Asia/Shanghai
- 真实 backfill：已执行，但失败；未执行幂等重跑
- 结论：dry-run 通过且未写业务表；真实 backfill 在 activityId=3859423 上失败，sync state 记录 PostgreSQL deadlock，不能进入 Phase 4-2。

## 2. 执行范围

- scope=CUSTOM_ACTIVITY_IDS
- activityIds=["3859423"]
- 未执行 RECENT_30D
- 未执行 ALL_LOCAL_ACTIVITIES
- 未执行 ALL_BOUND
- 未清库，未 truncate/delete/reset 业务表，未直接改 DISPLAYING 状态。

## 3. 前置状态

- 继承上一轮 commits：4f9977c9、4711014b、ba61e8d1
- 已读报告：evidence-20260615-114620.md、product-library-full-backfill-evidence-20260615.md、product-library-count-audit-20260615-1110.md
- 本轮前置：分支正确；工作区在真实写库前为 clean；backend/frontend/postgres/redis healthy；backend health=UP；frontend /login=200。
- 为遵守 reports 目录 10 文件限制，先提交纯归档 commit 2adb0bb2，移动两个旧 content-retire 报告到 archive。

## 4. dry-run 复核结果

| 指标 | 数值 |
| --- | ---: |
| jobId | product-backfill-f4f27f1b-102e-4060-9e0f-6a1d3984012b |
| apiFetchedRows | 4064 |
| apiDistinctProductIds | 4063 |
| dbRowsForScannedActivities | 2000 |
| estimatedGapRows | 2064 |
| stopReason | DONE_NO_MORE |
| activitiesSuccess | 1 |
| activitiesIncomplete | 0 |
| activitiesFailed | 0 |

dry-run 业务表写入复核：

| 指标 | before | after |
| --- | ---: | ---: |
| product_snapshot activity rows | 2000 | 2000 |
| product_operation_state activity rows | 2000 | 2000 |
| product_snapshot rows | 7983 | 7983 |
| product_operation_state rows | 7983 | 7983 |

## 5. 第一次真实 backfill 结果

| 指标 | 数值 |
| --- | ---: |
| jobId | product-backfill-6af56b55-0dae-446f-90a6-599dea5501c0 |
| job log status | FAILED |
| API response code | 200 |
| activitiesScanned | 1 |
| activitiesSuccess | 0 |
| activitiesFailed | 1 |
| apiFetchedRows | 0 |
| apiDistinctProductIds | 0 |
| inserted | 0 |
| updated | 0 |
| skipped | 0 |
| failed | 1 |
| stopReasonStats | {"API_ERROR":1} |

失败证据：`product_activity_sync_state.last_status=FAILED`，`last_stop_reason=API_ERROR`，`last_error_message` 记录 PostgreSQL `deadlock detected`，位置为 `product_operation_state` update。

## 6. DB before/after 对比

| 指标 | before | after | delta |
| --- | ---: | ---: | ---: |
| activity 3859423 rows | 2000 | 2000 | 0 |
| activity 3859423 distinct products | 2000 | 2000 | 0 |
| product_snapshot rows | 7983 | 7983 | 0 |
| product_snapshot distinct products | 7024 | 7024 | 0 |
| product_operation_state rows | 7983 | 7983 | 0 |
| DISPLAYING rows | 2977 | 2977 | 0 |
| /api/products total | 2977 | 2977 | 0 |
| admin relationTotal | 7983 | 7983 | 0 |
| admin snapshotTotal | 7983 | 7983 | 0 |

补充观察：activity 3859423 的 DISPLAYING 从 1452 变为 1480，HIDDEN 从 531 变为 503；job 执行窗口内有 34 条 operation_state 被更新。由于真实 job 最终 FAILED，此变化只能记录为失败过程中的部分副作用，不能视为成功补数。

## 7. 幂等重跑结果

未执行。原因：第一次真实 backfill 已 FAILED，且 stopReasonStats 含 API_ERROR；按本阶段限制，失败后不得重跑或扩大范围。

| 指标 | 第一次后 | 第二次后 | delta |
| --- | ---: | ---: | ---: |
| activity 3859423 rows | 2000 | 未执行 | 未执行 |
| distinct products | 2000 | 未执行 | 未执行 |
| duplicate rows | 0 | 未执行 | 未执行 |
| product_snapshot rows | 7983 | 未执行 | 未执行 |
| DISPLAYING total | 2977 | 未执行 | 未执行 |
| /api/products total | 2977 | 未执行 | 未执行 |

## 8. job log 验证

- dry-run job：SUCCESS，dry_run=true，DONE_NO_MORE，apiFetchedRows=4064，apiDistinctProductIds=4063。
- 第一次真实 backfill job：FAILED，dry_run=false，activitiesFailed=1，failed=1，stopReasonStats={"API_ERROR":1}。
- 第二次幂等重跑 job：未执行，原因同第 7 节。

## 9. activity sync state 验证

| 字段 | 值 |
| --- | --- |
| activity_id | 3859423 |
| scope | CUSTOM_ACTIVITY_IDS |
| last_status | FAILED |
| last_stop_reason | API_ERROR |
| last_success_at | 空 |
| last_attempt_at | 2026-06-15 05:29:20 UTC |
| last_fetched_rows | 0 |
| last_distinct_product_ids | 0 |
| last_inserted | 0 |
| last_updated | 0 |
| last_skipped | 0 |
| last_failed | 1 |
| consecutive_failures | 1 |
| last_error_message | PostgreSQL deadlock detected while updating product_operation_state |

## 10. 商品库展示口径验证

- `/api/products?page=1&size=100` 连续两次 total=2977，records=100。
- DB DISPLAYING=2977，说明 `/api/products` total 仍是 DISPLAYING 口径。
- 页面 smoke：`/product/library` 可加载，显示“已加载 100 / 2977 件”。
- `/api/products/admin/counts` 连续两次一致：snapshotTotal=7983，relationTotal=7983，displayingTotal=2977，pendingTotal=281，hiddenTotal=4725。
- Redis scan 未发现 `*product*` 或 `*library*` key。

## 11. 服务健康

- backend `/api/system/health`：UP
- frontend `/login`：HTTP 200
- Docker：backend/frontend/postgres/redis 均 healthy
- frontend logs：页面资源与 API 返回 200；浏览器 console 有 Google Fonts CSP 拦截噪声。
- backend logs：真实 backfill 期间出现 PostgreSQL deadlock，必须作为 FAIL 证据。

## 12. 测试 / harness 结果

- `safety-check.ps1 -Env real-pre -Scope docs -DryRun`：PASS
- 页面 smoke：PASS，商品库口径文本可见
- 未跑全量构建/重启：本轮未改源码；真实 backfill 已失败，继续 full 构建不能证明写库链路成功。

## 13. 风险与回滚

- 本轮仅触发 activityId=3859423，但真实 job FAILED 且发生部分 operation_state 状态更新。
- 如需回滚展示异常，必须优先基于审计字段和 job 时间窗口定位状态差异，不得删除事实数据。
- 可临时禁用 backfill API 或保持仅 dry-run，直到 deadlock 并发/事务边界修复。
- 不建议进入 RECENT_30D 或任何全量真实 backfill。

## 14. 下一步建议

- 不进入 Phase 4-2。
- 先定位 `product_operation_state` 更新 deadlock：重点检查真实 backfill 与展示规则重算/定时同步是否并发更新同一 activity/product，补充互斥锁、排序更新或重试策略后再重做单活动真实验证。

## 15. 2026-06-15 13:47 daemon 重启后复核

- 触发：Mavis daemon 在 13:30 与 13:47 之间发生过一次崩溃重启，本会话被中断。重启后我重新拉起 Docker Desktop、确认 real-pre 容器 healthy，并再次复核本报告所有数字仍然成立。
- 服务健康复核：backend `/api/system/health`=UP；frontend `/login`=HTTP 200；postgres/redis=healthy。
- DB 关键数字复核（与第 6 节"after"完全一致）：
  - `product_snapshot rows`=7983，`product_operation_state rows`=7983。
  - `display_status='DISPLAYING'`=2977。
  - `activity_id='3859423' rows`=2000，`3859423 DISPLAYING`=1480。
- job log 复核：仍为 3 条；最新一条 `product-backfill-6af56b55-...` status=FAILED、`stopReasonStats={"API_ERROR":1}`、`finished_at=2026-06-15 05:29:20.913734`；与第 5 节一致。
- activity sync state 复核：`activity_id=3859423` 仍为 `last_status=FAILED`、`last_stop_reason=API_ERROR`、错误信息为 PostgreSQL `deadlock detected`，与第 9 节一致。
- 结论不变：本报告全部数字与 daemon 重启后现场一致；Phase 4-1 结论仍是 FAIL、不进入 Phase 4-2。
- 本轮工作区处理：清理临时基线目录 `harness/reports/_phase4-1-baseline/`；不提交 `harness/reports/latest-harness-limits-check.md`（harness auto-generate 中间产物）。
