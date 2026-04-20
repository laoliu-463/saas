# 需求：独家机制

**文档版本**：V1.0
**来源**：V2.2 定稿文档 §3.4、§3.7
**智能体入口**：直接读取此文件

---

## 一、独家达人

### 1.1 触发条件

> **两个条件必须同时满足**

| 条件 | 阈值 | 说明 |
|------|------|------|
| 服务费占比 | ≥ 本团该达人总服务费的 **70%**（含70%） | 该渠道产生的服务费 ÷ 该达人总服务费 |
| 月寄样数量 | ≥ **10 个**（待发货及之后状态） | 包含待发货、快递中、待交作业、已完成 |

### 1.2 统计口径

- 寄样数量：统计**待发货及之后**状态（已通过审核，寄样流程已启动）
- 服务费：以抖音接口返回的 `service_fee` 为准

### 1.3 生效/退出机制

| 场景 | 生效时间 |
|------|----------|
| 当月满足条件 | 整月享受独家资格 |
| 当月不满足条件 | **下月初生效**（失去独家资格） |

### 1.4 独家达人效果

> 该达人所有后续订单的服务费收益，**全部归该渠道所有**（无论订单通过谁的推广链接产生）

---

## 二、独家商家

### 2.1 触发条件

| 条件 | 阈值 |
|------|------|
| 服务费占比 | ≥ 该招商个人总服务费的 **70%**（含70%） |

### 2.2 生效/退出机制

| 场景 | 生效时间 |
|------|----------|
| 当月满足条件 | 整月享受独家资格 |
| 当月不满足条件 | **下月初生效**（失去独家资格） |

### 2.3 独家商家效果

> 该商家所有后续订单的服务费收益，**全部归该招商所有**（无论订单通过哪个招商审核上架的）

### 2.4 商家库建立

- 数据来源：通过**订单同步接口**，从订单详情中提取商家 ID 和商家名称
- 被动填充，无需主动同步全量商家
- 商家信息存储在 `merchant` 表

---

## 三、订单业绩归属优先级

```
1. 获取订单的商家 ID
2. 查询该商家是否有"生效中"的独家招商
   → 有 → 业绩归该独家招商（按商品归属失效）
   → 无 → 继续
3. 获取订单的达人 UID
4. 查询该达人是否有"生效中"的独家渠道
   → 有 → 业绩归该独家渠道（pick_source 归因失效）
   → 无 → 继续
5. 通过 pick_source 映射表 → 渠道
6. 通过商品 → 活动 → 招商负责人
```

---

## 四、配置项

| 规则项 | 默认值 | 配置位置 |
|--------|--------|----------|
| 独家达人服务费占比阈值 | 70% | system_config |
| 独家达人月寄样数量阈值 | 10 个 | system_config |
| 独家商家服务费占比阈值 | 70% | system_config |

---

## 五、触发判断实现

### 5.1 独家达人判断

```java
// ExclusiveTalentService.checkAndCreate()
public void evaluateMonthly(UUID talentId, UUID channelId, YearMonth month) {
    // 1. 获取该达人当月总服务费
    Long totalServiceFee = orderService.sumServiceFeeByTalent(talentId, month);
    if (totalServiceFee == 0) return;

    // 2. 获取该渠道该达人的服务费
    Long channelServiceFee = orderService.sumServiceFeeByTalentAndChannel(
        talentId, channelId, month);

    // 3. 获取该渠道该达人当月寄样数量（待发货及之后）
    long sampleCount = sampleRequestService.countEffectiveSamples(channelId, talentId, month);

    // 4. 获取配置阈值
    SystemConfig config = systemConfigService.getConfig();
    BigDecimal feeRatio = BigDecimal.valueOf(channelServiceFee)
        .divide(BigDecimal.valueOf(totalServiceFee), 4, RoundingMode.HALF_UP);

    boolean feeThreshold = feeRatio.compareTo(config.getExclusiveTalentFeeRatio()) >= 0;
    boolean sampleThreshold = sampleCount >= config.getExclusiveTalentSampleCount();

    if (feeThreshold && sampleThreshold) {
        // 满足条件，创建独家达人记录
        createExclusiveTalent(talentId, channelId, month, feeRatio, sampleCount);
    } else {
        // 不满足，删除当月独家记录（如果有）
        removeExclusiveTalent(talentId, channelId, month);
    }
}
```

### 5.2 独家商家判断

```java
// ExclusiveMerchantService.evaluateMonthly()
public void evaluateMonthly(UUID merchantId, UUID zsManagerId, YearMonth month) {
    // 1. 获取该招商个人当月总服务费
    Long totalServiceFee = orderService.sumServiceFeeByZsManager(zsManagerId, month);
    if (totalServiceFee == 0) return;

    // 2. 获取该商家给该招商的服务费
    Long merchantServiceFee = orderService.sumServiceFeeByMerchantAndZsManager(
        merchantId, zsManagerId, month);

    BigDecimal ratio = BigDecimal.valueOf(merchantServiceFee)
        .divide(BigDecimal.valueOf(totalServiceFee), 4, RoundingMode.HALF_UP);

    SystemConfig config = systemConfigService.getConfig();
    if (ratio.compareTo(config.getExclusiveMerchantFeeRatio()) >= 0) {
        createExclusiveMerchant(merchantId, zsManagerId, month, ratio);
    } else {
        removeExclusiveMerchant(merchantId, zsManagerId, month);
    }
}
```

---

## 六、定时任务

| 任务 | 频率 | 说明 |
|------|------|------|
| 独家达人月终评估 | 每月1日 03:00 | 判断上月是否满足条件 |
| 独家商家月终评估 | 每月1日 03:30 | 判断上月是否满足条件 |
| 独家生效检查 | 订单入库时 | 实时判断是否生效独家 |

---

## 六.1 命名约定

> Java 代码使用 camelCase（如 `zsManagerId`），数据库字段使用 snake_case（如 `zs_manager_id`），映射关系如下：

| Java 属性 | 数据库字段 | 说明 |
|-----------|------------|------|
| `zsManagerId` | `zs_manager_id` | 招商负责人 |
| `channelId` | `channel_id` | 渠道 |
| `talentId` | `talent_id` | 达人 |
| `merchantId` | `merchant_id` | 商家 |

---

## 七、业务约束

| 约束 | 文件 | 级别 |
|------|------|------|
| 提成比例必须引用配置 | `rules/exclusive-triggers.md` | **CRITICAL** |
| 归因逻辑优先级正确 | `rules/attribution-logic.md` | **CRITICAL** |

---

## 八、相关文件索引

| 文件 | 路径 |
|------|------|
| 独家达人实体 | `backend/src/main/java/com/colonel/saas/entity/ExclusiveTalent.java` |
| 独家商家实体 | `backend/src/main/java/com/colonel/saas/entity/ExclusiveMerchant.java` |
| 商家实体 | `backend/src/main/java/com/colonel/saas/entity/Merchant.java` |
| 独家达人服务 | `backend/src/main/java/com/colonel/saas/service/ExclusiveTalentService.java` |
| 独家商家服务 | `backend/src/main/java/com/colonel/saas/service/ExclusiveMerchantService.java` |
| 独家触发约束 | `rules/exclusive-triggers.md` |
