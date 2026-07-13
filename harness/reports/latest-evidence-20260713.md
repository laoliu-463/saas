# Evidence Report

## Metadata

- Time: 2026-07-13 15:08 +08:00
- Environment: real-pre
- Scope: frontend
- Branch: codex/ddd-user-role-application
- Base commit before this task: 2105cf40
- Worktree: dirty before and after; unrelated existing changes preserved
- Remote deploy: not requested, not executed

## Changes

- `frontend/src/views/product/components/ProductEditModal.vue`
- `frontend/src/views/product/components/ProductEditModal.test.ts`
- Right-side drawer fields reduced to exclusive-price amount input (yuan), exclusive-price remark, ad-support flag, reward remark, participation requirements, start time and end time.
- Start/end time are read-only snapshot facts; hand-card, tags, script, selling points and remark inputs were removed.

## Verification

| Check | Result | Evidence |
|---|---|---|
| Component test | PASS | 3/3 `ProductEditModal` tests |
| Frontend test suite | PASS | 92 files, 692 tests |
| Typecheck | PASS | `npm --prefix frontend run typecheck` |
| Frontend build | PASS | `npm --prefix frontend run build` |
| Docker rebuild/restart | PASS after retry | First attempt hit a Docker name conflict; retry rebuilt and started all four real-pre services |
| Local health | PASS | `verify-local.ps1 -Env real-pre -Scope frontend`; frontend `/healthz` HTTP 200; compose services healthy |
| real-pre preflight | FAIL/BLOCKED | `runtime/qa/out/real-pre-preflight-20260713-150835/report.md`; admin login HTTP 401, admin token unavailable |
| Product edit API/E2E | BLOCKED | No admin token, so authenticated product-save smoke was not executed |

## Conclusion

PARTIAL. The frontend drawer refactor and amount input are build- and unit-tested and are loaded by the local real-pre frontend container. Authenticated real-pre business verification is blocked by the existing admin login 401. The frontend submits `exclusivePriceAmount`, but the current backend contract does not yet expose a corresponding persistence field/edit endpoint.

## Residual risks

- The edit API call remains unverified against an authenticated real-pre session.
- Start/end time remain read-only snapshot facts. `exclusivePriceAmount` is currently a frontend request field pending backend contract support.
- Existing unrelated dirty files were not staged or modified.

## 本任务补充证据：商品库服务费率与双佣展示

- 代码范围：`ProductService.java`、`ProductServiceFilterTest.java`、`product-library-display.ts`、`product-library-display.test.ts`、`ProductSelectionCard.vue` 及对应测试。
- 根因证据：后端原逻辑把 `activity_ad_cos_ratio`（投放佣金）作为服务费兜底；前端原逻辑把百分制 `1.00` 按小数比例乘 100，显示成 `100%`，且把缺失值与真实 `0` 混淆。
- 修复口径：普通商品只读取上游 `service_ratio`；双佣商品读取 `ad_service_ratio`；缺失服务费返回空值，不再伪造 `0%`；显式上游 `0` 保留为 `0%`。
- 双佣展示：商品卡与详情抽屉分别展示 `双佣金`、`投放期佣`、`投放服务费`；普通商品不展示投放字段。
- 前端定向测试：73/73 PASS；前端全量测试：93 files / 695 tests PASS；typecheck PASS；生产构建 PASS。
- 后端定向测试：`ProductServiceFilterTest` 26/26 PASS；固定 real-pre agent-do 后端 package PASS、前端 build PASS。
- 容器与健康：real-pre backend/frontend 重建重启 PASS；backend `/api/system/health` 200/UP；frontend `/healthz` 200。
- 真实业务验证：`runtime/qa/out/real-pre-preflight-20260713-144159/report.md` 为 FAIL/BLOCKED_AUTH；admin 登录 5 次 HTTP 401，token 不可用，未执行认证商品库业务流。
- 远端部署：未执行；用户未要求。代码提交：`67c6a0fb`；随后已合并远端分支历史，未强推。

### 本任务结论

PARTIAL：代码、测试、构建、容器和健康检查通过；真实 real-pre 商品库页面/API 验收受 admin 401 阻塞，不能声明线上业务流已通过。
