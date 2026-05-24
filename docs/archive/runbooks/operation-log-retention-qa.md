# 操作日志 90 天保留 — QA 验收说明

## 定版口径（P1-LOG-RETENTION）

- **状态**：已实现，V1 不改业务清理逻辑。
- **机制**：`LogCleanupJob` 每日调度（默认 `0 30 2 * * ?`），调用 `OperationLogService.cleanupOldPartitions(retentionDays)`。
- **保留策略**：按月分区 `DROP` 过期分区；配置项 `operation.log.retention-days` 默认 **90**。
- **粒度说明**：实际保留时间 **≥ 90 天**，最多多保留一个自然月（非按天精确删除）。

## 自动化验收

运行：

```bash
cd backend
mvn -Dtest=OperationLogRetentionAcceptanceTest,LogCleanupJobTest,OperationLogServiceTest test
```

覆盖点：

1. Spring 容器中存在 `LogCleanupJob` Bean
2. `operation.log.retention-days` 默认值为 90
3. 构造过期月分区 + 近月分区
4. 调用 `cleanupOldPartitions(90)`
5. 断言过期分区被 drop、近月分区保留

## 手工抽检（可选）

```sql
-- 查看 operation_log 子分区
SELECT c.relname AS partition_name
FROM pg_inherits i
JOIN pg_class p ON i.inhparent = p.oid
JOIN pg_class c ON i.inhrelid = c.oid
WHERE p.relname = 'operation_log'
ORDER BY 1;
```

确认：不存在明显早于「当前日期 - 90 天」对应 cutoff 月之前的分区。

## 相关代码

- `backend/src/main/java/com/colonel/saas/job/LogCleanupJob.java`
- `backend/src/main/java/com/colonel/saas/service/OperationLogService.java#cleanupOldPartitions`
