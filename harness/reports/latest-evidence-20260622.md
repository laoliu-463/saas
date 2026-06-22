# 活动商品数量缺失只读排查

## 结论
PARTIAL

## 摘要
- 是否存在商品缺失：当前不能下最终结论，缺少抖店后台导出明细或全量上游 product_id 集合。
- 本地系统结论：24 个活动本地 `product_snapshot` 与本地活动商品接口 total 全部一致。
- 本地汇总：活动数 24，`product_snapshot` 去重商品数 53140，`product_operation_state` 去重商品数 53140，重复行 0。
- 关键阻塞：附件未提供后台 CSV；联调首屏探针返回商品行和分页游标，但 `remoteResponse.total=0`，该字段不能作为后台数量证据。

## 证据
- 环境：本地 real-pre，backend `/api/system/health` 返回 `{"status":"UP"}`。
- 数据库：`docker exec saas-active-postgres-real-pre-1 ... psql ... SET default_transaction_read_only=on`。
- 接口：管理员只读 `GET /api/colonel/activities/{activityId}/products?count=20&refresh=false`。
- 上游探针：管理员只读 `GET /api/douyin/activity-product-list?activityId={activityId}&count=1`，24 个活动均 `status=success`，但 `remoteResponse.total=0` 且 `items_count=1`，total 不可信。
- 代码证据：`ColonelActivityController.listProducts` 在 `refresh=false` 且有快照时走 `buildActivityProductListViewFromDb`；`ProductService` 的 DB total 来自 `product_snapshot` count；`ProductSyncProbeController` 标注 dry-run 不写 `product_snapshot/product_operation_state`。
- 唯一键：数据库存在 `uk_product_snapshot_activity_product ON product_snapshot(activity_id, product_id)`。
- 同步状态：多数活动最新状态为 `SUCCESS/DONE_NO_MORE`；历史 dry-run 存在 `PARTIAL/MAX_ROWS_REACHED` 和 `API_ERROR`，但不能直接证明当前缺数。

## 活动维度数量对账

| activity_id | activity_name | backend_product_count | local_snapshot_product_count | local_api_total | missing_count | extra_local_count | last_sync_time | 判断结果 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| 3223881 | 星链达客3 | PENDING | 10842 | 10842 | PENDING | PENDING | 2026-06-16 12:47:10 | 本地一致，待后台明细 |
| 3272470 | 星链达客-yy | PENDING | 8981 | 8981 | PENDING | PENDING | 2026-06-16 12:54:12 | 本地一致，待后台明细 |
| 3419461 | 星链达客-5 | PENDING | 6327 | 6327 | PENDING | PENDING | 2026-06-17 04:47:56 | 本地一致，待后台明细 |
| 3558291 | 星链达客-ly | PENDING | 4669 | 4669 | PENDING | PENDING | 2026-06-17 05:06:06 | 本地一致，待后台明细 |
| 3859423 | 星链达客-nn | PENDING | 4099 | 4099 | PENDING | PENDING | 2026-06-16 00:24:16 | 本地一致，待后台明细 |
| 3176208 | 星链达客2 | PENDING | 2469 | 2469 | PENDING | PENDING | 2026-06-16 12:38:47 | 本地一致，待后台明细 |
| 3592624 | 星链达客-ly2 | PENDING | 2211 | 2211 | PENDING | PENDING | 2026-06-17 05:09:29 | 本地一致，待后台明细 |
| 3543332 | 灵龙 | PENDING | 2097 | 2097 | PENDING | PENDING | 2026-06-17 04:58:08 | 本地一致，待后台明细 |
| 3864871 | 二级专用 | PENDING | 2015 | 2015 | PENDING | PENDING | 2026-05-30 08:01:52 | 本地一致，待后台明细 |
| 3686015 | 星链达客-hlg | PENDING | 1899 | 1899 | PENDING | PENDING | 2026-06-17 04:54:29 | 本地一致，待后台明细 |
| 3371572 | 星链达客-yy-2 | PENDING | 1453 | 1453 | PENDING | PENDING | 2026-06-17 04:24:05 | 本地一致，待后台明细 |
| 3142741 | 星链达客1 | PENDING | 1312 | 1312 | PENDING | PENDING | 2026-06-16 12:36:33 | 本地一致，待后台明细 |
| 3916506 | 星链达客-zy | PENDING | 1290 | 1290 | PENDING | PENDING | 2026-06-22 12:51:25 | 本地一致，待后台明细 |
| 3667047 | 星链达客6 | PENDING | 711 | 711 | PENDING | PENDING | 2026-06-17 04:28:47 | 本地一致，待后台明细 |
| 3859426 | 星链达客-nn2 | PENDING | 496 | 496 | PENDING | PENDING | 2026-06-17 05:17:51 | 本地一致，待后台明细 |
| 3929905 | 星链达客-tz | PENDING | 453 | 453 | PENDING | PENDING | 2026-06-22 13:25:28 | 本地一致，待后台明细 |
| 3920684 | 星链达客-zy2 | PENDING | 425 | 425 | PENDING | PENDING | 2026-06-22 13:20:38 | 本地一致，待后台明细 |
| 3686016 | 星链达客-hlg1 | PENDING | 327 | 327 | PENDING | PENDING | 2026-06-17 05:11:40 | 本地一致，待后台明细 |
| 3601935 | 星链达客-yy-3 | PENDING | 284 | 284 | PENDING | PENDING | 2026-06-17 04:47:41 | 本地一致，待后台明细 |
| 3891192 | 星链达客-nn3 | PENDING | 253 | 253 | PENDING | PENDING | 2026-06-20 17:32:13 | 本地一致，待后台明细 |
| 3929906 | 星链达客-tz2 | PENDING | 221 | 221 | PENDING | PENDING | 2026-06-22 13:20:13 | 本地一致，待后台明细 |
| 3148875 | 星链达客（推广周期2） | PENDING | 173 | 173 | PENDING | PENDING | 2026-06-16 12:36:42 | 本地一致，待后台明细 |
| 3676949 | 星链达客-ly3 | PENDING | 101 | 101 | PENDING | PENDING | 2026-06-17 04:49:01 | 本地一致，待后台明细 |
| 3749687 | 星链达客-hlg2 | PENDING | 32 | 32 | PENDING | PENDING | 2026-06-17 05:14:26 | 本地一致，待后台明细 |

## 单活动核验：3916506

用户提供后台状态数：待审核 10、推广中 614、申请未通过 373、合作已终止 23、合作已到期 7；后台合计 1027。

本地接口 `/api/colonel/activities/3916506/products` 与 `product_snapshot` 一致：

| 状态 | 后台期望 | 本地 API/DB | 差异 |
| --- | ---: | ---: | ---: |
| 待审核 | 10 | 10 | 0 |
| 推广中 | 614 | 726 | +112 |
| 申请未通过 | 373 | 502 | +129 |
| 合作已终止 | 23 | 46 | +23 |
| 合作已到期 | 7 | 6 | -1 |
| 合计 | 1027 | 1290 | +263 |

上游只读全量分页探针 `/api/douyin/activity-product-list?activityId=3916506&count=20`：

- 翻页结果：65 页，原始 1297 行，去重商品 1290 个，停止原因 `DONE_NO_MORE_CURSOR`。
- 重复商品：7 个，均为 `2:申请未通过` 重复行。
- 按商品 ID 去重后的上游状态分布：待审核 10、推广中 726、申请未通过 502、合作已终止 46、合作已到期 6。
- 去重后上游分布与本地 API/DB 完全一致。
- 注意：该联调探针当前没有 `status` 入参，代码中传给网关的 `status` 为 `null`，因此不能用它验证“上游按状态筛选”结果。

阶段性结论：3916506 当前本地 API、`product_snapshot`、上游全量去重结果三者一致，均为 1290 个唯一商品；与用户提供的后台 1027 个不一致。当前证据不支持“本地同步少拉”或“前端状态过滤错误”作为根因；剩余关键差异是后台页面/导出口径与 `alliance.colonelActivityProduct` 全量 API 口径不一致，仍需后台截图筛选条件或后台导出 product_id 明细做集合差异。

## 前端页面核验：/product/manage/products

用户问题：商品管理页 `http://localhost:3001/product/manage/products` 显示与实际商品信息不同步。

页面与接口证据：

- 浏览器打开 `/product/manage/products` 后 Network 实际请求 `/api/products?page=1&size=5`，不是活动商品全量接口。
- `/api/products?page=1&size=20` 返回 `total=14713`，这是商品推进池 / 商品库展示口径。
- `/api/products?page=1&size=20&recruitActivityId=3916506` 返回 `total=671`。
- `/api/colonel/activities/3916506/products?count=20&status=1` 返回 `total=726`。
- 代码证据：`frontend/src/views/product/index.vue` 在 `/product/manage/products` 且未选择活动时走 `getProducts('/products')`；后端 `ProductController` 标注为旧兼容商品库接口。

SQL 口径：

- 全量 `product_snapshot` 去重活动商品事实：53140。
- 上游推广中事实：17734。
- `selected_to_library=true`：17734。
- `display_status='DISPLAYING'` 且未暂停、未本地拒绝、上游推广中：14713。
- 3916506 推广中 726，其中商品管理页可见 671。
- 3916506 被隐藏的 55 条均已同步并入库：`REPLACED_BY_HIGHER_PRIORITY` 42 条，`REPLACED_BY_ADVANTAGE` 13 条。

阶段性结论：前端页面不是未同步，而是页面口径不同。`/product/manage/products` 展示“商品推进池 / 共享商品库可见关系”，只显示上游推广中、已入库、未暂停、未本地拒绝且展示规则选中的关系；活动商品全量事实应以 `/api/colonel/activities/{activityId}/products` 为准。若业务期望该页面展示活动商品全量，需要产品确认页面定位并改接口口径，不能只做前端兜底。

## 缺失商品明细
- 未生成。原因：未取得后台 CSV 或可信上游全量 product_id 集合，不能构造 `backend_activity_products` 临时表。

## 本地多余商品明细
- 未生成。原因同上；没有后台 product_id 集合时不能判断本地多余。

## 根因判断
- A 同步分页漏拉：未证实。多数历史同步状态为 `SUCCESS/DONE_NO_MORE`；但 6 月 22 日部分活动缺少可追溯同步日志，仍需后台明细或深度 dry-run 复核。
- B 同步接口失败后未重试：未证实。job_log 有历史 dry-run `API_ERROR`，但不能证明真实写库链路当前失败。
- C 快照 upsert 覆盖错误：当前证据不支持。唯一键为 `(activity_id, product_id)`，本地重复行 0。
- D activity_id/product_id 关联错误：当前证据不支持。接口 total 与快照 count 一致；仍需后台 product_id 明细做集合差异。
- E 前端接口二次过滤：当前证据不支持。管理员无筛选接口 total 与快照数量 24/24 一致。
- F 权限范围过滤导致少展示：本轮管理员接口验证不支持该结论；非 admin 角色仍可能受活动分配权限影响，未作为本次主口径。
- G 后台导出口径和本地口径不同：未验证。缺少后台导出字段与筛选条件。

## 修复建议
- 临时止血：不要直接补数据；先提供后台 CSV，字段至少包含 `activity_id,product_id,status,status_text`，再执行临时表 anti-join。
- 根因修复：若后台 CSV 证明确有缺失，只对差异 activity_id 执行只读 deep dry-run，保存将新增/更新的 product_id 清单；用户确认后才允许 dryRun=false。
- 长期治理：把后台导出对账或上游全量 dry-run 做成限流、分批、可恢复的只读对账任务，输出 activity/product 级差异报告。

## 验证清单
- DB 只读查询：已执行。
- 本地接口 total：已执行，24/24 与快照一致。
- 后台 CSV 临时表对账：BLOCKED，未提供 CSV。
- product_id 集合差异：BLOCKED，缺后台全量集合。
- 构建/重启：未执行；本轮无代码修改且用户明确禁止重启 real-pre/生产容器。
- Harness limits：PASS。

## Retro Summary
- 本次无需 Harness 升级；缺口在外部后台明细证据，不在执行框架。
