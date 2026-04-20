# 规则：实体类约束

**版本**：V1.0
**状态**：强制执行
**适用范围**：所有 Java 实体类

---

## 一、主键约束

### 1.1 UUID 主键（业务表）

```java
// ✅ 正确：使用 UUID 类型主键
@Data
@TableName("product")
public class Product extends BaseEntity {
    // id 继承自 BaseEntity，已配置 UUID 主键
}

// ❌ 错误：使用 Long 主键
private Long id; // 禁止！业务表应使用 UUID
```

### 1.2 复合主键（分区表）

```java
// colonelsettlement_order 和 operation_log 使用复合主键
@TableName("colonelsettlement_order")
@EqualsAndHashCode(callSuper = true)
public class Order extends BaseEntity {
    // 分区表复合主键在 DDL 中定义：PRIMARY KEY (id, create_time)
}
```

---

## 二、字段类型约束

### 2.1 UUID 外键

```java
// ✅ 正确：使用 UUID 类型
private UUID userId;
private UUID deptId;
private UUID talentId;
private UUID channelId;

// ❌ 错误：使用 String 类型
private String userId; // 禁止！
```

### 2.2 金额字段

```java
// ✅ 正确：使用 Long（单位：分）
private Long commissionFee;    // 服务费（分）
private Long productPrice;    // 商品价格（分）
private Long settlementAmount; // 结算金额（分）

// ❌ 错误：使用 Double
private Double commissionFee; // 禁止！精度问题
```

### 2.3 比例字段

```java
// ✅ 正确：使用 BigDecimal
private BigDecimal commissionRatio; // 提成比例（0.15 表示 15%）
private BigDecimal discountRate;    // 折扣率

// ❌ 错误：使用 Double 存储比例
private Double ratio; // 禁止！
```

---

## 三、JSONB 列约束

### 3.1 JSONB 字段声明

```java
// ✅ 正确：使用 Map<String, Object> + JacksonTypeHandler
@TableField(typeHandler = JacksonTypeHandler.class)
private Map<String, Object> extraData;

@TableField(typeHandler = JacksonTypeHandler.class)
private Map<String, Object> permissions;
```

### 3.2 JSONB 大小限制

- [ ] **禁止**：存储超过 1MB 的 JSONB 数据
- [ ] 建议在实体层添加大小校验

```java
@PrePersist
public void validateJsonbSize() {
    if (extraData != null) {
        String json = JsonUtils.toJson(extraData);
        if (json.getBytes().length > 1024 * 1024) {
            throw new BusinessException("JSONB 字段超过 1MB 限制");
        }
    }
}
```

---

## 四、审计字段约束

### 4.1 必须继承 BaseEntity

```java
// ✅ 正确：继承 BaseEntity
@Data
@TableName("product")
public class Product extends BaseEntity {
    // 自动获得：id, createTime, updateTime, createBy, updateBy, deleted
}

// ❌ 错误：不继承
@Data
@TableName("product")
public class WrongProduct {
    // 禁止！缺少审计字段
}
```

### 4.2 日志表除外

```java
// 追加型日志表不继承 BaseEntity，仅含 deleted
@Data
@TableName("sample_status_log")
public class SampleStatusLog {
    @TableId(type = IdType.AUTO)
    private UUID id;

    private LocalDateTime createTime;
    private UUID operatorId;

    @TableLogic
    private Integer deleted = 0;
}
```

---

## 五、禁止做法汇总

| 序号 | 禁止 | 正确做法 |
|------|------|----------|
| 1 | String 存储 UUID | 使用 UUID 类型 |
| 2 | Double 存储金额 | 使用 Long（分） |
| 3 | Double 存储比例 | 使用 BigDecimal |
| 4 | 业务表使用 Long 主键 | 继承 BaseEntity（UUID） |
| 5 | JSONB 超过 1MB | 校验大小，超过则拒绝 |
| 6 | 不继承 BaseEntity | 业务表必须继承 |

---

## 六、验收测试

```java
@Test
void shouldRejectStringUuidInEntity() {
    // 此规则由代码审查强制执行，无自动测试
    // 审查时应检查实体类字段类型
}

@Test
void shouldRejectDoubleForMoney() {
    // 此规则由代码审查强制执行
    // 审查时应检查金额字段类型
}
```

---

## 七、相关文件索引

| 文件 | 说明 |
|------|------|
| `BaseEntity.java` | 统一基类 |
| `CustomMetaObjectHandler.java` | 自动填充处理器 |
| `Product.java` | 商品实体（示例） |
| `Order.java` | 订单实体（分区表示例） |
| 需求入口 | `requirements/02-data-schema.md` |
