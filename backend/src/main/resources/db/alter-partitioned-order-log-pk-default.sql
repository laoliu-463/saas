-- =============================================
-- 分区表：订单 / 操作日志 — id 默认值与主键（棕地一次执行）
-- =============================================
-- 背景：
--   - PostgreSQL 分区表的主键必须包含分区键；故为复合主键 (id, create_time)，不是「id 单独主键」。
--   - 部分建模工具会误报「id 无主键」；库层面以 contype='p' 的复合约束为准。
-- 本脚本：
--   - 为 colonelsettlement_order.id 补齐 DEFAULT gen_random_uuid() 与 NOT NULL（与 init-db 绿场一致）。
--   - 若表上尚无任何 PRIMARY KEY，则添加命名约束 pk_cso / pk_ol（已存在则跳过）。

-- --- colonelsettlement_order ---
ALTER TABLE colonelsettlement_order
    ALTER COLUMN id SET DEFAULT gen_random_uuid();

UPDATE colonelsettlement_order SET id = gen_random_uuid() WHERE id IS NULL;

ALTER TABLE colonelsettlement_order
    ALTER COLUMN id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON c.conrelid = t.oid
        JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE n.nspname = current_schema()
          AND t.relname = 'colonelsettlement_order'
          AND c.contype = 'p'
    ) THEN
        ALTER TABLE colonelsettlement_order
            ADD CONSTRAINT pk_cso PRIMARY KEY (id, create_time);
    END IF;
END $$;

-- --- operation_log（绿场通常已有 pk_ol；棕地若仅有匿名主键可跳过或手工核对）---
ALTER TABLE operation_log
    ALTER COLUMN id SET DEFAULT gen_random_uuid();

UPDATE operation_log SET id = gen_random_uuid() WHERE id IS NULL;

ALTER TABLE operation_log
    ALTER COLUMN id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON c.conrelid = t.oid
        JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE n.nspname = current_schema()
          AND t.relname = 'operation_log'
          AND c.contype = 'p'
    ) THEN
        ALTER TABLE operation_log
            ADD CONSTRAINT pk_ol PRIMARY KEY (id, create_time);
    END IF;
END $$;
