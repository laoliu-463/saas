# Evidence Report

## Metadata

- Time: 2026-07-18 23:23 +08:00
- Environment: local `real-pre`
- Scope: backend
- Branch: `codex/ddd-user-role-application`
- Base commit: `6721584d`
- Worktree: dirty（待提交）
- Remote deploy: pending

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
PENDING：提交推送后仅构建并重启远端 backend-real-pre；不备份数据库，不运行迁移，不重建前端/PostgreSQL/Redis。
```

## Retro

- 结论：根因在寄样域事实固化和历史读模型回填，不在 CORS、前端按钮、Token 或抖音网关。
- 改进：寄样复制推广文案回归固定覆盖“新申请固化、历史申请回填、商品主记录缺失”三种路径。
- 验证：红绿单测、寄样控制器全量回归、本地容器健康、管理员真实 API 调用。

## Conclusion

`PARTIAL`：本地修复、构建、容器和真实 API 验证通过；远端部署与远端业务验证待完成。

## Residual Risk

- 后端全量测试仍有本次修改前已存在的大类文件行数债务基线失败；本次保持文件物理行数不变。
- 远端尚未验证，不能把本地结果等同于远端完成。
