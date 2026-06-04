# Order-List-Field-Mapping-001 — Field Coverage Matrix

> **任务**：ORDER-LIST-FIELD-MAPPING-001 — 复刻截图订单明细列表前，先做后端字段审查
> **时间**：2026-06-04 16:10 +08:00
> **环境**：本地工作区 / feature/auth-system / commit f601a70c
> **Scope**：backend + frontend 订单域字段对账（只读、不写库、不改业务规则）

---

## 1. 审查范围与方法

### 1.1 审查对象

| 层级 | 文件 | 用途 |
| --- | --- | --- |
| 实体 | `backend/src/main/java/com/colonel/saas/entity/ColonelsettlementOrder.java` | 订单表实体 |
| 实体 | `backend/src/main/java/com/colonel/saas/entity/ColonelOrderSettlement.java` | 团长订单结算实体（含 delivery_time / expire_time） |
| VO | `backend/src/main/java/com/colonel/saas/vo/data/OrderVO.java` | Dashboard / 旧订单列表展示 VO |
| Controller | `backend/src/main/java/com/colonel/saas/controller/OrderController.java` | `/orders` 列表 / 详情 / 统计 / 筛选项 |
| Service | `backend/src/main/java/com/colonel/saas/service/OrderService.java` | `enrichOrderProductInfo` / `normalizeOrderRow` |
| Service | `backend/src/main/java/com/colonel/saas/service/OrderSyncService.java` | 同步入口、`rawDateTime` 解析 |
| Service | `backend/src/main/java/com/colonel/saas/service/OrderPaymentSchemaBootstrap.java` | DB schema bootstrap |
| Mapper | `backend/src/main/java/com/colonel/saas/mapper/ColonelsettlementOrderMapper.java` | MyBatis-Plus Mapper |
| Mapper XML | `backend/src/main/resources/mapper/ColonelsettlementOrderMapper.xml` | 自定义 SQL（display info 投影） |
| DB schema | `backend/src/main/resources/db/migrate-all.sql` | 总迁移 |
| DB schema | `backend/src/main/resources/db/init-db.sql` | 初始化脚本 |
| Gateway | `backend/src/main/java/com/colonel/saas/gateway/douyin/real/RealDouyinOrderGateway.java` | 抖店真实订单网关 |
| Gateway | `backend/src/main/java/com/colonel/saas/gateway/douyin/test/TestDouyinOrderGateway.java` | 抖店测试网关 |
| 前端 VO/API | `frontend/src/api/order.ts` | OrderDetail / OrderList 接口定义 |
| 前端页面 | `frontend/src/views/orders/index.vue` | 订单列表页（含列渲染） |
| 前端单测 | `frontend/src/views/orders/index.test.ts` | 列表页单测 |
| 前端单测 | `frontend/src/api/order.test.ts` | API 单测 |

### 1.2 审查方法

1. 静态阅读所有相关 Java / TS / SQL / XML 文件，记录每个目标字段在 DB / Entity / VO / API / 前端 5 个层级的存在情况。
2. 不连接数据库执行只读 SQL（避免在审查阶段做不可逆动作）。
3. 不修改任何代码，先输出对账矩阵。
4. 截图所需字段逐项打勾或登记缺口处理方案。

---

## 2. 字段覆盖矩阵（截图 7 列需求）

> 标记规则：
> ✅ 已有 / 已返回
> ⚠️ 部分覆盖（DB 或 API 存在但前端需注意）
> ❌ 完全缺失
> 🟡 取决于上游接口（raw_payload 有但 DB 没有，前端展示空即可）

### 2.1 订单ID 列

| 字段 | DB | Entity | API 返回 | 前端使用 | 处理方案 |
| --- | --- | --- | --- | --- | --- |
| `order_id` | ✅ `colonelsettlement_order.order_id` | ✅ `ColonelsettlementOrder.orderId` | ✅ Controller `getOrders` 返回 | ✅ `renderOrderId` 取 `row.orderId` | 已有 |
| `order_type` (Integer) | ✅ `order_type INT`（`migrate-all.sql:751` + bootstrap） | ✅ `ColonelsettlementOrder.orderType: Integer` | ✅ 同上 | ❌ 前端未读 `orderType`，需 `orderTypeText` | **补：** 后端 VO 派生 `orderTypeText`（MAIN/SETTLEMENT 映射） |
| `order_type_text` | ❌ 无 | ❌ 无 | ❌ 无 | ⚠️ 前端读 `row.orderTypeText`（line 388）但永远为空 | **补：** 后端 `enrichOrderList` 派生 |
| `content_type` | ❌ 库无字段 | ❌ 无 | ❌ 无 | ❌ 前端 `row.contentTypeText`（line 389）永远空 | **补：** 后端从 `extra_data.content_type / contentType` 安全解析；上游未返回则空字符串 |
| `content_type_text` | ❌ 无 | ❌ 无 | ❌ 无 | ⚠️ 前端 `row.contentTypeText`（line 389）永远空 | **补：** 同上，标签文本（如"短视频/图文/直播"） |

### 2.2 活动信息 列

| 字段 | DB | Entity | API 返回 | 前端使用 | 处理方案 |
| --- | --- | --- | --- | --- | --- |
| `activity_id` (colonel_activity_id) | ✅ `colonelsettlement_order.colonel_activity_id` | ✅ `ColonelsettlementOrder.activityId` | ✅ | ✅ `row.activityId` | 已有 |
| `activity_name` | ⚠️ DB 无 `activity_name` 列 | ❌ 无 | ❌ 后端无关联活动表 | ⚠️ 前端 `row.activityName`（line 371）永远空 | **补：** 后端从活动表 `colonelsettlement_activity` left join 取 `activity_name`（不在本任务最小化范围内，按"上游/历史缺失"处理） |
| 处理方案 |  |  |  |  | **临时方案：** 前端展示 `ID: ${activityId}` 兜底；后台 `activityName` 缺失时显示空。后端不动活动表联查（属商品域范围），通过 `extra_data`/`pick_source_mapping` 解析 |

### 2.3 商品信息 列

| 字段 | DB | Entity | API 返回 | 前端使用 | 处理方案 |
| --- | --- | --- | --- | --- | --- |
| `product_id` | ✅ `product_id` | ✅ | ✅ | ✅ `row.productId` | 已有 |
| `product_name` / `product_title` | ✅ `product_name` / `product_title` | ✅ | ✅ | ✅ `firstDisplayValue` | 已有 |
| `product_image` / `product_pic` | ✅ `product_pic` + alias `product_image`（exist=false） | ✅ | ✅ | ✅ `getProductInfo().image` | 已有（`enrichOrderProductInfo` 已补） |
| `shop_name` | ✅ | ✅ | ✅ | ✅ `product.shop` | 已有 |
| `quantity` / `item_num` | ⚠️ DB 无 `quantity`；`item_num` 在 `extra_data` 投影 | ✅ `itemNum` + alias `productQuantity`（exist=false） | ✅（`enrichOrderProductInfo` 注入） | ✅ `getProductInfo().quantity` | 已有 |
| `commission_rate` | ⚠️ DB 无 | ✅ alias `commissionRate`（exist=false） | ✅（`enrichOrderProductInfo` 从 extra_data / snapshot / product 补齐） | ✅ `formatRate` | 已有 |
| `service_fee_rate` | ⚠️ DB 无 | ✅ alias | ✅ | ✅ `formatRate` | 已有 |

> 备注：商品图 / 数量 / 佣金率 / 服务费率已在 commit f601a70c / f86c0ea0 落地，本任务不再重复。

### 2.4 合作方信息 列

| 字段 | DB | Entity | API 返回 | 前端使用 | 处理方案 |
| --- | --- | --- | --- | --- | --- |
| `shop_name`（商家） | ✅ | ✅ | ✅ | ✅ `partner.shopName` | 已有 |
| `partner_name` | ❌ DB 字段名 `shop_name`，无独立 `partner_name` | ❌ 无 | ❌ 无 | ⚠️ 前端 fallback `partnerName`（line 245）永远空 | **方案：** 前端回退到 `shopName`；"商家"展示统一使用 `shopName` |
| `colonel_user_name`（团长） | ✅ `colonel_user_name` | ✅ | ✅ | ✅ `partner.colonelName` | 已有 |

### 2.5 推广者 列

| 字段 | DB | Entity | API 返回 | 前端使用 | 处理方案 |
| --- | --- | --- | --- | --- | --- |
| `talent_id` | ✅ `talent_id UUID` | ✅ | ✅ | ✅ `talentId` | 已有 |
| `talent_name`（达人昵称） | ✅ `talent_name` | ✅ | ✅ | ✅ `talentName` | 已有 |
| `talent_uid` / `author_id` / `author_buyin_id` | ❌ DB 无 `talent_uid`，`colonel_buyin_id` 是团长百应 ID | ❌ `talentId` = UUID，前端 `firstDisplayValue` 优先取 `talentUid/authorId/authorBuyinId`，永远空 | ❌ | ⚠️ 前端 ID 展示会退化到 talentId | **方案：** 达人的对外 ID 实际就是 `talent_id` UUID，前端显示 `talent_id` 即可；不伪造 `author_buyin_id` |
| `aweme_id` / `video_id` / `item_id`（出单视频） | ❌ DB 无 | ❌ Entity 无 | ❌ 后端无 | ⚠️ 前端 `awemeId/videoId/itemId`（line 316）全空 → 出单视频行不渲染 | **补：** 后端从 `extra_data` 安全解析 `aweme_id / video_id / item_id`，写入 `ColonelsettlementOrder` 的 exist=false 字段 `awemeId`（一个即可，前端 `firstDisplayValue` 已支持三选一） |

### 2.6 渠道 列

| 字段 | DB | Entity | API 返回 | 前端使用 | 处理方案 |
| --- | --- | --- | --- | --- | --- |
| `channel_user_id` | ✅ | ✅ | ✅ | ✅ `channelId`（line 255） | 已有（已被 commit 38ddecd7 修正命名） |
| `channel_user_name` | ✅ | ✅ | ✅ | ✅ `channelName`（line 265） | 已有 |
| "媒介" → "渠道" 文案 | n/a | n/a | n/a | ⚠️ 前端 `title: '渠道'` 已是渠道（line 437） | **已对齐**（commit 38ddecd7 修过） |
| 默认招商 / 默认渠道 | ❌ DB 字段 `user_id`/`dept_id` 是负责人，不是"默认招商"显式字段 | ❌ 无 | ❌ 无 | ❌ 前端无此列 | **本任务不做：** 招商归属不展示在"系统订单明细"列表，列已删除 |
| 未归因 | n/a | n/a | n/a | ✅ `name === '-'` 显示"未归因" | 已有 |

### 2.7 订单时间 列

| 字段 | DB | Entity | API 返回 | 前端使用 | 处理方案 |
| --- | --- | --- | --- | --- | --- |
| `pay_time`（付款） | ✅ `colonelsettlement_order.pay_time`（`OrderPaymentSchemaBootstrap` 加列） | ✅ `payTime` | ✅ | ✅ `payTime` 优先 | 已有 |
| `delivery_time`（收货） | ❌ **DB 缺列**（仅 `ColonelOrderSettlement` 表有） | ❌ Entity 无 | ❌ | ⚠️ 前端 `firstDisplayValue(['deliveryTime', 'delivery_time'])` 永远空 | **补：** 走"上游接口未返回"路径，前端展示空字符串；后端不强行伪造 |
| `settle_time`（结算） | ✅ `settle_time` | ✅ | ✅ | ✅ `settleTime` | 已有 |
| `expire_time`（失效） | ❌ **DB 缺列** | ❌ Entity 无 | ❌ | ⚠️ 前端 `firstDisplayValue(['expireTime', 'expire_time'])` 永远空 | **补：** 同上，前端展示空 |
| `order_create_time` | ✅ `OrderPaymentSchemaBootstrap` 加列 | ✅ `orderCreateTime` | ✅ | ⚠️ 当前未读 | 已有 |

### 2.8 渠道与活动相关独立列（截图未要求）

> 截图 7 列不包含"金额"列、单独的"媒介/招商"列；前端"金额"通过详情页展示，列表只做"合作方/渠道/时间"。

---

## 3. 缺失字段处理原则（V1 严格遵守）

### 3.1 不允许

- ❌ 伪造历史数据或为缺失字段填默认值
- ❌ 改 DB 双轨金额（`pay_amount`/`settle_amount`）口径
- ❌ 改 `pay_time` 与 `order_create_time` 关系
- ❌ 改业绩计算规则、独家达人/独家商家
- ❌ 在前端硬编码"短视频/图文"等内容类型

### 3.2 允许

- ✅ 在 Entity 增加 `exist=false` 别名字段，从 `extra_data` 安全解析展示
- ✅ 前端 `firstDisplayValue` 已对多 alias 兜底，新增 alias 不破坏旧逻辑
- ✅ DB 缺列（`delivery_time` / `expire_time`）登记为"上游接口未返回/历史订单缺失"，前端展示空字符串（不是 null）

---

## 4. 缺口处理方案（最小化）

### 4.1 后端 Entity 补 `awemeId`（exist=false）展示字段

| 字段 | 位置 | 来源 |
| --- | --- | --- |
| `awemeId` | `ColonelsettlementOrder` 新增 `@TableField(exist = false) String awemeId` | 由 `OrderService.enrichOrderList` 从 `extra_data` 解析 `aweme_id / video_id / item_id`（取第一个非空） |

**不改 DB schema，不改 SQL，不改 rawPayload gateway 同步路径。**

### 4.2 后端 Controller 委托 `orderService.enrichOrderList(records)`

| 步骤 | 内容 |
| --- | --- |
| 1 | 复用现有 `enrichOrderProductInfo`，扩展为 `enrichOrderList`，再补 `awemeId` |
| 2 | 不增加新 SQL、不增加新列、不影响分页/排序 |
| 3 | 缺数据时字段为 null，前端走 fallback 路径 |

### 4.3 不补的字段（明确不做）

| 字段 | 原因 |
| --- | --- |
| `delivery_time` / `expire_time` | DB 缺列；添加 migration 会改 schema；属抖店订单回流范畴，超出本任务范围。前端展示空字符串。 |
| `content_type` | DB 缺列；上游可能不返回。属未来拓展项。 |
| `order_type_text` 文本 | 改在 Entity 派生即可（用现成 `order_type` Integer → MAIN/SETTLEMENT/UNKNOWN 映射） |
| `author_buyin_id` 等达人对外 ID | 业务上游无此字段；以 `talent_id` UUID 展示即可。 |
| `activity_name` | 跨域（活动域）改动；本任务不引入活动表联查；前端兜底显示 ID。 |

### 4.4 前端微调

| 项 | 现状 | 调整 |
| --- | --- | --- |
| `orderTypeText` fallback | 仅依赖后端字段 | 保留 `row.orderTypeText` 优先；缺值时回退 `orderType`（数字字符串） |
| `contentTypeText` fallback | 仅依赖后端字段 | 缺值时显示空字符串（不显示 "null"）；标签节点不渲染 |
| `awemeId` 来源 | `firstDisplayValue` 已支持 | 等后端 `awemeId` 就位后即展示；无值时不渲染出单视频行 |
| 推广者行达人 ID | `talentId` | 已用 `talentId` UUID，OK |

---

## 5. 验证清单

| 检查 | 命令 | 预期 |
| --- | --- | --- |
| 实体含 `awemeId` | `grep awemeId backend/src/main/java/com/colonel/saas/entity/ColonelsettlementOrder.java` | 1 行 |
| `enrichOrderList` 存在 | `grep enrichOrderList backend/src/main/java/com/colonel/saas/service/OrderService.java` | 1 行 |
| Controller 委托 | `grep enrichOrderList backend/src/main/java/com/colonel/saas/controller/OrderController.java` | 1 行 |
| 前端 7 列结构 | `grep "title:" frontend/src/views/orders/index.vue \| head -7` | 订单ID/活动/商品/合作方/推广者/渠道/订单时间 |
| "媒介" 不出现 | `grep -i "媒介" frontend/src/views/orders/index.vue` | 0 行 |
| 单测 | `cd frontend && npx vitest run src/views/orders` | PASS |
| 后端编译 | `cd backend && mvn -DskipTests -q compile` | SUCCESS |
| 前端 typecheck | `cd frontend && npx vue-tsc --noEmit` | SUCCESS |

---

## 6. 结论

**结论 A：后端已具备"7 列订单明细"所需全部基础数据，约 80% 字段已落地。**

**结论 B：本任务真正需补的是"展示侧"3 处微缺口：**

1. 后端 Entity 增加 `awemeId`（exist=false）展示字段，从 `extra_data` 安全解析。
2. 后端 `orderTypeText` 派生（Integer → MAIN/SETTLEMENT/UNKNOWN）——Entity `@Transient` 派生或在 service 中 set。
3. 前端 `orderTypeText` / `contentTypeText` / `awemeId` 三处空值友好处理（不显示 "null"）。

**结论 C：DB 真正缺列的 `delivery_time` / `expire_time` / `content_type` 不在本任务修复范围（超出最小化），前端展示空字符串。**

**结论 D：不动双轨金额、不动业绩计算、不改 rawPayload 同步路径、不改 DB schema。**

---

## 7. 不属于本任务的字段（已记录但不做）

- `delivery_time` 列迁移
- `expire_time` 列迁移
- `content_type` 列迁移
- `activity_name` 跨域联查
- 招商归属列（截图未要求）
- 金额列（截图未要求，由详情页提供）

