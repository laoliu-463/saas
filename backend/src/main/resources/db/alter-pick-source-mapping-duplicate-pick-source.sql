-- 抖店真实转链场景下，同一个 pick_source 可能被上游复用到多个活动商品。
-- 不能再把 pick_source 作为全局唯一键，否则后一次转链会覆盖前一次映射。

DROP INDEX IF EXISTS uk_psm_pick_source;

CREATE INDEX IF NOT EXISTS idx_psm_pick_source
    ON pick_source_mapping(pick_source);

CREATE UNIQUE INDEX IF NOT EXISTS uk_psm_pick_source_product_activity_user
    ON pick_source_mapping(pick_source, product_id, activity_id, user_id)
    WHERE deleted = 0;
