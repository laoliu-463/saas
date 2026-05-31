# Dashboard 13 秒性能分析

日期：2026-05-18
分析：Hermes Agent（架构评估视角）
文件：runtime/qa/out/e2e-base-smoke-20260518-203042/api-responses/04-dashboard-summary.json

---

## 现象

```
GET /api/dashboard/summary 耗时 12789ms（13 秒）
其他接口：27ms / 284ms / 20ms
```

Dashboard 数据（test 环境）：
- 订单总量：7152
- 归因成功：0（0%）
- 未归因原因：UPSTREAM_PRODUCT_UNCOVERED（7078条）

---

## 根因

`DashboardService.getSummary()` 执行 **8 次顺序查询**，其中最慢的一次是 `loadActivityProductBreakdown()` 的活动商品下钻 SQL。

核心问题：N+1 相关子查询

```sql
-- 查出 20 个商品，但每行内嵌 4 个子查询
SELECT
  co.activity_id, co.product_id,
  COALESCE((SELECT COUNT(*) FROM pick_source_mapping psm WHERE ...), 0) AS mapping_count,   -- 子查询 1
  COALESCE((SELECT COUNT(*) FROM promotion_link pl WHERE ...), 0) AS promotion_link_count  -- 子查询 2
FROM colonelsettlement_order co
LEFT JOIN product_snapshot ps ON ...
LEFT JOIN product_operation_state pos ON ...
LEFT JOIN sys_user su ON ...
GROUP BY co.activity_id, co.product_id
LIMIT 20 OFFSET 0
```

20 行结果 × 4 个相关子查询 = 约 80 次隐性查询，全部打在 `pick_source_mapping`、`promotion_link`、`product_snapshot`、`product_operation_state` 四张表上。

其他慢查询：
- `diagnosticSql()`：CTE + CASE WHEN 嵌套，在 7152 条订单上逐行评估归因原因
- `activityProductBreakdownSql()`：4 表 JOIN + 4 个子查询，每次 GROUP BY 全表扫描

---

## 耗时分布（推测）

```
查询 1-6（基础聚合）：         ~200ms
diagnosticSql（CTE）：          ~1-2s
activityProductCountSql：       ~500ms
activityProductBreakdownSql：   ~10s+  ← 主要瓶颈
────────────────────────────────────
总计：                          ~13s
```

---

## 判断

| 维度 | 结论 |
|---|---|
| 是否 bug | 否，架构性 N+1 查询问题 |
| V1 是否需要修 | 否，V1 可接受 |
| V2 是否必须修 | 是，7 万量级会导致超时 |
| 修复方向 | 预聚合表 / Redis 缓存 / 改 JOIN 消除子查询 |

---

## 关联问题

Dashboard 展示 7152 条订单 0% 归因，根因是 `mappingCount=0`（没有推广链接生成记录），不是归因逻辑错误。需要业务方在测试环境真正走一遍"选品→转链→分享→下单"链路才能验证归因。

---

## 后续动作（V2）

- [ ] 预聚合表：在订单入库时提前写入 `order_product_summary`
- [ ] 缓存：Redis 缓存 Dashboard 结果，设置 5 分钟 TTL
- [ ] SQL 重构：将相关子查询改写为 JOIN，预计算 mapping_count
- [ ] 监控：接入 Prometheus，记录 `/api/dashboard/summary` 耗时，超过 5s 告警
