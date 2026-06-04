# Retro Summary - Order Product Info Fix

## Task
Fix: 商品图片和订单内容不显示。

## Root Cause
1. 后端 ColonelsettlementOrder 实体缺少 productPic、itemNum、commissionRate 字段映射
2. 数据库 product_pic 列存在但 Java 实体未映射；item_num、commission_rate 列不存在
3. 抖音 API 原始数据（extra_data JSON）包含 product_img、item_num、commission_rate，但同步时未提取到实体字段
4. 前端 formatRate 函数不支持基点格式（500 = 5%）
5. Product 表和 Order 表的 productId 无交集（不同商品集合），通过 Product 表补充图片方案不可行

## Solution
1. 实体新增 productPic / itemNum / commissionRate 三个字段
2. DB 新增 item_num、commission_rate 列，从 extra_data 回填 516 条订单
3. OrderSyncService.mapOrder() 新增从 rawPayload 提取 product_img / item_num / commission_rate
4. 前端 formatRate 增加基点格式处理（>=100 的值除以 100）
5. 前端 renderProductInfo 优先读取 row.productPic / row.itemNum

## Changes
- backend: ColonelsettlementOrder.java (+25 lines)
- backend: OrderSyncService.java (+4 lines)
- backend: migrate-all.sql (+15 lines)
- frontend: orders/index.vue (+14/-6 lines)
- frontend: orders/index.test.ts (+35/-4 lines)

## Risk
- 服务费率 (serviceFeeRate) 后端未返回，前端显示 -
- commissionRate 基点换算假设 100 = 1%，需确认抖音文档
- 未进行远端部署
