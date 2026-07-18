# Evidence Report

## Metadata

- Time: 2026-07-18 22:31:11 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 7efb0f8e
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/config/DomainPolicyConfig.java
backend/src/main/java/com/colonel/saas/controller/SampleController.java
backend/src/main/java/com/colonel/saas/domain/product/application/CopyPromotionApplicationService.java
backend/src/main/java/com/colonel/saas/domain/product/facade/dto/ProductPromotionCopyDTO.java
backend/src/main/java/com/colonel/saas/domain/product/facade/LegacyProductPromotionFacade.java
backend/src/main/java/com/colonel/saas/domain/product/facade/ProductPromotionFacade.java
backend/src/main/java/com/colonel/saas/domain/product/infrastructure/DouyinPromotionGatewayConvertAdapter.java
backend/src/main/java/com/colonel/saas/domain/product/policy/CopyTextPolicy.java
backend/src/main/java/com/colonel/saas/domain/sample/application/SampleApplicationPortImpl.java
backend/src/main/java/com/colonel/saas/domain/sample/application/SampleApplicationService.java
backend/src/main/java/com/colonel/saas/domain/sample/application/SampleCooperationApplicationService.java
backend/src/main/java/com/colonel/saas/domain/sample/application/SampleQueryApplicationService.java
backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleActionPermissionPolicy.java
backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleCooperationActionPolicy.java
backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleOrderCopyPolicy.java
backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleRemarkPolicy.java
backend/src/main/java/com/colonel/saas/domain/talent/facade/dto/TalentClaimAddressDTO.java
backend/src/main/java/com/colonel/saas/domain/talent/facade/dto/TalentReadDTO.java
backend/src/main/java/com/colonel/saas/domain/talent/facade/LegacyTalentDomainFacade.java
backend/src/main/java/com/colonel/saas/domain/talent/facade/TalentDomainFacade.java
backend/src/main/java/com/colonel/saas/dto/sample/SampleCooperationUpdateRequest.java
backend/src/main/java/com/colonel/saas/dto/sample/SamplePrivateNoteRequest.java
backend/src/main/java/com/colonel/saas/entity/SamplePrivateNote.java
backend/src/main/java/com/colonel/saas/entity/SampleRequest.java
backend/src/main/java/com/colonel/saas/mapper/SamplePrivateNoteMapper.java
backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java
backend/src/main/java/com/colonel/saas/vo/sample/SampleActionAvailabilityVO.java
backend/src/main/java/com/colonel/saas/vo/sample/SampleCopyTextVO.java
backend/src/main/java/com/colonel/saas/vo/sample/SampleEditContextVO.java
backend/src/main/java/com/colonel/saas/vo/sample/SamplePrivateNoteVO.java
backend/src/main/java/com/colonel/saas/vo/sample/SampleVO.java
backend/src/main/resources/db/init-db.sql
backend/src/main/resources/db/migrate/V20260716_001__cooperation_workbench_actions.sql
backend/src/main/resources/db/migrate-all.sql
backend/src/test/java/com/colonel/saas/architecture/DddSampleAccessActionOrderEventEvidenceTest.java
backend/src/test/java/com/colonel/saas/architecture/DddSamplePermissionOverreachNegativeContractTest.java
backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java
backend/src/test/java/com/colonel/saas/domain/sample/policy/SampleActionPermissionPolicyTest.java
backend/src/test/java/com/colonel/saas/domain/sample/policy/SampleCooperationActionPolicyTest.java
backend/src/test/java/com/colonel/saas/domain/sample/policy/SampleOrderCopyPolicyTest.java
backend/src/test/resources/db/mapper-integration-schema.sql
docs/07-权限与数据范围.md
docs/09-05-MCP寄样接口梳理.md
frontend/src/api/sample.ts
frontend/src/types/index.ts
frontend/src/views/sample/CooperationActionColumn.test.ts
frontend/src/views/sample/CooperationActionColumn.vue
frontend/src/views/sample/cooperation-actions.test.ts
frontend/src/views/sample/cooperation-actions.ts
frontend/src/views/sample/CooperationWorkbench.actions.test.ts
frontend/src/views/sample/CooperationWorkbench.interaction.test.ts
frontend/src/views/sample/CooperationWorkbench.vue
frontend/src/views/sample/ManualCopyModal.test.ts
frontend/src/views/sample/ManualCopyModal.vue
frontend/src/views/sample/PrivateNoteModal.test.ts
frontend/src/views/sample/PrivateNoteModal.vue
frontend/src/views/sample/SampleEditModal.test.ts
frontend/src/views/sample/SampleEditModal.vue
frontend/src/views/sample/sample-permissions.test.ts
frontend/src/views/sample/sample-permissions.ts
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
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    32 seconds ago   Up 28 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   30 seconds ago   Up 12 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   4 hours ago      Up 4 hours (healthy)      5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 hours ago      Up 2 hours (healthy)      6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 12 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 28 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-redis-real-pre-1      Up 2 hours (healthy)      6379/tcp
saas-test-frontend-1              Up 3 hours (healthy)      0.0.0.0:3000->3000/tcp, [::]:3000->3000/tcp
saas-test-backend-1               Up 3 hours (healthy)      0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 3 hours (healthy)      0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-active-postgres-real-pre-1   Up 4 hours (healthy)      5432/tcp
campus_frontend                   Up 5 hours                0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 5 hours (healthy)      0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 5 hours (healthy)      0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-redis-1                 Up 5 hours (healthy)      6379/tcp
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

真实页面空操作栏由子组件缺失 Naive UI 导入导致；回归测试改为渲染真实组件，并验证本地 authenticated /sample 七个操作按钮可见且可点击。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
