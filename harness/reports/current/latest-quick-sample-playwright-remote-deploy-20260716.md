# Evidence Report

## Metadata

- Time: 2026-07-16 11:41:51 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/merge-feature-auth-system-20260715
- Commit: 954cdd4a
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/domain/sample/application/SampleApplicationPortImpl.java
backend/src/main/java/com/colonel/saas/domain/talent/application/TalentProfileApplicationService.java
backend/src/main/java/com/colonel/saas/domain/talent/facade/dto/TalentReadDTO.java
backend/src/main/java/com/colonel/saas/domain/talent/facade/LegacyTalentDomainFacade.java
backend/src/main/java/com/colonel/saas/dto/talent/TalentCreateRequest.java
backend/src/main/java/com/colonel/saas/dto/talent/TalentDetailResponse.java
backend/src/main/java/com/colonel/saas/dto/talent/TalentUpdateRequest.java
backend/src/main/java/com/colonel/saas/service/talent/profile/TalentProfileSyncService.java
backend/src/main/java/com/colonel/saas/service/talent/provider/ManualTalentProvider.java
backend/src/main/java/com/colonel/saas/service/talent/provider/ThirdPartyTalentProvider.java
backend/src/main/java/com/colonel/saas/service/talent/TalentEnrichOrchestrator.java
backend/src/main/java/com/colonel/saas/service/TalentQueryService.java
backend/src/main/java/com/colonel/saas/vo/TalentVO.java
backend/src/main/resources/db/alter-sample-default-standard-disable-20260716.sql
backend/src/main/resources/db/init-db.sql
backend/src/main/resources/db/migrate-all.sql
backend/src/test/java/com/colonel/saas/domain/sample/application/SampleApplicationPortImplTest.java
backend/src/test/java/com/colonel/saas/domain/talent/application/TalentProfileApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/domain/talent/facade/LegacyTalentDomainFacadeTest.java
backend/src/test/java/com/colonel/saas/dto/talent/TalentDtoTest.java
backend/src/test/java/com/colonel/saas/service/BusinessRuleConfigServiceTest.java
backend/src/test/java/com/colonel/saas/service/talent/profile/TalentProfileSyncServiceTest.java
backend/src/test/java/com/colonel/saas/service/talent/provider/ManualTalentProviderTest.java
backend/src/test/java/com/colonel/saas/service/talent/TalentDataProviderTest.java
backend/src/test/java/com/colonel/saas/service/talent/TalentEnrichOrchestratorTest.java
frontend/src/api/talent.ts
harness/reports/current/latest-content-retire.md
harness/scripts/commands/deploy-remote.ps1
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
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED         STATUS                   PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    2 minutes ago   Up 2 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   2 minutes ago   Up 2 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   16 hours ago    Up 16 hours (healthy)    5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      20 hours ago    Up 20 hours (healthy)    6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 2 minutes (healthy)    127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 2 minutes (healthy)    127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 16 hours (healthy)     5432/tcp
saas-active-redis-real-pre-1      Up 20 hours (healthy)     6379/tcp
campus_frontend                   Up 44 hours               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 44 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 44 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 44 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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
Content maintenance: Plan. Manifest=. DryRun=False.
~~~

## Remote Deploy Result

~~~text
Remote deploy: PASS
~~~

## Playwright Remote Business Verification

~~~text
远端真实页面：http://1.14.108.159，账号角色：channel_staff（渠道专员测试）。
部署前基线：选择 QA20260618164425_t33_1781772268238，保存地址和备注后提交；POST /api/products/7d404f72-6618-3339-a266-d6551ac1144d/quick-sample 返回 HTTP 200，successCount=1、failureCount=0，sampleRequestId=b3f76060-b475-434e-a5e0-7a8218345996。
部署后复测：选择 QA20260618_164518_t33_1781772366255，显示昵称、抖音号和 L5 等级；地址保存后重新打开选择达人页面仍回显；提交返回 HTTP 200，successCount=1、failureCount=0，sampleRequestId=ec15c84b-4fe3-4ed9-93bb-1f3aa24c6a22。
数据库核对：第二条 sample_request 已落库，status=1、expected_sample_num=1、apply_source=LOCAL_FALLBACK，收货人、电话、地址、备注均与页面填写一致。
远端运行核对：应用提交时运行提交 954cdd4a，backend/frontend/postgres/redis 均 healthy，/api/system/health 返回 UP，/healthz 返回 ok；后端日志中该请求 HTTP 200 且无“达人不符合”或“默认寄样要求”错误。
外部能力边界：接口返回 externalEnabled=false、externalSupported=false、gatewayStatus=UNSUPPORTED_BY_SDK；因此本次 PASS 证明系统内寄样申请可创建，不证明抖店外部寄样已接通。
~~~

## Retro Summary

Playwright 已完成部署前基线与部署后第二条 QA 数据复测；地址持久化、达人信息回显、快速寄样系统内申请创建均通过。外部抖店寄样仍受 SDK 未接通限制，后续若要验证外部发送需先接通对应 SDK 能力。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
- 本次真实链路的“发送成功”范围是系统内寄样申请创建；抖店外部寄样仍为 UNSUPPORTED_BY_SDK。
