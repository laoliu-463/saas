# DDD-AUDIT-ANALYSIS-001 证据清单

| 字段 | 值 |
| --- | --- |
| 时间 | 2026-06-09 09:05:00 |
| 任务 | DDD-AUDIT-ANALYSIS-001 |
| 性质 | docs-only / read-only |
| 状态 | DONE_AUDIT（分析域） |

## A. 外部 KB 路径证据

```
$ ls -la "D:/Docs/Books/my second brain/团长SaaS知识库/plans/ddd-refactor/"
drw-r--r--  plans/ddd-refactor/
  drw-r--r--  tasks/
    - ddd-audit-analysis-001.md       <-- 任务卡（DONE_AUDIT, last_verified 2026-06-09 08:10）
  drw-r--r--  audits/
    - ddd-audit-analysis-001.md       <-- 审计正文（476 行，2026-06-09 08:10）
  drw-r--r--  domains/
    - analysis-ddd-plan.md            <-- 分析域 DDD 计划骨架
  drw-r--r--  docs/领域/
    - 分析模块.md                      <-- V1 领域合同（9 项 metric + 双轨公式）
```

## B. 后端源码证据（行号 / 内容片段）

### B.1 `DataApplicationService.java`（2279 行，核心 God Service）

```
$ wc -l backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java
2279

构造器注入（L81-90，9 端点 God Service + 9 跨域 Mapper/Service）：
  ColonelsettlementOrderMapper
  ColonelsettlementActivityMapper
  CommissionService
  PerformanceMetricsQueryService
  PerformanceRecordMapper
  ExclusiveTalentMapper
  ExclusiveMerchantMapper
  SysUserMapper
  ShortTtlCacheService

L230:   getOrderPage(...)
L363:   getOrderDetailPage(...)
L699:   getOrderSummary(...)
L874:   @GetMapping("/dashboard/metrics") —— public DualTrackMetricsVO getMetrics(...)
L878-879: 双轨缓存键独立
  String cacheKeySettle   = METRICS_CACHE_PREFIX + metricsCacheKey("settleTime", userId, deptId, scope);
  String cacheKeyEstimate = METRICS_CACHE_PREFIX + metricsCacheKey("createTime", userId, deptId, scope);

L892-947: buildMetrics
L946:   metrics.setGrossProfit(centToYuan(aggregate.grossProfitCent()));        <-- HIGH-1 残留 #1

L976:   boolean estimateTrack = isEstimateTrack(timeField);
L1027:  metrics.setGrossProfit(centToYuan(commissionSummary.grossProfit()));   <-- HIGH-1 残留 #2
L1022-1023: 同一 serviceFeeNet() 写两次 serviceFee + serviceFeeProfit
L1034:  exportOrders(...)
L1136:  exportOrderDetail(...)
L1234:  getExclusiveTalentStatus(...)
L1263:  getExclusiveMerchantStatus(...)
L1292:  exportActivities(...)

L1380-1389: cacheKey() 通用 helper
L1391-1403: metricsCacheKey 走 PERSONAL/DEPT/ALL/NO_SCOPE 四档
L1405-1419: resolveTimeColumn / isEstimateTrack(estimate when create_time)
L1421-1428: serviceFeeExpenseCent = Math.max(income - techDeduction - profit, 0L)  <-- HIGH-2 镜像公式
L1430-1432: resolveAliasedOrderTimeColumn
L1434-1461: toOrderVO
```

### B.2 `DataController.java`（285 行）

```
$ wc -l backend/src/main/java/com/colonel/saas/controller/DataController.java
285

L46:   @RequestMapping("/data")
L77-79: @RestController public class DataController extends DataApplicationService   <-- CRITICAL-1

9 端点列表：
  GET /data/orders
  GET /data/orders/detail
  GET /data/orders/summary
  GET /dashboard/metrics
  GET /orders/exports
  GET /orders/exports/detail
  GET /operations/exclusive-talents
  GET /operations/exclusive-merchants
  GET /activities/exports
```

### B.3 `DashboardService.java`（1138 行，老看板）

```
$ wc -l backend/src/main/java/com/colonel/saas/service/DashboardService.java
1138

L129:  public Summary getSummary(LocalDateTime startTime, LocalDateTime endTime,
                                UUID userId, UUID deptId, DataScope dataScope)
L130:  boolean usePerformanceRecords = performanceMetricsQueryService.hasPerformanceRecords();
L133-148: QueryWrapper for total/attributed/unattributed orders
L174-201: channel/colonel/reason aggregations
注入：ColonelsettlementOrderMapper（订单域）+ PerformanceMetricsQueryService（业绩域）

只扫 settle_time（单轨），无双轨支持 —— 与新 DataApplicationService 行为不一致
```

### B.4 `DashboardController.java`（157 行）

```
$ wc -l backend/src/main/java/com/colonel/saas/controller/DashboardController.java
157

L46:    @RequestMapping("/dashboard")
L48-49: SUMMARY_CACHE_TTL = Duration.ofSeconds(30); SUMMARY_CACHE_PREFIX = "dashboard:summary:";
L84-99: /dashboard/summary 单一端点（老看板）
L117-136: /dashboard/activity-products 第二端点
L147-156: 内联 cacheKey 串接 userId|deptId|scope
```

### B.5 `DashboardPerformanceSummaryService.java`（77 行）

```
$ wc -l backend/src/main/java/com/colonel/saas/service/DashboardPerformanceSummaryService.java
77
```

### B.6 `CommissionService.java`（507 行，业绩域权威口径）

```
$ wc -l backend/src/main/java/com/colonel/saas/service/CommissionService.java
507

L28: 注释 "服务费收益（serviceFeeNet）= 服务费收入 - 调用方传入的技术服务费扣减额"
L183-184: public static long serviceFeeNetCent(long serviceFeeIncome, long serviceFeeExpense, long techServiceFee) {
            return Math.max(serviceFeeIncome - techServiceFee - serviceFeeExpense, 0L);
          }
L289-310: grossProfit 计算
L466: 另一次 serviceFeeNet 使用
```

### B.7 VO 文件

```
MetricsVO.java  - 56 行
  L29:  todayOrderCount
  L31:  totalOrders
  L32:  totalAmount
  L33:  todayGmv
  L37:  serviceFeeIncome
  L38:  techServiceFee
  L39:  serviceFeeExpense
  L43:  serviceFee
  L44:  serviceFeeProfit
  L46:  bizCommission
  L47:  channelCommission
  L49:  grossProfit              <-- HIGH-1 V2 残留

DualTrackMetricsVO.java
  L17:  private MetricsVO estimate;
  L17:  private MetricsVO settle;

OrderSummaryRowVO.java
  L41:  grossProfit              <-- HIGH-1 V2 残留

OrderDetailVO.java
  L15-16: 注释 "双轨金额：预估轨（estimate*）基于支付金额实时估算，结算轨（effective*）基于结算金额最终确认"
  L101-145: estimate*/effective* 双轨字段
  L114-117: 公式注释
  L142-143: 注释 "预估毛利（元）= estimateServiceProfit - estimateRecruiterCommission - estimateChannelCommission"
  L143: estimateGrossProfit      <-- HIGH-1 V2 残留
  L145: effectiveGrossProfit     <-- HIGH-1 V2 残留
```

## C. 前端源码证据

```
frontend/src/api/dashboard.ts
  23 行  老版本（单轨 getSummary）  <-- 旧 API 残留

frontend/src/api/data.ts
  153 行 新版本（9 函数：getOrderPage/getOrderDetailPage/getOrderSummary/
                getMetrics/exportOrders/exportOrderDetail/exportActivities/
                getExclusiveTalentStatus/getExclusiveMerchantStatus）
  OrderDetailRecord 接口保留 estimate/effective 双轨字段

frontend/src/views/dashboard/index.vue
  454 行 老看板视图

frontend/src/views/data/index.vue
  1310 行 新分析视图
  L25-28: n-radio-group 切 createTime/settleTime
  含 PageHeader、4 metric cards、ECharts trend、dual-track-bar
```

## D. 9 项 metric 矩阵证据对齐

| # | 指标 | V1 合同 | MetricsVO 字段 | 行号 | 双轨支持 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 总订单数 | ✓ | totalOrders / todayOrderCount | L29, L31 | trend7d 估算 | OK |
| 2 | 订单额 | ✓ | totalAmount / todayGmv | L32, L33 | trend7d 估算 | OK |
| 3 | 服务费收入 | ✓ | serviceFeeIncome | L37 | L935-940 双公式 | OK |
| 4 | 技术服务费 | ✓ | techServiceFee | L38 | 双轨共用 | OK |
| 5 | 服务费支出 | ✓ | serviceFeeExpense | L39 | L935 计算 | OK |
| 6 | 服务费收益 | ✓ | serviceFee + serviceFeeProfit | L43, L44 | 同值 | 同一 serviceFeeNet() 写两次 |
| 7 | 招商提成 | ✓ | bizCommission | L46 | 双轨共用 | OK |
| 8 | 媒介/渠道提成 | ✓ | channelCommission | L47 | 双轨共用 | OK |
| 9 | 毛利 | ✓ | grossProfit | L49 | 字段存在 | **V2 残留，未消费** |

## E. 双轨公式证据

```
业绩域权威公式（CommissionService L183-184）：
  serviceFeeNetCent(income, expense, tech) = max(income - tech - expense, 0)

分析域镜像公式（DataApplicationService L1421-1428）：
  serviceFeeExpenseCent = max(income - techDeduction - profit, 0)
  where techDeduction = estimateTrack ? tech : 0L

风险点：
  - 两个公式的输入参数顺序相反（一个 expense 在前、一个 profit 在前）
  - 同一对象的镜像计算至少两处
  - 公式等价性无任何单元测试锁定
```

## F. DataScope 实现证据（三档重复）

```
实现 1: DataApplicationService.metricsCacheKey（L1391-1403）
  cacheKey(timeColumn, scope, userId|deptId)
  分支：PERSONAL / DEPT / ALL / NO_SCOPE

实现 2: DashboardController 内联 cacheKey（L147-156）
  简单 | 拼接所有参数

实现 3: DashboardService.applyScope（全文）
  QueryWrapper 注入 userId/deptId/in 部门子集
```

## G. 跨域 Mapper/Service 证据

```
DataApplicationService 注入：
  ColonelsettlementOrderMapper    - 订单域（事实层）
  ColonelsettlementActivityMapper - 订单域（事实层）
  CommissionService               - 业绩域（公式耦合）
  PerformanceMetricsQueryService  - 业绩域（汇总表）
  PerformanceRecordMapper         - 业绩域（直查）
  ExclusiveTalentMapper           - 达人域
  ExclusiveMerchantMapper         - 招商域
  SysUserMapper                   - 用户域（跨域渗透）
  ShortTtlCacheService            - 通用（OK）

DashboardService 注入：
  ColonelsettlementOrderMapper    - 订单域
  PerformanceMetricsQueryService  - 业绩域
```

## H. 25 条禁止动作自检证据

```
$ git status --short
?? harness/reports/ddd-audit-config-001-20260608-153000.md
?? harness/reports/ddd-audit-product-001-20260608-170000.md
?? harness/reports/ddd-audit-talent-001-20260609-093000.md
?? harness/reports/ddd-audit-user-001-20260608-160500.md
?? harness/reports/ddd-phase0-audit-status-sync-001-20260609-101500.md
?? harness/reports/evidence-20260608-153000-ddd-audit-config-001.md
?? harness/reports/evidence-20260608-160500-ddd-audit-user-001.md
?? harness/reports/evidence-20260608-170000-ddd-audit-product-001.md
?? harness/reports/evidence-20260609-093000-ddd-audit-talent-001.md
?? harness/reports/evidence-20260609-101500-ddd-phase0-audit-status-sync-001.md
?? harness/reports/retro-20260608-151631.md
?? harness/reports/retro-20260608-153000-ddd-audit-config-001.md
?? harness/reports/retro-20260608-160500-ddd-audit-user-001.md
?? harness/reports/retro-20260608-170000-ddd-audit-product-001.md
?? harness/reports/retro-20260609-093000-ddd-audit-talent-001.md
?? harness/reports/retro-20260609-101500-ddd-phase0-audit-status-sync-001.md
?? harness/reports/ddd-audit-analysis-001-20260609-090500.md     <-- 本次新增
?? harness/reports/evidence-20260609-090500-ddd-audit-analysis-001.md  <-- 本次新增
?? harness/reports/retro-20260609-090500-ddd-audit-analysis-001.md     <-- 本次新增

$ git diff --check
(no output) - 无冲突标记

$ git log -1 --format="%H %ci %s"
90701c739367f9804ae3f6814bbb548cf02432e3 2026-06-08 15:16:17 +0800 docs: complete DDD-AUDIT-SAMPLE-001 audit report

$ grep -rinE "secret|token|client_secret|password|cookie|api_key|apikey" harness/reports/ddd-audit-analysis-001-20260609-090500.md
(0 命中)
```

## I. 与 phase0-sync 报告冲突的复核证据

```
本任务时间：2026-06-09 09:05:00
phase0-sync 报告时间：2026-06-09 10:15:00（晚于本任务 1h10m）

phase0-sync 报告 §4 写"DDD-AUDIT-ANALYSIS-001 | 任务卡缺失" —— 与 KB 不符：
  实际路径：D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor\tasks\ddd-audit-analysis-001.md
  文件状态：存在，状态 DONE_AUDIT，last_verified 2026-06-09 08:10

phase0-sync 报告 §3 写"ddd-audit-analysis-001.md | True" —— 与 KB 实际一致：
  实际路径：D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor\audits\ddd-audit-analysis-001.md
  文件状态：存在，476 行，2026-06-09 08:10

phase0-sync 报告 §11 写"Phase 0 完成，DONE_AUDIT_COMPLETE" —— 与本任务五、§25 冲突：
  本任务明文"禁止把 Phase 0 写成已收口完成"
  本任务默认下一任务：DDD-PHASE0-AUDIT-STATUS-SYNC-001
  phase0-sync 报告默认下一任务：DDD-TEST-ORDER-SYNC-001（**冲突**）

处置：本报告不修改 phase0-sync 报告（属 docs-only 范围内但属另一任务）；
      冲突由 DDD-PHASE0-AUDIT-STATUS-SYNC-001 显式仲裁（非本任务范围）
```

## J. 证据自检

- [x] A 段 KB 路径存在
- [x] B 段 Java 行号均已 Read 校验
- [x] C 段前端路径 + 函数均已 grep
- [x] D 段 9 metric 与 V1 合同逐项对齐
- [x] E 段双轨公式两处均已 Read 校验
- [x] F 段 DataScope 3 套实现均已定位
- [x] G 段 9 跨域 Mapper/Service 全部列出
- [x] H 段 git status 0 业务代码 dirty
- [x] I 段冲突项已逐项复核
- [x] 全文 0 secret / token / 凭证 命中

## 证据使用说明

- 本证据清单只读 docs / 只读 git status
- 本证据清单**不**作为"事实成立"的唯一依据：与 `audits/ddd-audit-analysis-001.md` KB 文本 + `docs/领域/分析模块.md` V1 合同三方互验
- 任何行号以 KB 审计发布时为基准；本任务仅做"事实复核 + 行号校对"，不发起新事实
