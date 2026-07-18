# Evidence Report

## Metadata

- Time: 2026-07-18 18:05:11 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: f952f6b6
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/dto/sample/SampleActionRequest.java
backend/src/main/java/com/colonel/saas/dto/sample/SampleBatchShipItem.java
backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java
backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java
docker-compose.real-pre.yml
frontend/src/api/sample.ts
frontend/src/utils/shippingBatch.test.ts
frontend/src/utils/shippingBatch.ts
frontend/src/views/sample/SampleDetail.vue
harness/reports/current/latest-logistics-shipper-code-fix.md
harness/scripts/commands/_lib.ps1
harness/scripts/commands/deploy-remote.ps1
scripts/run-real-pre-db-migrations.sh
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
PASS: 本地后端 SampleControllerTest 83 tests / 0 failures；后端 package PASS；前端 shippingBatch + sample API 8 tests PASS；前端 build PASS。远端构建曾通过 Maven 与前端构建，但最终部署未完成。
~~~

## Docker Status

~~~text
not collected
not collected
~~~

## Health Check Result

~~~text
远端当前 backend /api/system/health=UP、frontend /healthz=ok，PostgreSQL/Redis healthy；运行容器仍为 colonel-saas/backend/frontend:672baed6，未切换到本任务目标。
~~~

## Business Validation Result

~~~text
PASS: 物流公司编码必填的后端控制器回归与前端批量发货校验通过。完整 real-pre preflight 为 BLOCKED_AUTH（缺少 Douyin access/refresh token），未冒充真实抖音闭环通过。
~~~

## Content Maintenance Result

~~~text
not collected
~~~

## Remote Deploy Result

~~~text
BLOCKED: 固定脚本已验证并修复 stdin、镜像 revision label、迁移 include 资产问题；部署期间远端被并行任务持续推进（最终 HEAD=7d71a8c3，IMAGE_TAG 仍为 2a0e51c5），并发生 PostgreSQL 重启/SSH 中断。为避免并行迁移冲突已停止本任务，未声明远端部署成功。
~~~

## Retro Summary

根因已修复并通过定向回归；部署门禁暴露出两个可治理项：1) deploy-remote.ps1 必须使用远端临时脚本文件并显式传递 GIT_COMMIT，已落地；2) real-pre 部署需要独占锁/串行窗口，避免并行 git 快进、pg_dump 与迁移互相干扰。建议由部署责任人增加 deploy lock，并在验证方式中检查 remote HEAD、IMAGE_TAG 与容器 revision 三者一致。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
