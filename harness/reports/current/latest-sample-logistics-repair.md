# Evidence Report

## Metadata

- Time: 2026-07-18 20:06:57 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 4bd2c63f
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
backend/Dockerfile.test
backend/src/main/java/com/colonel/saas/controller/SampleController.java
backend/src/main/java/com/colonel/saas/domain/sample/application/SampleApplicationService.java
backend/src/main/java/com/colonel/saas/domain/sample/application/SampleCommandApplicationService.java
backend/src/main/java/com/colonel/saas/dto/sample/SampleLogisticsRepairRequest.java
backend/src/main/java/com/colonel/saas/service/sample/LegacySampleCommandService.java
backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java
backend/src/main/java/com/colonel/saas/service/sample/SampleCommandService.java
backend/src/main/java/com/colonel/saas/testsupport/TestDataService.java
backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java
frontend/src/api/sample.test.ts
frontend/src/api/sample.ts
frontend/src/views/sample/SampleDetail.vue
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
Remote backend Maven package PASS; remote backend/frontend immutable image build PASS (IMAGE_TAG=4bd2c63f821f5d7f0d8e14a2b7db35fb3da2ce03)
~~~

## Docker Status

~~~text
not collected
not collected
~~~

## Health Check Result

~~~text
Remote health PASS: backend /api/system/health returned {"status":"UP"}; frontend /healthz returned ok; all four saas-active services healthy; backend jar size matched host/container.
~~~

## Business Validation Result

~~~text
Remote real-pre P0/roles not executed; user will perform logistics verification.
~~~

## Content Maintenance Result

~~~text
Content maintenance skipped for deployment.
~~~

## Remote Deploy Result

~~~text
Remote deploy PASS: checkout and IMAGE_TAG aligned to 4bd2c63f821f5d7f0d8e14a2b7db35fb3da2ce03; startup check PASS; compose config PASS; Flyway 20260718.001/002 present; schema contract PASS; backend/frontend restarted without PostgreSQL/Redis volume deletion. Backup intentionally skipped per user instruction; no backup file retained.
~~~

## Retro Summary

固定 agent-do 因本机 .env.real-pre 占位密钥提前阻断；按用户要求跳过备份，使用文档中的远端流程完成代码对齐、构建、迁移、重启和远端健康检查。未运行 real-pre P0/roles，留给用户验收物流补录。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
