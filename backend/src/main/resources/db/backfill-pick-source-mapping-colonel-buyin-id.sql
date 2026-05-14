-- 用真实订单 raw payload 中的 19 位 colonel_buyin_id 回填已有活动商品映射。
-- 该数据回填与 schema 变更分离，便于 real-pre 重放、排错和审计。

UPDATE pick_source_mapping psm
SET colonel_buyin_id = source.colonel_buyin_id
FROM (
    SELECT
        product_id,
        colonel_activity_id AS activity_id,
        MIN(extra_data ->> 'colonel_buyin_id') AS colonel_buyin_id
    FROM colonelsettlement_order
    WHERE deleted = 0
      AND extra_data ? 'colonel_buyin_id'
      AND product_id IS NOT NULL
      AND colonel_activity_id IS NOT NULL
    GROUP BY product_id, colonel_activity_id
) source
WHERE psm.deleted = 0
  AND psm.colonel_buyin_id IS NULL
  AND psm.product_id = source.product_id
  AND psm.activity_id = source.activity_id;
