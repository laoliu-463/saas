# Evidence Report

## Metadata

- Time: 2026-07-20 15:50:03 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/182-talent-claim-oom-guard
- Commit: c40c5cac
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
harness/engineering/issues-index.md
harness/reports/current/latest-talent-claim-oom-permanent-fix.md
harness/rules/changelog.md
harness/rules/state/snapshots/01-当前项目状态.md
harness/rules/state/snapshots/DOMAIN_STATUS.md
harness/rules/state/snapshots/KNOWN_ISSUES.md
harness/scripts/tests/release-queue-governance.Tests.ps1
~~~

## Owned Git Status

~~~text
M harness/reports/current/latest-talent-claim-oom-permanent-fix.md
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                              IMAGE                                                         COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre                                 "sh -c 'java $JAVA_O…"   backend-real-pre    40 seconds ago   Up 38 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:rbac-permission-enforcement-main-sync   "/docker-entrypoint.…"   frontend-real-pre   7 minutes ago    Up 7 minutes (healthy)    127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine                                            "docker-entrypoint.s…"   postgres-real-pre   2 minutes ago    Up 2 minutes (healthy)    5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                                                "docker-entrypoint.s…"   redis-real-pre      43 hours ago     Up 43 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-backend-real-pre-1    Up 39 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 2 minutes (healthy)    5432/tcp
saas-active-frontend-real-pre-1   Up 7 minutes (healthy)    127.0.0.1:3001->80/tcp
saas-active-redis-real-pre-1      Up 43 hours (healthy)     6379/tcp
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

重放最新 main 后完整后端测试 3326/0/0/3，定向测试 87 通过，发布治理 Pester 11/11；双重审查确认无 P0/P1；本次无数据库迁移差异、无远端部署。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
