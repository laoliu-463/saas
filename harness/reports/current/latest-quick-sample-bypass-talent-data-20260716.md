# Evidence Report

## Metadata

- Time: 2026-07-16 11:07:03 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/merge-feature-auth-system-20260715
- Commit: 5dd43e08
- Owned worktree: clean
- Deploy remote: false

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
clean after commit 5dd43e08
~~~

## Build Result

~~~text
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
Scoped backend tests: PASS (51 tests, 0 failures, 0 errors)
Full backend tests: FAIL (3 failures in SysUserGroupMembershipApplicationTest; the failure is outside the owned files and reports "用户不存在")
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    42 seconds ago   Up 38 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   40 seconds ago   Up 21 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   15 hours ago     Up 15 hours (healthy)     5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      19 hours ago     Up 19 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 21 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 38 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 15 hours (healthy)     5432/tcp
saas-active-redis-real-pre-1      Up 19 hours (healthy)     6379/tcp
campus_frontend                   Up 43 hours               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 43 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 43 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 43 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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

No actionable Harness improvement was recorded; no standalone retro is required.

## Conclusion

PASS

## Residual Risk

- Full backend suite is not all green because of the unrelated user-group test fixture failure described above; this task's scoped tests are green.
- npm reported 6 dependency audit findings during the frontend build (1 low, 1 moderate, 2 high, 2 critical); dependency upgrades were not part of this task.
