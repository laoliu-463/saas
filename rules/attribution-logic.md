# 规则：归因逻辑约束

**版本**：V1.0
**状态**：强制执行
**适用范围**：所有涉及订单归因的代码

---

## 一、归因核心约束

### 1.1 必须通过 pick_source 映射 channel_id

```java
// ✅ 正确：必须通过 PickSourceMapping 表归因
public UUID resolveChannelId(String pickSource) {
    PickSourceMapping mapping = pickSourceMappingMapper
        .selectOne(Wrappers.<PickSourceMapping>lambdaQuery()
            .eq(PickSourceMapping::getPickSource, pickSource));
    return mapping != null ? mapping.getChannelId() : null;
}

// ❌ 错误：直接从 pick_source 解析 channel_id
public UUID wrongResolve(String pickSource) {
    // 禁止：从 pick_source 字符串解析 channel_id
    String channelId = pickSource.split("_")[0]; // 禁止！
    return UUID.fromString(channelId);
}
```

### 1.2 pick_source 格式验证

| 字段 | 约束 | 验证正则 |
|------|------|----------|
| pick_source | 非空，≤128字符 | `^[a-zA-Z0-9_-]{1,128}$` |
| pick_extra | ≤20字符 | `^.{0,20}$` |

---

## 二、归因触发时机

### 2.1 订单写入时必须归因

```java
// 订单入库前必须完成归因
@PrePersist
public void attributeOrder() {
    if (this.channelId == null && this.pickSource != null) {
        this.channelId = resolveChannelId(this.pickSource);
        if (this.channelId == null) {
            throw new BusinessException("归因失败：pick_source 未找到对应渠道");
        }
    }
}
```

### 2.2 禁止无归因订单

- [ ] 禁止将 `channel_id` 为 NULL 的订单写入 `colonelsettlement_order`
- [ ] 禁止在归因完成前执行业务操作（如计算提成）

---

## 三、短链生成约束

### 3.1 pick_source 生成规则

```java
// pick_source = {userId前缀}_{shortId}_{timestamp}
public String generatePickSource(UUID userId, String activityId) {
    String shortId = generateUniqueShortId();
    return String.format("%s_%s_%d",
        userId.toString().substring(0, 8).toLowerCase(),
        shortId,
        System.currentTimeMillis() / 1000
    );
}
```

### 3.2 short_id 唯一性保证

- [ ] `short_id` 必须在 `pick_source_mapping` 表中 UNIQUE
- [ ] 重复生成时必须处理 `DuplicateKeyException`
- [ ] 必须使用数据库 Sequence 或分布式 ID 生成器

---

## 四、验收测试

```java
@Test
void shouldAttributeOrderThroughPickSourceMapping() {
    // given
    String pickSource = "usr_abc12345_1712000000";

    // when
    UUID channelId = attributionService.resolveChannelId(pickSource);

    // then
    assertThat(channelId).isNotNull();
}

@Test
void shouldRejectOrderWithoutChannelId() {
    // given
    Order order = new Order();
    order.setPickSource("valid_pick_source");
    order.setChannelId(null); // 未归因

    // when/then
    assertThatThrownBy(() -> orderService.save(order))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("归因失败");
}
```

---

## 五、相关文件索引

| 文件 | 说明 |
|------|------|
| `PickSourceMapping.java` | 归因映射实体 |
| `AttributionService.java` | 归因服务 |
| `Order.java` | 订单实体 |
| 需求入口 | `requirements/03-api-specs.md` |
