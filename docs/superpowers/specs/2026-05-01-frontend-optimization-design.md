# 前端页面设计优化规格

> 日期: 2026-05-01
> 方案: Approach B — Structured Refresh
> 风格: 延续现有 coral-red 设计 token 体系

## 概述

对 5 个核心业务页面进行设计优化，统一视觉层级、替换硬编码样式为设计 token、补充数据摘要栏、拆分过大文件。所有页面保持 Naive UI 组件库 + CSS 变量体系不变。

## 设计 Token 引用

所有页面统一使用 `tokens.css` 中定义的变量：
- 颜色: `--color-primary`, `--color-success`, `--color-warning`, `--color-danger`, `--color-info`
- 文本: `--text-primary`, `--text-secondary`, `--text-tertiary`
- 间距: `--spacing-xs`(4) `--spacing-sm`(8) `--spacing-md`(12) `--spacing-lg`(16) `--spacing-xl`(24) `--spacing-2xl`(32)
- 圆角: `--radius-sm`(6) `--radius-md`(8) `--radius-lg`(12) `--radius-xl`(16)
- 阴影: `--shadow-sm`, `--shadow-md`, `--shadow-lg`

---

## 1. Dashboard (`frontend/src/views/dashboard/index.vue`)

**现状问题:**
- 4 个 KPI 卡片数据全部硬编码
- 趋势图区域为占位文字 `[趋势图表加载中...]`
- 排行榜数据硬编码
- 样式中硬编码颜色 `#999`, `#18a058`, `#d03050`

**优化方案:**

### 1a. 统计卡片
- 保留 `n-grid` 4 列布局，绑定 `stats` 数组到 API 返回数据
- 趋势值从 API `trend` 字段获取，正值显示绿色（`--color-success`），负值显示红色（`--color-danger`）
- 去掉硬编码颜色，改用 token 变量

### 1b. 趋势图区域
- 移除 `[趋势图表加载中...]` 占位符
- 替换为纯 CSS 横向柱状图：近 7 天每日订单量/归因率，用 `div` + `height` 百分比 + `background: var(--color-primary)` 实现
- 数据来源：`/dashboard/summary` API 返回近 7 天趋势数组；如 API 未就绪，显示空态提示
- 不引入 ECharts，保持轻量

### 1c. 右侧面板
- 保留排行榜（前 5 名招商团队表现）
- 新增"快捷入口"区域：订单归因、商品库、达人 CRM、数据看板 4 个跳转按钮
- 按钮样式：`n-button quaternary type="primary"`

### 1d. 样式清理
- `padding: 24px` → `padding: var(--spacing-xl)`
- `border-radius: 12px` → `border-radius: var(--radius-lg)`
- `#999` → `var(--text-tertiary)`
- `#18a058` → `var(--color-success)`
- `#d03050` → `var(--color-danger)`

**文件变更:** 仅修改 `dashboard/index.vue`（~88 行 → ~100 行）

---

## 2. Data 核心看板 (`frontend/src/views/data/index.vue`)

**现状问题:**
- 使用 `h2.page-title` 而非 `PageHeader` 组件
- 趋势百分比硬编码 `"较上周 +12.5%"`
- 快捷入口使用 emoji 图标

**优化方案:**

### 2a. 页面头部
- `h2.page-title` → 替换为 `<PageHeader title="核心看板" description="实时掌握订单、金额、服务费和利润等核心指标。">`
- actions slot 中保留"查看完整明细"按钮

### 2b. 趋势数据
- `metrics` 对象增加 `trendOrders`, `trendAmount`, `trendFee`, `trendProfit` 字段（从 API 返回）
- 趋势文字条件渲染：有数据时显示 `较上周 +X%`，无数据时不显示 `.metric-trend`
- 正/负值分别使用 `--color-success` / `--color-danger`

### 2c. 快捷入口
- emoji 替换为 SVG 图标（复用已有 SVG pattern 风格）

### 2d. 样式清理
- 移除内联 `style="margin-bottom: 24px"` → class + token
- 保持已有的 token 使用，仅修复少数遗漏

**文件变更:** 仅修改 `data/index.vue`（~332 行，改动量小）

---

## 3. Talent CRM (`frontend/src/views/talent/index.vue`)

**现状问题:**
- 硬编码 `padding: 24px`, `border-radius: 8px`, `background: #fff`
- 无汇总统计，无法一眼看到达人总量

**优化方案:**

### 3a. 摘要统计栏
- 在 `.toolbar` 上方新增一行摘要栏：
  ```
  总达人数: 156 · 公海: 89 人 · 私海: 67 人
  ```
- 使用 `n-space` + `n-tag` 呈现
- 数据来源：前端从当前页列表数据聚合（`poolStatus` 字段计数），无需后端改动

### 3b. 样式清理
- `.talent-page` padding → `var(--spacing-xl)`
- `.toolbar` border-radius → `var(--radius-md)`
- `.toolbar` background → `var(--color-bg-card)` (如存在) 或保留 `#fff` 但注释 token
- `.main-card` border-radius → `var(--radius-md)`

**文件变更:** 仅修改 `talent/index.vue`（~372 行 → ~390 行）

---

## 4. Orders 订单归因 (`frontend/src/views/orders/index.vue`)

**现状问题:**
- 列渲染中硬编码 `font-size: 12px; color: #999`
- 页面 padding 硬编码
- 无归因率概览

**优化方案:**

### 4a. 归因摘要栏
- 在 `.toolbar` 上方新增一行：
  ```
  已归因: 142 单 (94.2%) · 待排查: 6 单 · 部分归因: 3 单
  ```
- 使用 `n-space` + `n-tag` 呈现，颜色分别：success / error / info
- 数据来源：前端从当前页 `records` 聚合（仅反映当前页数据，分页场景下为近似值）；如后端后续提供 `summary` 字段则直接绑定

### 4b. 样式清理
- 列渲染中 `font-size: 12px; color: #999` → `font-size: 12px; color: var(--text-tertiary)`
- `.orders-page` padding → `var(--spacing-xl)`
- `.toolbar` padding/border-radius → tokens

**文件变更:** 仅修改 `orders/index.vue`（~288 行 → ~300 行）

---

## 5. Product 商品库 (`frontend/src/views/product/index.vue`)

**现状问题:**
- 文件 1121 行，违反 <800 行规范
- 商品卡片渲染、筛选器逻辑全部内联

**优化方案 — 文件拆分:**

### 5a. 提取 `ProductFilters.vue` (~120 行)
- **Props:** `filters`, `productOptions`, `categoryOptions`, `commissionOptions`, `yesNoOptions`, `sortOptions`, `selectedProduct`, `loading`
- **Emits:** `update:filters`, `update:selectedProduct`, `search`, `refresh`
- 包含：商品搜索 select、7 个筛选 select、排序 select、搜索/重置按钮

### 5b. 提取 `ProductCard.vue` (~350 行)
- **Props:** `product`, `expanded`
- **Emits:** `toggle`, `detail`, `audit`, `assign`, `copyLink`, `createSample`
- 包含：商品卡片主体、展开的快捷操作面板、商品图片/信息/价格展示

### 5c. 精简 `index.vue` (~500 行)
- 引入 `ProductFilters` 和 `ProductCard`
- 保留：页面布局、数据加载逻辑、Modal/Drawer 控制、路由参数处理
- 样式统一使用 tokens

### 5d. 目录结构
```
frontend/src/views/product/
├── index.vue              (~500 行)
├── components/
│   ├── ProductCard.vue    (~350 行)
│   └── ProductFilters.vue (~120 行)
├── ProductDetailModal.vue (已有)
├── ProductAuditDialog.vue (已有)
├── ProductAssignDialog.vue (已有)
└── ProductOperationLogDrawer.vue (已有)
```

**文件变更:** 新增 2 个文件，重写 1 个文件

---

## 实施优先级

| 优先级 | 页面 | 工作量 | 说明 |
|--------|------|--------|------|
| P1 | Product (拆分) | ~2h | 最高风险，文件超限 |
| P2 | Dashboard | ~1h | 移除占位符，绑定 API |
| P3 | Orders | ~0.5h | 摘要栏 + token 替换 |
| P4 | Talent | ~0.5h | 摘要栏 + token 替换 |
| P5 | Data | ~0.5h | PageHeader + 趋势绑定 |

**总计:** ~4.5h

## 验证标准

1. `vue-tsc --noEmit` 零错误
2. `vite build` 构建成功
3. 所有页面在浏览器中正常渲染
4. 无硬编码颜色残留（`grep -r '#999\|#fff\|#18a058\|#d03050' src/views/` 仅允许白名单值）
5. Product 页面拆分后 `index.vue` < 600 行

## 不在范围

- 后端 API 改动（本规格纯前端优化）
- 新增路由或菜单
- 移动端适配
- 国际化
