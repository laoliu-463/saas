# Retro Summary — ORDER-FRONTEND-PRODUCT-INFO-001

**时间**: 2026-06-04  
**任务**: 修复订单归因页面商品信息列展示  

---

## 本次做了什么

1. 定位目标页面为 `orders/index.vue`（订单归因页），确认后端已返回 `productId`, `productTitle`, `productName`, `shopName` 字段。
2. 在商品信息列新增 `renderProductInfo` 渲染函数，实现左图右文布局。
3. 新增 `formatRate` 辅助函数，智能处理小数/整数百分比格式化，修复浮点精度。
4. 新增完整的 scoped CSS 样式。
5. 编写 9 条单元测试覆盖正常渲染、空值、图片、费率格式化等场景。
6. 修复浮点精度问题：`0.07 * 100 = 7.000000000000001` → 使用 `Math.round(num * 10000) / 100`。

## 什么没做

- 未修改后端代码（后端商品图片/数量/佣金率/服务费率字段缺失，前端以 `-` 兜底）
- 未修改 Docker/Compose/.env 配置
- 未修改数据库结构
- 未部署远端

## 学到了什么

- 本项目 `data/OrderList.vue` 是汇总表（按日期聚合），`orders/index.vue` 才是逐行订单列表页
- `NDataTable` 的 `column.render` 返回 VNode 时需要用 `h()` 函数
- 测试中需要自定义 `NDataTable` stub 来实际调用 `column.render` 才能验证渲染结果
- JavaScript 浮点精度问题需要在百分比转换时用 `Math.round` 处理

## Harness 是否需要升级

否 — 本次为标准前端展示修复，无需升级 Harness。
