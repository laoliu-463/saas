# 规则：独家达人/商家判定约束

**版本**：V1.0
**状态**：强制执行
**适用范围**：独家服务费计算、达人/商家升降级

---

## 一、独家达人判定规则

### 1.1 判定条件（必须同时满足）

| 条件 | 阈值 | 说明 |
|------|------|------|
| 服务费占比 | ≥ 70% | 该达人带来订单的服务费占总服务费的比例 |
| 寄样数量 | ≥ 10 单 | 自然月内该达人的有效寄样数量 |

### 1.2 实现约束

```java
// ✅ 正确：必须同时检查两个条件
public boolean isExclusiveTalent(Talent talent, String month) {
    // 条件1：服务费占比 >= 70%
    BigDecimal commissionRatio = calculateCommissionRatio(talent, month);
    if (commissionRatio.compareTo(BigDecimal.valueOf(70)) < 0) {
        return false;
    }

    // 条件2：寄样数量 >= 10
    Integer sampleCount = countValidSamples(talent.getId(), month);
    return sampleCount >= 10;
}

// ❌ 错误：只检查单一条件
public boolean wrongCheck(Talent talent) {
    return calculateCommissionRatio(talent) >= 70; // 缺少寄样数量检查！
}
```

---

## 二、独家商家判定规则

### 2.1 判定条件

| 条件 | 阈值 | 说明 |
|------|------|------|
| 服务费占比 | ≥ 70% | 该商家贡献的服务费占比 |

### 2.2 月度重算机制

- [ ] 每月 1 日凌晨重新计算上月独家状态
- [ ] 状态变更记录到 `exclusive_status_log` 表
- [ ] 降级时发送通知（站内信/短信）

---

## 三、提成比例配置

### 3.1 必须引用配置，不得硬编码

```java
// ✅ 正确：从 SystemConfig 读取
public BigDecimal getCommissionRatio(String talentLevel) {
    String configKey = "commission_ratio_" + talentLevel;
    String ratio = systemConfigService.getValue(configKey);
    return new BigDecimal(ratio);
}

// ❌ 错误：硬编码数字
public BigDecimal wrongGetRatio() {
    return new BigDecimal("0.15"); // 禁止硬编码！
}
```

### 3.2 配置键定义

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `commission_ratio_normal` | 0.10 | 普通达人提成比例 |
| `commission_ratio_exclusive` | 0.15 | 独家达人提成比例 |
| `exclusive_talent_threshold` | 70 | 独家门槛（%） |
| `exclusive_talent_samples` | 10 | 独家寄样门槛 |

---

## 四、禁止做法

- [ ] 禁止硬编码提成比例（必须引用配置）
- [ ] 禁止在判定逻辑中使用魔法数字
- [ ] 禁止跳过服务费占比检查直接判定独家
- [ ] 禁止在非月度周期重算独家状态

---

## 五、验收测试

```java
@Test
void shouldNotBeExclusiveWhenCommissionRatioBelow70() {
    // given
    talent.setTotalCommission(BigDecimal.valueOf(1000));
    talent.setMyCommission(BigDecimal.valueOf(500)); // 50%，不足70%

    // when
    boolean result = exclusiveService.isExclusiveTalent(talent, "2024-03");

    // then
    assertThat(result).isFalse();
}

@Test
void shouldNotBeExclusiveWhenSampleCountBelow10() {
    // given
    talent.setTotalCommission(BigDecimal.valueOf(10000));
    talent.setMyCommission(BigDecimal.valueOf(8000)); // 80%，够70%
    when(sampleMapper.countValidSamples(any(), any())).thenReturn(5); // 仅5单

    // when
    boolean result = exclusiveService.isExclusiveTalent(talent, "2024-03");

    // then
    assertThat(result).isFalse();
}
```

---

## 六、相关文件索引

| 文件 | 说明 |
|------|------|
| `ExclusiveService.java` | 独家判定服务 |
| `SystemConfigService.java` | 配置服务 |
| `ExclusiveStatusLog.java` | 状态变更日志 |
| 需求入口 | `requirements/01-roles-permissions.md` |
