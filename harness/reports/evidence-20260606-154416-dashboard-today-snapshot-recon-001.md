# Evidence Report — DASHBOARD-TODAY-SNAPSHOT-RECON-001

## 基本信息

| 字段 | 值 |
|------|-----|
| 时间 | 2026-06-06 15:44:16 +08:00 |
| 环境 | real-pre (local) |
| 分支 | main |
| 工作区 | 只读审查，无修改 |
| 构建结果 | N/A (只读) |
| Docker 状态 | healthy (postgres + redis + backend) |
| 健康检查 | N/A (只读) |
| 业务验证 | 见对账报告 |
| 是否部署远端 | 否 |
| 结论 | **FAIL_MONEY_FORMULA_DRIFT** |

## 执行清单

| 步骤 | 状态 | 说明 |
|------|------|------|
| 确认时间/时区 | ✅ | 2026-06-06 15:44:16 +08:00, Asia/Shanghai |
| SQL 1: 今天订单表汇总 | ✅ | 4709 单, ¥99631.33 |
| SQL 2: 今天订单状态分布 | ✅ | status=1: 4326, status=4: 383 |
| SQL 3: 今天业绩表汇总 | ✅ | 4709 条, valid=4326, reversed=383 |
| SQL 4: 今天 anti-join | ✅ | 0 (无缺失) |
| SQL 5: 今天重复 performance | ✅ | 0 (无重复) |
| SQL 6: 今天服务费支出反推 | ✅ | inferred expense = 0 (valid only) |
| API: /api/orders (pay_time today) | ✅ | total=4709 |
| API: /api/data/orders/summary | ✅ | serviceFeeExpense=415.32 (错误) |
| API: /api/dashboard/metrics | ✅ | serviceFeeProfit=1279.08, 无 serviceFeeExpense |
| API: /api/performance/summary | ✅ | serviceFeeExpense=386.40 (错误) |
| 代码搜索: serviceFeeExpense | ✅ | 定位 3 处 BUG |
| 代码搜索: CommissionService 公式 | ✅ | 提成基数未扣 serviceFeeExpense |
| 报告生成 | ✅ | 本报告 + 对账报告 |

## 剩余风险

1. serviceFeeExpense 公式错误影响 3 个 API 端点 + 前端 fallback
2. 服务费收入偏差 267.32 元根因未定位（可能需要对比上游活动/费率配置）
3. CommissionService 提成基数可能需要扣除 serviceFeeExpense（需产品确认）
4. performance_records.estimate_service_profit 字段可能需要修正（需确认上游写入逻辑）
