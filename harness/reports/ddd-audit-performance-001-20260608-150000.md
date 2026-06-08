# Main Audit Report - DDD-AUDIT-PERFORMANCE-001

## 1. 任务目标
只读审查业绩域（Performance）关于订单 synced 事件消费、双轨提成/收益/毛利公式映射、退款零提成策略、批量与月度重算服务实现、以及数据范围鉴权和跨域耦合情况，为接下来的单测与重构提供坚实的事实保障。

## 2. 读取范围
- `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\service\PerformanceCalculationService.java`
- `PerformanceBackfillService.java`, `CommissionService.java`, `OrderCommissionPolicy.java`
- `PerformanceRecordSyncListener.java`, `PerformanceController.java`
- 业绩域测试与外部只读知识库文件。

## 3. 业绩域代码结构
- **Controller**: `PerformanceController`
- **Service**: `PerformanceCalculationService` (核心计算), `PerformanceBackfillService` (补算), `PerformanceQueryService`, `PerformanceSummaryService`, `PerformanceMonthRecalculationService`, `PerformanceMetricsQueryService`, `PerformanceExportService`, `CommissionService`, `PerformanceAccessScope` (数据权限)
- **Mapper/Entity**: `PerformanceRecordMapper`, `PerformanceRecord`
- **Listener/Job**: `PerformanceRecordSyncListener` (事件消费), `PerformanceBackfillJob` 等

## 4. performance_records 写入 / 更新链路
- 触发方式：`PerformanceRecordSyncListener` 消费 `OrderSyncedEvent`；或者是通过 `PerformanceBackfillService` 在 API 触发补算。
- 底层机制：调用 `PerformanceCalculationService.upsertFromOrder` 整合订单数据构建并执行 upsert 持久化。包含行级乐观锁，每次更新后计算版本（`calculation_version`）递增。

## 5. 订单事件进入业绩计算链路
- 异步 `@Async` 监听 `OrderSyncedEvent`。
- 监听器内根据事件中传递的 `orderId` 查询 `orderMapper`，然后调用计算服务。
- 事件发布已改为 `afterCommit` 时机，保证了数据在物理持久化并提交后才触发异步监听，消除了原先的并发 null 数据脏读。

## 6. PerformanceCalculationService 审查
- 代码规模为 219 行。
- 缺点：它集成了订单数据解析、三链默认归因映射、双轨金额映射、状态机校验以及持久化逻辑，职责过重，具备 God Service 倾向。建议提取 `PerformanceCalculationPolicy`。

## 7. PerformanceBackfillService 审查
- 提供批量补全 (`backfill`) 与重算已失效业绩 (`reconcileInvalidatedPerformance`)，并使用 `NOT EXISTS` 进行排重与过滤，防重复覆写。

## 8. 双轨金额字段与公式
- 公式定义：
  - **预估服务费收益** = 预估服务费收入（`estimateServiceFee`）- 预估服务费支出（`estimateServiceFeeExpense`）- 技术服务费。
  - **结算服务费收益** = 实际服务费收入 - 实际服务费支出（已在落库时扣除技术服务费）。
  - **毛利** = 服务费收益 - 招商提成（`bizCommission`）- 渠道提成（`channelCommission`）。
- **已修复缺陷**: 统一了 `nvl` 防护和零提成重算，去除了原先 `settle_amount` 回退污染的逻辑。
- **毛利口径**: 媒介/渠道提成和招募者提成已剥离计算，毛利属于 V1 口径，需在前端正确展示。

## 9. 配置域提成比例读取
- 优先级：规则库（`CommissionRuleService`）-> 配置表活动级覆盖 (`commission.business_activity_ratio.*`) -> 全局默认 (`commission.business_default_ratio`)，无配置时兜底 15%。
- 缺点：直接在服务层通过 `jdbcTemplate` 连库查配置，属于典型的跨域 Mapper 横穿。

## 10. 默认归因与最终归因
- 业绩表将 `final_channel` 和 `final_recruiter` 直接等价于订单域的归属 (`final = default`)。
- V1 暂跳过独家达人/独家商家覆盖，保持该状态（Dormant/V2）。

## 11. 退款 / 失效 / 冲正
- 订单状态为 4（失效）或 5（退款）时，业绩计算强置 `zeroCommissions` 将提成置 0，并标记为 `is_reversed=true` 和 `is_valid=false`。

## 12. 业绩查询 / 汇总 / Dashboard 读取路径
- 看板 `/api/dashboard/metrics` 直接通过 SQL 聚合业绩表（`performance_records`），明细读取隔离。

## 13. 用户域数据范围过滤
- 采用 `PerformanceAccessScope` 切面在 SQL 中拼接 `PERSONAL`、`DEPT` 或 `ALL`。

## 14. Mapper / Repository 横穿
- `CommissionService` 通过 `jdbcTemplate` 跨域查 `system_config`。
- `PerformanceRecordSyncListener` 跨域注入并调用 `ColonelsettlementOrderMapper` 查订单详情。

## 15. DTO / Entity / VO 混用
- `PerformanceController` 返回了 `PerformanceDetailDTO` 等 VO，但在一些汇总方法中，返回的是拼接的 `PerformanceItem` 等非标准类。

## 16. God Service / 胖 Service
- `PerformanceCalculationService` (219行) 具备典型的 God Service 属性。

## 17. DDD 拆分建议
1. 提炼 `PerformanceCalculationPolicy` 以解耦提成和毛利计算。
2. 封装 `ConfigDomainFacade` 以彻底隔离对 `system_config` 数据库的直接 SQL 读取。
3. 创建 `PerformanceDomainFacade` 收敛明细与统计，解耦分析模块的耦合。

## 18. 后续任务建议
- 下阶段建议：`DDD-AUDIT-SAMPLE-001` (寄样域只读审查)。

## 19. 不建议现在做的事
- 禁止包结构物理迁移。
- 禁止修改金额和招商/渠道提成公式。

## 20. 外部知识库更新清单
- 新增：`plans/ddd-refactor/audits/ddd-audit-performance-001.md`
- 更新：`plans/ddd-refactor/domains/performance-ddd-plan.md`
- 更新：`plans/ddd-refactor/tasks/ddd-audit-performance-001.md`
- 更新：`plans/ddd-refactor/tasks/00-task-index.md`
- 更新：`plans/ddd-refactor/03-execution-order.md`
- 更新：`domains/07-performance-domain.md`

## 21. 验证结果
- 运行了 `git status --short` 确认源码无变动。
- 验证所有 KB 文件均 `Test-Path` 为 `True`。

## 22. Git status
```text
?? harness/reports/ddd-audit-performance-001-20260608-150000.md
?? harness/reports/evidence-20260608-150000-ddd-audit-performance-001.md
?? harness/reports/retro-20260608-150000-ddd-audit-performance-001.md
```

## 23. 最终结论
- **DONE_WITH_REGISTERED_DIRTY** (只读审查已完成，有本任务及前序任务 reports dirty)。
