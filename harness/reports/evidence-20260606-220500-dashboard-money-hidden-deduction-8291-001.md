# Evidence �?DASHBOARD-MONEY-HIDDEN-DEDUCTION-8291-001

- 时间�?026-06-06 22:05:00 +08:00
- 环境：real-pre local Docker
- 分支：feature/auth-system @ cadfb220
- 任务�?git：`M OrderDualTrackAmountResolver.java`
- 任务�?git�? CommissionService, DataApplicationService, PerformanceSummaryService, tests

## SQL�?291 冻结窗口�?
```sql
-- 窗口：pay_time 2026-06-06 .. 21:32:25
orders=8291 pay=178617.79 income=3278.92 tech=279.49 expense=0
invalid_cnt=634 invalid_net=226.74
pr_valid_profit=2772.69
formula_profit_all=2999.43
hidden_gap=226.74
anti_join=0 duplicate_perf=0
```

## API（修复后�?
```text
POST /api/auth/login �?200
GET /api/data/orders/summary?startDate=2026-06-06&endDate=2026-06-06
  serviceFeeIncome=3824.31 tech=326.75 expense=0.00 profit=3497.56
  check: 3824.31-326.75-0=3497.56 PASS
GET /api/performance/summary timeFilterType=pay 2026-06-06
  profit formula closes in cents PASS
GET /api/system/health �?UP
```

## 构建

```text
mvn test �?1760 tests PASS
mvn package -DskipTests �?BUILD SUCCESS
docker compose up -d --build backend-real-pre �?healthy
```

## 修改文件

- backend/src/main/java/com/colonel/saas/service/CommissionService.java
- backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java
- backend/src/main/java/com/colonel/saas/service/PerformanceSummaryService.java
- backend/src/test/java/com/colonel/saas/service/ServiceFeeMoneyFormula8291Test.java
- backend/src/test/java/com/colonel/saas/service/PerformanceSummaryServiceTest.java
- backend/src/test/java/com/colonel/saas/controller/DataControllerTest.java

## 结论

**PARTIAL** �?226 隐藏扣减已修复；expense 1.90 �?BLOCKED_BY_UPSTREAM_FIELD
