# Evidence Report

## Metadata

- Time: 2026-07-25 13:48:33 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/183-talent-claim-oom-guard-release
- Commit: 2bd6494a
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/douyin/api/ProductApi.java
backend/src/main/java/com/colonel/saas/gateway/douyin/contract/DouyinContractFixtureProvider.java
backend/src/main/java/com/colonel/saas/gateway/douyin/DouyinActivityGateway.java
backend/src/main/java/com/colonel/saas/gateway/douyin/real/RealDouyinActivityGateway.java
backend/src/main/java/com/colonel/saas/gateway/douyin/test/TestDouyinActivityGateway.java
backend/src/main/java/com/colonel/saas/gateway/douyin/test/TestDouyinProductGateway.java
backend/src/main/java/com/colonel/saas/service/ProductAuditApplyIdResolver.java
backend/src/main/java/com/colonel/saas/service/ProductService.java
backend/src/test/java/com/colonel/saas/douyin/api/ProductApiTest.java
backend/src/test/java/com/colonel/saas/gateway/douyin/real/RealDouyinActivityGatewayTest.java
backend/src/test/java/com/colonel/saas/service/ProductServiceActivityStatusIndependenceTest.java
docs/对接/活动商品同步.md
docs/接口/活动分配与推广入库API契约.md
docs/领域/商品域.md
harness/rules/changelog.md
harness/rules/state/snapshots/01-当前项目状态.md
harness/rules/state/snapshots/DOMAIN_STATUS.md
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/douyin/api/ProductApi.java
 M backend/src/main/java/com/colonel/saas/gateway/douyin/DouyinActivityGateway.java
 M backend/src/main/java/com/colonel/saas/gateway/douyin/contract/DouyinContractFixtureProvider.java
 M backend/src/main/java/com/colonel/saas/gateway/douyin/real/RealDouyinActivityGateway.java
 M backend/src/main/java/com/colonel/saas/gateway/douyin/test/TestDouyinActivityGateway.java
 M backend/src/main/java/com/colonel/saas/gateway/douyin/test/TestDouyinProductGateway.java
 M backend/src/main/java/com/colonel/saas/service/ProductService.java
 M backend/src/test/java/com/colonel/saas/douyin/api/ProductApiTest.java
 M backend/src/test/java/com/colonel/saas/gateway/douyin/real/RealDouyinActivityGatewayTest.java
 M backend/src/test/java/com/colonel/saas/service/ProductServiceActivityStatusIndependenceTest.java
 M docs/对接/活动商品同步.md
 M docs/接口/活动分配与推广入库API契约.md
 M docs/领域/商品域.md
 M harness/rules/changelog.md
 M harness/rules/state/snapshots/01-当前项目状态.md
 M harness/rules/state/snapshots/DOMAIN_STATUS.md
?? backend/src/main/java/com/colonel/saas/service/ProductAuditApplyIdResolver.java
~~~

## Build Result

~~~text
mvn -f backend/pom.xml -DskipTests package: PASS. Targeted regression: 78 tests, 0 failures, 0 errors. Full mvn package: PARTIAL/FAIL in the existing dirty workspace: 3275 tests ran with 4 failures and 10 errors; blockers include no local Docker/Testcontainers, pre-existing missing docker-compose.test.yml, and unrelated repository/state contract failures.
~~~

## Docker Status

~~~text
not collected
not collected
~~~

## Health Check Result

~~~text
remote-verify.ps1 -Start: PASS. Remote saas-active backend/frontend/PostgreSQL/Redis healthy; /api/system/health and /healthz passed. Remote image is existing migration-20260723, not this branch.
~~~

## Business Validation Result

~~~text
Local targeted business/service tests: PASS. Real-pre approve/reject side-effect validation: BLOCKED/PENDING; it requires an explicit real activity/product sample and authorized business decision, and remote currently runs the existing migration-20260723 image. No real upstream product state was mutated.
~~~

## Content Maintenance Result

~~~text
not collected
~~~

## Remote Deploy Result

~~~text
SSH Docker verification only; no formal release/deployment. Jenkins release queue was not invoked.
~~~

## Git Publish Result

~~~text
Git commit/push not yet collected.
~~~

## Retro Summary

本轮已补齐 apply_id 夹具、上游审核明细校验和缺失申请 ID 的阻断测试；未发现可执行的独立 Harness 改进项，retro 内联完成。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.