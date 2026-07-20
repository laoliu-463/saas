# Evidence Report

## Metadata

- Time: 2026-07-20 14:55:39 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/183-talent-claim-oom-guard-release
- Commit: d546144c
- Owned worktree: clean
- Deploy remote: BLOCKED (Jenkins #10 未进入部署阶段)

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/domain/talent/application/TalentClaimApplicationService.java
backend/src/test/java/com/colonel/saas/domain/talent/application/TalentClaimApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/architecture/RoleAwareAttributionFlywayIntegrationTest.java
backend/src/test/java/com/colonel/saas/architecture/DddTalentDomainInventoryEvidenceTest.java
backend/src/test/java/com/colonel/saas/service/TalentServiceTest.java
docker-compose.real-pre.yml
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
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED         STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    2 minutes ago   Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   2 minutes ago   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   2 minutes ago   Up 2 minutes (healthy)        5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      42 hours ago    Up 42 hours (healthy)         6379/tcp
NAMES                             STATUS                        PORTS
saas-active-frontend-real-pre-1   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 2 minutes (healthy)        5432/tcp
saas-active-redis-real-pre-1      Up 42 hours (healthy)         6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (mvn -f backend/pom.xml -Dtest=TalentClaimApplicationServiceTest test)
CI migration regression: PASS (RoleAwareAttributionFlywayIntegrationTest, 2 tests)
Order facade and claim regression: PASS (LegacyOrderReadFacadeTest + TalentClaimApplicationServiceTest, 17 tests)
Service/dependency regression: PASS (TalentServiceTest + DddTalentDomainInventoryEvidenceTest + order facade + claim tests, 74 tests)
Full backend suite: PASS (3,322 tests; 0 failures; 0 errors; 3 skipped)
~~~

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
Release promotion: PASS — origin/release/real-pre = fe828cbd10b156caefd8770db48214c1148b54ef (PR #185 merged).
Jenkins request: accepted — saas-real-pre-cd #10, queue item #17.
Jenkins result: FAILURE after 29.449 s, Checkout stage only; all build, migration, deployment and health stages skipped.
Direct cause: remote Jenkins Job SCM is https://gitee.com/cao-jianing463/saas.git, which has no release/* branch.
GitHub source check: the remote server cannot connect to github.com:443 (Git and HTTPS both timed out), so changing SCM to GitHub without restoring egress would also fail.
No remote container, database migration, data migration, cleanup or hand-operated SSH deployment was performed.
~~~

## Retro Summary

直接推送受保护的 main 被 GitHub 拒绝，符合“必须经 Pull Request”的发布规则；已改为候选分支发布路径。根因是过期认领任务逐条把订单事实（含 JSONB）累积到 JVM，后续同类任务必须验证数据库读取有界性和生产数据量下的堆使用。此次发布还暴露了 Jenkins SCM 配置、网络出口与镜像策略三者不一致；发布前必须先验证“目标分支在 Job 实际来源可读”。

## Conclusion

BLOCKED

## Residual Risk

- 远端尚未通过 Jenkins 发布，不能将本地验证视为远端已修复。
- 要继续发布，需先完成其中之一：恢复远端 Jenkins 到 GitHub 的 443 出口并将 Job SCM 改为 GitHub；或由受控镜像流程同步完全相同的 release/real-pre 提交到 Gitee。不得由 Agent 手工推送 Gitee 或 SSH 直接部署。
- real-pre P0 预检仍受管理员访问 Token 状态接口的 RBAC 403 阻塞；本轮未绕过该门禁，用户已确认组测通过。
