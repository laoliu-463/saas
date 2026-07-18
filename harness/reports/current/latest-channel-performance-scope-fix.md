# Evidence Report

## Metadata

- Time: 2026-07-18 12:32:34 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 53ab3f99
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/controller/DashboardController.java
backend/src/main/java/com/colonel/saas/controller/DataController.java
backend/src/main/java/com/colonel/saas/domain/order/facade/LegacyOrderReadFacade.java
backend/src/main/java/com/colonel/saas/domain/order/facade/OrderReadFacade.java
backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java
backend/src/main/java/com/colonel/saas/service/DashboardService.java
backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java
backend/src/main/java/com/colonel/saas/service/PerformanceMetricsQueryService.java
backend/src/test/java/com/colonel/saas/controller/DataControllerTest.java
backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/service/PerformanceMetricsQueryServiceTest.java
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/controller/DashboardController.java
 M backend/src/main/java/com/colonel/saas/controller/DataController.java
 M backend/src/main/java/com/colonel/saas/domain/order/facade/LegacyOrderReadFacade.java
 M backend/src/main/java/com/colonel/saas/domain/order/facade/OrderReadFacade.java
 M backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java
 M backend/src/main/java/com/colonel/saas/service/DashboardService.java
 M backend/src/main/java/com/colonel/saas/service/PerformanceMetricsQueryService.java
 M backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java
 M backend/src/test/java/com/colonel/saas/controller/DataControllerTest.java
 M backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java
 M backend/src/test/java/com/colonel/saas/service/PerformanceMetricsQueryServiceTest.java
~~~

## Build Result

~~~text
Backend package: PASS (mvn -q -DskipTests package); Frontend build: PASS (npm run build); Targeted permission regression: PASS (DataControllerTest, PerformanceAggregateApplicationServiceTest, PerformanceMetricsQueryServiceTest, DashboardControllerTest, DashboardServiceTest with -DforkCount=0).
~~~

## Docker Status

~~~text
not collected
not collected
~~~

## Health Check Result

~~~text
BLOCKED: agent-do safety check stopped before restart/HTTP health because D:\Projects\SAAS\.env.real-pre is missing; existing container status was observed healthy but this run did not re-verify it.
~~~

## Business Validation Result

~~~text
BLOCKED: real-pre business validation was not executed because the required .env.real-pre safety gate failed.
~~~

## Content Maintenance Result

~~~text
Not run: agent-do stopped at safety check.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

Actionable improvement: restore the local real-pre env file through the team secret-management workflow, then rerun agent-do with the same ReportKey and OwnedFiles to complete restart, health, business validation, and evidence.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
