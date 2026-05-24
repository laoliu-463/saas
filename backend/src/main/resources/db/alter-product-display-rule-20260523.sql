-- 商品库展示去重基础规则：同 product_id 仅一条 DISPLAYING
ALTER TABLE product_operation_state
    ADD COLUMN IF NOT EXISTS display_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS first_displayed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_displayed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS hidden_reason VARCHAR(128),
    ADD COLUMN IF NOT EXISTS display_rule_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS selected_to_library BOOLEAN DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_pos_product_display_status
    ON product_operation_state (product_id, display_status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pos_displaying_library
    ON product_operation_state (display_status, selected_to_library)
    WHERE deleted = 0 AND display_status = 'DISPLAYING';

COMMENT ON COLUMN product_operation_state.display_status IS '商品库展示状态：PENDING/DISPLAYING/HIDDEN';
COMMENT ON COLUMN product_operation_state.first_displayed_at IS '首次被选中展示时间（保护期基准，V2）';
COMMENT ON COLUMN product_operation_state.last_displayed_at IS '最近一次被选中展示时间';
COMMENT ON COLUMN product_operation_state.hidden_reason IS '隐藏原因：REPLACED_BY_HIGHER_PRIORITY/NOT_ELIGIBLE 等';
COMMENT ON COLUMN product_operation_state.display_rule_version IS '展示规则版本，便于 V2 引擎升级';
