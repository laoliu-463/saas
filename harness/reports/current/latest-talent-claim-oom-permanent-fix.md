# Evidence Report

## Metadata

- Time: 2026-07-20 15:29:20 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/182-talent-claim-oom-guard
- Commit: 95e376b3
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/domain/order/facade/LegacyOrderReadFacade.java
backend/src/main/java/com/colonel/saas/domain/order/facade/OrderReadFacade.java
backend/src/main/java/com/colonel/saas/domain/talent/application/TalentClaimApplicationService.java
backend/src/main/java/com/colonel/saas/job/TalentClaimReleaseJob.java
backend/src/test/java/com/colonel/saas/architecture/DddTalentDomainInventoryEvidenceTest.java
backend/src/test/java/com/colonel/saas/domain/order/facade/LegacyOrderReadFacadeTest.java
backend/src/test/java/com/colonel/saas/domain/talent/application/TalentClaimApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/job/TalentClaimReleaseJobTest.java
backend/src/test/java/com/colonel/saas/service/TalentServiceTest.java
docker-compose.real-pre.yml
harness/engineering/issues-index.md
harness/rules/changelog.md
harness/rules/state/snapshots/01-当前项目状态.md
harness/rules/state/snapshots/DOMAIN_STATUS.md
harness/rules/state/snapshots/KNOWN_ISSUES.md
harness/scripts/tests/release-queue-governance.Tests.ps1
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/domain/order/facade/LegacyOrderReadFacade.java
 M backend/src/main/java/com/colonel/saas/domain/order/facade/OrderReadFacade.java
 M backend/src/main/java/com/colonel/saas/domain/talent/application/TalentClaimApplicationService.java
 M backend/src/main/java/com/colonel/saas/job/TalentClaimReleaseJob.java
 M backend/src/test/java/com/colonel/saas/architecture/DddTalentDomainInventoryEvidenceTest.java
 M backend/src/test/java/com/colonel/saas/domain/order/facade/LegacyOrderReadFacadeTest.java
 M backend/src/test/java/com/colonel/saas/domain/talent/application/TalentClaimApplicationServiceTest.java
 M backend/src/test/java/com/colonel/saas/job/TalentClaimReleaseJobTest.java
 M backend/src/test/java/com/colonel/saas/service/TalentServiceTest.java
 M docker-compose.real-pre.yml
 M harness/engineering/issues-index.md
 M harness/rules/changelog.md
 M harness/rules/state/snapshots/01-当前项目状态.md
 M harness/rules/state/snapshots/DOMAIN_STATUS.md
 M harness/rules/state/snapshots/KNOWN_ISSUES.md
 M harness/scripts/tests/release-queue-governance.Tests.ps1
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                              IMAGE                                               COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre                       "sh -c 'java $JAVA_O…"   backend-real-pre    54 seconds ago   Up 39 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:rbac-permission-enforcement   "/docker-entrypoint.…"   frontend-real-pre   29 minutes ago   Up 28 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine                                  "docker-entrypoint.s…"   postgres-real-pre   55 seconds ago   Up 51 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                                      "docker-entrypoint.s…"   redis-real-pre      43 hours ago     Up 43 hours (healthy)     6379/tcp
NAMES                                                      STATUS                    PORTS
objective_moser                                            Up 19 seconds             0.0.0.0:45123->5432/tcp, [::]:45123->5432/tcp
saas-active-backend-real-pre-1                             Up 39 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1                            Up 52 seconds (healthy)   5432/tcp
testcontainers-ryuk-c7d534dc-a3ee-4442-9ac7-5546ba89a868   Up 4 minutes              0.0.0.0:44917->8080/tcp, [::]:44917->8080/tcp
saas-rbac-ci-full-postgres-1                               Up 5 minutes (healthy)    0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-rbac-ci-full-redis-1                                  Up 5 minutes (healthy)    6379/tcp
saas-active-frontend-real-pre-1                            Up 28 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-redis-real-pre-1                               Up 43 hours (healthy)     6379/tcp
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

Root cause removed with bounded OrderReadFacade existence query; 87 targeted tests, 3324 full backend tests, 11 Pester governance tests, and two-axis review passed. No database migration change and no remote deployment.

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
