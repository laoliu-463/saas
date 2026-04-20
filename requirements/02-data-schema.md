# 需求：数据库设计规范

**文档版本**：V1.0
**状态**：已定稿
**智能体入口**：直接读取此文件

---

## 一、PostgreSQL → Java 类型映射

| PostgreSQL 类型 | Java 类型 | MyBatis-Plus 注解 | 说明 |
|-----------------|-----------|-------------------|------|
| `UUID` (主键) | `java.util.UUID` | `@TableId(type = IdType.AUTO)` | DB: `gen_random_uuid()` |
| `UUID` (外键) | `java.util.UUID` | `@TableField("xxx_id")` | user_id, dept_id 等 |
| `TIMESTAMP` | `LocalDateTime` | 自动映射 | - |
| `DATE` | `LocalDate` | 自动映射 | 仅日期部分 |
| `BIGINT` | `Long` | 自动映射 | 金额（分）、数量 |
| `INTEGER / INT` | `Integer` | 自动映射 | 状态码、计数 |
| `SMALLINT` | `Integer` | 自动映射 | 布尔标记 |
| `BOOLEAN` | `Boolean` | 自动映射 | - |
| `NUMERIC(p,s)` | `BigDecimal` | 自动映射 | 比例、金额 |
| `VARCHAR(n)` | `String` | 自动映射 | - |
| `TEXT` | `String` | 自动映射 | 长文本、密文 |
| `JSONB` | `Map<String, Object>` | `@TableField(typeHandler = JacksonTypeHandler.class)` | 配置数据 |
| `BYTEA` | `byte[]` | 自动映射 | 文件二进制 |

---

## 二、主键策略

### 2.1 UUID 主键（推荐用于所有业务表）

```java
// Java 实体类
@TableId(type = IdType.AUTO)
private UUID id;

// 数据库 DDL
id UUID DEFAULT gen_random_uuid() PRIMARY KEY
```

### 2.2 复合主键（分区表专用）

```sql
-- colonelsettlement_order 和 operation_log 使用复合主键
PRIMARY KEY (id, create_time)
```

> **警告**：分区键 `create_time` 必须在 INSERT 时赋值，否则 PostgreSQL 报错。

---

## 三、BaseEntity 统一基类

路径：`com.colonel.saas.common.base.BaseEntity`

```java
@Data
public abstract class BaseEntity implements Serializable {
    @TableId(type = IdType.AUTO)
    private UUID id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private UUID createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private UUID updateBy;

    @TableLogic
    private Integer deleted = 0;
}
```

---

## 四、分区表规范

### 4.1 按月分区表

| 表名 | 分区键 | 分区策略 | 说明 |
|------|--------|----------|------|
| `colonelsettlement_order` | `create_time` | RANGE (Monthly) | 订单数据，量大 |
| `operation_log` | `create_time` | RANGE (Monthly) | 操作日志 |

### 4.2 分区表约束

- [ ] **必须**：INSERT 时 `create_time` 必须有值（非 NULL）
- [ ] **必须**：查询时带上时间范围（`WHERE create_time BETWEEN ? AND ?`）
- [ ] **禁止**：对分区表进行全表扫描
- [ ] **必须**：按月创建分区，提前创建下季度分区

---

## 五、JSONB 列处理

```java
// Java 端使用 Map<String, Object>
@TableField(typeHandler = JacksonTypeHandler.class)
private Map<String, Object> permissions;

@TableField(typeHandler = JacksonTypeHandler.class)
private Map<String, Object> extraData;

// 存储结构示例
Map<String, Object> permissions = Map.of(
    "menus", List.of("product:list", "order:view"),
    "dataScope", 1,
    "apis", List.of("GET /api/products", "POST /api/orders")
);
```

---

## 六、审计字段规范

### 6.1 完整审计（继承 BaseEntity）

适用于：所有业务实体

| 字段 | 类型 | 说明 |
|------|------|------|
| create_time | TIMESTAMP | 创建时间 |
| update_time | TIMESTAMP | 更新时间 |
| create_by | UUID | 创建人 |
| update_by | UUID | 更新人 |
| deleted | INTEGER | 逻辑删除标记 |

### 6.2 追加型日志（仅含 deleted）

适用于：`sample_status_log`, `order_decrypt_record`

```java
// 不继承 BaseEntity，仅含 deleted
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

## 七、索引规范

### 7.1 必须创建的索引

| 表名 | 索引字段 | 索引类型 | 说明 |
|------|----------|----------|------|
| `sys_user` | `username` | UNIQUE | 登录账号唯一 |
| `sys_user` | `dept_id` | INDEX | 按部门查询 |
| `colonel_activity` | `activity_id` | UNIQUE | 活动 ID 唯一 |
| `colonel_activity` | `shop_id` | INDEX | 按店铺查询 |
| `product` | `product_id` | UNIQUE | 商品 ID 唯一 |
| `talent` | `douyin_uid` | UNIQUE | 抖音 UID 唯一 |
| `talent_claim` | `talent_id, user_id` | UNIQUE | 防重复认领 |
| `pick_source_mapping` | `pick_source` | UNIQUE | 归因参数唯一 |
| `pick_source_mapping` | `short_id` | UNIQUE | 短 ID 唯一 |
| `pick_source_mapping` | `uuid_seed` | INDEX | ShortID 反查还原 |
| `sample_request` | `request_no` | UNIQUE | 申请编号唯一 |
| `sample_request` | `talent_id, product_id, status` | INDEX | 防重复申请 |
| `colonelsettlement_order` | `create_time` | 分区键 | 分区表必需 |
| `colonelsettlement_order` | `order_id` | INDEX | 按订单号查询 |
| `operation_log` | `create_time` | 分区键 | 分区表必需 |
| `sample_status_log` | `create_time` | INDEX | 按时间查询 |
| `sample_status_log` | `sample_request_id` | INDEX | 按申请单查询 |

> **表关系说明**：`commission_config` 存储提成配置规则（比例），`commission_settlement` 存储结算结果（计算后的金额），两者通过 `order_id` 关联。

---

## 八、开发约束

### 8.1 必须遵守

- [ ] 新增实体类必须继承 `BaseEntity`（日志表除外）
- [ ] UUID 外键字段必须使用 `UUID` 类型，禁止 `String`
- [ ] 金额字段使用 `Long`（单位：分），禁止 `Double`
- [ ] 分区表插入时必须设置 `createTime`
- [ ] 查询分区表必须带时间范围条件

### 8.2 禁止做法

- [ ] 禁止使用 `String` 类型存储 UUID
- [ ] 禁止使用 `Double` 存储金额（精度问题）
- [ ] 禁止对分区表执行无时间条件的全表扫描
- [ ] 禁止在 JSONB 字段存储超过 1MB 的数据

---

## 九、验收标准

1. **编译通过**：所有实体类正确继承 BaseEntity
2. **单元测试**：UUID 主键生成、JSONB 序列化/反序列化正常
3. **分区表测试**：插入数据时 `create_time` 为空应抛出明确异常
4. **索引检查**：数据库迁移脚本包含所有必需索引

---

## 十、相关文件索引

| 文件 | 路径 | 说明 |
|------|------|------|
| BaseEntity | `backend/src/main/java/com/colonel/saas/common/base/BaseEntity.java` | 统一基类 |
| MetaObjectHandler | `backend/src/main/java/com/colonel/saas/config/CustomMetaObjectHandler.java` | 自动填充 |
| 数据库 DDL | `backend/src/main/resources/db/init-db.sql` | 建表脚本 |
| Lint 规则 | `rules/entity-constraints.md` | 实体类约束 |

---

## 十一、完整 DDL 参考

> 完整 SQL 脚本位于 `backend/src/main/resources/db/init-db.sql`，以下为权威版本摘要。
> 幂等设计：`CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` / `INSERT ... ON CONFLICT DO NOTHING`

### 11.1 表清单（23张）

| 序号 | 表名 | 类型 | 说明 |
|------|------|------|------|
| 1 | `sys_user` | 普通 | 用户，含 `channel_code`（pick_extra 透传） |
| 2 | `sys_role` | 普通 | 角色，含 `permissions JSONB`、`menu_config JSONB` |
| 3 | `sys_user_role` | 普通 | 用户-角色关联 |
| 4 | `sys_department` | 普通 | 部门，含 `dept_type`（business/channel） |
| 5 | `douyin_token` | 普通 | 抖音 API Token，含 `extra_data JSONB` |
| 6 | `colonel_activity` | 普通 | 团长活动，含 `colonel_buyin_id`、`commission_rate`、`service_rate` |
| 7 | `product` | 普通 | 商品，含 `cos_ratio`、`cos_fee`、`service_ratio`（V1.3） |
| 8 | `colonel_activity_product` | 普通 | 活动商品关联，含 `assignee_id`、`min_refer_amount`（V1.3） |
| 9 | `talent` | 普通 | 达人，含 `crawl_status`、`crawl_message`（V1.2） |
| 10 | `talent_claim` | 普通 | 达人认领，含 `expire_time`（保护期） |
| 11 | `exclusive_talent` | 普通 | 独家达人，含 `trigger_type`、`audit_user_id` |
| 12 | `exclusive_merchant` | 普通 | 独家商家 |
| 13 | `merchant` | 普通 | 商家，含 `status`（V1.3） |
| 14 | `pick_source_mapping` | 普通 | 归因映射，含 `short_id`（方案B）、`uuid_seed`（V1.3） |
| 15 | `sample_request` | 普通 | 寄样申请，含收件人信息（V1.2） |
| 16 | `sample_status_log` | 普通 | 状态变更日志 |
| 17 | `colonelsettlement_order` | **分区** | 订单（按月 RANGE，`create_time`） |
| 18 | `commission_settlement` | 普通 | 分佣结算 |
| 19 | `commission_config` | 普通 | 提成配置（含全局/个人/活动/商品级别） |
| 20 | `order_detail` | 普通 | 订单解密详情（含虚拟号处理） |
| 21 | `order_decrypt_record` | 普通 | 解密操作记录 |
| 22 | `system_config` | 普通 | 系统配置 |
| 23 | `operation_log` | **分区** | 操作日志（按月 RANGE，`create_time`） |

### 11.2 分区表规范

```sql
-- 分区键：create_time（必须）
-- 分区策略：RANGE (Monthly)
CREATE TABLE colonelsettlement_order (
    id         UUID,
    order_id   VARCHAR(50) NOT NULL,
    ...
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, create_time)
) PARTITION BY RANGE (create_time);

-- 子分区命名：cso_YYYY_MM（订单）、op_log_YYYY_MM（日志）
-- 当前已创建：2026-04 ~ 2027-03 共12个月分区
```

### 11.3 方案B归因字段（V1.3）

```sql
pick_source_mapping 表新增：
- short_id   VARCHAR(10) UNIQUE  -- 8位Base36透传值（≤10字符）
- uuid_seed  UUID                 -- 原始UUID，反查还原
- pick_extra VARCHAR(10)         -- 实际透传值=short_id
```

### 11.4 自动分区管理

```sql
-- 每月1号自动创建下月分区（建议由 pg_cron 调用）
SELECT create_next_month_partitions();
```

### 11.5 关键修正记录（V1.2/V1.3）

| 修正 | 级别 | 说明 |
|------|------|------|
| `sys_user.channel_code` | P0 | UUID 超 pick_extra 20字符限制，新增渠道短码 |
| `pick_source_mapping.short_id` + `uuid_seed` | P0 | 方案B ShortID 透传机制 |
| `pick_source_mapping.pick_source` VARCHAR(128) | P2 | API 实际返回可能较长 |
| `colonelsettlement_order.order_amount/actual_amount` | P0 | 订单金额字段（分） |
| `product.cos_ratio/cos_fee/service_ratio` | P0 | 商品级分佣字段 |
| `merchant.status` | P0 | 商家启用/禁用控制 |
| `douyin_token.app_id` | P1 | 抖店应用ID标识 |
| `colonel_activity_product.min_refer_amount` | P1 | 最低推广金额门槛（分） |
| `talent.crawl_status/crawl_message` | P1 | 采集状态追踪 |
| `sample_request` 收件人字段 | P1 | recipient_name/phone/address |
| `sys_role.permissions/menu_config JSONB` | P1 | 操作权限+菜单可配置 |

---

## 十二、相关文件索引

| 文件 | 路径 | 说明 |
|------|------|------|
| 完整 DDL | `backend/src/main/resources/db/init-db.sql` | V1.3 全部 20 表 + 分区 + 种子数据 |
| BaseEntity | `backend/src/main/java/com/colonel/saas/common/base/BaseEntity.java` | 统一基类 |
| MetaObjectHandler | `backend/src/main/java/com/colonel/saas/config/CustomMetaObjectHandler.java` | 自动填充 |
| Lint 规则 | `rules/entity-constraints.md` | 实体类约束 |
| 分区约束 | `rules/partition-table.md` | 分区表强制约束 |
