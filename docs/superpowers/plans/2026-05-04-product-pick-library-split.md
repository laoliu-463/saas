# 选品库 / 商品库拆分改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把现有 `/product` 从“商品库”改成“选品库”，新增一个“商品库”视图，只有完成选品沉淀的商品才进入商品库并对全员可见。

**Architecture:** 保持现有商品主链路不拆域，继续复用 `ProductService`、`ColonelActivityProductController` 和现有商品卡片/详情组件。在后端为 `product_operation_state` 增加“已入商品库”标记，在前端复用现有商品列表页，通过 route mode 区分“选品库”与“商品库”。

**Tech Stack:** Vue 3、Vite、Naive UI、Spring Boot、MyBatis-Plus、JUnit 5、MockMvc

---

## File Map

- Modify: `backend/src/main/java/com/colonel/saas/entity/ProductOperationState.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/ProductService.java`
- Modify: `backend/src/main/java/com/colonel/saas/controller/ColonelActivityProductController.java`
- Modify: `backend/src/main/java/com/colonel/saas/controller/ProductController.java`
- Modify: `backend/src/main/resources/db/init-db.sql`
- Modify: `backend/src/test/java/com/colonel/saas/service/ProductServiceTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/controller/ColonelActivityProductControllerTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/controller/ProductControllerTest.java`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/views/layout/Sider.vue`
- Modify: `frontend/src/api/activityProduct.ts`
- Modify: `frontend/src/api/product.ts`
- Modify: `frontend/src/views/product/index.vue`
- Modify: `frontend/src/views/product/components/ProductCard.vue`
- Modify: `frontend/src/views/product/ProductDetail.vue`
- Modify: `docs/01-业务闭环.md`
- Modify: `docs/04-开发进度.md`
- Modify: `docs/05-接口与数据模型.md`

## Task 1: 后端补“加入商品库”动作与共享列表

- [ ] 为 `ProductServiceTest` 新增失败测试，覆盖：
  - 选品完成后可写入商品库标记
  - 商品库分页只返回已入库商品
- [ ] 运行定向测试并确认先失败
- [ ] 在 `ProductOperationState` 增加 `selectedToLibrary / selectedAt / selectedBy`
- [ ] 在 `ProductService` 增加：
  - `putIntoLibrary(activityId, productId, operatorId, deptId)`
  - `getSelectedLibraryPage(page, size, keyword, status)`
- [ ] 在 `ColonelActivityProductController` 增加 `POST /{productId}/library-entry`
- [ ] 在 `ProductController` 保留旧 `/products`，但改为返回“商品库已入库商品”
- [ ] 更新 `init-db.sql` 增量字段
- [ ] 重跑后端定向测试

## Task 2: 前端拆“选品库 / 商品库”

- [ ] 先让路由与侧边栏出现两个入口：
  - `/product` -> 选品库
  - `/product/library` -> 商品库
- [ ] 复用现有商品页，用 route mode 区分页面标题、描述、数据源与空态文案
- [ ] 在选品库卡片 / 详情增加“加入商品库”动作
- [ ] 商品库页只展示已入库商品，且对当前业务角色统一可见
- [ ] 跑 `frontend npm run build`

## Task 3: 文档回写

- [ ] 把“商品库闭环”改成“选品库 -> 商品库 -> 转链”
- [ ] 在进度文档里写明新能力和当前验证结果
- [ ] 在接口文档里补：
  - `POST /api/colonel/activities/{activityId}/products/{productId}/library-entry`
  - `/api/products` 当前口径改为商品库共享列表
