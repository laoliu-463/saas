-- legacy colonel_activity_product 收缩为纯关联事实表
-- 同时把审核/入库/负责人等操作状态统一回填到 product_operation_state
-- 执行前置：init-db.sql、alter-product-main-chain.sql、alter-product-biz-status.sql

ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS biz_status VARCHAR(64);

ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS selected_to_library BOOLEAN DEFAULT FALSE;

ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS selected_at TIMESTAMP;

ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS selected_by UUID;

ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS pinned_at TIMESTAMP;

ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS pinned_until TIMESTAMP;

ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS audit_payload TEXT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'colonel_activity_product'
          AND column_name = 'assignee_id'
    ) THEN
        EXECUTE $sql$
            UPDATE colonel_activity_product
            SET extra_data = COALESCE(extra_data, '{}'::jsonb)
                || jsonb_build_object(
                    'legacyActivityProduct',
                    jsonb_strip_nulls(jsonb_build_object(
                        'activityName', activity_name,
                        'title', title,
                        'price', price,
                        'cosRatio', cos_ratio,
                        'cosFee', cos_fee,
                        'serviceRatio', service_ratio,
                        'shopName', shop_name,
                        'activityStartTime', activity_start_time,
                        'activityEndTime', activity_end_time,
                        'promotionStartTime', promotion_start_time,
                        'promotionEndTime', promotion_end_time,
                        'monthsOfProtection', months_of_protection,
                        'cover', cover,
                        'detailUrl', detail_url,
                        'firstCid', first_cid,
                        'secondCid', second_cid,
                        'thirdCid', third_cid,
                        'sampleRequirement', sample_requirement,
                        'promotionInfo', promotion_info,
                        'auditStatus', audit_status,
                        'auditTime', audit_time,
                        'auditRemark', audit_remark,
                        'minReferAmount', min_refer_amount
                    ))
                )
            WHERE (extra_data IS NULL OR NOT (extra_data ? 'legacyActivityProduct'))
              AND (
                    activity_name IS NOT NULL
                 OR title IS NOT NULL
                 OR price IS NOT NULL
                 OR cos_ratio IS NOT NULL
                 OR cos_fee IS NOT NULL
                 OR service_ratio IS NOT NULL
                 OR shop_name IS NOT NULL
                 OR activity_start_time IS NOT NULL
                 OR activity_end_time IS NOT NULL
                 OR promotion_start_time IS NOT NULL
                 OR promotion_end_time IS NOT NULL
                 OR months_of_protection IS NOT NULL
                 OR cover IS NOT NULL
                 OR detail_url IS NOT NULL
                 OR first_cid IS NOT NULL
                 OR second_cid IS NOT NULL
                 OR third_cid IS NOT NULL
                 OR sample_requirement IS NOT NULL
                 OR promotion_info IS NOT NULL
                 OR audit_time IS NOT NULL
                 OR audit_remark IS NOT NULL
                 OR min_refer_amount IS NOT NULL
              );
        $sql$;

        EXECUTE $sql$
            INSERT INTO product_operation_state (
                id,
                activity_id,
                product_id,
                biz_status,
                assignee_id,
                audit_status,
                audit_remark,
                audit_payload,
                selected_to_library,
                selected_at,
                selected_by,
                last_operation_at,
                deleted,
                create_time,
                update_time,
                create_by,
                update_by
            )
            SELECT
                gen_random_uuid(),
                cap.activity_id,
                cap.product_id,
                CASE
                    WHEN cap.audit_status = 2 THEN 'APPROVED'
                    WHEN cap.audit_status = 3 THEN 'REJECTED'
                    ELSE 'PENDING_AUDIT'
                END,
                cap.assignee_id,
                cap.audit_status,
                cap.audit_remark,
                CASE
                    WHEN cap.promotion_info IS NOT NULL AND cap.sample_requirement IS NOT NULL
                        THEN jsonb_strip_nulls(jsonb_build_object(
                            'legacyPromotionInfo', cap.promotion_info,
                            'legacySampleRequirement', cap.sample_requirement
                        ))::text
                    WHEN cap.promotion_info IS NOT NULL
                        THEN cap.promotion_info::text
                    WHEN cap.sample_requirement IS NOT NULL
                        THEN cap.sample_requirement::text
                    ELSE NULL
                END,
                CASE WHEN cap.audit_status = 2 THEN TRUE ELSE FALSE END,
                CASE
                    WHEN cap.audit_status = 2 THEN COALESCE(cap.audit_time, cap.update_time, cap.create_time)
                    ELSE NULL
                END,
                CASE
                    WHEN cap.audit_status = 2 THEN COALESCE(cap.update_by, cap.create_by)
                    ELSE NULL
                END,
                COALESCE(cap.audit_time, cap.update_time, cap.create_time),
                COALESCE(cap.deleted, 0),
                COALESCE(cap.create_time, CURRENT_TIMESTAMP),
                COALESCE(cap.update_time, cap.create_time, CURRENT_TIMESTAMP),
                cap.create_by,
                cap.update_by
            FROM colonel_activity_product cap
            WHERE NOT EXISTS (
                SELECT 1
                FROM product_operation_state pos
                WHERE pos.activity_id = cap.activity_id
                  AND pos.product_id = cap.product_id
            );
        $sql$;

        EXECUTE $sql$
            UPDATE product_operation_state pos
            SET assignee_id = COALESCE(pos.assignee_id, legacy.assignee_id),
                audit_status = CASE
                    WHEN pos.audit_status IS NULL OR pos.audit_status = 0 THEN COALESCE(legacy.audit_status, pos.audit_status, 0)
                    ELSE pos.audit_status
                END,
                audit_remark = COALESCE(NULLIF(pos.audit_remark, ''), legacy.audit_remark),
                audit_payload = COALESCE(NULLIF(pos.audit_payload, ''), legacy.legacy_audit_payload),
                selected_to_library = CASE
                    WHEN COALESCE(pos.selected_to_library, FALSE) THEN TRUE
                    WHEN legacy.legacy_selected_to_library THEN TRUE
                    ELSE COALESCE(pos.selected_to_library, FALSE)
                END,
                selected_at = COALESCE(
                    pos.selected_at,
                    CASE WHEN legacy.legacy_selected_to_library THEN legacy.legacy_selected_at END
                ),
                selected_by = COALESCE(
                    pos.selected_by,
                    CASE WHEN legacy.legacy_selected_to_library THEN legacy.legacy_selected_by END
                ),
                biz_status = COALESCE(
                    NULLIF(pos.biz_status, ''),
                    CASE
                        WHEN COALESCE(pos.selected_to_library, FALSE) OR legacy.legacy_selected_to_library THEN 'APPROVED'
                        WHEN COALESCE(legacy.audit_status, pos.audit_status) = 3 THEN 'REJECTED'
                        ELSE 'PENDING_AUDIT'
                    END
                ),
                last_operation_at = COALESCE(pos.last_operation_at, legacy.legacy_last_operation_at),
                deleted = CASE
                    WHEN COALESCE(legacy.deleted, 0) = 1 THEN 1
                    ELSE COALESCE(pos.deleted, 0)
                END,
                update_time = GREATEST(
                    COALESCE(pos.update_time, legacy.legacy_update_time, CURRENT_TIMESTAMP),
                    COALESCE(legacy.legacy_update_time, pos.update_time, CURRENT_TIMESTAMP)
                )
            FROM (
                SELECT
                    activity_id,
                    product_id,
                    assignee_id,
                    audit_status,
                    audit_remark,
                    deleted,
                    update_time AS legacy_update_time,
                    COALESCE(audit_time, update_time, create_time) AS legacy_last_operation_at,
                    CASE WHEN audit_status = 2 THEN TRUE ELSE FALSE END AS legacy_selected_to_library,
                    CASE
                        WHEN audit_status = 2 THEN COALESCE(audit_time, update_time, create_time)
                        ELSE NULL
                    END AS legacy_selected_at,
                    CASE
                        WHEN audit_status = 2 THEN COALESCE(update_by, create_by)
                        ELSE NULL
                    END AS legacy_selected_by,
                    CASE
                        WHEN promotion_info IS NOT NULL AND sample_requirement IS NOT NULL
                            THEN jsonb_strip_nulls(jsonb_build_object(
                                'legacyPromotionInfo', promotion_info,
                                'legacySampleRequirement', sample_requirement
                            ))::text
                        WHEN promotion_info IS NOT NULL
                            THEN promotion_info::text
                        WHEN sample_requirement IS NOT NULL
                            THEN sample_requirement::text
                        ELSE NULL
                    END AS legacy_audit_payload
                FROM colonel_activity_product
            ) legacy
            WHERE pos.activity_id = legacy.activity_id
              AND pos.product_id = legacy.product_id;
        $sql$;
    END IF;
END $$;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS activity_name;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS title;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS price;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS cos_ratio;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS cos_fee;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS service_ratio;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS shop_name;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS activity_start_time;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS activity_end_time;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS promotion_start_time;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS promotion_end_time;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS months_of_protection;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS cover;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS detail_url;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS first_cid;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS second_cid;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS third_cid;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS assignee_id;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS sample_requirement;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS promotion_info;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS audit_status;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS audit_time;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS audit_remark;

ALTER TABLE colonel_activity_product
    DROP COLUMN IF EXISTS min_refer_amount;

CREATE INDEX IF NOT EXISTS idx_product_operation_state_product_id
    ON product_operation_state(product_id);

CREATE INDEX IF NOT EXISTS idx_product_operation_state_selected_to_library
    ON product_operation_state(selected_to_library);

CREATE INDEX IF NOT EXISTS idx_product_operation_state_pinned_until
    ON product_operation_state(pinned_until);

CREATE INDEX IF NOT EXISTS idx_ap_activity_id
    ON colonel_activity_product(activity_id);

CREATE INDEX IF NOT EXISTS idx_ap_product_id
    ON colonel_activity_product(product_id);

CREATE INDEX IF NOT EXISTS idx_ap_shop_id
    ON colonel_activity_product(shop_id);

CREATE INDEX IF NOT EXISTS idx_ap_status
    ON colonel_activity_product(status);

CREATE INDEX IF NOT EXISTS idx_ap_deleted
    ON colonel_activity_product(deleted);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ap_activity_product
    ON colonel_activity_product(activity_id, product_id);
