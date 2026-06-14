# COMMISSION-RULE-SOURCE-001 验收报告

## 结论

- 结论: PARTIAL / BLOCKED_BY_COMMISSION_RULE_SOURCE
- 环境: real-pre
- 分支: feature/ddd/DDD-VERIFY-001
- 基线 commit: 229c573b
- 本轮提交: 5aae96f1
- 是否改业务代码: 否
- 是否改配置: 否
- 是否历史重算: 否
- 是否重建汇总表: 否
- 是否清理缓存: 否

当前毛利不能修成 PASS。原因不是公式错误，而是缺少可追溯的差异化提成规则来源；当前系统只能命中全局 15% + 15%，而用户基准隐含总提成率在 21.77%~23.96% 间变化。

## 当前链路 15 问

| # | 问题 | 证据结论 |
|---|---|---|
| 1 | `/api/data/orders/summary` 毛利入口 | `DataController` -> `DataApplicationService.buildOrderSummary` -> `toOrderSummaryRow` |
| 2 | 是否经过 `CommissionService` | 是，`queryOrderSummaryCommission` 调 `calculateByActivityBuckets` |
| 3 | 规则优先级现状 | `commissions`: product > activity > user > global；未命中后查 system_config 活动键和全局键 |
| 4 | 空表是否回退 system_config | 是，最终回退 `commission.business/channel_default_ratio`，再回退硬编码 0.15 |
| 5 | recruiter 全局配置 | `commission.business_default_ratio=0.15` |
| 6 | channel 全局配置 | `commission.channel_default_ratio=0.15` |
| 7 | 系统每日总提成率 | 订单汇总口径 30.00%~30.01% |
| 8 | 用户基准隐含总率 | 21.77%~23.96% |
| 9 | 是否有维度规则配置 | 表存在但无规则：`commissions=0`、`commission_config=0` |
| 10 | 是否有订单级快照 | 有金额和比例快照；无 rule_id / source 字段 |
| 11 | PR 与 summary 是否同源 | 不同；PR 读已落快照，summary 当前从订单实时聚合后重算提成 |
| 12 | 主要差异 | summary 服务费收益已对齐，毛利差在招商/渠道提成规则 |
| 13 | 是否有历史重算 | 有 `/api/performance/recalculate-month` |
| 14 | 是否有按日期重算 | 无；现有接口按月且仅未结算订单 |
| 15 | 汇总表/缓存影响 | `dashboard_performance_daily` 不存毛利；短缓存 TTL 30 秒，Redis 无 `dashboard:*` 键 |

## SQL/API 证据

`system_config` 当前配置：

```text
commission.business_default_ratio = 0.15
commission.channel_default_ratio  = 0.15
commissions rows                  = 0
commission_config rows            = 0
```

订单汇总当前 15%+15% 复算：

| 日期 | 服务费收益 | 当前总提成 | 当前率 | 当前毛利 | 用户毛利 |
|---|---:|---:|---:|---:|---:|
| 2026-06-08 | 3321.22 | 996.32 | 30.00% | 2324.90 | 2558.26 |
| 2026-06-09 | 3290.47 | 987.26 | 30.00% | 2303.21 | 2501.97 |
| 2026-06-10 | 4941.21 | 1482.52 | 30.00% | 3458.69 | 3865.43 |
| 2026-06-11 | 3518.31 | 1055.74 | 30.01% | 2462.57 | 2743.39 |
| 2026-06-12 | 3726.94 | 1118.06 | 30.00% | 2608.88 | 2867.83 |
| 2026-06-13 | 3610.36 | 1083.06 | 30.00% | 2527.30 | 2808.20 |

`/api/commission-rules?page=1&size=10` 返回 `total=0`。`/api/data/orders/summary` 返回的 06-08..13 毛利与上表当前毛利一致。

## 为什么不能用单一全局比例

用户基准隐含总提成率逐日变化：06-08 22.97%、06-09 23.96%、06-10 21.77%、06-11 22.03%、06-12 23.05%、06-13 22.22%。单一全局比例只能偶然对齐某一天，不能解释跨日差异，也无法拆分招商/渠道提成。

## 当前可用事实与缺口

- 已有维度事实：06-08..13 每天约 21~22 个活动、424~443 个商品、2 个招商用户。
- 已有规则表：`commissions` 支持 `global/activity/product/user` + `recruiter/channel` + 生效区间。
- 已有快照字段：`performance_records.recruiter_commission_rate`、`channel_commission_rate`、`calculation_version`、`calculated_at`。
- 缺口：没有订单级导入源、没有 `commission_rule_id_recruiter/channel`、没有 `commission_rule_source`、没有按日期受控重算能力。

## 推荐 V1.5 最小模型

优先复用 `commissions`，不新造第二套规则表：

| 模型 | 方案 |
|---|---|
| 差异化规则 | 继续使用 `commissions` 存 `global/activity/product/user` 维度规则 |
| 规则扩展 | 后续迁移增加 `priority`、`source`、`remark`，用于审计和业务来源说明 |
| 订单级导入 | 新增受控导入明细表或导入到业绩快照，保留批次、来源文件、操作者、回滚依据 |
| 业绩快照 | 扩展 `performance_records` 增加 `commission_rule_id_recruiter`、`commission_rule_id_channel`、`commission_rule_source` |
| 历史重算 | 新增按日期闭开区间的受控重算；默认 dry-run，禁止全库无边界 |

规则没有命中时继续回退全局配置；所有最终金额必须落入 `performance_records` 快照，summary 不应临时实现另一套业务规则。

## 推荐规则优先级

建议优先级：订单级导入快照 > 商品 > 活动 > 用户 > 全局。当前代码仅支持 product > activity > user > global，缺订单级导入层。若业务确认优先级不同，以业务书面确认为准。

## 业务侧所需数据

详见 `harness/reports/commission-rule-source-needed-20260614.md`。最低需要每日招商/渠道提成拆分；最好提供订单级提成明细或官方后台导出。没有这些来源，不进入实现和历史重算。

## 后续受控导入与重算流程

1. 业务侧提交规则或订单级明细。
2. 工程侧先做 dry-run 导入，输出匹配率、缺失订单、冲突规则。
3. 写入 `commissions` 或订单级导入快照。
4. 对 2026-06-08..13 做受控重算，记录旧值备份、版本号和操作者。
5. 验证 `performance_records`、summary API、前端三层一致。
6. evidence report 结论才可从 PARTIAL 升级。

## 本轮新增资产

- `harness/reports/commission-rule-source-needed-20260614.md`
- `harness/reports/commission-rule-source-20260614.md`
- `harness/scripts/probes/commission-rule-source-probe.sql`
- `harness/scripts/probes/commission-rule-source-verify.ps1`
- `backend/src/test/java/com/colonel/saas/service/CommissionRuleSourceAlignmentTest.java`，`@Disabled`，仅记录后续实现验收目标。

## 测试与检查

| 命令 | 结果 |
|---|---|
| `git status --short` / `git diff` / `git diff --numstat` / `git diff --check` | 初始工作区异常复查无内容差异 |
| `powershell ... commission-rule-source-verify.ps1` | PASS，只读 SQL 和 Docker 健康检查通过 |
| `harness/scripts/check-harness-limits.ps1` | PASS |
| `mvn -f backend/pom.xml -Dtest=CommissionRuleSourceAlignmentTest test` | BUILD SUCCESS；2 个 `@Disabled` 测试均 skipped |
| 前端构建 | 未执行；本轮未改前端 |

## 最终状态

当前仍为 PARTIAL / BLOCKED_BY_COMMISSION_RULE_SOURCE。缺少正确规则来源前，不修改 `CommissionService`、`DataApplicationService`、`PerformanceSummaryService`，不改迁移，不执行历史重算。
