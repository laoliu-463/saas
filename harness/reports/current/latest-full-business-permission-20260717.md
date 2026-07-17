# Evidence Report

## Metadata

- Time: 2026-07-17 21:41:56 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 3b3a55c9
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/controller/ProductController.java
backend/src/main/java/com/colonel/saas/controller/SampleController.java
backend/src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScope.java
backend/src/main/java/com/colonel/saas/domain/sample/application/SampleApplicationPortImpl.java
backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleActionPermissionPolicy.java
backend/src/main/java/com/colonel/saas/domain/user/policy/CurrentUserPermissionChecker.java
backend/src/main/java/com/colonel/saas/domain/user/policy/CurrentUserPermissionPolicy.java
backend/src/main/java/com/colonel/saas/service/ProductQuickSampleService.java
backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java
backend/src/main/java/com/colonel/saas/service/SampleFilterOptionsService.java
backend/src/test/java/com/colonel/saas/architecture/DddSampleAccessActionOrderEventEvidenceTest.java
backend/src/test/java/com/colonel/saas/architecture/DddSampleExceptionBranchCoverageContractTest.java
backend/src/test/java/com/colonel/saas/architecture/DddSamplePermissionOverreachNegativeContractTest.java
backend/src/test/java/com/colonel/saas/architecture/DddSampleStateMachineIntegrationClosureContractTest.java
backend/src/test/java/com/colonel/saas/architecture/DddUserFacadeSampleFilterBoundaryTest.java
backend/src/test/java/com/colonel/saas/config/DomainPolicyConfigTest.java
backend/src/test/java/com/colonel/saas/controller/ProductControllerTest.java
backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java
backend/src/test/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScopeTest.java
backend/src/test/java/com/colonel/saas/domain/sample/application/SampleApplicationPortPermissionTest.java
backend/src/test/java/com/colonel/saas/domain/sample/policy/SampleActionPermissionPolicyTest.java
backend/src/test/java/com/colonel/saas/domain/user/policy/CurrentUserPermissionCheckerTest.java
backend/src/test/java/com/colonel/saas/domain/user/policy/CurrentUserPermissionPolicyTest.java
backend/src/test/java/com/colonel/saas/service/QuickSampleApplyTest.java
docs/07-权限与数据范围.md
docs/09-03-MCP用户权限与数据范围接口梳理.md
docs/09-04-MCP配置规则接口梳理.md
docs/领域/寄样域.md
frontend/src/architecture/frontend-business-rule-boundary.test.ts
frontend/src/constants/rbac.test.ts
frontend/src/constants/rbac.ts
frontend/src/router/index.test.ts
frontend/src/router/index.ts
frontend/src/views/dashboard/index.vue
frontend/src/views/data/index.vue
frontend/src/views/layout/Header.vue
frontend/src/views/layout/Sider.vue
frontend/src/views/product/index.vue
frontend/src/views/product/ProductLibrary.vue
frontend/src/views/product/product-permissions.test.ts
frontend/src/views/product/product-permissions.ts
frontend/src/views/sample/CooperationWorkbench.vue
frontend/src/views/sample/SampleDetail.vue
frontend/src/views/sample/sample-permissions.test.ts
frontend/src/views/sample/sample-permissions.ts
frontend/src/views/talent/components/TalentDetailModal.vue
frontend/src/views/talent/index.vue
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/controller/ProductController.java
 M backend/src/main/java/com/colonel/saas/controller/SampleController.java
 M backend/src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScope.java
 M backend/src/main/java/com/colonel/saas/domain/sample/application/SampleApplicationPortImpl.java
 M backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleActionPermissionPolicy.java
 M backend/src/main/java/com/colonel/saas/domain/user/policy/CurrentUserPermissionChecker.java
 M backend/src/main/java/com/colonel/saas/domain/user/policy/CurrentUserPermissionPolicy.java
 M backend/src/main/java/com/colonel/saas/service/ProductQuickSampleService.java
 M backend/src/main/java/com/colonel/saas/service/SampleFilterOptionsService.java
 M backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java
 M backend/src/test/java/com/colonel/saas/architecture/DddSampleAccessActionOrderEventEvidenceTest.java
 M backend/src/test/java/com/colonel/saas/architecture/DddSampleExceptionBranchCoverageContractTest.java
 M backend/src/test/java/com/colonel/saas/architecture/DddSamplePermissionOverreachNegativeContractTest.java
 M backend/src/test/java/com/colonel/saas/architecture/DddSampleStateMachineIntegrationClosureContractTest.java
 M backend/src/test/java/com/colonel/saas/architecture/DddUserFacadeSampleFilterBoundaryTest.java
 M backend/src/test/java/com/colonel/saas/config/DomainPolicyConfigTest.java
 M backend/src/test/java/com/colonel/saas/controller/ProductControllerTest.java
 M backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java
 M backend/src/test/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScopeTest.java
 M backend/src/test/java/com/colonel/saas/domain/sample/policy/SampleActionPermissionPolicyTest.java
 M backend/src/test/java/com/colonel/saas/domain/user/policy/CurrentUserPermissionCheckerTest.java
 M backend/src/test/java/com/colonel/saas/domain/user/policy/CurrentUserPermissionPolicyTest.java
 M backend/src/test/java/com/colonel/saas/service/QuickSampleApplyTest.java
 M docs/07-权限与数据范围.md
 M docs/09-03-MCP用户权限与数据范围接口梳理.md
 M docs/09-04-MCP配置规则接口梳理.md
 M docs/领域/寄样域.md
 M frontend/src/architecture/frontend-business-rule-boundary.test.ts
 M frontend/src/constants/rbac.test.ts
 M frontend/src/constants/rbac.ts
 M frontend/src/router/index.test.ts
 M frontend/src/router/index.ts
 M frontend/src/views/dashboard/index.vue
 M frontend/src/views/data/index.vue
 M frontend/src/views/layout/Header.vue
 M frontend/src/views/layout/Sider.vue
 M frontend/src/views/product/ProductLibrary.vue
 M frontend/src/views/product/index.vue
 M frontend/src/views/sample/CooperationWorkbench.vue
 M frontend/src/views/sample/SampleDetail.vue
 M frontend/src/views/sample/sample-permissions.test.ts
 M frontend/src/views/sample/sample-permissions.ts
 M frontend/src/views/talent/components/TalentDetailModal.vue
 M frontend/src/views/talent/index.vue
?? backend/src/test/java/com/colonel/saas/domain/sample/application/SampleApplicationPortPermissionTest.java
?? frontend/src/views/product/product-permissions.test.ts
?? frontend/src/views/product/product-permissions.ts
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    54 seconds ago   Up 38 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   51 seconds ago   Up 21 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   57 seconds ago   Up 49 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 days ago       Up 2 days (healthy)       6379/tcp
NAMES                                                      STATUS                    PORTS
laughing_banzai                                            Up 11 seconds             0.0.0.0:45137->5432/tcp, [::]:45137->5432/tcp
saas-active-frontend-real-pre-1                            Up 22 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1                             Up 38 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1                            Up 50 seconds (healthy)   5432/tcp
testcontainers-ryuk-570d0c79-ad59-481a-9e99-a26b9b981777   Up 5 minutes              0.0.0.0:45831->8080/tcp, [::]:45831->8080/tcp
saas-active-redis-real-pre-1                               Up 2 days (healthy)       6379/tcp
campus_frontend                                            Up 3 days                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                                             Up 3 days (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                                            Up 3 days (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1                                        Up 3 days (unhealthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (npm run e2e:real-pre:p0:preflight)
~~~

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

多角色回归源于各页面与领域分别判断纯角色；已收口内置岗位唯一性判断、复合角色业绩范围 OR 合并，并补权限矩阵与复合角色测试。能力码下发仍列为 V2 治理项。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
