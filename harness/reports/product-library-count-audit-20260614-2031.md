# 商品库数量缺失审计报告

## 1. 结论
状态：FAIL。

已确认至少存在一个明确同步丢数点：活动 `3859423` 的上游只读 dry-run 到第 105 页仍持续返回数据，而当前生产同步代码固定最多拉 100 页，且每页最多 20 条。因此该活动在当前同步路径下单次最多写入 2000 条，已不能完整覆盖上游。

本轮仍不能最终证明“上游真实总数为几万”，因为当前已有探针 `/api/douyin/activity-product-list` 的 `total` 返回为 0，必须通过全活动 dry-run 逐页累计才能确认全量上游数量。本报告结论是：同步分页上限已构成真实缺失原因之一；是否解释全部差异需补全全量 dry-run。

## 2. 关键数字
| 口径 | 数量 |
|---|---:|
| 上游 dry-run 活动数 | 1 个活动深分页，5 个活动首页探测 |
| 上游 dry-run 活动商品行数 | 活动 `3859423` 前 105 页返回 2100 行，仍有 nextCursor |
| 上游 dry-run distinct product_id | 活动 `3859423` 前 105 页 2099 |
| DB activities | 24 |
| DB activity_product_relation 等价表 `product_snapshot` | 7962 |
| DB products distinct (`product_snapshot.product_id`) | 7004 |
| DB `product` 主表 distinct | 12 |
| 展示候选 `ps.status=1 + selected=true` | 3151 |
| display_status=DISPLAYING | 2956 |
| 商品库接口 total | 2956 |
| 前端页面显示 | 使用 `/api/products` 返回 total，管理员/招商/渠道均为 2956 |

## 3. 漏斗分析
| 步骤 | 条件 | 剩余数量 | 减少数量 |
|---|---|---:|---:|
| 0 | `product_snapshot` 全量 | 7962 | - |
| 1 | `ps.status=1` 推广中 | 3151 | 4811 |
| 2 | `selected_to_library=true` | 3151 | 0 |
| 3 | `manual_disabled=false` | 3151 | 0 |
| 4 | `audit_status<>3` | 3151 | 0 |
| 5 | `display_status=DISPLAYING` | 2956 | 195 |
| 6 | visible distinct product_id | 2956 | 0 |

隐藏原因分布：
| display_status | hidden_reason | 数量 |
|---|---|---:|
| DISPLAYING | 空 | 2956 |
| HIDDEN | UPSTREAM_NOT_PROMOTING | 4559 |
| HIDDEN | REPLACED_BY_HIGHER_PRIORITY | 158 |
| HIDDEN | REPLACED_BY_ADVANTAGE | 37 |
| PENDING | 空 | 252 |

## 4. 根因排序
P0-1 分页上限导致活动商品未拉完：已确认。代码 `ProductService.refreshActivitySnapshots` 固定 `maxPages=100`，每页上限 20；活动 `3859423` 第 101 页仍返回 20 条，当前同步会提前停止。

P0-2 同步活动范围过窄：高风险。定时任务只选 `end_time >= now` 且 `last_sync_at < now-30min` 的活动，每轮最多 20 个；最近日志出现多轮 `ok=0`，说明不是持续全量回补。

P0-3 relation 写库丢失：本轮未确认。当前等价关系表 `product_snapshot`、`product_operation_state` 均有 `(activity_id, product_id)` 唯一索引，不是单 product_id 唯一键。旧表 `colonel_activity_product` 为 0 行，需确认是否仍有 consumer 读取该旧表。

P0-4 展示规则过滤错误：不是当前“几千可见”的主要根因。API total=2956 与 SQL `DISPLAYING` 一致；195 条推广候选被去重/优势规则隐藏，252 条仍 PENDING。

P1-1 权限过滤错误：暂未发现。admin、biz_leader、biz_staff、channel_leader、channel_staff 请求 `/api/products` 均返回 total=2956。

P1-2 缓存陈旧：本轮未发现证据。接口 total 与实时 SQL 一致。

P1-3 前端 total 误读：暂未发现。前端 `ProductLibrary.vue` 读取 `data.total`，不是 `records.length`。

## 5. 代码证据
- `backend/src/main/java/com/colonel/saas/service/ProductService.java:1679`：同步 pageSize 被限制到最大 20。
- `backend/src/main/java/com/colonel/saas/service/ProductService.java:1682`：`maxPages = 100` 硬编码。
- `backend/src/main/java/com/colonel/saas/service/ProductService.java:1722`：依赖 `nextCursor` 判断是否继续。
- `backend/src/main/java/com/colonel/saas/service/ProductActivityManualSyncService.java:23`：手动同步默认 page size 为 20。
- `backend/src/main/java/com/colonel/saas/job/ProductActivitySyncJob.java:38`：定时同步默认启用开关读取配置。
- `backend/src/main/java/com/colonel/saas/job/ProductActivitySyncJob.java:117`：无白名单时只读取待同步活动。
- `backend/src/main/resources/mapper/ColonelsettlementActivityMapper.xml:338`：待同步活动筛选排除已过期活动，并要求 `last_sync_at` 超过 30 分钟。
- `backend/src/main/java/com/colonel/saas/douyin/api/ProductApi.java:182`：真实调用 `alliance.colonelActivityProduct`。
- `backend/src/main/java/com/colonel/saas/gateway/douyin/real/RealDouyinProductGateway.java:110`：从 `data` 节点解析行、`total`、`next_cursor`。
- `backend/src/main/java/com/colonel/saas/service/ProductService.java:312`：商品库接口只查询 `selected_to_library=true` 且 `display_status=DISPLAYING`。
- `frontend/src/views/product/ProductLibrary.vue:280`：前端 total 来自后端 `data.total`。

## 6. DB 证据
数量审计 SQL：
```sql
select count(*) from colonel_activity;
select count(*), count(distinct product_id) from product_snapshot;
select display_status, hidden_reason, count(*) from product_operation_state where deleted=0 group by display_status, hidden_reason;
```

关键结果：
- `colonel_activity`: 24。
- `product_snapshot`: 7962 行，7004 个 distinct product_id。
- `product_operation_state`: 7962 行，DISPLAYING 2956，HIDDEN 4754，PENDING 252。
- `product`: 12 行；当前商品库主查询并不以该主表作为全量关系事实。
- 约束：`product_snapshot`、`product_operation_state`、`colonel_activity_product` 均存在 `(activity_id, product_id)` 唯一索引；`product` 才是 `product_id` 唯一。

Top 活动 DB 行数：
| 活动ID | 活动名 | snapshot rows | 推广中 | DISPLAYING |
|---|---|---:|---:|---:|
| 3864871 | 二级专用 | 2015 | 5 | 5 |
| 3859423 | 星链达客-nn | 2000 | 1490 | 1480 |
| 3916506 | 星链达客-zy | 1125 | 596 | 563 |
| 3419461 | 星链达客-5 | 500 | 3 | 3 |

## 7. API 证据
只读探针：`GET /api/douyin/activity-product-list?activityId=3859423&count=20`。

首页结果：`returned=20`，`nextCursor` 非空，`total=0`。因此上游返回的 `total` 当前不可作为真实总量。

深分页 dry-run：
| 活动ID | max_pages | pages | fetched | distinct | stopped_reason |
|---|---:|---:|---:|---:|---|
| 3859423 | 105 | 105 | 2100 | 2099 | MAX_PAGES |

关键页：
| 页 | returned | hasNext |
|---:|---:|---|
| 99 | 20 | true |
| 100 | 20 | true |
| 101 | 20 | true |
| 102 | 20 | true |
| 105 | 20 | true |

商品库接口：
| 用户 | 角色 | data_scope | `/api/products` total |
|---|---|---|---:|
| admin | admin | all | 2956 |
| biz_leader | biz_leader | group | 2956 |
| biz_staff | biz_staff | self | 2956 |
| channel_leader | channel_leader | group | 2956 |
| channel_staff | channel_staff | self | 2956 |

## 8. 修复方案
临时止血方案：增加只读全量 dry-run probe，不写库，按所有未过期活动逐页累计 `returned/distinct/nextCursor/repeatedCursor/stoppedReason`，用于确认上游真实总量。风险是触发上游限流；回滚方式是不发布该 probe 或仅 admin 可用；验收是能输出每活动页数和总 distinct。

根因修复方案：将 `ProductService.refreshActivitySnapshots` 的 `maxPages=100` 改为配置项，并按 `nextCursor`、空页、重复 cursor、限流错误、最大页数分别记录 stopReason。若 API `total=0` 但仍有 cursor，不能按 total 停。补 `ProductSyncPaginationTest` 覆盖第 101 页仍有数据的场景。

长期治理方案：新增全量 backfill job，支持 `dry_run`、按 activity_id 单独补、按全部有效活动补、失败不推进 `last_sync_at`。同步日志必须记录 fetched/inserted/updated/skipped/failed/pages/stopReason。

## 9. 验收标准
1. full-products-dry-run 能解释上游真实数量，至少覆盖当前 24 个活动。
2. 单活动 `3859423` 修复后能拉过第 100 页，并继续到 `NO_MORE`、`EMPTY_PAGE`、`REPEATED_CURSOR` 或受控 `MAX_PAGES`。
3. DB `product_snapshot` 接近上游活动商品行数。
4. DB distinct product_id 接近上游 distinct product_id。
5. `/api/products` visible 数量与 `DISPLAYING` 口径一致。
6. 管理端补充“全量商品 / 展示中 / 待补充 / 已隐藏 / 上游非推广中”分口径数量。
7. 后端目标测试覆盖分页、重复 cursor、空 cursor、限流、失败不推进同步时间。
8. real-pre 容器重启后 `/api/system/health`、`/api/products`、商品库 health 稳定。
9. 不使用 mock 数据冒充真实上游闭环。

## 10. 本轮执行说明
- 本轮只读：未改数据库，未触发写库同步，未重启容器。
- 本轮新增文件仅为审计报告。
- `docs/01-V1交付范围与边界.md` 在当前工作区不存在，已改读兼容入口 `docs/01-V1交付合同.md` 和商品域/对接/real-pre 文档。
- retro summary：本次无需 Harness 升级；下一步应在明确授权后进入第二阶段，新增 dry-run probe 与测试。
