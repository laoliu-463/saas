# Evidence Report

## Metadata

- Time: 2026-07-15 15:28:23 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/feature-auth-integration-20260715
- Commit: 15387a20
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/domain/sample/application/port/SampleBoardQueryPort.java
backend/src/main/java/com/colonel/saas/domain/sample/application/port/SampleExportQueryPort.java
backend/src/main/java/com/colonel/saas/domain/sample/application/port/SampleLogisticsQueryPort.java
backend/src/main/java/com/colonel/saas/domain/sample/application/SampleQueryApplicationService.java
backend/src/main/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleBoardQueryAdapter.java
backend/src/main/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleExportQueryAdapter.java
backend/src/main/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleLogisticsQueryAdapter.java
backend/src/main/java/com/colonel/saas/domain/talent/application/port/TalentClaimReleasePort.java
backend/src/main/java/com/colonel/saas/domain/talent/application/TalentClaimReleaseApplicationService.java
backend/src/main/java/com/colonel/saas/domain/talent/infrastructure/LegacyTalentClaimReleaseAdapter.java
backend/src/main/java/com/colonel/saas/job/TalentClaimReleaseJob.java
backend/src/test/java/com/colonel/saas/architecture/DddTalentLegacyEntrypointMigrationTest.java
backend/src/test/java/com/colonel/saas/domain/sample/application/SampleQueryApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleBoardQueryAdapterTest.java
backend/src/test/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleExportQueryAdapterTest.java
backend/src/test/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleLogisticsQueryAdapterTest.java
backend/src/test/java/com/colonel/saas/domain/talent/application/TalentClaimReleaseApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/domain/talent/infrastructure/LegacyTalentClaimReleaseAdapterTest.java
backend/src/test/java/com/colonel/saas/job/TalentClaimReleaseJobTest.java
docs/10-/351/203/250/347/275/262/350/277/220/350/241/214/346/200/273/350/247/210.md
harness/reports/current/latest-feature-auth-branch-integration-20260715.md
harness/rules/changelog.md
harness/rules/runbooks/remote-deploy.md
harness/scripts/commands/agent-do.ps1
harness/scripts/commands/deploy-remote.ps1
harness/scripts/commands/git-push-safe.ps1
harness/scripts/tests/agent-do.Tests.ps1
harness/scripts/tests/agent-do-conclusion.Tests.ps1
harness/scripts/tests/deploy-remote.Tests.ps1
harness/scripts/tests/git-push-safe.Tests.ps1
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                                                                     COMMAND                  SERVICE             CREATED          STATUS                        PORTS
saas-active-backend-real-pre-1    sha256:da50a0f50b3b0c663383ff17147bf4c0c55ec1fc47cf9c68ecb6dfa804f25bcb   "sh -c 'java $JAVA_O…"   backend-real-pre    2 minutes ago    Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   sha256:c694db8fbd53454b4e8d1d5f154911585f3799b4e49f1630a30addb567112da1   "/docker-entrypoint.…"   frontend-real-pre   2 minutes ago    Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine                                                        "docker-entrypoint.s…"   postgres-real-pre   25 minutes ago   Up 25 minutes (healthy)       5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                                                            "docker-entrypoint.s…"   redis-real-pre      4 weeks ago      Up 24 hours (healthy)         6379/tcp
NAMES                             STATUS                        PORTS
saas-active-frontend-real-pre-1   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
campus_frontend                   Up 24 hours                   0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 24 hours (healthy)         0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 24 hours (healthy)         0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 24 hours (healthy)         6379/tcp
saas-test-backend-1               Up 23 hours (unhealthy)       0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation skipped by -SkipBusinessValidation; not a full PASS.
~~~

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
Remote deploy: PASS
~~~

## Retro Summary

分支审计与全量测试通过；本地 real-pre 管理员凭据已与现有 volume 脱钩，P0 为 BLOCKED_AUTH，未修改账号或数据库。部署治理已增加精确 commit、canonical env、stateful provenance、显式 upstream ref，并保证跳过业务验证后结论保持 PARTIAL。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
