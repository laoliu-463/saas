# 数据口径 Open Questions 审计

审计日期：2026-05-21

审计范围：订单同步、订单归因、Dashboard 数据权限、达人认领归属。

相关代码：

- `backend/src/main/java/com/colonel/saas/service/OrderSyncService.java`
- `backend/src/main/java/com/colonel/saas/service/AttributionService.java`
- `backend/src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java`
- `backend/src/main/java/com/colonel/saas/service/DashboardService.java`
- `backend/src/main/java/com/colonel/saas/controller/OrderController.java`
- `backend/src/main/java/com/colonel/saas/gateway/douyin/real/RealDouyinOrderGateway.java`
- `backend/src/main/java/com/colonel/saas/service/TalentService.java`
- `backend/src/main/java/com/colonel/saas/service/TalentQueryService.java`

## 一、结论摘要

本轮 4 个 Open Questions 均可按当前代码事实收口：

1. `colonelsettlement_order.user_id` 不是抖店上游用户字段，也不是触发同步接口的 JWT 用户；它是本地归因后的业绩归属用户，当前通常等同渠道归属人。
2. 主订单同步与按订单号补拉的结算订单样本最终都落同一张 `colonelsettlement_order`，按 `order_id` 去重更新；同笔订单不会因来源不同产生两条业务订单记录。
3. 当前归因优先级已经由代码固定为：独家商家 / 独家达人优先，其次原生团长字段 `colonel_buyin_id` / `second_colonel_buyin_id`，最后才是 `pick_source` / `pick_extra`。
4. `Talent.dept_id` 不是当前持久化字段；达人归属和数据范围以有效 `talent_claim` 记录为准，`TalentClaim.dept_id` 不需要与 `Talent` 表同步。

## 二、问题 1：订单 `user_id` 来源

代码事实：

- `OrderController.syncOrders` 仅要求管理员角色，不把请求 JWT 的 `userId` 传入订单同步服务。
- `OrderSyncService.mapOrder` 从上游订单映射订单基础字段时，不设置 `user_id`。
- `OrderSyncService.syncItems` 会先调用 `AttributionService.resolveAttribution`，然后把返回的 `userId` 写入 `order.userId`，并同步写入 `channelUserId` / `deptId`。
- `DashboardService` 和 `OrderController` 的个人 / 部门数据范围均按 `colonelsettlement_order.user_id` / `dept_id` 过滤。

收口口径：

- `colonelsettlement_order.user_id` 应解释为“本地业绩归属用户 / 数据范围归属用户”，不是“抖店买家 / 达人 / 上游用户 ID”。
- `channel_user_id` 是渠道归属人展示字段；当前归因成功时通常与 `user_id` 同值。
- `colonel_user_id` 是招商 / 团长侧负责人字段，来自商品操作状态，不参与个人数据范围过滤。
- JWT 当前用户只用于接口鉴权和查询 DataScope，不决定订单入库归属。

## 三、问题 2：主订单与结算订单是否会重复入库

代码事实：

- 时间窗口主同步调用 `DouyinOrderGateway.listSettlement`，真实实现落到 `OrderApi.listSettlement`，上游方法为 `buyin.instituteOrderColonel`。
- 按订单号补拉调用 `DouyinOrderGateway.listSettlementByOrderIds`，真实实现会调用 `OrderApi.listColonelMultiSettlementOrders`，上游方法为 `buyin.colonelMultiSettlementOrders`。
- 两条来源都会被 `RealDouyinOrderGateway.toOrderItem` 归一为 `DouyinOrderItem`，再进入同一个 `OrderSyncService.mapOrder` 和 `AttributionService.resolveAttribution`。
- `OrderSyncPersistenceService.persistOrder` 先查 `findByOrderId`，已有订单则复用原 `id` 并执行 `updateSyncedById`；新订单才 `insertIgnoreByOrderId`。

收口口径：

- 只要上游返回同一个 `order_id`，本地只保留一条 `colonelsettlement_order` 记录。
- 主订单与结算订单补样的 `user_id` 来源一致，均来自本地归因结果。
- 结算补样主要补充金额、结算时间、团长原生字段等事实，不另建第二套订单归属。

边界提醒：

- 当前 `/douyin/order-settlements` 文案描述为分次结算样本入口；按 `orderIds` 查询时会走 `buyin.colonelMultiSettlementOrders`。但无 `orderIds` 的时间范围分支仍复用 `DouyinOrderGateway.listSettlement`，实际会走主订单接口。若后续要把该接口作为分次结算时间窗口证据，应单独修复或补测试锁定。

## 四、问题 3：`pick_source` 与 `colonel_buyin_id` 归因优先级

当前代码优先级：

1. 商品缺失：直接 `PRODUCT_NOT_FOUND`。
2. 独家商家命中：归因到独家商家负责人。
3. 独家达人命中：归因到独家达人负责人。
4. 订单携带 `colonel_buyin_id` 或 `second_colonel_buyin_id`：按原生团长字段匹配 `pick_source_mapping.source_type=NATIVE`。
5. 无原生团长字段时：按 `pick_source` / `pick_extra` / `short_id` 匹配普通推广映射。
6. 匹配失败后按 `COLONEL_MAPPING_NOT_FOUND`、`COLONEL_MAPPING_AMBIGUOUS`、`MAPPING_NOT_FOUND`、`NO_PICK_SOURCE` 等原因进入未归因排查。

收口口径：

- 现阶段应固定为“原生团长字段优先于 `pick_source`”的代码口径，因为 real-pre 真实订单经常缺少 `pick_source`，但存在 `colonel_order_info` / `colonel_order_info_second`。
- 如果产品后续要求“订单明确携带 `pick_source` 时优先使用推广链接归因”，需要作为单独业务规则变更处理，并补充“同一订单同时存在 `pick_source` 与原生团长字段但指向不同负责人”的反例测试。

## 五、问题 4：达人 `dept_id` 与认领 `dept_id`

代码事实：

- `talent` 表当前没有 `dept_id` 字段，`Talent` 实体也没有持久化 `deptId`。
- `Talent.ownerId`、`claimedAt` 等归属字段在实体上标记为 `exist=false`，用于页面 DTO / 列表展示，不作为数据库主事实。
- `TalentService.claim` 在认领时写入 `talent_claim.user_id` 和 `talent_claim.dept_id`。
- `TalentQueryService` 的个人 / 部门访问控制读取有效 `talent_claim` 记录判断归属。
- 当前已支持多人同时认领同一达人，因此一个达人可以同时存在多个有效 `talent_claim.dept_id`。

收口口径：

- 达人归属主事实是有效 `talent_claim`，不是 `talent` 表字段。
- `TalentClaim.dept_id` 表示“该次认领发生时的用户所属组”。
- 不存在 `Talent.dept_id` 与 `TalentClaim.dept_id` 必须同步的当前规则。
- 若未来新增 `talent.dept_id`，只能作为派生缓存或主归属快照，必须重新定义多人认领时的冲突规则。

## 六、测试 P1 输入

后续测试补强建议优先覆盖以下断言：

- 订单同步不使用触发同步接口的 JWT 用户写入 `colonelsettlement_order.user_id`。
- 同一个 `order_id` 先经主同步、再经按订单号结算补拉时只更新一条订单记录。
- 带原生团长字段的真实订单优先走 `source_type=NATIVE` 映射。
- 无原生团长字段时才回落 `pick_source` / `pick_extra` 映射。
- 达人详情和列表的数据范围由有效 `talent_claim` 决定，不读取不存在的 `talent.dept_id`。

