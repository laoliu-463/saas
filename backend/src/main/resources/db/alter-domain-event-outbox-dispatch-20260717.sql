-- 让待派发事件按 occurred_at 取最早可处理记录时只扫描可重试状态。
-- next_retry_at 与 CURRENT_TIMESTAMP 相关，不能放进部分索引谓词；由查询继续过滤退避窗口。
CREATE INDEX IF NOT EXISTS idx_domain_event_outbox_dispatch_order
    ON domain_event_outbox (occurred_at)
    WHERE status IN ('PENDING', 'FAILED');
