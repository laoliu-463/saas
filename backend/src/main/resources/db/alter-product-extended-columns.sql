-- 商品表补充字段（原 test 专用 schema 独有，现统一到主链路）
-- 执行前置：init-db.sql
-- 说明：这些字段供 TestDataService 种子数据使用

ALTER TABLE product
    ADD COLUMN IF NOT EXISTS price BIGINT,
    ADD COLUMN IF NOT EXISTS category VARCHAR(100),
    ADD COLUMN IF NOT EXISTS activity_id UUID;

UPDATE product
SET price = COALESCE(price, discount_price, market_price)
WHERE price IS NULL;
