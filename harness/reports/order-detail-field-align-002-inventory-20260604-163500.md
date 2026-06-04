# ORDER-DETAIL-FIELD-ALIGN-002 — Field Inventory & Migration Plan

> **任务**：ORDER-DETAIL-FIELD-ALIGN-002 — 把 7 列订单列表升级为 16 列订单明细表（含金额双轨 + 业绩域补全）
> **时间**：2026-06-04 16:35 +08:00
> **环境**：本地工作区 / feature/auth-system
> **Scope**：后端 4 域字段对账 + 前端 columns 重构 + E2E 升级 + 不破坏现有看板汇总
> **前置任务**：ORDER-LIST-FIELD-MAPPING-001（已完成 7 列结构）

---

## 0. 任务边界再确认

### 0.1 16 列最终表头（来自新任务指令）

| # | 列名 | 数据来源域 | 现有状态 | 是否需要改动 |
|---|------|------------|----------|---------------|
| 1 | 订单ID | 订单域 | ✅ 已有 | 复用 |
| 2 | 活动信息 | 订单域 | ✅ 已有（缺 name） | 部分补 |
| 3 | 商品信息 | 订单+商品 | ✅ 已有 | 复用 |
| 4 | 合作方信息 | 订单域 | ✅ 已有 | 复用 |
| 5 | 推广者 | 订单域 | ✅ 已有 | 复用 |
| 6 | 渠道 | 订单+业绩 | ⚠️ 仅默认归属，缺最终归属 | **补业绩域** |
| 7 | 招商 | 业绩域 | ❌ 订单域无 | **补业绩域** |
| 8 | 订单状态 | 订单域 | ⚠️ 需枚举中文 | **加中文派生** |
| 9 | 订单额 | 订单域 | ⚠️ 需 pay/settle 双轨 | **加展示** |
| 10 | 服务费收入 | 订单+业绩 | ⚠️ DB 已有 estimate/effective_service_fee | **加展示** |
| 11 | 技术服务费 | 订单+业绩 | ⚠️ DB 已有 estimate/effective_tech_service_fee | **加展示** |
| 12 | 服务费支出 | 业绩域 | ❌ DB 无 | **业绩域算/前端算** |
| 13 | 服务费收益 | 业绩域 | ⚠️ DB 已有 estimate/effective_service_profit | **加展示** |
| 14 | 招商提成 | 业绩域 | ⚠️ DB 已有 estimate/effective_recruiter_commission | **业绩域补** |
| 15 | 渠道提成 | 业绩域 | ⚠️ DB 已有 estimate/effective_channel_commission | **业绩域补** |
| 16 | 订单时间 | 订单域 | ✅ 已有 | 复用 |

### 0.2 不可破坏的现有能力

- ❌ 不破坏 `getOrderStats` 看板汇总接口（用 `findStats` 路径，line 142）
- ❌ 不破坏 `getOrderDetail` 详情接口（OrderQueryService 路径，line 27）
- ❌ 不破坏 `OrderDetailResponse`（这是详情页用的，不是列表）
- ❌ 不破坏 7 列 → 16 列升级前的前端 7 列结构
- ❌ 不破坏 DB schema / 双轨金额口径 / 业绩计算规则

---

## 1. 后端字段盘点（4 域 × 16 列）

### 1.1 订单域 `colonelsettlement_order` (ColonelsettlementOrder.java)

| 字段 | 用途 | 是否可作订单明细来源 |
|------|------|----------------------|
| `order_id` | 订单ID | ✅ |
| `colonel_activity_id` (activityId) | 活动ID | ✅（缺 name） |
| `product_id` / `product_name` / `product_title` / `product_pic` | 商品信息 | ✅（已 enrich） |
| `shop_id` / `shop_name` | 合作方（商家） | ✅ |
| `colonel_user_id` / `colonel_user_name` | 团长 | ✅ |
| `talent_id` / `talent_name` | 推广者 | ✅ |
| `channel_user_id` / `channel_user_name` | **默认**渠道归属 | ✅（仅默认；最终归属看业绩域） |
| `order_status` | 订单状态编码 | ✅（需中文） |
| `order_amount` / `actual_amount` / `settle_amount` | 订单额 3 套 | ✅（注意 payAmount 用 actual_amount） |
| `estimate_service_fee` / `effective_service_fee` | 服务费收入 | ✅（DB Mapper XML 已投影） |
| `estimate_tech_service_fee` / `effective_tech_service_fee` | 技术服务费 | ✅（DB Mapper XML 已投影） |
| `pay_time` / `order_create_time` / `settle_time` | 订单时间 | ✅ |
| `attribution_status` / `attribution_remark` | 归因状态 | ✅（不动） |

**关键缺口**：
- 招商字段（`recruiter_id` / `recruiter_name`）—— 订单域没有，**必须从业绩域取**
- 活动名称 `activity_name` —— 订单域没有，**前端走 ID 兜底**（按 ORDER-LIST-FIELD-MAPPING-001 inventory 决策）
- 订单状态中文 `orderStatusText` —— 已有 `orderTypeText` 派生逻辑，**复用模式补 `orderStatusText`**
- 服务费支出 / 服务费收益 / 招商提成 / 渠道提成 —— **业绩域有，需补**

### 1.2 业绩域 `performance_records` (PerformanceRecord.java)

| 字段 | 用途 | 在新任务表头中的角色 |
|------|------|----------------------|
| `default_channel_user_id` / `final_channel_user_id` | 渠道默认/最终归属 | 渠道列 — 应优先 final |
| `default_recruiter_user_id` / `final_recruiter_user_id` | 招商默认/最终归属 | 招商列 — 应优先 final |
| `estimate_service_fee` / `effective_service_fee` | 服务费（订单域同名字段对照） | 服务费收入（也可走订单域） |
| `estimate_tech_service_fee` / `effective_tech_service_fee` | 技术服务费（订单域同名字段对照） | 技术服务费（也可走订单域） |
| `estimate_service_profit` / `effective_service_profit` | **服务费收益** | ✅ **直接可用** |
| `estimate_recruiter_commission` / `effective_recruiter_commission` | **招商提成** | ✅ **直接可用** |
| `estimate_channel_commission` / `effective_channel_commission` | **渠道提成** | ✅ **直接可用** |
| `estimate_gross_profit` / `effective_gross_profit` | 毛利 | ❌ V1 不展示（按指令） |

**关键发现**：
- 业绩域**有全部 4 个双轨金额字段**（服务费收益 + 招商提成 + 渠道提成）
- 业绩域**有最终归属** `final_channel_user_id / final_recruiter_user_id`（订单域只有 `channel_user_id` 默认归属）
- 服务费支出 = 招商提成 + 渠道提成 —— **业绩域无该字段**，但**可由招商+渠道算**（前端 / 后端任选）

### 1.3 用户域（用于补全姓名）

订单域已经冗余存了 `channel_user_name` / `colonel_user_name` / `talent_name`，业绩域的 `final_channel_user_id` / `final_recruiter_user_id` 缺姓名补全。

**两个选择**：
- 方案 A：前端只展示 ID（"招商: 770e8400-..."）
- 方案 B：业绩域按 `final_channel_user_id / final_recruiter_user_id` 批量查 user 表补全姓名

> **建议**：方案 A（V1 最小化，与现有 `channelId` 列一致；姓名补全是 V2 范围）
> 理由：CLAUDE.md 写"V1 暂时不要默认展示毛利"，体现 V1 范围收敛精神；前端只显示 ID 与现有 channel 列行为一致

### 1.4 商品域（活动名兜底）

按 ORDER-LIST-FIELD-MAPPING-001 inventory 决策：**前端走 `ID: ${activityId}` 兜底**，不联查活动表。**新任务继承此决策**，不动商品域。

### 1.5 DB 字段汇总（服务费支出）

| 来源 | 字段 | 评价 |
|------|------|------|
| 业绩域 | 招商提成 + 渠道提成 | ✅ **可由公式得出** |
| 业绩域 | 没有 `service_fee_expense` 独立字段 | ❌ 无 DB 字段 |
| 订单域 | 没有 `service_fee_expense` 字段 | ❌ 无 DB 字段 |

> **方案**：**前端按公式 `招商提成 + 渠道提成` 计算**（ORDER-LIST-FIELD-MAPPING-001 retro 4.3 类似 "extra_data 解析而非 DB 列" 决策精神）

---

## 2. 业绩域按 orderIds 批量补全能力盘点

### 2.1 现状

| 项 | 现状 | 评价 |
|----|------|------|
| `PerformanceRecordMapper.findByOrderId` | ✅ 存在 | 单笔查询，**N+1 风险** |
| `PerformanceRecordMapper.findByOrderIds` | ❌ 不存在 | **必须新增** |
| `PerformanceRecord` Entity | ✅ 完整字段 | 直接可用 |
| 业绩域 service 暴露 | ⚠️ 私有 | 需在 service 层加新方法 |

### 2.2 新增接口最小化设计

```java
// 新增到 PerformanceRecordMapper.java
List<PerformanceRecord> findByOrderIds(@Param("orderIds") List<String> orderIds);

// 新增到 PerformanceRecordMapper.xml
<select id="findByOrderIds" resultType="com.colonel.saas.entity.PerformanceRecord">
    SELECT /*+ ... */
        order_id, final_channel_user_id, final_recruiter_user_id,
        estimate_service_profit, effective_service_profit,
        estimate_recruiter_commission, effective_recruiter_commission,
        estimate_channel_commission, effective_channel_commission
    FROM performance_records
    WHERE order_id IN
    <foreach collection="orderIds" item="orderId" open="(" close=")" separator=",">
        #{orderId}
    </foreach>
</select>

// 新增到 PerformanceCalculationService（或新建 OrderDetailEnrichmentService）
public Map<String, PerformanceRecord> mapByOrderIds(List<String> orderIds) {
    if (orderIds == null || orderIds.isEmpty()) return Map.of();
    return performanceRecordMapper.findByOrderIds(orderIds).stream()
        .collect(Collectors.toMap(PerformanceRecord::getOrderId, Function.identity()));
}
```

### 2.3 OrderService.enrichOrderList 集成点

现有 `enrichOrderList`（line 593-599）已存在，扩展为：

```java
public void enrichOrderList(List<ColonelsettlementOrder> orders) {
    if (orders == null || orders.isEmpty()) return;
    enrichOrderProductInfo(orders);
    enrichOrderListExtras(orders);
    enrichPerformanceFields(orders);  // 新增
}

private void enrichPerformanceFields(List<ColonelsettlementOrder> orders) {
    // 1. 收集 orderIds
    // 2. 调 performanceCalculationService.mapByOrderIds(orderIds)
    // 3. 按 orderId 注入到 order.performanceRecord (新增 @TableField(exist=false) record)
    // 4. 注入 finalChannelUserId / finalRecruiterUserId 到 order 现有字段
}
```

> **不引入 BFF 新接口**。沿用现有 `/orders` 端点 + `enrichOrderList` 扩展。理由：用户已答"先只读盘点后端,我拍板"。

---

## 3. 服务费支出 / 服务费收益 / 招商提成 / 渠道提成 数据流

### 3.1 决策矩阵

| 字段 | 数据源 | 计算方式 | 落点 |
|------|--------|----------|------|
| 订单额 payAmount | 订单域 `actual_amount` | 直接 | 后端注入 |
| 订单额 settleAmount | 订单域 `settle_amount` | 直接 | 后端注入 |
| 服务费收入 estimate | 订单域 `estimate_service_fee` | 直接 | 后端注入 |
| 服务费收入 effective | 订单域 `effective_service_fee` | 直接 | 后端注入 |
| 技术服务费 estimate | 订单域 `estimate_tech_service_fee` | 直接 | 后端注入 |
| 技术服务费 effective | 订单域 `effective_tech_service_fee` | 直接 | 后端注入 |
| 服务费收益 estimate | 业绩域 `estimate_service_profit` | 直接 | 业绩域补 |
| 服务费收益 effective | 业绩域 `effective_service_profit` | 直接 | 业绩域补 |
| 招商提成 estimate | 业绩域 `estimate_recruiter_commission` | 直接 | 业绩域补 |
| 招商提成 effective | 业绩域 `effective_recruiter_commission` | 直接 | 业绩域补 |
| 渠道提成 estimate | 业绩域 `estimate_channel_commission` | 直接 | 业绩域补 |
| 渠道提成 effective | 业绩域 `effective_channel_commission` | 直接 | 业绩域补 |
| 服务费支出 estimate | 招商+渠道 | 公式 = 招商 + 渠道 | **前端算** |
| 服务费支出 effective | 招商+渠道 | 公式 = 招商 + 渠道 | **前端算** |
| 渠道（最终） | 业绩域 `final_channel_user_id` | 直接（仅 ID） | 业绩域补 |
| 招商（最终） | 业绩域 `final_recruiter_user_id` | 直接（仅 ID） | 业绩域补 |

### 3.2 前端 vs 后端算服务费支出的取舍

| 选项 | 优点 | 缺点 |
|------|------|------|
| 前端算 | 后端零侵入 | 复制到导出/打印时容易漏 |
| 后端算 | 字段语义统一 | 业绩域需新加 service_fee_expense 派生 |
| 双向都算 | 兼容 | 重复 |

> **建议**：**后端算并注入**。理由：
> 1. 单测覆盖更易（在 service 层加 `serviceFeeExpense = recruiterCommission + channelCommission`）
> 2. 导出 / 打印 / E2E 不会漏
> 3. 字段语义统一（前端是展示层，不应做金额计算）
> 4. 与现有 `enrichOrderProductInfo` 模式一致（后端 enrich，前端只渲染）

---

## 4. 字段覆盖矩阵（16 列）

| 列 | DB | Entity | API 返回 | 前端使用 | 处理方案 |
|----|----|--------|----------|----------|----------|
| 订单ID | ✅ | ✅ | ✅ | ✅ | 已有 |
| 活动信息 | ✅ ID | ✅ ID / ❌ name | ✅ ID | ✅ ID | name 走 ID 兜底（继承上次决策） |
| 商品信息 | ✅ | ✅ | ✅ | ✅ | 已有 |
| 合作方信息 | ✅ | ✅ | ✅ | ✅ | 已有 |
| 推广者 | ✅ | ✅ | ✅ | ✅ | 已有 |
| 渠道 | ⚠️ 仅 default | ⚠️ 仅 default | ✅ default / ❌ final | ❌ final | **补业绩域 final** |
| 招商 | ❌ | ❌ | ❌ | ❌ | **补业绩域 final_recruiter_user_id** |
| 订单状态 | ✅ code | ✅ code / ❌ text | ✅ code | ❌ text | **派生 orderStatusText** |
| 订单额（pay） | ✅ actual_amount | ✅ actualAmount | ✅ | ❌ | **注入 payAmount 别名** |
| 订单额（settle） | ✅ settle_amount | ✅ settleAmount | ✅ | ❌ | **注入 settleAmount 别名** |
| 服务费收入（估/结） | ✅ | ✅ | ✅ | ❌ | **前端读取展示** |
| 技术服务费（估/结） | ✅ | ✅ | ✅ | ❌ | **前端读取展示** |
| 服务费支出（估/结） | ❌ | ❌ | ❌ | ❌ | **后端按公式算** |
| 服务费收益（估/结） | ✅ | ❌（业绩域） | ❌ | ❌ | **业绩域按 orderIds 补** |
| 招商提成（估/结） | ✅ | ❌（业绩域） | ❌ | ❌ | **业绩域按 orderIds 补** |
| 渠道提成（估/结） | ✅ | ❌（业绩域） | ❌ | ❌ | **业绩域按 orderIds 补** |
| 订单时间 | ✅ | ✅ | ✅ | ✅ | 已有 |

**统计**：现有 5/16 列直接可用；9 列需补（其中 1 列在前端做）；2 列部分可补。

---

## 5. 不变量与禁区

### 5.1 V1 不变量（CLAUDE.md 继承）

- ❌ 不修改 DB schema（无 migration 改动）
- ❌ 不动双轨金额口径（`estimate_*` / `effective_*` 不可回退）
- ❌ 不修改 rawPayload 同步路径（`OrderSyncService` 未变）
- ❌ 不动业绩计算（`PerformanceCalculationService` / `CommissionService` 内部不变，仅暴露 `mapByOrderIds`）
- ❌ 不清库、不伪造历史数据
- ❌ 不引入独家达人/独家商家/毛利（V2 范围）
- ❌ 不破坏现有看板汇总接口（`getOrderStats` 走 `findStats` 不变）

### 5.2 防回退规则（已写在 OrderQueryService.java:118 注释）

```java
// serviceFee 对齐业绩域双轨口径：取 effective_service_fee（结算服务费）。
// 历史口径曾取 settle_colonel_commission（结算给团长的佣金），二者语义不同。
```

新任务**继承此规则**：未结算订单的 `effective/settle` 字段**保持为 null**，**不允许**回退到 `estimate/pay`（前端展示 `-`）。

---

## 6. 缺口处理方案（最小化）

### 6.1 后端 Entity 补展示字段（5 个）

| # | 字段 | 类型 | 来源 | 注入位置 |
|---|------|------|------|----------|
| 1 | `payAmount` | Long | `actualAmount` 派生 | `normalizeOrderRow` |
| 2 | `orderStatusText` | String | `orderStatus` 派生中文 | `normalizeOrderRow` |
| 3 | `finalChannelUserId` | UUID | 业绩域 `final_channel_user_id` | `enrichOrderList` 业绩域补 |
| 4 | `finalRecruiterUserId` | UUID | 业绩域 `final_recruiter_user_id` | `enrichOrderList` 业绩域补 |
| 5 | `performance` | 嵌套 object | 业绩域 6 个金额 + 服务费支出 | `enrichOrderList` 业绩域补 |

> **5 个新 `@TableField(exist=false)` 字段**，模式与 ORDER-LIST-FIELD-MAPPING-001 完全一致。**不动 DB schema**。

### 6.2 后端 Mapper 新增 `findByOrderIds`（1 个）

详见 2.2 节。

### 6.3 后端 Service 扩展 `enrichOrderList`（1 处）

详见 2.3 节。**不引入新 BFF 端点**。

### 6.4 前端 columns 重构（1 文件）

`frontend/src/views/orders/index.vue`：
- 现有 7 列保留并升级为 16 列
- 复用现有 `renderXxx` 函数（`renderProductInfo` / `renderTalentInfo` / `renderOrderTime` / `renderPartnerInfo` / `renderActivityInfo`）
- 新增 `renderChannelInfo` 业绩域最终归属
- 新增 `renderRecruiterInfo` 业绩域招商
- 新增 `renderAmountPair` 双行金额组件
- 新增 `renderOrderStatusTag` 订单状态 tag
- 调整 `scroll-x: 2200 → 2600+`（16 列 + 横向滚动）
- 固定列 `orderId`（已有 `fixed: 'left'`，保留）

### 6.5 前端筛选补全

| 项 | 现状 | 调整 |
|----|------|------|
| 订单ID | ✅ | 复用 |
| 商品ID | ✅ | 复用 |
| 活动ID | ✅ | 复用 |
| 合作方 | ❌ 缺 | **新增**（按 shop_name 模糊查询，参考 `channelKeyword` 模式） |
| 推广者 | ❌ 缺 | **新增**（按 talent_name 模糊） |
| 渠道 | ✅ | 复用 |
| 招商 | ❌ 缺 | **新增**（按 final_recruiter_user_id 精确或姓名模糊） |
| 订单状态 | ❌ 缺 | **新增**（order_status select 多选） |
| 付款时间 | ✅ dateRange | 复用 |
| 结算时间 | ❌ 缺 | **新增**（timeField 已有 'settleTime' 分支，缺 UI 切换） |

### 6.6 前端新增 export + 自定义表头

- **导出**：调 `api/order.ts` 新增 `exportOrders(params)` → 后端 `/orders/export` 端点 → 输出 CSV/Excel
- **自定义表头**：用 `naive-ui` 的 `NPopover + NCheckbox` 组合，列选项同步为 16 列 + 默认全选
- **导出字段与表头一致**：16 列全部导出（除 "自定义表头" 隐藏的列）

### 6.7 不补的字段（明确不做）

| 字段 | 原因 |
|------|------|
| `activity_name` 联查 | 跨域（活动域），继承 ORDER-LIST-FIELD-MAPPING-001 决策 |
| `final_channel_user_name` 姓名补全 | 用户域 V1 范围收敛；前端只显示 ID |
| `final_recruiter_user_name` 姓名补全 | 同上 |
| `delivery_time` / `expire_time` DB 列 | 缺列；前端展示空字符串（继承上次决策） |
| `content_type` DB 列 | 缺列；前端展示空字符串（继承上次决策） |
| 导出 Excel 公式 | V1 导出 CSV 即可，Excel 公式是 V2 |
| 渠道/招商默认 vs 最终切换 UI | V1 默认展示最终归属（业绩域），如 default≠final 再提示 |
| 毛利 | V1 暂不展示（按指令） |

---

## 7. 实施阶段（建议拆 3 个 commit）

### Commit 1: 后端业绩域补全能力（最小依赖）

- `entity/ColonelsettlementOrder.java`：+5 `@TableField(exist=false)` 字段
- `mapper/PerformanceRecordMapper.java` + `.xml`：+`findByOrderIds` 批量方法
- `service/PerformanceCalculationService.java`（或新建 `OrderDetailEnrichmentService`）：+`mapByOrderIds` 入口
- `service/OrderService.java`：+`enrichPerformanceFields` 私有方法 + `enrichOrderList` 接入
- `service/OrderService.java`：+`deriveOrderStatusText` 派生 + `payAmount` 别名
- 单测：3 个新方法各 1 个 happy path

### Commit 2: 前端 16 列结构 + 金额双轨展示

- `frontend/src/views/orders/index.vue`：columns 7→16；新增 `renderXxx` 函数
- `frontend/src/views/orders/index.test.ts`：3 个新 render 函数测试
- 不动 API（已有 `/orders` 返回即可）

### Commit 3: 前端筛选 / 导出 / 自定义表头

- `frontend/src/views/orders/index.vue`：+筛选 4 项 + 导出按钮 + 自定义表头
- `frontend/src/api/order.ts`：+`exportOrders` 入口
- `backend/src/main/java/com/colonel/saas/controller/OrderController.java`：+`/orders/export` 端点（V1 范围，订单明细 16 列同步）
- `frontend/src/views/orders/index.test.ts`：+筛选 / 导出 / 自定义表头测试
- E2E：`harness/e2e/order-detail-p0.spec.ts`（新建，登录管理员 → 16 列验证 → 商品图可见 → 渠道非"媒介" → 未结算"-"）

---

## 8. 风险与限制

| 风险 | 等级 | 缓解 |
|------|------|------|
| 业绩域 N+1 查询 | MEDIUM | 一次性 `findByOrderIds` 批量，按主键 in |
| 业绩域与订单域 `estimate_service_fee` 不一致 | LOW | 订单明细用订单域；业绩域的 4 个字段（service_profit / recruiter_commission / channel_commission / gross_profit）业绩域独享，无歧义 |
| 16 列 + 导出 + 自定义表头 UI 工作量 | MEDIUM | 拆 3 个 commit，单测先红后绿 |
| 导出端点分页 / 大数据量 | MEDIUM | 限制 max=10000 行；超过提示导出范围 |
| 姓名 V1 不补，老板可能问 | LOW | 与现有 channelId 列行为一致；订单详情页可看 |
| 前端自定义表头持久化 | LOW | V1 存 localStorage，不存后端 |
| E2E 真实数据缺 | MEDIUM | test 环境 mock；real-pre 用 PENDING 标记 |
| 前端 `renderAmountPair` 视觉 | LOW | 复用现有 `renderOrderTime` 双行样式 |

---

## 9. 与 ORDER-LIST-FIELD-MAPPING-001 的关系

| 任务 | 关系 |
|------|------|
| 上次 (001) | 补 3 个展示字段（awemeId / orderTypeText / contentTypeText），前端零改动 |
| 本次 (002) | 7 列 → 16 列，**5 个新展示字段 + 1 个新 mapper 方法 + 1 个新 service 入口 + 前端大改** |

**继承上次决策**：
- DB schema 不动
- 双轨金额不互退
- 业绩计算规则不动
- `activityName` 不联查
- `delivery_time / expire_time / content_type` DB 缺列走"上游未返回"路径
- "媒介" → "渠道" 已在 commit 38ddecd7 修复，**本次继承不重复**

---

## 10. 用户拍板点（5 个决策）

> 以下决策影响实施工作量，请确认。

| # | 决策 | 默认建议 | 备选 |
|---|------|----------|------|
| 1 | 业绩域姓名补全（招商/渠道最终归属的 name） | ❌ 不补，前端只显示 ID | ✅ 后端按 user 表批量查 |
| 2 | 服务费支出计算位置 | ✅ 后端注入（serviceFeeExpense = recruiter + channel） | ❌ 前端按公式算 |
| 3 | 业绩域按 orderIds 补全的 mapper 方法放在哪 | ✅ 新建 `OrderDetailEnrichmentService`（不污染 PerformanceCalculationService） | ❌ 直接加到 PerformanceCalculationService |
| 4 | 导出格式 | ✅ CSV | ❌ Excel（xlsx 库依赖） |
| 5 | 自定义表头持久化 | ✅ localStorage | ❌ 后端 user_config 表 |

---

## 11. 文件影响清单

### 11.1 后端（预计 5 文件 + 1 XML）

| 文件 | 变更类型 |
|------|----------|
| `backend/src/main/java/com/colonel/saas/entity/ColonelsettlementOrder.java` | +5 `@TableField(exist=false)` |
| `backend/src/main/java/com/colonel/saas/mapper/PerformanceRecordMapper.java` | +1 `findByOrderIds` |
| `backend/src/main/resources/mapper/PerformanceRecordMapper.xml` | +1 `<select>` |
| `backend/src/main/java/com/colonel/saas/service/OrderService.java` | +2 私有方法（enrichPerformanceFields, deriveOrderStatusText） + normalizeOrderRow 加 payAmount |
| `backend/src/main/java/com/colonel/saas/service/OrderDetailEnrichmentService.java` | **新建** ~40 行 |
| `backend/src/test/java/.../OrderServiceTest.java` | +3 测试 |
| `backend/src/test/java/.../OrderDetailEnrichmentServiceTest.java` | **新建** ~50 行 |

### 11.2 前端（预计 2 文件 + 1 E2E）

| 文件 | 变更类型 |
|------|----------|
| `frontend/src/views/orders/index.vue` | columns 7→16；+6 `renderXxx`；+筛选 4 项；+导出按钮；+自定义表头 |
| `frontend/src/api/order.ts` | +`exportOrders` |
| `frontend/src/views/orders/index.test.ts` | +6 render 测试 + 1 export 测试 + 1 customHeader 测试 |
| `harness/e2e/order-detail-p0.spec.ts` | **新建** E2E 验证 |

### 11.3 报告

- `harness/reports/order-detail-field-align-002-inventory-20260604-163500.md`（本文档）
- `harness/reports/evidence-20260604-163500-order-detail-field-align-002.md`（待生成）
- `harness/reports/retro-20260604-163500-order-detail-field-align-002.md`（待生成）

---

## 12. 结论

**结论 A**：**16 列数据全部可由现有 4 域（订单+业绩+用户+商品）提供**，**无需 DB schema 改动**，**无需新增 BFF 端点**。

**结论 B**：**最小改动 = 5 个新 Entity 字段 + 1 个新 Mapper 方法 + 1 个新 Service 入口 + 1 处 `enrichOrderList` 扩展 + 1 处 `normalizeOrderRow` 扩展**。

**结论 C**：**前端是大头**：7→16 列、+6 个 render 函数、+4 个筛选、+1 个导出、+1 个自定义表头。E2E 升级。

**结论 D**：**5 个拍板点**（决策 1-5）请用户确认后开始实施。拆 3 个 commit，**每个 commit 单独跑** `mvn test / vitest / vue-tsc / build`，互不阻塞。
