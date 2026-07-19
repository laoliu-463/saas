# Evidence Report

## Metadata

- Time: 2026-07-19 19:26:58 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/180-real-pre-release-queue
- Commit: dd47d2490247468d2a5fcfda8da484fab7d7a744
- Owned worktree: clean after implementation commit; this report-only refresh is the remaining change
- Deploy remote: false

## Owned Files

~~~text
AGENTS.md
backend/src/main/java/com/colonel/saas/controller/SystemEnvController.java
backend/src/test/java/com/colonel/saas/controller/SystemEnvControllerTest.java
docker-compose.real-pre.yml
docs/10-部署运行总览.md
frontend/Dockerfile
harness/engineering/issues-index.md
harness/manifests/git-branch-governance-20260719.md
harness/reports/current/latest-harness-limits-check.md
harness/rules/changelog.md
harness/rules/cicd-real-pre-policy.md
harness/rules/governance/task-routing.md
harness/rules/policies/agent-contract.md
harness/rules/runbooks/remote-deploy.md
harness/rules/state/snapshots/01-当前项目状态.md
harness/rules/state/snapshots/DEPLOYMENT_STATE.md
harness/scripts/commands/agent-do.ps1
harness/scripts/commands/deploy-remote.ps1
harness/scripts/tests/agent-do-conclusion.Tests.ps1
harness/scripts/tests/release-queue-governance.Tests.ps1
Jenkinsfile
~~~

## Owned Git Status

~~~text
clean at implementation commit dd47d2490247468d2a5fcfda8da484fab7d7a744
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
Backend focused tests: PASS (8 tests, 0 failures, 0 errors)
Frontend tests/typecheck/build: PASS
Harness Pester: PASS (51 tests)
Release queue contract: PASS (9 tests)
Jenkins Groovy syntax: PASS
Jenkins shell syntax: PASS (17 blocks)
Harness TASK_GATE: PASS; REPOSITORY_HEALTH: PARTIAL (historical report debt only)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    43 seconds ago   Up 27 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   41 seconds ago   Up 10 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   43 seconds ago   Up 38 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      23 hours ago     Up 23 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 10 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 27 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 38 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 23 hours (healthy)     6379/tcp
saas-test-frontend-1              Up 24 hours (healthy)     0.0.0.0:3000->3000/tcp, [::]:3000->3000/tcp
saas-test-backend-1               Up 24 hours (healthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 24 hours (healthy)     0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
campus_frontend                   Up 26 hours               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 26 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 26 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-redis-1                 Up 26 hours (healthy)     6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
Backend: {"status":"UP","gitSha":"real-pre","imageDigest":"unknown"}
Frontend /version.json: {"gitSha":"real-pre"}
说明：本地通用 Compose 验证不模拟发布身份；完整 SHA/digest 一致性由 Jenkins 发布队列强制校验。
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

发布、迁移和版本核验已拆为显式门禁；后续由 Jenkins 实跑验证锁插件与远端发布清单。

## Conclusion

PASS

## Residual Risk

- 未执行远端部署、远端数据库备份/迁移、远端 E2E 或 `/opt/saas/releases/current.json` 更新。
- Jenkins 全局锁插件和完整发布清单仍需首个受控 Jenkins 构建验证；在此之前只证明代码、契约和本地运行态通过。
- 本地 Compose 使用通用 `real-pre` tag，因此本地接口显示 `gitSha=real-pre`、`imageDigest=unknown`；不得把它写成完整发布身份已验证。
- `npm ci` 报告现有依赖树 6 个漏洞（1 low、1 moderate、2 high、2 critical）；需单独依赖治理 PR，不能与本发布治理 PR 混合。
- Harness 历史报告目录仍有既有数量/行数债务，但本任务没有新增或恶化硬违规。
