# 订单查询性能优化报告

日期：2026-06-21
环境：本地 real-pre
分支：`feature/ddd/DDD-VERIFY-001`
代码提交：`d714ca8a`
Retro 提交：`5ef2b8f2`

## 一、优化范围

本次只优化订单查询相关接口的服务端查询路径和可选配置：

- `GET /orders`
- `GET /orders/unattributed`
- `GET /orders/stats`
- `GET /orders/filter-options` 的派生缓存失效联动
- `GET /orders/{orderId}` 只做设计分析，未改详情 SQL

未改变 API 入参结构、响应结构、订单归因规则、提成计算规则、权限规则或状态机。

## 二、已落地改动

1. `/orders` 和 `/orders/unattributed` 统一委托 `OrderService.findPage`，避免控制器与 DDD legacy facade 各自维护一份 wrapper 拼装逻辑。
2. 当前页订单的展示商品信息只加载一次，再复用于商品展示字段和列表 extras，降低同页重复 mapper 查询风险。
3. `channelKeyword` / `colonelKeyword` 对完整 UUID 增加等值快路径：
   - 完整 UUID：走 `channel_user_id = ?` 或 `colonel_user_id = ?`
   - 非完整 UUID、中文、英文：保留原 LIKE 行为
4. 新增默认关闭的订单默认时间窗：
   - `app.order-query.default-window-enabled=false`
   - `app.order-query.default-window-days=30`
   - 只有开启、未传 `orderId`、未传显式起止时间时才追加最近 N 天限制
5. `/orders/stats` 新增默认关闭的短 TTL 缓存：
   - `app.order-query.stats-cache-enabled=false`
   - `app.order-query.stats-cache-ttl-seconds=60`
   - cache key 包含用户、部门、数据范围和所有筛选条件
6. 订单同步和测试数据变更后，新增驱逐 `orders:stats:` 派生缓存。
7. 提供安全索引 SQL 文档，不自动执行数据库变更。

## 三、文档与设计

- 基线文档：`docs/performance/order-query-performance-baseline.md`
- 索引 SQL：`docs/performance/sql/order-query-indexes.sql`
- 游标分页设计：`docs/performance/order-query-cursor-pagination-design.md`
- 详情查询 OR JOIN 优化设计：`docs/performance/order-detail-query-optimization.md`

索引 SQL 只是建议脚本，尚未在 real-pre 执行。详情查询和游标分页只形成设计，不计入已上线功能。

## 四、验证结果

已通过：

- `mvn "-Dtest=OrderServiceTest,OrderControllerTest,OrderQueryServiceTest,DddOrder003RoutingTest" test`
  - 83 tests, 0 failures, 0 errors
- `mvn test`
  - 2503 tests, 0 failures, 0 errors, 3 skipped
- `mvn clean package -DskipTests`
  - BUILD SUCCESS
- `agent-do.ps1 -Env real-pre -Scope full -ContentMaintenance off`
  - 后端构建 PASS
  - 前端构建 PASS
  - Docker real-pre backend/frontend 已重建并重启
  - 本地健康检查 PASS
  - `npm run e2e:real-pre:p0:preflight` PASS

Evidence：`harness/reports/evidence-20260621-210019.md`
Retro：`harness/reports/retro-20260621-210058.md`

## 五、风险与回滚

低风险点：

- 默认时间窗关闭，默认 stats 缓存关闭，生产行为默认保持原样。
- 完整 UUID 快路径只收窄原来 UUID 精确关键词的执行计划，不改变非 UUID 关键词语义。
- 列表商品信息加载复用不改变字段优先级：display info > snapshot > product fallback。

剩余风险：

- 索引 SQL 未执行，数据库执行计划收益尚未通过 `EXPLAIN ANALYZE` 证明。
- `/orders/stats` 缓存默认关闭；开启前需要在 real-pre 观察缓存命中、失效和跨数据范围隔离。
- 详情 OR JOIN 和游标分页只是设计，深分页和详情慢查询仍需后续专项验证。
- 前端 `npm ci` 报告 5 个依赖漏洞，非本次订单查询优化引入，但仍是独立治理项。

回滚方式：

- 回滚 `d714ca8a` 可撤销本次代码和性能文档变更。
- 若只需停用新能力，保持或恢复以下默认值即可：
  - `APP_ORDER_QUERY_STATS_CACHE_ENABLED=false`
  - `APP_ORDER_QUERY_DEFAULT_WINDOW_ENABLED=false`

## 六、阶段性结论

本次低风险代码优化已通过订单定向测试、后端全量测试、前后端构建、real-pre 容器重启、健康检查和 P0 preflight。由于索引 SQL、详情 SQL 重写和游标分页未执行，本轮结论仅覆盖已落地的服务端查询路径优化，不覆盖数据库执行计划收益结论。
