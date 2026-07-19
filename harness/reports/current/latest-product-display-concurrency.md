# Evidence Report

## Metadata

- Time: 2026-07-19 16:50:14 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: 0ea549ea
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/service/ProductDisplayRuleService.java
backend/src/test/java/com/colonel/saas/service/ProductDisplayRuleServiceTest.java
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/service/ProductDisplayRuleService.java
 M backend/src/test/java/com/colonel/saas/service/ProductDisplayRuleServiceTest.java
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED             STATUS                       PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    39 seconds ago      Up 36 seconds (healthy)      127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   About an hour ago   Up About an hour (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   22 hours ago        Up 22 hours (healthy)        5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      20 hours ago        Up 20 hours (healthy)        6379/tcp
NAMES                             STATUS                       PORTS
saas-active-backend-real-pre-1    Up 36 seconds (healthy)      127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up About an hour (healthy)   127.0.0.1:3001->80/tcp
saas-active-redis-real-pre-1      Up 20 hours (healthy)        6379/tcp
saas-test-frontend-1              Up 21 hours (healthy)        0.0.0.0:3000->3000/tcp, [::]:3000->3000/tcp
saas-test-backend-1               Up 21 hours (healthy)        0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 21 hours (healthy)        0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-active-postgres-real-pre-1   Up 22 hours (healthy)        5432/tcp
campus_frontend                   Up 23 hours                  0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 23 hours (healthy)        0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 23 hours (healthy)        0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-redis-1                 Up 23 hours (healthy)        6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (mvn -q -f backend/pom.xml '-Djacoco.skip=true' '-Dtest=ProductDisplayRuleServiceTest,ProductActivitySyncJobTest,ProductServiceTest' test)
~~~

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

该段是首次本地 Harness 采集结果。随后已执行用户明确要求的无备份远端部署，以下为部署后补充证据。

## Root Cause and Fix

- 现象：两个活动并行处理共享商品状态时，`ProductDisplayRuleService.persistDisplayDecision` 发生 409 乐观锁冲突，旧实现把冲突直接上抛，导致整个活动失败。
- 根因：展示决策基于冲突前快照计算；共享商品状态被另一线程更新后，原决策不能直接重放。
- 修复：仅对 409 重新读取该商品全部最新操作状态和快照、重新计算展示策略，最多重算 3 次；非 409 或重算耗尽仍抛错。
- 边界：没有重新请求抖音上游，没有吞掉未知异常，没有放宽商品展示唯一性约束。

## Post-deploy Verification

- 修复提交：`db930364f577f965f93601297e5e9854b4ff1813`，已 push。
- 远端 `/opt/saas/app` HEAD、`IMAGE_TAG`、backend/frontend 镜像均为该提交。
- backend、frontend、PostgreSQL、Redis 均 healthy；Flyway、JAR 版本守卫与健康检查 PASS。
- 正式异步 job `activity-product-sync-5be260b5-31ee-4d6b-806f-caf0e520eca8`：活动 `3920684`，SUCCESS，fetched/distinct=452/452，updated=254，failed=0。
- 正式异步 job `activity-product-sync-9b5980db-3215-4bc5-a4a0-e6560a252f15`：活动 `3916506`，SUCCESS，fetched/distinct=1813/1806，updated=1064，failed=0。
- 两个活动涉及的已选商品中，存在多条 `DISPLAYING` 的商品数为 0。
- 新部署日志中 `optimistic conflict` 与 `activity sync failed` 均为 0。
- 活动 `3916506` 因已有自然调度锁而按队列串行执行；未人为破坏锁来强制制造线上竞争。真实冲突分支由定向单元测试和活动批次测试覆盖。
- 抖音对 `status=4` 预探测返回 `50002` 后，既定串行回退成功，两个 job 最终无失败；SDK 当前把预期回退写成 ERROR，保留为日志降噪项。

## Retro Summary

No actionable Harness improvement was recorded; no standalone retro is required.

## Conclusion

PASS

## Residual Risk

- 线上未通过破坏锁的方式强制复现竞争；以失败现场、冲突分支测试、部署后正式任务和数据不变量共同证明修复。
- `status=4` 不受上游支持时的预期回退仍产生 ERROR 日志，需要后续收敛为结构化 WARN。
