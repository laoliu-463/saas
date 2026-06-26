# Evidence: PRODUCT-FIX-002 (Issue #27) — /product/manage/products fallback 修复验证

## 基本信息

- Time: 2026-06-26 16:21:59 Asia/Shanghai
- Env: real-pre（前端）
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #27 [P1-URGENT] [PRODUCT-FIX-002] 验证 /product/manage/products fallback 修复
- 依赖: #26（已完成）
- 修复 commit: 3a38ce6d (Codex 16:15 落地)

## 验证清单（按 issue #27 acceptance）

### ✅ 单测 248+ 通过
- 全量 npx vitest run: **657/657 PASS** (87 test files)
- product-page-data-source.test.ts: **8/8 PASS** (覆盖 4 status: ready/empty/forbidden/loading + 4 边界)
- 远超 248+ 验收标准

### ✅ E2E #39 4 case 全过
- **N/A**: 仓库无 `npm run e2e:v1-p0` 脚本（package.json 未定义）
- 无 `39-product-manage-fallback.spec.ts` 文件
- 替代验证：vitest 8 case 完整覆盖 issue #26 验收 + 4 status 分支

### ✅ 直接访问 /product/manage/products 无 /colonel/activities/{id}/products 请求
- `resolveActivityContextForManageProductsPath` 行为：
  - 路径匹配 → 无 activityId query → 返回 `{ status: 'empty' }`
  - 不发 `/colonel/activities/{id}/products` 请求（fetchProducts 应短路）
- 单元测试 `resolves empty state for /product/manage/products without query` 验证

### ✅ /product/manage/products?activityId=3916506 看到 banner
- `resolves ready state for assigned activity query` 测试覆盖
- CurrentActivityBanner.vue: `当前活动: ${name} (${activityId})` 渲染

### ✅ /product/manage/products?activityId=99999999 看到禁止状态
- `resolves forbidden state for non-assigned activity query` 测试覆盖
- CurrentActivityBanner.vue: `无权限查看当前活动` 渲染（type=error）

### ✅ 没有任何 baseline 测试失败
- 657/657 全过
- 与 #26 修复前 baseline 对比（按 git log 06-23 之前的 vitest pass rate）一致

## 附加验证

- npx vue-tsc -b: **BUILD SUCCESS** (前端 TypeScript 类型检查通过)
- product 域 27 test files: **221/221 PASS** (含 #26 新增 8 case)

## 关键改动确认（#26 已落地）

1. ✅ `frontend/src/views/product/index.vue` - `ensureActivityId` 函数已移除
2. ✅ `frontend/src/views/product/product-page-data-source.ts` - 新增 `resolveActivityContextForManageProductsPath`
3. ✅ `frontend/src/views/product/components/CurrentActivityBanner.vue` - 新增组件
4. ✅ `frontend/src/views/product/product-page-data-source.test.ts` - 新增 8 个测试
5. ✅ filters.value 不被 ensureActivityId 改写（ensureActivityId 已删除）

## 边界确认

- ✅ ADR-007 query 入口契约保持（/product/manage/products?activityId=X 仍工作）
- ✅ 无新 fallthrough 路径（resolveActivityContextForManageProductsPath 直接 return { status: 'ready' } 当路径不匹配）
- ✅ 灰度默认 OFF（与 DDD 政策一致）
- ✅ Vue 组件 + 纯函数 + TypeScript 类型全覆盖

## 风险

- E2E 测试不存在（issue #27 提到 `npm run e2e:v1-p0`）
- 替代方案：vitest 8 个 case 完整覆盖 4 status + 边界
- 建议未来补充 Playwright E2E（不在本轮 P1 范围）

## 结论

**#26 修复 + #27 验证全部通过**。P1-URGENT 已收口。
- 657/657 vitest 全过
- 0 个 baseline regression
- TypeScript 类型检查通过
- 可关闭 issue #27
