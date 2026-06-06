# Retro — ORDER-SYNC-FRESHNESS-OPTIMIZE-001

## 做对了什么

1. **双链路拆分**：1 分钟小窗口 hot + 10 分钟补偿，避免 101 页任务每分钟跑。
2. **`syncItemsWithLimits`**：热同步与常规同步共用分页/去重逻辑，限 `maxPages`/`maxOrders`。
3. **独立锁与水位**：`institute_hot_last_time` 与 `institute_recent_last_time` 分离，失败不推进 hot 水位。
4. **现场效果**：部署后 3 分钟内 SQL lag 从 ~206s 降到 **52s**。

## 需要改进

1. **`freshnessLagSeconds` 日志**：初版 `EXTRACT(EPOCH FROM pay_time)` 未按时区换算，导致日志显示 0；已改为 `AT TIME ZONE 'Asia/Shanghai'`，需上游稳定后复验。
2. **观测窗口**：任务要求 10–15 分钟连续观测；二次重启遭遇 `signature-invalid` 中断，应把「复测观测」列为标准收尾步骤且避免无谓重启。
3. **测试债务**：`DataControllerTest` / `DataApplicationServiceOrderSummaryCacheTest` 需补 `JdbcTemplate` mock（本轮已修，属并行债务）。

## Harness 升级建议

- 在 `real-pre` 验收脚本增加 **freshness probe**：`max(pay_time)` lag + 最近 N 条 `INSTITUTE_HOT_RECENT` 日志解析。
- `agent-do` 对 `index.lock` 给出可恢复提示或重试，避免 build 已成功却整单 FAIL。

## 本次是否需要 Harness 升级

**建议后续小改**：增加 order-sync freshness SQL probe 到 preflight 或专项脚本；本轮不强制改 harness 代码。
