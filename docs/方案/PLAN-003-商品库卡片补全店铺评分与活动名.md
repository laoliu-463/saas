# PLAN-003 商品库卡片补全店铺评分 + 活动名

更新时间：2026-06-01

## 背景

`/product`（商品库）页面用 `ProductLibrary.vue` → `ProductSelectionCard.vue` 渲染卡片。
卡片 hover 时弹出"商品信息"抽屉（`infoFields`，`ProductSelectionCard.vue:286-330`）。

**症状**：
- 抽屉「活动」字段一直显示 `-`，从来没有活动名
- 抽屉根本没有「店铺评分」字段

**根因（两层）**：
1. 后端 `/api/products` 接口（`ProductController.java:204`）走 `getSelectedLibraryPage` →
   `toLegacyProduct`（`ProductService.java:3363-3430`）构造 `Product` 实体返回。
   - `Product` 实体（`Product.java`）**没有** `activityName` 和 `shopScore` 字段
   - `toLegacyProduct` 也**没**把这两个字段塞进去
   - 活动商品接口 `/colonel/activities/{id}/products` 走 `toActivityProductView`（line 3457-3532），
     那里 `activityName`（line 3469）和 `shopScore`（line 3481）都是有的 — 但商品库接口不走这条路
2. 前端 `infoFields` 漏了「店铺评分」字段。`ProductCardView` 已经有 `shopScore: number | null` 字段
   （`product-library-display.ts:369`），注释明确说"用于商品库卡片 hover 抽屉中的'店铺评分'字段"，
   `ProductSelectionCard.test.ts:86` 也预留了 `shopScore: null` —— 属于规划了没写完。

**结论**：两个症状同两个根因。

## 方案

### 后端

#### 1. `Product.java` 加虚拟字段
- `activityName: String`（`@TableField(exist = false)`，对应数据库 product 表的虚拟字段）
- `shopScore: Integer`（同）

#### 2. `ProductService.toLegacyProduct` 注入
- `product.setShopScore(resolveShopScoreFromSnapshot(snapshot));` — `resolveShopScoreFromSnapshot`
  已有（line 4514 附近），复用
- `product.setActivityName(activityName);` — 接收 `Map<String, String> activityNameMap` 参数，
  按 `snapshot.getActivityId()` 取名

#### 3. `collectSelectedLibraryProducts` 预加载 activityName
仿 `assigneeNameMap` 模式（`ProductService.java:320-323`）：
- 收集本批 state 的 `activityId` 集合
- 一次性查 `colonel_activity` 表，把 `activityId → activityName` map 构造好
- 透传给 `toLegacyProduct`

注意：`toLegacyProduct` 当前有 3 个重载（line 3355/3359/3363），新参数加在最低层那个（line 3363），
其它 2 个重载传 `null`。

### 前端

#### 1. `ProductSelectionCard.vue` 加「店铺评分」字段
在 `infoFields` 中 `shop` 后插入 `shopScore`：
```ts
{
  key: 'shopScore',
  label: '店铺评分',
  value: props.card.shopScore !== null ? String(props.card.shopScore) : '',
  copyText: props.card.shopScore !== null ? String(props.card.shopScore) : ''
}
```

`activity` 字段保持不变，等后端补传 `activityName` 后会自动显示。

#### 2. 抽屉布局调整
原 7 字段 → 8 字段。252px 宽，每行 5 个卡片（grid `repeat(5, 252px)`，`ProductLibrary.vue:488`）。
当前抽屉 max-height 280px（`ProductSelectionCard.vue:597`），可能不够装 8 行。
**调整**：
- max-height 280 → 320px（增加 40px）
- 字段行 gap 4px → 3px（line 637）
- 行高 line-height 1.4 → 1.35（line 645）

避免触屏分支（line 700-714）受影响 — 它 max-height: none，不受这个限制。

#### 3. `ProductSelectionCard.test.ts` 测试更新
- `baseCard.shopScore` 改 `null` → `90`
- 加用例：「店铺评分」字段在抽屉中渲染为 "90"
- 加用例：null 时显示 `-`

### 验证

1. `cd backend && mvn test -Dtest=ProductServiceShopScoreTest` — 已有测试覆盖 activity 商品链路，
   增补一个 `toLegacyProduct` 透传 `shopScore` 和 `activityName` 的测试
2. `cd frontend && npx vitest run components/product/ProductSelectionCard.test.ts` — 抽屉字段渲染
3. `cd frontend && npm run build` — 类型检查
4. E2E：复用 `tests/e2e` 里的 product-card-hover 脚本，截一张抽屉展开图，肉眼确认

## 范围

✅ 商品库（`/product`）卡片 hover 抽屉显示「活动」+「店铺评分」

⛔ 不动：
- 商品管理表格页（`/product/manage/products` 用 `ProductManageTable`，不在范围）
- 商品详情页（`/product/detail`）
- 活动商品页（`/colonel/activities/{id}/products`）— 那条链路字段已经全了
- 5 个卡片/行的布局（`ProductLibrary.vue:488` 保持 `repeat(5, 252px)`，用户已确认）

## 风险

- **抽屉 max-height 加大** → hover 抽屉展开时占位略高，但 CSS `position: absolute` 覆盖下方卡片
  （`ProductSelectionCard.vue:579-580`），不影响网格布局
- **activityName 查表** → 走 `colonel_activity` 表，按 `activityId` 查；该表已有 `name` 字段
  （`activity-list-display.ts:88` 用过），复用现有 mapper
- **shopScore 透传** → `resolveShopScoreFromSnapshot` 已处理 Integer/String/缺失/非法 4 种情况
  （`ProductService.java:4514-4524`），前端 `parseShopScore` 也对得上

## 不在范围（后续专项）

- 卡片置顶商品：当前是 v-if 渲染，已 OK
- 同品去重：见 V1-商品域现状审计 P-09，本方案不动
- 商家型/团长型区分：见 P-11，本方案不动
