# Evidence: PRODUCT-FIX-002 Issue #27

## 基本信息

- 时间: 2026-06-26 16:31 Asia/Shanghai
- 环境: local real-pre
- 分支: feature/ddd/DDD-VERIFY-001
- Issue: #27 `/product/manage/products` fallback 修复端到端验证
- 关键提交: 3a38ce6d, 08fd8729, 7fe57afd

## 现象

历史问题是直接访问 `/product/manage/products` 时会自动 fallback 到 `assigned[0]`，并改写 filters / URL 语义，导致活动商品数量与显式活动查询不一致。

## 修复摘要

- 新增 `resolveActivityContextForManageProductsPath`，把产品管理页活动上下文显式分成 `empty` / `loading` / `ready` / `forbidden`。
- 新增 `CurrentActivityBanner.vue`，把当前活动、无权限和未选择状态显式展示。
- 移除 `/product/manage/products` 的 `assigned[0]` fallback 与活动页兜底分支。
- 修复初始化竞态：产品管理页先加载可见活动选项，再按 query 加载活动商品。

## 单元与构建验证

- `npm --prefix frontend run test -- src/views/product/product-page-data-source.test.ts src/views/product/ActivityList.test.ts`: PASS，2 files / 11 tests。
- `npm --prefix frontend run test -- src/views/product src/router`: PASS，32 files / 272 tests。
- `npm --prefix frontend run build`: PASS。
- `mvn -q -f backend/pom.xml -DskipTests package`: PASS。

## real-pre Harness 验证

执行项目统一入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -ContentMaintenance off -Message "fix: load product manage activity options before products"
```

结果：

- backend build: PASS
- frontend build: PASS
- Docker backend/frontend restart: PASS
- backend health: PASS
- frontend healthz: PASS
- business validation `npm run e2e:real-pre:p0:preflight`: PASS
- evidence: `harness/reports/evidence-20260626-163014.md`

## 浏览器 Network Smoke

使用 Chromium + admin 登录态监听 `/api/colonel/activities/{id}/products`。

```json
{
  "ok": true,
  "checks": {
    "emptyNoProductRequest": true,
    "readyHasProductRequest": true,
    "forbiddenNoProductRequest": true,
    "banners": true
  }
}
```

逐项结果：

- `/product/manage/products`: banner `请先选择活动`，活动商品请求 0 次。
- `/product/manage/products?activityId=3916506`: banner `当前活动: 星链达客-zy (3916506)`，活动商品请求 1 次，HTTP 200。
- `/product/manage/products?activityId=99999999`: banner `无权限查看当前活动`，活动商品请求 0 次。

## 结论

#27 的核心验收通过：无 query 不再 fallback 到 `assigned[0]`，有效 query 可加载活动商品，无权限 query 不触发活动商品请求。仓库未发现 `39-product-manage-fallback.spec.ts`，本轮用真实浏览器 Network smoke 和现有产品/路由测试补足验证证据。
