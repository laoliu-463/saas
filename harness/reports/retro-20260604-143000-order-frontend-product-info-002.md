# Retro Summary — ORDER-FRONTEND-PRODUCT-INFO-002

**时间**: 2026-06-04  
**任务**: 订单列表商品信息列布局修复 + 媒介文案统一

---

## 本次做了什么

1. 将商品信息列布局严格对齐截图：图片 80px→96px，标题色 #f5222d→#ff2f2f，详情行 line-height 统一 22px
2. CSS 类名统一为 `order-product-*` 系列
3. 列宽从 minWidth: 380 改为 width: 430, minWidth: 430
4. scroll-x 从 1500 调整为 1600
5. formatRate 新增字符串 "10%" 直接返回逻辑
6. 占位图改为纯色块（无文字）
7. 全局搜索确认前端无"媒介"文案，资金卡片已使用"渠道提成"
8. 测试从 9 条扩展到 17 条，覆盖布局类名、费率格式化、渠道文案

## 什么没做

- 未改后端代码
- 未改资金逻辑
- 未改其他列
- 未改 Docker/Compose/.env

## 学到了什么

- 上一版 CSS 用 `product-info-*` 类名，本次统一为 `order-product-*` 更清晰
- formatRate 需要处理三种输入：小数(0.1)、整数(10)、字符串("10%")
- 前端代码中不存在"媒介"文案（之前版本已统一为"渠道"）

## Harness 是否需要升级: 否
