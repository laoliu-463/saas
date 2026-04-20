# 需求：数据平台

**文档版本**：V1.0
**来源**：V2.2 定稿文档 §3.6
**智能体入口**：直接读取此文件

---

## 一、功能定位

数据平台是核心业绩看板，支撑管理层决策。覆盖核心指标、订单明细、多维度筛选和数据导出。

---

## 二、核心指标卡片

| 指标 | 计算方式 | 数据来源 |
|------|----------|----------|
| 总订单数 | COUNT(order_id) | `colonelsettlement_order` |
| 订单总额 | SUM(order_amount) | `colonelsettlement_order` |
| 服务费收入 | SUM(service_fee) | `colonelsettlement_order` |
| 技术服务费 | SUM(platform_fee) | `colonelsettlement_order` |
| 服务费支出 | SUM(talent_commission) | `colonelsettlement_order` |
| 服务费收益 | 服务费收入 - 技术服务费 - 服务费支出 | 衍生计算 |
| 招商提成 | SUM(commission_config.zs_ratio × service_fee) | 订单 + 配置表 |
| 渠道提成 | SUM(commission_config.qd_ratio × service_fee) | 订单 + 配置表 |
| 毛利 | 服务费收益 - 招商提成 - 渠道提成 | 衍生计算 |

---

## 三、业绩归属规则

### 3.1 归属优先级

```
1. 独家达人 → 归认领渠道（pick_source 归因逻辑失效）
2. 独家商家 → 归独家招商（按商品归属逻辑失效）
3. 默认渠道归属 → pick_source → PickSourceMapping → channel_id
4. 默认招商归属 → 商品 → 活动 → 招商负责人
```

### 3.2 渠道业绩（pick_source 归因）

> 详见 `rules/attribution-logic.md`

```java
// OrderSettlementService.resolveChannel()
public UUID resolveChannel(Order order) {
    // 1. 检查独家达人
    ExclusiveTalent et = exclusiveTalentService.getEffective(order.getTalentUid());
    if (et != null) {
        return et.getChannelId();  // 独家达人优先
    }

    // 2. 通过 pick_source 映射表反查渠道
    return pickSourceMappingService.resolveChannel(order.getPickSource());
}
```

### 3.3 招商业绩（按活动归属）

```java
// OrderSettlementService.resolvezsManager()
public UUID resolvezsManager(Order order) {
    // 1. 检查独家商家
    ExclusiveMerchant em = exclusiveMerchantService.getEffective(order.getMerchantId());
    if (em != null) {
        return em.getzsManagerId();  // 独家商家优先
    }

    // 2. 商品 → 活动 → 招商负责人
    Product product = productService.getById(order.getProductId());
    ColonelActivity activity = colonelActivityService.getById(product.getActivityId());
    return activity.getzsManagerId();
}
```

---

## 四、提成公式

### 4.1 核心公式

```
服务费收益 = 订单金额 × 服务费率（以抖音接口返回的 service_fee 为准）
招商提成 = 服务费收益 × 招商提成比例
渠道提成 = 服务费收益 × 渠道提成比例
毛利 = 服务费收益 - 招商提成 - 渠道提成
```

### 4.2 提成配置引用

> 详见 `rules/exclusive-triggers.md`

```java
// CommissionService.getRatio()
public BigDecimal getCommissionRatio(UUID userId, CommissionType type) {
    // 1. 先查个人配置
    CommissionConfig personal = commissionConfigMapper.selectByUserId(userId);
    if (personal != null && personal.getRatio(type) != null) {
        return personal.getRatio(type);
    }

    // 2. 再查全局配置
    SystemConfig global = systemConfigService.getGlobalConfig();
    return global.getDefaultRatio(type);  // 必须引用配置，禁止硬编码
}
```

### 4.3 禁止硬编码

```java
// ❌ 禁止
BigDecimal ratio = new BigDecimal("0.15");

// ✅ 正确
BigDecimal ratio = systemConfig.getCommissionRatio(CommissionType.ZS);
```

---

## 五、业绩计算时间口径

| 类型 | 使用字段 | 说明 |
|------|----------|------|
| 预估/看板 | `create_time` | 实时监控，包含未结算订单 |
| 实际结算 | `settle_time` | 以抖音结算时间为准，用于财务核算 |

---

## 六、多维度筛选

| 筛选维度 | 字段 | 说明 |
|----------|------|------|
| 时间范围 | create_time / settle_time | 看板时间 vs 结算时间 |
| 订单状态 | status | 已结算/未结算/已退款 |
| 商品 | product_id | 支持多选 |
| 达人 | talent_id / talent_uid | 支持多选 |
| 招商 | user_id (招商角色) | 业绩归属的招商 |
| 渠道 | user_id (渠道角色) | 业绩归属的渠道 |
| 活动 | activity_id | 支持多选 |

### 6.1 DataScope 过滤

> 详见 `rules/data-scope-lint.md`

| 角色 | 数据范围 | SQL 过滤 |
|------|----------|----------|
| 管理员 | 全部 | 无过滤 |
| 组长 | 本组 + 自己 | `WHERE dept_id = ? OR user_id = ?` |
| 普通成员 | 仅自己 | `WHERE user_id = ?` |

---

## 七、订单明细表

### 7.1 展示字段

| 字段 | 来源 | 敏感 |
|------|------|------|
| 订单号 | order_id | |
| 下单时间 | create_time | |
| 结算时间 | settle_time | |
| 商品名称 | product.name | |
| 达人信息 | talent.nickname | |
| 订单金额 | order_amount | |
| 服务费 | service_fee | |
| 招商 | zs_manager.real_name | |
| 渠道 | channel.real_name | |
| 归因方式 | 独家/普通 | |

### 7.2 敏感数据处理

- **手机号解密**：调用 `order.batchSensitiveDataRequest` 接口
- **解密结果**：仅返回前端展示，**不持久化**
- > 详见 `rules/api-security.md`

---

## 八、数据导出

| 角色 | 导出权限 |
|------|----------|
| 管理员 | ✅ 可导出 |
| 组长 | ✅ 可导出 |
| 普通成员 | ❌ 仅看板查看，不导出 |

---

## 九、业务约束

| 约束 | 文件 | 级别 |
|------|------|------|
| 归因必须通过映射表 | `rules/attribution-logic.md` | **CRITICAL** |
| 提成比例必须引用配置 | `rules/exclusive-triggers.md` | **CRITICAL** |
| DataScope 必须过滤 | `rules/data-scope-lint.md` | **CRITICAL** |
| 敏感数据不持久化 | `rules/api-security.md` | **CRITICAL** |

---

## 十、相关文件索引

| 文件 | 路径 |
|------|------|
| 订单实体 | `backend/src/main/java/com/colonel/saas/entity/ColonelsettlementOrder.java` |
| 提成配置实体 | `backend/src/main/java/com/colonel/saas/entity/CommissionConfig.java` |
| 归因服务 | `backend/src/main/java/com/colonel/saas/service/AttributionService.java` |
| 提成服务 | `backend/src/main/java/com/colonel/saas/service/CommissionService.java` |
| 数据范围注解 | `backend/src/main/java/com/colonel/saas/annotation/DataScope.java` |
