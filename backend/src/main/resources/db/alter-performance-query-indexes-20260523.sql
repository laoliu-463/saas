-- Performance V1 query closure: indexes for list, summary, and export filters.
CREATE INDEX IF NOT EXISTS idx_performance_records_order_create_time
    ON performance_records (order_create_time DESC);

CREATE INDEX IF NOT EXISTS idx_performance_records_calculated_at
    ON performance_records (calculated_at DESC);

CREATE INDEX IF NOT EXISTS idx_performance_records_activity
    ON performance_records (activity_id);

CREATE INDEX IF NOT EXISTS idx_performance_records_product
    ON performance_records (product_id);

CREATE INDEX IF NOT EXISTS idx_performance_records_partner
    ON performance_records (partner_id);

CREATE INDEX IF NOT EXISTS idx_performance_records_talent
    ON performance_records (talent_id);

CREATE INDEX IF NOT EXISTS idx_performance_records_order_status
    ON performance_records (order_status);
