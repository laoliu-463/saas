# Evidence Report

## Metadata

- Time: 2026-07-16 11:39:43 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/merge-feature-auth-system-20260715
- Commit: 2b4bf6f0
- Owned worktree: dirty
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/domain/sample/application/SampleApplicationPortImpl.java
backend/src/main/java/com/colonel/saas/domain/talent/application/TalentProfileApplicationService.java
backend/src/main/java/com/colonel/saas/domain/talent/facade/dto/TalentReadDTO.java
backend/src/main/java/com/colonel/saas/domain/talent/facade/LegacyTalentDomainFacade.java
backend/src/main/java/com/colonel/saas/dto/talent/TalentCreateRequest.java
backend/src/main/java/com/colonel/saas/dto/talent/TalentDetailResponse.java
backend/src/main/java/com/colonel/saas/dto/talent/TalentUpdateRequest.java
backend/src/main/java/com/colonel/saas/service/talent/profile/TalentProfileSyncService.java
backend/src/main/java/com/colonel/saas/service/talent/provider/ManualTalentProvider.java
backend/src/main/java/com/colonel/saas/service/talent/provider/ThirdPartyTalentProvider.java
backend/src/main/java/com/colonel/saas/service/talent/TalentEnrichOrchestrator.java
backend/src/main/java/com/colonel/saas/service/TalentQueryService.java
backend/src/main/java/com/colonel/saas/vo/TalentVO.java
backend/src/main/resources/db/alter-sample-default-standard-disable-20260716.sql
backend/src/main/resources/db/init-db.sql
backend/src/main/resources/db/migrate-all.sql
backend/src/test/java/com/colonel/saas/domain/sample/application/SampleApplicationPortImplTest.java
backend/src/test/java/com/colonel/saas/domain/talent/application/TalentProfileApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/domain/talent/facade/LegacyTalentDomainFacadeTest.java
backend/src/test/java/com/colonel/saas/dto/talent/TalentDtoTest.java
backend/src/test/java/com/colonel/saas/service/BusinessRuleConfigServiceTest.java
backend/src/test/java/com/colonel/saas/service/talent/profile/TalentProfileSyncServiceTest.java
backend/src/test/java/com/colonel/saas/service/talent/provider/ManualTalentProviderTest.java
backend/src/test/java/com/colonel/saas/service/talent/TalentDataProviderTest.java
backend/src/test/java/com/colonel/saas/service/talent/TalentEnrichOrchestratorTest.java
frontend/src/api/talent.ts
harness/reports/current/latest-content-retire.md
harness/scripts/commands/deploy-remote.ps1
~~~

## Owned Git Status

~~~text
M harness/reports/current/latest-content-retire.md
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
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    44 seconds ago   Up 39 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   42 seconds ago   Up 17 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   16 hours ago     Up 16 hours (healthy)     5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      20 hours ago     Up 20 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 17 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 39 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 16 hours (healthy)     5432/tcp
saas-active-redis-real-pre-1      Up 20 hours (healthy)     6379/tcp
campus_frontend                   Up 44 hours               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 44 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 44 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 44 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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
Content maintenance: Plan. Manifest=. DryRun=False.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

Playwright 已在远端真实页面完成添加达人、保存地址、填写备注并提交；接口 HTTP 200 且 successCount=1，数据库已核对申请记录。部署后需用另一条 QA 数据再次验证。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
