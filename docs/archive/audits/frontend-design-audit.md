# 前端设计优化一致性审计报告与改造清单

## Task 1: 视觉一致性审计报告

通过对核心业务页面（`/dashboard`, `/orders`, `/product` 等）以及 Layout 和 Login 页面的代码扫描，发现部分页面仍存在未走 Design Token 的硬编码值。

| 页面 | 违规类型 | 代码位置 | 建议替换为 |
| --- | --- | --- | --- |
| `views/Login.vue` | 硬编码色 `#FF4757`, `#FF6B81` | L10, L152 | `var(--color-primary)`, `var(--color-primary-hover)` |
| `views/Login.vue` | 字号硬编码 `32px`, `16px` | L195, L203 | `var(--text-3xl)`, `var(--text-lg)` |
| `views/Login.vue` | 阴影硬编码 | L247 | `var(--shadow-card)` |
| `views/layout/Header.vue` | 硬编码色 `#ff4757` | L110 | `var(--color-primary)` |
| `views/layout/Header.vue` | 阴影硬编码 `rgba(255, 71, 87, 0.25)` | L114 | `var(--shadow-sm)` |
| `views/layout/Header.vue` | 字号硬编码 `17px`, `14px`, `11px` | L129, L144, L174 | `var(--text-xl)`, `var(--text-base)`, `var(--text-xs)` |
| `views/product/ProductDetail.vue` | 硬编码色 `#f8f9fb`, `#dcdfe6` | L694, L739 | `var(--bg-sidebar)`, `var(--border-color)` |
| `views/product/ProductDetail.vue` | 圆角硬编码 `8px`, `999px` | L694, L742 | `var(--radius-md)`, `var(--radius-full)` |
| `views/product/components/ProductCard.vue` | 阴影硬编码 `0 12px 28px rgba(0,0,0,0.12)` | L268 | `var(--shadow-card-hover)` |
| `views/product/components/ProductCard.vue` | 字号硬编码 `11px`~`14px` | L467, L483 | `var(--text-xs)`, `var(--text-base)` |
| `views/sample/SampleDetail.vue` | 硬编码色 `#d03050` | L230 | `var(--color-danger)` |
| `views/sample/SampleDetail.vue` | 字号硬编码 `12px`, `13px`, `15px` | L230, L351 | `var(--text-xs)`, `var(--text-sm)` |
| `views/orders/components/OrderDetailModal.vue`| 圆角硬编码 `8px` | L332 | `var(--radius-md)` |

---

## Task 2: 信息密度与业务手感优化改造清单

**1. Dashboard 首屏 (`views/dashboard/index.vue`)**
- **问题现象**：可能未完全使用 Token 的阴影与字号。
- **改造方案**：将阴影替换为 `var(--shadow-card)`，统计数字用 `var(--text-2xl)`，提供对比趋势色彩。

**2. 订单工作台 (`views/orders/index.vue`)**
- **问题现象**：表格内的时间等辅助信息有 `12px` 的硬编码字号。
- **改造方案**：替换为 `var(--text-xs)`，确保时间字段不换行且主次分明。

**3. 商品库 / 活动列表 (`views/product/*`)**
- **问题现象**：`ProductCard.vue` 内大量硬编码字号、边距、背景色和阴影。
- **改造方案**：统一使用 `--radius-md`、`--shadow-card`，标签背景色用 `--color-primary-light` 或对应状态色。

**4. 寄样模块 (`views/sample/*`)**
- **问题现象**：物流报错等信息直接行内写入了 `color: #d03050`。
- **改造方案**：替换为 `var(--color-danger)` 及 `var(--text-xs)`。

**5. Layout 与 Header (`views/layout/Header.vue` / `Login.vue`)**
- **问题现象**：品牌色渐变、Logo 尺寸等采用直接硬编码。
- **改造方案**：规范品牌展示，并使用 `var(--color-primary)`，让其与全局 Token 共进退。

---
