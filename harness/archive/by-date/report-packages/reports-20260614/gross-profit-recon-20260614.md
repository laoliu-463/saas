# GROSS-PROFIT-RECON-001 毛利差异只读审计

## 结论

- 时间: 2026-06-14 +08:00
- 环境: real-pre
- 分支: feature/ddd/DDD-VERIFY-001
- 基准 commit: e52838f0
- 结论: PARTIAL
- 根因分类: F 差异化配置缺失；A 当前全局配置 15%+15% 是直接触发点
- 阻塞: 缺少可追溯的正确招商/渠道提成拆分或 activity/product/user 规则来源，不能硬编码日期毛利或固定比例。

## 只读审计回答

1. `/api/data/orders/summary` 毛利不是 `performance_records` 直接 SUM，而是从 `colonelsettlement_order` 实时汇总后调用 `CommissionService` 计算。
2. `performance_records.estimate_recruiter_commission`: 06-08 445.75，06-09 447.19，06-10 682.85，06-11 512.31，06-12 565.64，06-13 547.58。
3. `performance_records.estimate_channel_commission`: 06-08 445.75，06-09 447.19，06-10 682.85，06-11 512.31，06-12 565.64，06-13 547.58。
4. `performance_records.estimate_gross_profit`: 06-08 2086.82，06-09 2086.26，06-10 3156.15，06-11 2361.98，06-12 2595.66，06-13 2514.93。
5. 06-13 当前 API 毛利 2527.30 = 3610.36 - 541.53 - 541.53；成立。
6. `performance_records` 有 `recruiter_commission_rate` / `channel_commission_rate` 快照字段。
7. 06-08..06-13 业绩快照中两项比例全为 0.15，总提成率为 30%。
8. `system_config`: `commission.business_default_ratio=0.15`，`commission.channel_default_ratio=0.15`，均启用。
9. `system_config_change_log` 中未查到 commission 相关变更记录。
10. 当前配置不是正确比例；该问题不适用。即使改配置，也需要受控历史重算才会改变既有 `performance_records`。
11. 仅发现 `dashboard_performance_daily`，字段为 `stat_date/order_count/order_amount/service_fee_net/updated_at`，不存毛利。
12. `ShortTtlCacheService` 是 30 秒本地内存缓存；Redis 未发现 `dashboard:*` 键。SQL 与 API 一致，不是缓存导致。
13. 存在 `commissions` 差异化规则表和 `commission_config` 旧表。
14. `commissions`、`commission_config` 当前均为 0 行，未实际启用差异化规则。
15. 未发现服务费支出重复扣减。公式为 `service_profit = income - tech - expense`，毛利再扣 recruiter/channel。

## SQL 结果一：业绩记录按日汇总

| 日期 | 订单数 | estimate_service_profit | recruiter | channel | estimate_gross_profit | 总提成 | 总提成率 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 2026-06-08 | 8443 | 2978.32 | 445.75 | 445.75 | 2086.82 | 891.50 | 29.93% |
| 2026-06-09 | 7888 | 2980.64 | 447.19 | 447.19 | 2086.26 | 894.38 | 30.01% |
| 2026-06-10 | 9992 | 4521.85 | 682.85 | 682.85 | 3156.15 | 1365.70 | 30.20% |
| 2026-06-11 | 8723 | 3386.60 | 512.31 | 512.31 | 2361.98 | 1024.62 | 30.25% |
| 2026-06-12 | 10352 | 3726.94 | 565.64 | 565.64 | 2595.66 | 1131.28 | 30.36% |
| 2026-06-13 | 10454 | 3610.09 | 547.58 | 547.58 | 2514.93 | 1095.16 | 30.34% |

## SQL 结果二：正确基准反推

| 日期 | 正确服务费收益 | 正确毛利 | 正确总提成 | 正确总提成率 |
|---|---:|---:|---:|---:|
| 2026-06-08 | 3321.22 | 2558.26 | 762.96 | 22.97% |
| 2026-06-09 | 3290.47 | 2501.97 | 788.50 | 23.96% |
| 2026-06-10 | 4941.21 | 3865.43 | 1075.78 | 21.77% |
| 2026-06-11 | 3518.31 | 2743.39 | 774.92 | 22.03% |
| 2026-06-12 | 3726.94 | 2867.83 | 859.11 | 23.05% |
| 2026-06-13 | 3610.36 | 2808.20 | 802.16 | 22.22% |

## SQL 结果三：当前 API 口径差异

| 日期 | 当前毛利 | 正确毛利 | 差异 | 当前总提成 | 正确总提成 | 多扣提成 | 当前率 | 正确率 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 2026-06-08 | 2324.90 | 2558.26 | -233.36 | 996.32 | 762.96 | 233.36 | 30.00% | 22.97% |
| 2026-06-09 | 2303.21 | 2501.97 | -198.76 | 987.26 | 788.50 | 198.76 | 30.00% | 23.96% |
| 2026-06-10 | 3458.69 | 3865.43 | -406.74 | 1482.52 | 1075.78 | 406.74 | 30.00% | 21.77% |
| 2026-06-11 | 2462.57 | 2743.39 | -280.82 | 1055.74 | 774.92 | 280.82 | 30.01% | 22.03% |
| 2026-06-12 | 2608.88 | 2867.83 | -258.95 | 1118.06 | 859.11 | 258.95 | 30.00% | 23.05% |
| 2026-06-13 | 2527.30 | 2808.20 | -280.90 | 1083.06 | 802.16 | 280.90 | 30.00% | 22.22% |

## API 证据

`GET /api/data/orders/summary?startDate=2026-06-08&endDate=2026-06-13&timeField=createTime`

| 日期 | orderCount | serviceFeeProfit | grossProfit |
|---|---:|---:|---:|
| 2026-06-08 | 9337 | 3321.22 | 2324.90 |
| 2026-06-09 | 8667 | 3290.47 | 2303.21 |
| 2026-06-10 | 10846 | 4941.21 | 3458.69 |
| 2026-06-11 | 9014 | 3518.31 | 2462.57 |
| 2026-06-12 | 10352 | 3726.94 | 2608.88 |
| 2026-06-13 | 10455 | 3610.36 | 2527.30 |

## 代码证据

- `DataApplicationService#getOrderSummary` 使用 30 秒短 TTL，然后调用 `buildOrderSummary`。
- `buildOrderSummary` 从订单表查询金额，再通过 `queryOrderSummaryCommission` / `queryDailyOrderSummaryCommission` 调 `CommissionService`。
- `toOrderSummaryRow` 设置毛利: `serviceProfitCent - summary.bizCommission() - summary.channelCommission()`。
- `CommissionService` 读取 `commission.business_default_ratio` 和 `commission.channel_default_ratio`，找不到差异化规则时回退全局比例。
- `PerformanceMoneyPolicy` 公式为 `grossProfit = serviceFeeNet - bizCommission - channelCommission`。

## 修复策略判断

- 不执行 A 类“改全局配置”：正确总提成率 21.77%~23.96% 逐日变化，不是一个全局比例。
- 不执行 B 类历史重算：缺少正确 recruiter/channel 拆分或差异化规则源，重算会继续得到 30%。
- 不执行 C 类汇总表刷新：SQL 与 API 一致，且汇总表不存毛利。
- 不执行 D/E 类公式修复：公式正确，未发现重复扣减。
- F 类最小可行后续：补齐 activity/product/user 规则来源，计算时写入 `performance_records` 提成比例快照，再对 2026-06-08..06-13 做受控历史重算。

## 本轮执行状态

- 修改业务代码: 否
- 修改配置: 否
- 历史重算: 否
- 重建汇总表: 否
- 清理缓存: 否
- 前端展示证据: 未执行；API 尚未对齐，前端验证不能证明业务修复。
- 测试: 未执行业务修复测试；无可证据修复输入。
- 最终验收: PARTIAL，阻塞于正确差异化提成规则来源。
