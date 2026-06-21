# 订单详情查询优化设计

日期：2026-06-21
状态：设计文档，不直接改详情 SQL。

## 当前 SQL 证据

源码位置：`backend/src/main/java/com/colonel/saas/service/OrderQueryService.java`

`findOrderDetailRow` 当前主查询从 `colonelsettlement_order co` 出发：

```sql
FROM colonelsettlement_order co
LEFT JOIN pick_source_mapping psm
  ON psm.pick_source = co.pick_source
LEFT JOIN promotion_link pl
  ON (
    (co.promotion_link_id IS NOT NULL AND pl.id = co.promotion_link_id)
    OR (psm.promotion_link_id IS NOT NULL AND pl.id = psm.promotion_link_id)
    OR (co.pick_source IS NOT NULL AND pl.pick_source = co.pick_source)
  )
...
WHERE co.order_id = ?
ORDER BY co.create_time DESC
LIMIT 1
```

## OR JOIN 风险

- `promotion_link` 的 join 条件包含三个 OR 分支，planner 可能无法稳定选择 `pl.id` 或 `pl.pick_source` 单一路径。
- 当 `pick_source_mapping` 多行命中或 `promotion_link` 多路径命中时，join 结果可能膨胀，再依赖 `ORDER BY co.create_time DESC LIMIT 1` 收口。
- 详情接口是按 `order_id` 查单条，理论上应先锁定订单行，再选择一个最优 promotion link，而不是让 OR JOIN 参与主查询路径。

## LATERAL JOIN 改造方案

先用 `order_id` 定位最新订单行，再用 `LEFT JOIN LATERAL` 按优先级取一条推广链接：

```sql
WITH target_order AS (
  SELECT *
  FROM colonelsettlement_order
  WHERE deleted = 0
    AND order_id = :orderId
  ORDER BY create_time DESC
  LIMIT 1
)
SELECT ...
FROM target_order co
LEFT JOIN pick_source_mapping psm
  ON psm.pick_source = co.pick_source
LEFT JOIN LATERAL (
  SELECT pl.*
  FROM promotion_link pl
  WHERE (co.promotion_link_id IS NOT NULL AND pl.id = co.promotion_link_id)
     OR (psm.promotion_link_id IS NOT NULL AND pl.id = psm.promotion_link_id)
     OR (co.pick_source IS NOT NULL AND pl.pick_source = co.pick_source)
  ORDER BY
    CASE
      WHEN pl.id = co.promotion_link_id THEN 1
      WHEN pl.id = psm.promotion_link_id THEN 2
      WHEN pl.pick_source = co.pick_source THEN 3
      ELSE 9
    END,
    pl.created_at DESC
  LIMIT 1
) pl ON true;
```

更激进的方案是把三个分支改为 `UNION ALL` 后排序取一条，减少 OR 条件：

```sql
LEFT JOIN LATERAL (
  SELECT * FROM promotion_link WHERE id = co.promotion_link_id
  UNION ALL
  SELECT * FROM promotion_link WHERE id = psm.promotion_link_id
  UNION ALL
  SELECT * FROM promotion_link WHERE pick_source = co.pick_source
  ORDER BY created_at DESC
  LIMIT 1
) pl ON true
```

实际落地前必须补优先级字段，防止 `UNION ALL` 改变旧逻辑的匹配优先级。

## 所需索引

已有：

- `idx_pl_pick_source ON promotion_link(pick_source)`
- `promotion_link` 主键 `id`
- `idx_psm_pick_source` 或同类 pick_source 索引

建议补充：

```sql
CREATE INDEX IF NOT EXISTS idx_cso_active_order_id_create
ON colonelsettlement_order (order_id, create_time DESC)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_psm_pick_source_promotion_link
ON pick_source_mapping (pick_source, promotion_link_id)
WHERE deleted = 0 AND pick_source IS NOT NULL;
```

## 暂不直接改代码原因

- 当前详情 SQL 同时组装订单、推广映射、商品快照、达人和寄样信息，回归面较大。
- 现有 `OrderQueryServiceTest` 覆盖 DTO 字段和权限，但没有覆盖 SQL 计划、重复 promotion link 命中优先级和真实数据 join 基数。
- 本阶段先提交设计和 EXPLAIN 模板；只有拿到 real-pre 的 `EXPLAIN (ANALYZE, BUFFERS)` 后，再决定是否落地 LATERAL/UNION ALL。
