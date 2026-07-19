# Evidence Report

## Metadata

- Time: 2026-07-19 18:31:47 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/repository-governance-mainline-20260719
- Commit: 04851d1f
- Owned worktree: clean
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/service/OrderSyncAuditService.java
backend/src/main/java/com/colonel/saas/service/OrderSyncService.java
backend/src/test/java/com/colonel/saas/architecture/DddEventDispatcherRuntimeRetryContractTest.java
backend/src/test/java/com/colonel/saas/architecture/DddOutboxInventoryContractTest.java
backend/src/test/java/com/colonel/saas/architecture/RoleAwareAttributionFlywayIntegrationTest.java
backend/src/test/java/com/colonel/saas/service/OrderSyncServiceTest.java
backend/src/test/java/com/colonel/saas/service/ProductServiceActivityStatusIndependenceTest.java
backend/src/test/resources/ddd/large-service-line-baseline.csv
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
Backend package PASS; mvn -B test: 3318 tests, 0 failures, 0 errors, 3 skipped; Flyway integration: 2/2 PASS.
~~~

## Docker Status

~~~text
collection failed: time="2026-07-19T18:31:47+08:00" level=warning msg="The \"DB_NAME\" variable is not set. Defaulting to a blank string."
collection failed: time="2026-07-19T18:31:47+08:00" level=warning msg="The \"DB_NAME\" variable is not set. Defaulting to a blank string."
~~~

## Health Check Result

~~~text
Local backend health PASS; Docker backend/postgres/redis/frontend healthy.
~~~

## Business Validation Result

~~~text
Business validation PASS: npm run e2e:real-pre:p0:preflight.
~~~

## Content Maintenance Result

~~~text
Content maintenance not applicable.
~~~

## Remote Deploy Result

~~~text
Remote deploy not requested and not executed.
~~~

## Retro Summary

主线切换前必须执行完整后端回归；迁移测试夹具、并发测试容器和大类债务基线应随服务器主线同步冻结。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
