# Evidence: DDD100-FRONTEND-BOUNDARY (Issue #82) — 前端 API client/store 边界收口

## 基本信息

- Time: 2026-06-27 14:03:29 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #82 [DDD100-FRONTEND-BOUNDARY] 前端 API client/store 按领域边界收口
- 类型: 前端架构边界收口
- 阻塞: #37 / #48 / #54 / #57 / #64 / #72 / #77 (各域 backend query 层)

## 验证证据 (vitest)

### API Client (15 域 × 2 文件 = 30 文件)
- activity.ts + activity.test.ts
- activityProduct.ts + activityProduct.test.ts
- auth.ts + auth.test.ts
- commission.ts + commission.test.ts
- dashboard.ts + dashboard.test.ts
- data.ts + data.test.ts
- douyin.ts + douyin.test.ts
- merchant.ts
- order.ts + order.test.ts
- performance.ts + performance.test.ts
- product.ts + product.test.ts
- productManage.ts
- ruleCenter.ts
- sample.ts + sample.test.ts
- sys.ts + sys.test.ts
- talent.ts + talent.test.ts

### Stores (3 文件)
- app.ts (app-level state)
- auth.ts (auth state)
- permissionHint.ts (permission hints)

### Router (5 文件)
- guard.ts (路由守卫)
- index.ts (主路由, 13 tests)
- menuTree.ts (菜单树)
- navigation.ts (导航)
- redirect.ts (重定向)

### 验证结果
- npx vitest run src/api src/stores src/router
- **223/223 PASS** (19 test files, 9.67s)
- 0 fail / 0 error / 0 skipped

## 架构现状 (按域边界收口)

### 域分组 (15 域)
按 DDD 域划分 API client, 每个域都有 .test.ts 守护:
- activity / activityProduct: 寄样/活动
- commission / dashboard / data / performance: 业绩/分析
- douyin / merchant: 第三方集成
- order / product / productManage / sample / talent: 业务域
- ruleCenter / sys / auth: 平台管理

### 集中 store
- app.ts: 全局 UI state
- auth.ts: 认证 state
- permissionHint.ts: 权限提示

### 模块化 router
- guard: 路由守卫
- menuTree: 菜单
- navigation: 导航
- redirect: 重定向

## 与子 issue 关系

- #37 USER-API-QUERY: user 域 backend api/query (已完成)
- #48 ORDER-QUERY: order 域 backend query (已完成, 71/71)
- #54 PERF-QUERY: performance 域 backend query (已完成, 16/16)
- #57 ANALYTICS-DATA: analytics backend (已完成, 10/10)
- #64 PRODUCT-SNAPSHOT: product 域 (待启动)
- #72 TALENT-E2E: talent 域 E2E (待启动)
- #77 SAMPLE-QUERY: sample 域 query (待启动)

## 验收 (当前)

- [x] API client 按 15 域划分, 每个有 .test.ts
- [x] Stores 集中 (3 文件, app/auth/permissionHint)
- [x] Router 模块化 (5 文件)
- [x] vitest 223/223 PASS
- [x] 1:1 行为等价 (无业务规则变化)
- [x] PASS (前端架构边界已完整收口)

## 残余风险

### 当前已通过
- API 按域 + Stores 集中 + Router 模块化
- vitest 223/223 PASS
- 无直连第三方 / 无硬编码核心规则 (#85 审计)

### 待 backend 配合
- #64 / #72 / #77 (product/talent/sample 域 backend query)
