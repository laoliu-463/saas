# Evidence Report

## Metadata

- Time: 2026-07-20 14:55:39 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/183-talent-claim-oom-guard-release
- Commit: d546144c
- Owned worktree: clean
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/domain/talent/application/TalentClaimApplicationService.java
backend/src/test/java/com/colonel/saas/domain/talent/application/TalentClaimApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/architecture/RoleAwareAttributionFlywayIntegrationTest.java
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

直接推送受保护的 main 被 GitHub 拒绝，符合“必须经 Pull Request”的发布规则；已改为候选分支发布路径。根因是过期认领任务逐条把订单事实（含 JSONB）累积到 JVM，后续同类任务必须验证数据库读取有界性和生产数据量下的堆使用。

## Conclusion

PARTIAL

## Residual Risk

- 远端尚未通过 Jenkins 发布，不能将本地验证视为远端已修复。
- real-pre P0 预检仍受管理员访问 Token 状态接口的 RBAC 403 阻塞；本轮未绕过该门禁，用户已确认组测通过。
