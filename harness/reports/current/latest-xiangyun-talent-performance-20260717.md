# Evidence Report

## Metadata

- Time: 2026-07-17 14:10:55 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: cd3bd3ad
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/controller/TalentController.java
backend/src/main/java/com/colonel/saas/service/TalentService.java
backend/src/test/java/com/colonel/saas/controller/TalentControllerTest.java
backend/src/test/java/com/colonel/saas/service/TalentServiceTest.java
frontend/src/views/data/index.vue
frontend/src/views/talent/index.test.ts
frontend/src/views/talent/index.vue
harness/reports/current/latest-xiangyun-talent-performance-20260717.md
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
PASS
- Backend: mvn -DskipTests package passed in the Harness full run.
- Frontend: npm ci and npm run build (vue-tsc -b && vite build) passed.
- Remote baseline targeted backend tests: TalentServiceTest and TalentControllerTest passed with JaCoCo report skipped because of a Windows file-lock conflict in the isolated worktree.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    10 minutes ago   Up 9 minutes (healthy)    127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   10 minutes ago   Up 9 minutes (healthy)    127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   10 minutes ago   Up 10 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      46 hours ago     Up 46 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 9 minutes (healthy)    127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 9 minutes (healthy)    127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 10 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 46 hours (healthy)     6379/tcp
campus_frontend                   Up 2 days                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 2 days (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 2 days (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 2 days (unhealthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
PASS
- Local real-pre backend http://127.0.0.1:8081/api/system/health => 200 {"status":"UP"}.
- Local real-pre frontend http://127.0.0.1:3001/healthz => 200 ok.
- Remote real-pre backend => 200 {"status":"UP"}; frontend => 200 ok.
~~~

## Business Validation Result

~~~text
PASS (local regression and preflight)
- Frontend talent view test: 6 passed, including post-create MY_TALENTS routing and channel_staff create-button visibility.
- Backend targeted tests: TalentServiceTest and TalentControllerTest passed; manual HTTP creation carries creator context and creates the owner claim.
- Existing talent API tests: 4 passed.
- real-pre P0 preflight passed: runtime/qa/out/real-pre-preflight-20260717-140118.
- Evidence chain: remote operation_log showed the affected account's POST /api/talents 403 was role-consistent for biz_staff; the reported successful channel_staff create path inserted talent but no talent_claim and then routed to TEAM_PUBLIC.
- Dashboard toggle was verified to change aria-selected; no API contract change was made because metrics intentionally returns dual-track data and the affected account has zero performance rows.
~~~

## Content Maintenance Result

~~~text
NOT APPLICABLE: no docs or Harness behavior changes were made for this task.
~~~

## Remote Deploy Result

~~~text
PASS: gitee feature/auth-system deployed at fde84e3250c6f4d76994b119b19845759692d556; remote backend/frontend health passed; compose services healthy; remote worktree clean
~~~

## Retro Summary

根因已由远端 API/数据库审计和本地 Playwright 证据确认；修复收敛为手动创建自动生成 owner claim、创建后回到 MY_TALENTS，并按角色收敛空数据提示。后续改进：为远端真实账号补充一次无污染的手动创建回归验收，记录 POST /api/talents、talent_claim 和 MY_TALENTS 三者关联证据。

## Conclusion

PARTIAL

## Residual Risk

- Business code, local validation, container restart, health checks, and remote deployment passed.
- Harness limits check returned TASK_GATE=FAIL / REPOSITORY_HEALTH=PARTIAL because pre-existing unowned timestamp reports and report-root debt are outside this task's OwnedFiles; they were preserved and not deleted.
- The exact post-deploy manual create flow for a real remote channel_staff account remains a follow-up validation to avoid adding persistent diagnostic data.
