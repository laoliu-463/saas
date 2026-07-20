# Evidence Report

## Metadata

- Time: 2026-07-20 16:01:23 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/182-talent-claim-oom-guard
- Commit: b456d437
- Owned worktree: clean
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
(clean)
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                              IMAGE                                                         COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre                                 "sh -c 'java $JAVA_O…"   backend-real-pre    44 seconds ago   Up 29 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:rbac-permission-enforcement-main-sync   "/docker-entrypoint.…"   frontend-real-pre   19 minutes ago   Up 18 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine                                            "docker-entrypoint.s…"   postgres-real-pre   45 seconds ago   Up 41 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                                                "docker-entrypoint.s…"   redis-real-pre      43 hours ago     Up 43 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-backend-real-pre-1    Up 29 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 41 seconds (healthy)   5432/tcp
saas-active-frontend-real-pre-1   Up 18 minutes (healthy)   127.0.0.1:3001->80/tcp
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

基于最新 main 完整后端测试 3331/0/0/3，核心定向测试 80/80，发布治理 Pester 11/11；双重审查确认无 P0/P1；本次无数据库迁移差异、无远端部署。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
