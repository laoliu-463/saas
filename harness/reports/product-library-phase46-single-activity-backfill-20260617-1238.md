# 商品库 Phase 4-6 单活动串行 backfill 报告

## 1. 结论

**PARTIAL / BLOCKED_BY_CONCURRENT_BACKFILL**

本轮由当前会话受控完成 2 个 activity：

- `3371572`
- `3419461`

执行期间检测到其他会话/脚本并行启动 backfill，违反 Phase 4-6 附件规则：

- 不允许并发多个 backfill job。
- 不允许 RUNNING job 未清零时启动新活动。
- 不允许 Redis backfill lock 未清零时启动新活动。

因此当前会话停止继续启动新 activity。本报告不声明 RECENT_30D 全部完成。

## 2. 执行策略

- 单活动串行执行。
- 每个 activity 独立 dry-run。
- dry-run SUCCESS 后才 real backfill。
- 每个 activity 独立验收。
- 不批量、不全量、不主动并发。

## 3. RECENT_30D 活动总览

DB 重新计算：

| 指标 | 数值 |
|---|---:|
| RECENT_30D activityTotal | 24 |
| 已有 SUCCESS/DONE_NO_MORE activity | 14 |
| 剩余未成功 activity | 10 |

剩余 activity：

`3543332`, `3558291`, `3592624`, `3601935`, `3676949`, `3686015`, `3686016`, `3749687`, `3859426`, `3864871`

## 4. 本轮逐活动结果

| activityId | dryRunJobId | dryRunStatus | realJobId | realStatus | apiRows | apiDistinct | inserted | updated | failed | stopReason | 结论 |
|---|---|---|---|---|---:|---:|---:|---:|---:|---|---|
| 3371572 | product-backfill-3a70045c-eaa9-44b2-b7d1-9ff235ddfff4 | SUCCESS | product-backfill-5c59888d-fc03-473d-84b6-09dcd9cc6c95 | SUCCESS | 1456 | 1453 | 1433 | 23 | 0 | DONE_NO_MORE:1 | PASS |
| 3419461 | product-backfill-84e4498a-0abf-4465-b9a5-ca56a535f81b | SUCCESS | product-backfill-304100ff-4015-4498-b804-e2c57211d903 | SUCCESS | 6329 | 6327 | 5827 | 418 | 0 | DONE_NO_MORE:1 | PASS |

并行观察到的非当前会话任务：

| activityId | jobId | dryRun | status | 说明 |
|---|---|---:|---|---|
| 3667047 | product-backfill-3cbdf647-fed1-4435-a449-d286e259d203 | true | SUCCESS | 非当前会话 dry-run |
| 3667047 | product-backfill-c4bfd19d-bb29-459b-a147-16a76006e622 | false | SUCCESS | 非当前会话 real |
| 3419461 | product-backfill-01736c96-d310-4fb7-a9a5-3294ff6b42f7 | true | SUCCESS | 当前会话 real 期间并行 dry-run |
| 3419461 | product-backfill-34b3fb02-f467-4428-a4e0-4a1595efd48b | true | SUCCESS | 当前会话 real 后重复 dry-run |
| 3419461 | product-backfill-c9f1fd58-2f87-43cd-b11c-ae4dc68d6233 | false | RUNNING | 非当前会话重复 real，阻塞收口 |
| 3601935 | product-backfill-8d3311b2-c7b8-496d-bd67-7a31ee886ee9 | true | SUCCESS | 非当前会话 dry-run |
| 3601935 | product-backfill-af062dab-497b-49bf-b3ce-1052bae0cdb1 | false | FAILED_LOCKED | 并发锁竞争 |
| 3601935 | product-backfill-49d5c4b5-a1ee-4ea0-bb42-d8af125d0d4a | false | RUNNING | 非当前会话 real，阻塞收口 |
| 3891192 | product-backfill-e5764d23-a72e-45f5-86f7-c390646e43a3 | true | RUNNING | 用户要求并行后，仅只读检查发现的非当前会话 dry-run；activity 来自 Redis lock |

## 5. 数据 before / after

当前会话开始前基线：

| 指标 | before |
|---|---:|
| product_snapshot | 33901 |
| product_operation_state | 33901 |
| distinctProduct | 28999 |
| DISPLAYING | 11099 |
| HIDDEN | 21408 |
| PENDING | 1394 |
| /api/products total | 11099 |
| duplicate_groups | 0 |

最后一次只读快照（外部 RUNNING 仍存在，数据已被并行任务继续改动）：

| 指标 | after |
|---|---:|
| product_snapshot | 42197 |
| product_operation_state | 42197 |
| distinctProduct | 35135 |
| DISPLAYING | 11095 |
| HIDDEN | 29926 |
| PENDING | 1176 |
| duplicate_groups | 0 |

由于并行任务介入，after delta 不能完全归因给当前会话。

## 6. 锁与健康

| 检查项 | 结果 |
|---|---|
| RUNNING job | 1，`product-backfill-e5764d23-a72e-45f5-86f7-c390646e43a3`，`dry_run=true` |
| Redis backfill lock | 存在 global lock + `product:backfill:activity:3891192:job:lock` |
| duplicate_groups | 0 |
| backend health | UP |
| Docker health | backend/frontend/postgres/redis 均 healthy |
| ClientAbortException / Broken pipe | 未观察到 |
| harness limits | FAIL |

## 7. Git / 文件风险

- 当前分支：`feature/ddd/DDD-VERIFY-001`
- 当前 commit：`65ee41cc`
- 本轮不提交、不推送。
- 原因：工作区含并行会话创建的未跟踪脚本、报告和 token 临时文件，不能安全 staging。
- 发现敏感临时文件名：`tmp-jwt-token.txt`, `tmp-auth-header.txt`。未读取、未输出内容。

## 8. 阶段性结论

当前会话成功完成 `3371572` 与 `3419461` 的 dry-run -> real -> 验收链路。

但环境已被并行 backfill 干扰，且存在 RUNNING job 与 Redis lock，继续启动新活动会违反 Phase 4-6 规则。本阶段只能标记为 PARTIAL / BLOCKED_BY_CONCURRENT_BACKFILL。

## 9. 下一步建议

1. 等所有 RUNNING job 清零。
2. 确认 Redis `product:backfill:*` lock 清零。
3. 清理或移出未跟踪 token 临时文件，禁止提交。
4. 只保留一个执行者继续 Phase 4-6 单活动串行。
5. 下一轮从剩余列表中按低风险小活动继续：`3543332`, `3558291`, `3592624`, `3601935`, `3676949`, `3686015`, `3686016`, `3749687`, `3859426`, `3864871`。
