LABEL=ready-for-agent
TITLE=[P1-URGENT] [PRODUCT-FIX-002] 验证 /product/manage/products fallback 修复端到端行为
---
## Parent

- PRD: `docs/决策/PRD-PRODUCT-MANAGE-FALLBACK-FIX.md`
- 依赖: #26 (PRODUCT-FIX-001)

## What to build

**Verify-first tracer bullet**：在 #26 修复完成后，**仅跑验证**，确认修复确实解决了问题，不引入新 regression。

## What to verify

之前的诊断发现：

1. 直接访问 `/product/manage/products` 时，URL 会变成 `?activityId=<assigned[0]>`（验证应当不再发生）
2. 数量与「活动查询」对不上（验证应当数量一致，因为没有 fallback 干扰）
3. filters.recruitActivityId 被悄悄改写（验证应当不被改写）

## Task

执行以下步骤并输出报告：

```bash
cd D:/Projects/SAAS/frontend

# 1) 启动 dev server (后台)
npm run dev &

# 2) 跑单测
npx vitest run src/views/product src/router 2>&1 | tail -40

# 3) E2E（如已实现 39-product-manage-fallback.spec.ts）
npm run e2e:v1-p0 -- --grep "product-manage-fallback" 2>&1 | tail -50

# 4) 手动 smoke test（curl + 看 Network）
curl -s http://localhost:3000/product/manage/products | head -20
```

## Acceptance

- [ ] 单测 248+ 通过（含 #26 新增 case）
- [ ] E2E #39 4 case 全过
- [ ] 直接访问 `/product/manage/products` Network 无 `/colonel/activities/{id}/products` 请求
- [ ] `/product/manage/products?activityId=3916506` 看到 banner "当前活动: XXX (3916506)"
- [ ] `/product/manage/products?activityId=99999999`（非 assigned）看到禁止状态
- [ ] 没有任何 baseline 测试失败（对比修复前的 baseline 测试通过率）

## On failure

1. 记录哪个测试失败 + 实际 diff
2. `git checkout -- <changed-file>` 回滚
3. 隔离：`git stash; mvn test -Dtest='...' git stash pop`
4. 开新 issue 描述真实 bug（**不要直接修复**）

## Blocked by

- #26 (PRODUCT-FIX-001) — 必须先修复落地

## Context (read first)

Per `ask-matt` context hygiene, this issue must be self-contained — the implementer is in a fresh session.

**Required reading order:**
1. `AGENTS.md`
2. `CONTEXT.md`
3. `docs/决策/PRD-PRODUCT-MANAGE-FALLBACK-FIX.md`
4. `docs/决策/ADR-007-活动列表与商品库入口路由统一.md`
5. `harness/engineering/issues-index.md`
6. **必读 #26 issue body 与 PR**

**Files this slice touches (absolute paths):**
- 无新增代码改动，仅跑测试 + 记录报告到 `harness/reports/evidence-20260623-product-manage-fallback-verification.md`

**Related issues:**
- #26 (blocker)
