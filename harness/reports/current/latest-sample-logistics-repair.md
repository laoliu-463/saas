# Evidence Report

## Metadata

- Time: 2026-07-18 19:34:15 +08:00
- Environment: test
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 3e569784
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/Dockerfile.test
backend/src/main/java/com/colonel/saas/controller/SampleController.java
backend/src/main/java/com/colonel/saas/domain/sample/application/SampleApplicationService.java
backend/src/main/java/com/colonel/saas/domain/sample/application/SampleCommandApplicationService.java
backend/src/main/java/com/colonel/saas/dto/sample/SampleLogisticsRepairRequest.java
backend/src/main/java/com/colonel/saas/service/sample/LegacySampleCommandService.java
backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java
backend/src/main/java/com/colonel/saas/service/sample/SampleCommandService.java
backend/src/main/java/com/colonel/saas/testsupport/TestDataService.java
backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java
frontend/src/api/sample.test.ts
frontend/src/api/sample.ts
frontend/src/views/sample/SampleDetail.vue
~~~

## Owned Git Status

~~~text
M backend/Dockerfile.test
 M backend/src/main/java/com/colonel/saas/controller/SampleController.java
 M backend/src/main/java/com/colonel/saas/domain/sample/application/SampleApplicationService.java
 M backend/src/main/java/com/colonel/saas/domain/sample/application/SampleCommandApplicationService.java
 M backend/src/main/java/com/colonel/saas/service/sample/LegacySampleCommandService.java
 M backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java
 M backend/src/main/java/com/colonel/saas/service/sample/SampleCommandService.java
 M backend/src/main/java/com/colonel/saas/testsupport/TestDataService.java
 M backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java
 M frontend/src/api/sample.test.ts
 M frontend/src/api/sample.ts
 M frontend/src/views/sample/SampleDetail.vue
?? backend/src/main/java/com/colonel/saas/dto/sample/SampleLogisticsRepairRequest.java
~~~

## Build Result

~~~text
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
Backend targeted test: PASS (SampleControllerTest, 84/84)
Frontend targeted test: PASS (src/api/sample.test.ts, 7/7)
~~~

## Docker Status

~~~text
NAME                   IMAGE                COMMAND                  SERVICE    CREATED         STATUS                   PORTS
saas-test-backend-1    saas-test-backend    "sh /app/scripts/run…"   backend    4 minutes ago   Up 4 minutes (healthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-frontend-1   saas-test-frontend   "docker-entrypoint.s…"   frontend   4 minutes ago   Up 3 minutes (healthy)   0.0.0.0:3000->3000/tcp, [::]:3000->3000/tcp
saas-test-postgres-1   postgres:15-alpine   "docker-entrypoint.s…"   postgres   8 minutes ago   Up 8 minutes (healthy)   0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1      redis:7-alpine       "docker-entrypoint.s…"   redis      6 weeks ago     Up 2 hours (healthy)     6379/tcp
NAMES                                                      STATUS                    PORTS
elastic_mendeleev                                          Up 6 seconds              0.0.0.0:44887->5432/tcp, [::]:44887->5432/tcp
testcontainers-ryuk-5831eb35-e2c2-489f-b1e1-3d752b5d19bd   Up 3 minutes              0.0.0.0:46359->8080/tcp, [::]:46359->8080/tcp
saas-test-frontend-1                                       Up 3 minutes (healthy)    0.0.0.0:3000->3000/tcp, [::]:3000->3000/tcp
saas-test-backend-1                                        Up 4 minutes (healthy)    0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1                                       Up 8 minutes (healthy)    0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-active-frontend-real-pre-1                            Up 12 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1                             Up 12 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-redis-real-pre-1                               Up 26 minutes (healthy)   6379/tcp
saas-active-postgres-real-pre-1                            Up 38 minutes (healthy)   5432/tcp
campus_frontend                                            Up 2 hours                0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                                             Up 2 hours (healthy)      0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                                            Up 2 hours (healthy)      0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-redis-1                                          Up 2 hours (healthy)      6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
npm run e2e:v1-p0: FAIL (74 passed, 9 failed)
失败断言集中在现有招商链/RBAC 页面与权限场景；本次物流补录没有对应 E2E 失败项。
全量 E2E 未通过，因此不能认定测试环境业务回归全部通过。
~~~

## Content Maintenance Result

~~~text
not collected
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

1. 首次测试部署发现 backend/Dockerfile.test 在 eclipse-temurin:17-jre 中执行 apk，已移除无用安装步骤。
2. 测试库缺少商品同步状态表，已应用非破坏性建表/索引迁移；测试种子补齐非空费用字段默认值。
3. 构建、容器健康检查、物流后端定向测试和前端定向测试通过；全量 E2E 仍有 9 项失败，需单独处理招商链/RBAC 基线问题。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
- 全量 E2E 的 9 项失败未在本任务中修复；远端 real-pre 未部署。
