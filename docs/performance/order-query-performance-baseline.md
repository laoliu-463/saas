# 订单查询性能基线

日期：2026-06-21
环境：本地工作区，默认 real-pre 口径
分支：`feature/ddd/DDD-VERIFY-001`
commit：`5a399fd7`（本阶段扫描基点，最终 evidence 以收口提交为准）

## 扫描边界

- 本阶段围绕订单查询性能小切片，未修改真实数据，未执行索引 SQL。
- `git status --short` 显示未提交改动集中在订单查询 Java、测试、`application.yml` 与 `docs/performance/**`，本任务不得覆盖无关文件或回滚他人变更。
- 已使用 code-review-graph MCP 获取最小上下文，风险评分为 medium，关键节点为 `OrderController/getOrders/getStats` 与 `OrderDerivedCacheKeys`；源码细节用 `rg` 与定向文件读取补充。

## 调用链

### `GET /orders`

1. `OrderController.getOrders`
2. 默认路径：`OrderService.buildWrapper` 拼装查询条件
3. `OrderService.selectOrderListColumns` 排除 `extra_data`
4. `OrderService.applyDataScope`
5. `ColonelsettlementOrderMapper.selectPage`
6. `OrderService.normalizeOrderRow`
7. `OrderService.enrichOrderList`
8. `OrderListAssembler.toView`

当 `ddd.refactor.enabled=true` 且 `ddd.refactor.order-application.enabled=true` 时，入口改为 `orderDomainFacade.getOrders`；默认配置为关闭。

### `GET /orders/stats`

1. `OrderController.getStats`
2. 默认路径构造两次 `QueryWrapper`
3. 第一次 `selectMaps`：按 `attribution_status` group by
4. 第二次 `selectMaps`：未归因记录按 `attribution_remark` group by
5. 组装 `OrderStats`

DDD 开关开启时委托 `LegacyOrderDomainFacade.getStats`，逻辑同样是两次 group by 查询。

### `GET /orders/filter-options`

1. `OrderController.getFilterOptions`
2. 使用 `ShortTtlCacheService`，key 前缀 `orders:filter-options:`
3. 依次执行 distinct 查询：
   - `order_status`
   - `attribution_status`
   - `attribution_remark`
   - `product_id/product_name`
   - `channel_user_name`
   - `colonel_user_name`
4. 部门选项来自 `UserDomainFacade.listDepartments`

### `GET /orders/{orderId}`

1. `OrderController.getOrderDetail`
2. 默认路径：`OrderQueryService.getOrderDetail`
3. `OrderQueryService.findOrderDetailRow` 使用 `JdbcTemplate.queryForList`
4. 主 SQL 从 `colonelsettlement_order` 查询，左连接 `pick_source_mapping`、`promotion_link`、`product_operation_state`、`sys_user`、`product_snapshot`、`talent`、`crawler_talent_info`
5. 详情服务内按 `DataScope` 校验 `user_id/dept_id`

## 当前查询方式

| 接口 | 查询方式 | 主要过滤 | 排序 / 聚合 |
| --- | --- | --- | --- |
| `/orders` | MyBatis-Plus `selectPage` | `deleted=0`、订单号、归因状态、活动、商品、人员关键词、订单状态、时间、部门、数据范围 | `update_time DESC, create_time DESC` |
| `/orders/stats` | `selectMaps` 两次聚合 | 与列表筛选基本一致 | `group by attribution_status`、`group by attribution_remark` |
| `/orders/filter-options` | 多次 distinct | 关键词、数据范围 | 每项 distinct 后 limit |
| `/orders/{orderId}` | 手写 SQL | `co.order_id = ?`、详情内存权限校验 | `co.create_time DESC limit 1` |

## 已有订单相关索引

源码扫描范围：`backend/src/main/resources/db/**`。

| 索引 | 来源 | 说明 |
| --- | --- | --- |
| `idx_cso_deleted` | `alter-db-performance-contract-20260521.sql`、`migrate-all.sql` | `deleted` 单列 |
| `idx_cso_user_create_time` | `alter-db-performance-contract-20260521.sql`、`migrate-all.sql` | `(user_id, create_time DESC) where deleted=0` |
| `idx_cso_dept_create_time` | `alter-db-performance-contract-20260521.sql`、`migrate-all.sql` | `(dept_id, create_time DESC) where deleted=0` |
| `idx_cso_attribution_status` | `alter-order-attribution-mvp.sql`、`migrate-all.sql` | `attribution_status` 单列 |
| `idx_cso_attribution` | `migrate-all.sql` | `(attribution_status, create_time DESC) where attribution_status='ATTRIBUTED'` |
| `idx_cso_talent_id` | `alter-order-attribution-mvp-v2.sql`、`alter-order-talent-id.sql` | `talent_id` |
| `idx_cso_channel_user_id` | `alter-cso-sample-attribution-indexes.sql` | `channel_user_id where deleted=0` |
| `idx_cso_colonel_user_id` | `alter-cso-sample-attribution-indexes.sql` | `colonel_user_id where deleted=0` |
| `idx_cso_pay_time` | `alter-order-pay-time.sql`、`create-colonel-order-settlement.sql` | `pay_time` |
| `idx_cso_order_create_time` | `alter-order-pay-time.sql`、`create-colonel-order-settlement.sql` | `order_create_time` |
| `idx_cso_pick_source` | `migrate-all.sql` | `pick_source where deleted=0` |
| `idx_cso_colonel_id` | `migrate-all.sql` | `colonel_buyin_id where deleted=0` |

相关 join 索引：

- `promotion_link`: `idx_pl_product_id`、`idx_pl_pick_source`、`idx_pl_channel_user`
- `pick_source_mapping`: `idx_psm_scene`、`idx_psm_colonel_id`、`idx_psm_pick_source`、`idx_psm_colonel_buyin_id`
- `product_snapshot`: `idx_product_snapshot_activity`、`idx_product_snapshot_product`、`idx_product_snapshot_deleted`

`alter-cso-sample-attribution-indexes.sql` 明确说明 `colonelsettlement_order` 是 PostgreSQL 分区父表，父表建索引会落到子分区，新建分区需注意继承和同名索引冲突。

## 慢查询风险点

- MyBatis-Plus `selectPage` 默认会额外执行 count；深分页或宽筛选时 count 成本可能高。
- `/orders` 排序为 `order by update_time desc, create_time desc`，现有索引主要覆盖 `create_time`，缺少默认排序组合索引。
- `channelKeyword` / `colonelKeyword` 对姓名和 UUID 字段均使用 LIKE，完整 UUID 查询无法使用现有 `channel_user_id` / `colonel_user_id` 等值索引。
- `/orders/stats` 每次请求做两次 group by 扫描，当前没有 stats 短 TTL 缓存。
- `/orders/filter-options` 需要多次 distinct；虽已有短 TTL 缓存，但冷启动/缓存失效后仍会多次扫订单表。
- `OrderService.enrichOrderList` 当前先调用 `enrichOrderProductInfo`，再调用 `enrichOrderListExtras`，两条链路都会调用 `loadDisplayProductInfo`，当前页 display product info 可能重复加载。
- `/orders/{orderId}` 详情 SQL 对 `promotion_link` 使用 OR JOIN：`pl.id = co.promotion_link_id OR pl.id = psm.promotion_link_id OR pl.pick_source = co.pick_source`，可能导致 planner 难以稳定使用单一路径索引。

## 阶段性结论

当前不能仅凭源码给出真实耗时结论，仍缺少 real-pre 的 `EXPLAIN (ANALYZE, BUFFERS)`、慢查询日志和接口响应时间采样。基于源码可确认的低风险优化方向是：减少当前页重复补字段查询、为完整 UUID 关键词增加等值快路径、补充只提交不执行的索引 SQL、为 stats/default window 增加默认关闭配置，并将深分页和详情 OR JOIN 改造先做设计。
