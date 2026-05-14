-- 用 promotion_link 回填被 pick_source 覆盖掉的归因映射。
-- 只补缺少的 (pick_source, product_id, activity_id, channel_user_id) 组合，不覆盖已有记录。

INSERT INTO pick_source_mapping (
    id,
    user_id,
    short_id,
    uuid_seed,
    dept_id,
    pick_source,
    product_id,
    activity_id,
    promotion_link_id,
    channel_user_name,
    talent_id,
    talent_name,
    source_url,
    converted_url,
    pick_extra,
    scene,
    valid_from,
    valid_until,
    status,
    deleted,
    create_time,
    update_time
)
SELECT
    gen_random_uuid(),
    pl.channel_user_id,
    UPPER(SUBSTRING(REPLACE(pl.id::text, '-', '') FROM 1 FOR 10)),
    NULL,
    su.dept_id,
    pl.pick_source,
    pl.product_id,
    pl.activity_id,
    pl.id,
    pl.channel_user_name,
    pl.talent_id,
    pl.talent_name,
    pl.original_product_url,
    pl.promotion_url,
    pl.pick_extra,
    'PRODUCT_LIBRARY',
    COALESCE(pl.created_at, NOW()),
    COALESCE(pl.created_at, NOW()) + INTERVAL '3 months',
    1,
    0,
    COALESCE(pl.created_at, NOW()),
    COALESCE(pl.updated_at, pl.created_at, NOW())
FROM promotion_link pl
LEFT JOIN sys_user su
    ON su.id = pl.channel_user_id
   AND su.deleted = 0
WHERE pl.deleted = 0
  AND pl.pick_source IS NOT NULL
  AND pl.channel_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM pick_source_mapping psm
      WHERE psm.deleted = 0
        AND psm.pick_source = pl.pick_source
        AND COALESCE(psm.product_id, '') = COALESCE(pl.product_id, '')
        AND COALESCE(psm.activity_id, '') = COALESCE(pl.activity_id, '')
        AND psm.user_id = pl.channel_user_id
  );
