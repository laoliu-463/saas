-- =============================================
-- M6/M7/M8 归因与独家达人统计 — 索引补齐
-- =============================================
-- 前置建议（列须已存在）：
--   - colonel_user_id / talent_id：alter-order-attribution-mvp-v2.sql
--   - pick_extra 列与长度：alter-order-attribution-mvp-v2.sql（mapping 表）
--
-- 说明：
--   1) PostgreSQL 分区表在父表上 CREATE INDEX 会落到各子分区（PG11+）。
--   2) pick_source_mapping.pick_extra 索引已放在 alter-order-pick-source-length.sql，
--      本脚本不重复创建，避免维护两份。
--   3) colonelsettlement_order 无 pick_extra 列；pick_extra 在订单侧落在 extra_data JSONB，
--      若需表达式索引可另开脚本（按实际 SQL 查询编写）。

-- --- M6：订单分区父表索引（渠道 / 招商负责人）---
-- talent_id 索引：alter-order-attribution-mvp-v2.sql 已含 idx_cso_talent_id（勿在本文件重复同名索引，避免定义不一致）
CREATE INDEX IF NOT EXISTS idx_cso_channel_user_id
    ON colonelsettlement_order (channel_user_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_cso_colonel_user_id
    ON colonelsettlement_order (colonel_user_id)
    WHERE deleted = 0;

-- --- M8：寄样单 — ExclusiveTalent 月度统计（ship_time 范围 + GROUP BY channel_user_id, talent_uid）---
CREATE INDEX IF NOT EXISTS idx_sr_channel_talent_ship_time
    ON sample_request (channel_user_id, talent_uid, ship_time)
    WHERE deleted = 0
      AND channel_user_id IS NOT NULL
      AND talent_uid IS NOT NULL
      AND ship_time IS NOT NULL;

-- =============================================
-- 同步：下月分区创建函数与 init-db.sql 一致
-- colonelsettlement_order 仅在父表建索引（PG11+ 新分区自动继承）；此处只 CREATE PARTITION，避免
-- 与子分区重复建同名索引导致 schema 内冲突。
-- =============================================
CREATE OR REPLACE FUNCTION create_next_month_partitions()
RETURNS void AS $$
DECLARE
    next_month_start DATE;
    next_month_end   DATE;
    month_str        VARCHAR(7);
    cso_part_name    VARCHAR(20);
    ol_part_name     VARCHAR(20);
BEGIN
    next_month_start := date_trunc('month', CURRENT_DATE + INTERVAL '1 month');
    next_month_end   := date_trunc('month', CURRENT_DATE + INTERVAL '2 months');
    month_str        := to_char(next_month_start, 'YYYY_MM');
    cso_part_name    := 'cso_'  || month_str;
    ol_part_name     := 'op_log_' || month_str;

    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF colonelsettlement_order FOR VALUES FROM (%L) TO (%L)',
        cso_part_name, next_month_start, next_month_end
    );

    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF operation_log FOR VALUES FROM (%L) TO (%L)',
        ol_part_name, next_month_start, next_month_end
    );

    RAISE NOTICE 'Created partitions: %, %', cso_part_name, ol_part_name;
END;
$$ LANGUAGE plpgsql;
