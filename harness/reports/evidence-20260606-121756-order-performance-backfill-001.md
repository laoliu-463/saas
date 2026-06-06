# Evidence Report — ORDER-PERFORMANCE-BACKFILL-001

| 项目 | 值 |
|------|-----|
| 时间 | 2026-06-06 12:17 (UTC+8) |
| 环境 | real-pre (local) |
| 分支 | feature/auth-system |
| commit | 367f118c fix(order): publish order synced event after commit |
| 工作区 | dirty (有其他未提交变更，不影响本次补算) |

## 补算方法

通过正式 API `POST /api/orders/performance-backfill` 执行，请求体：

```json
{"onlyMissing": true, "limit": 200}
```

完整链路：OrderController → PerformanceBackfillService → PerformanceCalculationService.upsertFromOrder → PerformanceRecordMapper.upsert

## 补算前基线

| 指标 | 值 |
|------|-----|
| anti-join (缺失 performance) | 15 |
| performance_records 重复 order_id | 0 |
| 总订单数 (non-deleted) | 1360 |
| 总 performance_records | 1345 |

### 缺失订单列表

| order_id | order_status | order_amount | create_time |
|----------|-------------|--------------|-------------|
| 6953431256633513748 | 1 | 790 | 2026-06-05 11:43:48 |
| 6953431268316550663 | 1 | 759 | 2026-06-05 11:44:29 |
| 6953431288217933446 | 1 | 1990 | 2026-06-05 11:45:57 |
| 6953431246090671810 | 1 | 990 | 2026-06-05 11:47:44 |
| 6953433051934365549 | 1 | 2390 | 2026-06-05 11:56:14 |
| 6953437860430878314 | 4 | 3990 | 2026-06-05 11:56:36 |
| 6953433058201311013 | 1 | 990 | 2026-06-05 11:56:40 |
| 6953433058605078208 | 1 | 850 | 2026-06-05 12:08:12 |
| 6953433069081663231 | 1 | 1990 | 2026-06-05 12:15:20 |
| 6953433130388887346 | 1 | 1990 | 2026-06-05 12:24:28 |
| 6953440260362867822 | 1 | 1890 | 2026-06-05 16:12:49 |
| 6953438309677012678 | 1 | 2290 | 2026-06-05 16:14:37 |
| 6953438309292053517 | 1 | 1890 | 2026-06-05 16:16:30 |
| 6953438297135585101 | 1 | 2390 | 2026-06-05 16:17:34 |
| 6953438316795401907 | 1 | 2360 | 2026-06-05 16:18:47 |

## 补算 API 结果

```json
{
  "code": 200,
  "data": {
    "scanned": 15,
    "upserted": 15,
    "failed": 0,
    "onlyMissing": true,
    "errors": []
  }
}
```

## 补算后验证

| 验收标准 | 期望 | 实际 | 状态 |
|---------|------|------|------|
| 1. 补算前 anti-join | = 15 | 15 | PASS |
| 2. 补算后 anti-join | = 0 | **0** | PASS |
| 3. performance_records 重复 order_id | = 0 | **0** | PASS |
| 4. 补算日志 | scanned/upserted/failed | scanned=15, upserted=15, failed=0 | PASS |
| 5. 金额一致性 (order_amount = pay_amount) | 0 mismatch | **0** | PASS |
| 6. real-pre 后端运行 | UP | Up (healthy) | PASS |
| 7. dashboard/summary API | 200 无异常 | 200, orderCount=1266 | PASS |

## 总量交叉验证

| 指标 | 值 |
|------|-----|
| 总订单 (non-deleted) | 1360 |
| 总 performance_records | **1360** (完全对齐) |
| valid | 1266 |
| reversed | 94 |
| dashboard orderCount | 1266 (一致) |

## 补算记录详情

所有 15 条新记录 calculation_version=1，calculated_at 均为补算时间戳。
14 条 is_valid=true，1 条 is_reversed=true（order_status=4 已取消，提成自动置零）。

## 构建 / 重启

本次未修改代码，仅通过正式 API 执行数据补算，无需重新构建或重启。

## 结论

**PASS**

7 项验收标准全部通过。历史缺失的 15 条业绩记录已通过 PerformanceBackfillService 正式补算链路完成回填，anti-join 从 15 清零至 0，无重复记录，金额一致，dashboard 无异常跳变。

## 剩余风险

- 无。本次补算走的是正式 upsert 逻辑，幂等安全。
- PerformanceBackfillJob（每日 03:30）会自动处理未来可能的遗漏。
- 根因（事务竞态 afterCommit）已在上一步修复，增量订单不会再漏。
