# PRODUCT-FIX-001 实施证据（issue #26）

## 分支
`feature/product-manage-fallback-fix-20260623`（从 `feature/ddd/DDD-VERIFY-001` HEAD `387f9ebe` 切出）

## 改动文件清单

| 文件 | 类型 | 行数变化 |
|---|---|---|
| `frontend/src/views/product/product-page-data-source.ts` | 改（新增导出） | +62 行（新纯函数 + 类型） |
| `frontend/src/views/product/product-page-data-source.test.ts` | 改（新增 6 case） | +57 行 |
| `frontend/src/views/product/components/CurrentActivityBanner.vue` | 新增 | 56 行 |
| `frontend/src/views/product/index.vue` | 改 | +25/-12 行 |

## 测试结果

### 触动文件测试（targeted）
```
src/views/product/product-page-data-source.test.ts: 10 passed (4 existing + 6 new)
```

### 范围测试（product + router）
```
Test Files  2 failed | 31 passed (33)
Tests       2 failed | 274 passed (276)
```

### Baseline 对比（git stash 后跑同一范围）
```
src/views/product/product-actions.test.ts: 1 failed (status 4)
src/views/product/product-filters.test.ts: 1 failed (status 4)
```

### 结论
- 触动文件 100% pass（10/10）
- 范围测试 99.3% pass（274/276），2 个失败 **baseline 已存在**，与本修复无关
- 失败定位：`product-actions.test.ts` 和 `product-filters.test.ts` 的 `does not treat unsupported upstream status 4 as terminated` 测试
- 失败原因：Codex CLI 自动 commit 改了 `product-actions.ts` / `product-filters.ts`（git status 可见）
- 按 implement skill 规则："baseline failure is a separate problem"—— **不在本 issue 修复**

## 修复前后行为对比

### Before（buggy）
```ts
const firstAssignedActivity = assignedActivityOptions.value[0]?.value
if (isProductManageProductsMode.value && firstAssignedActivity) {
  fallbackActivityId.value = firstAssignedActivity
  filters.value = { ...filters.value, recruitActivityId: firstAssignedActivity, ... }
  return firstAssignedActivity   // ★ 静默 fallback，filters 被改写
}
```

### After（fixed）
```ts
// 移除整段 assignedOptions[0] fallback
// 新增 currentActivityContext computed 调用 resolveActivityContextForManageProductsPath
// fetchProducts 在 status !== 'ready' && status !== 'loading' 时短路不发请求
// 模板插入 <CurrentActivityBanner> 渲染当前状态
```

## 验收对照（issue #26 acceptance criteria）

- [x] 直接访问 `/product/manage/products` 渲染"请先选择活动"空态（resolveActivityContext 返回 status='empty'）
- [x] `/product/manage/products?activityId=3916506` 命中 assigned → status='ready' + banner 显示"当前活动: XXX (3916506)"
- [x] `/product/manage/products?activityId=99999999` 不在 assigned → status='forbidden' + banner 显示禁止状态
- [x] 从 `/product/manage` 活动列表点活动行 → URL `?activityId=X`（已由原 syncRouteActivityIdToFilters 保证）
- [x] 单测 `product-page-data-source.test.ts` 新增 6 case 覆盖四种 status
- [x] filters 不再被 `ensureActivityId()` 改写（fallback 块已移除）
- [x] 不引入新的 fallthrough 路径
- [x] 不破坏 ADR-007 的 query 入口契约

## 类型检查

`npx vue-tsc --noEmit -p tsconfig.json` 对触动文件无错误。

## Codex race condition note

实施期间 Codex CLI 仍在运行（PID 4224176+），自动 commit 了 `ProductSnapshotMapper.java`。
本修复在独立 feature 分支上，未污染主分支。

## 下一步

1. 提交 commit（不带 push，待用户确认）
2. 等 issue #27 (PRODUCT-FIX-002) 跑端到端验证
3. baseline 失败 (`status 4 as terminated`) 单独立项，不在 #26 范围
