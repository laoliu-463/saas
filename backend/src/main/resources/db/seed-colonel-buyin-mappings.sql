-- 种子数据：为抖店原生团长订单归因创建 pick_source_mapping 记录
-- 每个 colonel_buyin_id 仅创建 1 条记录（避免 tier3 AMBIGUOUS）
-- 使用 admin 用户作为默认归因对象

INSERT INTO pick_source_mapping (
    id, short_id, pick_source, pick_extra,
    activity_id, product_id,
    scene, status, user_id, dept_id,
    valid_from, valid_until,
    colonel_buyin_id,
    deleted, create_time, update_time
)
SELECT
    gen_random_uuid(),
    'CB' || LEFT(buyin_id, 8),
    'colonel_native_' || buyin_id,
    NULL,
    NULL, NULL,
    'COLONEL_NATIVE', 1,
    (SELECT id FROM sys_user WHERE username = 'admin' AND deleted = 0 LIMIT 1),
    NULL,
    '2025-01-01'::timestamp, '2099-12-31'::timestamp,
    buyin_id,
    0, NOW(), NOW()
FROM (VALUES
    ('7351155267604218149'),
    ('7345890512227811619'),
    ('7622387250219827506'),
    ('7349597984361611561'),
    ('7341320980353073418'),
    ('7108286947231105312'),
    ('7109679864001364265'),
    ('7350227679947440424')
) AS t(buyin_id)
WHERE NOT EXISTS (
    SELECT 1 FROM pick_source_mapping psm
    WHERE psm.colonel_buyin_id = t.buyin_id AND psm.deleted = 0
);

-- 同时把 colonel_buyin_id 回填到已有的映射记录（如果需要）
-- UPDATE pick_source_mapping SET colonel_buyin_id = '...' WHERE pick_source = '...';

-- 验证
-- SELECT id, short_id, pick_source, colonel_buyin_id, scene, user_id FROM pick_source_mapping WHERE colonel_buyin_id IS NOT NULL;
