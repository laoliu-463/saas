# DDD-ORDER-006 Handover

## Done

- 订单列表返回 `OrderQueryView`，详情返回 `OrderDetailView`。
- Legacy facade 将旧 `ColonelsettlementOrder` / `OrderDetailResponse` 组装为 query view。
- Controller 和 facade 不再在公共方法签名中直接暴露订单 Entity。

## Verified

- `mvn clean "-Dtest=DddOrder003RoutingTest,OrderControllerTest" test`
- Result: 30 tests, 0 failures, 0 errors.
- `mvn "-Dtest=DddOrder003RoutingTest,OrderQueryViewTest" test`
- Result: 10 tests, 0 failures, 0 errors.
- `agent-do.ps1 -Env real-pre -Scope full`
- Result: backend build PASS, frontend build PASS, Docker restart PASS, local health PASS, real-pre preflight PASS.

## Watch Points

- 后续应补 JSON response snapshot，证明前端字段结构未变。
- `OrderQueryView` 当前使用 `BeanUtils.copyProperties`，新增字段时需同步字段名。
- CLEAN 仍禁止进入，直到 SLIM 和最终验收完成。

## Next Step

- 进入 SLIM / FRONT / VERIFY 前补 JSON response snapshot，确认前端字段兼容。
