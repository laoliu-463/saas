# Evidence Report

## Metadata

- Time: 2026-07-18 23:35 +08:00
- Environment: local `real-pre` + remote `real-pre`
- Scope: backend
- Branch: `codex/ddd-user-role-application`
- Deployed commit: `0376d4816eb11254f8ed18ef3f8913f55be7fd78`
- Worktree: dirty（仅本报告待提交）
- Remote deploy: backend-only completed

## Owned Files

```text
backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java
backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java
docs/领域/寄样域.md
.claude/subagents/寄样域代理.md
harness/engineering/context.md
harness/reports/current/latest-restore-admin-promotion-copy.md
```

## Root Cause

- 寄样创建只保存了本地商品关联，没有固化活动 ID 与抖音商品 ID。
- 历史寄样详情读取快照时依赖商品主记录存在，商品主记录缺失会短路快照回填。
- `promotion-copy` 在调用推广网关前要求活动、商品、达人推广事实齐全，因此在网关和 Token 逻辑之前返回 461。

## Change

- 新寄样从活动商品快照固化 `activityId` 与 `activityProductId`。
- 历史寄样按 `sample_request.product_id` 保存的快照关联 ID 只读回填活动和抖音商品事实，不更新历史数据。
- 未修改数据库结构、状态机、权限、前端或抖音网关。

## Build And Tests

```text
RED: getSampleById_shouldRecoverPromotionFactsFromSnapshotWhenProductIsMissing
     expected activityId=3916506, actual=null
GREEN: focused regression 1/1 PASS
SampleControllerTest: 84/84 PASS
Backend package: PASS (mvn -f backend/pom.xml -DskipTests package)
Full backend suite baseline: 3300 run / 3296 PASS / 3 skipped / 1 FAIL
Known failure: LargeServiceDebtRedlineTest, SampleApplicationService 3665 > recorded 3613
Current source physical lines: 3665; HEAD physical lines: 3665（本次未恶化）
```

## Local Runtime

```text
backend-real-pre: healthy（已重建）
frontend-real-pre: healthy（未重建）
postgres-real-pre: healthy（未重启）
redis-real-pre: healthy（未重启）
GET /api/system/health: status=UP
```

## Business Validation

- 首次统一入口预检在后端刚重启时失败；容器健康后原命令复跑 PASS。
- Preflight evidence: `runtime/qa/out/real-pre-preflight-20260718-232238/`。
- 管理员登录、`real-pre` 环境守卫、抖音 Token readiness、数据库 schema readiness、可复用推广映射均 PASS。
- 只读详情验证：HTTP 200，返回 `activityId=3929905`、`productExternalId=3790316043336024472`、达人推广标识非空。
- 真实业务接口验证：`POST /api/samples/6c220990-2749-4614-b285-091721474454/promotion-copy` 返回 HTTP 200 / code 200 / copyText 非空；未命中“缺少活动、商品或达人推广事实”461。

## Remote Deploy

```text
remote checkout/image revision: 0376d4816eb11254f8ed18ef3f8913f55be7fd78
backend-real-pre: replaced, healthy, container bff3897a64db...
backend JAR guard: PASS, host/container size=82324342
frontend-real-pre: container ID unchanged
postgres-real-pre: container ID unchanged
redis-real-pre: container ID unchanged
backup directory count: 13 -> 13（未执行数据库备份）
Flyway history: 3/3 -> 3/3（未执行数据库迁移）
remote worktree: clean
rollback commit/image: 7efb0f8e577b00a3abf14efb7ab0a0fa78426acb
```

## Remote Business Validation

- 只读 SQL：当前 9 条寄样记录均可由寄样单关联的 `product_snapshot` 补齐活动 ID 与抖音商品 ID，达人推广标识均非空。
- 远端 `ADMIN_PASSWORD` 与数据库现有管理员口令不一致；再使用本地已通过 preflight 的 QA 管理员凭据验证，仍被远端登录拒绝。
- 应用内浏览器只有本地 `127.0.0.1:3001` 会话，没有远端登录会话；未绕过认证，也未尝试猜测其他口令。
- 因此远端后端部署、健康、事实数据和制品一致性已验证；远端登录后的 `promotion-copy` 正向调用为 `BLOCKED_AUTH`，不能写成 PASS。

## Retro

- 结论：根因在寄样域事实固化和历史读模型回填，不在 CORS、前端按钮、Token 或抖音网关。
- 改进：寄样复制推广文案回归固定覆盖“新申请固化、历史申请回填、商品主记录缺失”三种路径。
- 验证：红绿单测、寄样控制器全量回归、本地容器健康、管理员真实 API 调用。

## Conclusion

`PARTIAL`：本地修复、构建、容器和真实 API 验证通过；远端后端单服务部署与健康检查通过，且确认未备份/迁移数据库、未重建前端或数据服务；远端登录后业务 API 验收因管理员凭据不一致为 `BLOCKED_AUTH`。

## Residual Risk

- 后端全量测试仍有本次修改前已存在的大类文件行数债务基线失败；本次保持文件物理行数不变。
- 需要有效的远端用户会话再次点击“复制链接”，才能把远端业务验收从 `BLOCKED_AUTH` 提升为 PASS。
