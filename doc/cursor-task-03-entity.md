# TASK-03：Entity 层架构设计

**文档版本**：V1.1
**生成时间**：2026-04-20
**任务类型**：B类（常规开发任务）
**前置依赖**：TASK-02 项目脚手架已完成
**状态**：待实现

### V1.1 修订说明（2026-04-20）

基于 TASK-02 骨架代码实际状态，修正以下冲突：

| # | 修正项 | 原值 | 修正值 | 原因 |
|---|--------|------|--------|------|
| 1 | 包名 | `com.colonelsaas` | `com.colonel.saas` | TASK-02 实际包名含点号 |
| 2 | BaseEntity位置 | `com.colonel.saas.entity` | `com.colonel.saas.common.base` | TASK-02 已在此位置创建 |
| 3 | BaseEntity继承 | extends BaseModel | implements Serializable | 无 BaseModel 类 |
| 4 | ID类型 | `String` + `ASSIGN_UUID` | `UUID` + `IdType.AUTO` | DB用gen_random_uuid()，Java侧用UUID类型 |
| 5 | createBy/updateBy | `String` | `UUID` | DB字段为UUID类型 |
| 6 | Lombok风格 | @Getter/@Setter | @Data | 与TASK-02统一 |
| 7 | Security依赖 | SecurityContextHolder | 移除，返回null | pom.xml无Spring Security |
| 8 | UUID外键字段 | String类型 | UUID类型 | DB所有user_id/dept_id等为UUID |
| 9 | SysRole | 缺少status字段 | 添加status | DB有status字段 |
| 10 | yml id-type | assign_uuid | auto | 让DB自生成UUID |

---

## 一、设计原则

1. **BaseEntity 统一基类**：所有含 `deleted/create_time/update_time/create_by/update_by` 的实体继承 `BaseEntity`
2. **UUID 主键策略**：`@TableId(type = IdType.AUTO)`，类型为 `java.util.UUID`，数据库默认 `gen_random_uuid()`
3. **JSONB 列处理**：`@TableField(typeHandler = JacksonTypeHandler.class)` → Java 类型 `Map<String, Object>`
4. **分区表特殊处理**：`colonelsettlement_order` 和 `operation_log` 的 `create_time` 必须作为插入字段（分区键）
5. **追加型日志表**：不继承 `BaseEntity`，仅含 `deleted` 字段（无 `update_time/update_by`）
6. **DATE 类型**：`LocalDate`（不含时间部分）；`TIMESTAMP` → `LocalDateTime`
7. **数值精度**：`NUMERIC(5,4)` → `BigDecimal`；`NUMERIC(5,2)` → `BigDecimal`

---

## 二、PostgreSQL → Java 类型映射表

| PostgreSQL 类型 | Java 类型 | 注解策略 | 说明 |
|-----------------|-----------|----------|------|
| `UUID` | `java.util.UUID` | `@TableId(type = IdType.AUTO)` | 数据库默认 `gen_random_uuid()`，Java 侧用 UUID 类型 |
| `UUID`（外键） | `java.util.UUID` | `@TableField("xxx_id")` | 如 user_id, dept_id 等外键字段 |
| `TIMESTAMP` | `LocalDateTime` | 自动映射 | MyBatis-Plus 内置支持 |
| `DATE` | `LocalDate` | 自动映射 | 仅日期，无时间 |
| `BIGINT` | `Long` | 自动映射 | 金额（分为单位）、数量 |
| `INTEGER / INT` | `Integer` | 自动映射 | 计数、排序 |
| `SMALLINT` | `Integer` | 自动映射 | 状态码、布尔标记 |
| `BOOLEAN` | `Boolean` | 自动映射 | - |
| `NUMERIC(p,s)` | `BigDecimal` | 自动映射 | 比例、金额 |
| `VARCHAR(n)` | `String` | 自动映射 | - |
| `TEXT` | `String` | 自动映射 | 长文本、密文 |
| `JSONB` | `Map<String, Object>` | `@TableField(typeHandler = JacksonTypeHandler.class)` | 配置、扩展数据 |
| `BYTEA` | `byte[]` | 自动映射 | 文件二进制 |

---

## 三、BaseEntity 基类

路径：`com.colonel.saas.common.base.BaseEntity`

```java
package com.colonel.saas.common.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 所有业务实体的统一基类
 * - 统一主键策略（UUID，由数据库 gen_random_uuid() 生成）
 * - 统一审计字段（create/update）
 * - 统一逻辑删除（@TableLogic）
 */
@Data
@EqualsAndHashCode(callSuper = false)
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

> **注意**：`BaseEntity` 位于 `com.colonel.saas.common.base` 包（TASK-02 已创建），所有实体继承此类。主键 `UUID` 类型由数据库 `DEFAULT gen_random_uuid()` 自动生成，`IdType.AUTO` 表示使用数据库默认值。`createBy`/`updateBy` 类型为 `UUID`，与数据库 `create_by UUID` 字段一致。

---

## 四、CustomMetaObjectHandler 配置

路径：`com.colonel.saas.config.CustomMetaObjectHandler`（TASK-02 已创建，需更新）

```java
package com.colonel.saas.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 自动填充 createTime / updateTime / createBy / updateBy
 * createBy/updateBy 类型为 UUID，与数据库字段一致
 * TODO: 接入认证模块后，从 SecurityContext 获取当前用户 UUID
 */
@Component
public class CustomMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "deleted",    Integer.class, 0);
        this.strictInsertFill(metaObject, "createBy",   UUID.class, getCurrentUserId());
        this.strictInsertFill(metaObject, "updateBy",   UUID.class, getCurrentUserId());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updateBy",   UUID.class, getCurrentUserId());
    }

    /**
     * 获取当前登录用户 UUID
     * TODO: 接入 Spring Security 后从 SecurityContextHolder 获取
     * 当前返回 null（未登录/系统任务场景）
     */
    private UUID getCurrentUserId() {
        return null;
    }
}
```

---

## 五、实体类详细设计

### 5.1 系统管理模块 (`com.colonel.saas.entity.system`)

#### SysUser

| Java 字段 | 类型 | 注解 | 数据库字段 |
|-----------|------|------|-----------|
| id | UUID | `@TableId(type = IdType.AUTO)` | id |
| username | String | - | username |
| password | String | - | password |
| realName | String | - | real_name |
| phone | String | - | phone |
| email | String | - | email |
| deptId | UUID | `@TableField("dept_id")` | dept_id |
| channelCode | String | `@TableField("channel_code")` | channel_code |
| status | Integer | - | status |
| lastLoginAt | LocalDateTime | `@TableField("last_login_at")` | last_login_at |
| deleted | Integer | `@TableLogic` | deleted |
| createTime | LocalDateTime | `@TableField(fill = INSERT)` | create_time |
| updateTime | LocalDateTime | `@TableField(fill = INSERT_UPDATE)` | update_time |
| createBy | UUID | `@TableField(fill = INSERT)` | create_by |
| updateBy | UUID | `@TableField(fill = INSERT_UPDATE)` | update_by |

```java
package com.colonel.saas.entity.system;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;
    private String password;
    private String realName;
    private String phone;
    private String email;

    @TableField("dept_id")
    private UUID deptId;

    @TableField("channel_code")
    private String channelCode;

    /** 1=启用, 0=禁用 */
    private Integer status;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;
}
```

#### SysRole

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| roleCode | String | - |
| roleName | String | - |
| dataScope | Integer | `data_scope`：1=本人，2=本组，3=全部 |
| permissions | `Map<String, Object>` | `@TableField(typeHandler = JacksonTypeHandler.class)` |
| menuConfig | `Map<String, Object>` | `@TableField(typeHandler = JacksonTypeHandler.class)` |
| status | Integer | `1=启用, 0=禁用` |
| remark | String | - |

```java
package com.colonel.saas.entity.system;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    private String roleCode;
    private String roleName;

    /** 1=本人, 2=本组, 3=全部 */
    private Integer dataScope;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> permissions;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> menuConfig;

    /** 1=启用, 0=禁用 */
    private Integer status;

    private String remark;
}
```

#### SysUserRole（关联表，仅含插入审计字段）

```java
package com.colonel.saas.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_user_role")
public class SysUserRole {

    @TableId(type = IdType.AUTO)
    private UUID id;

    @TableField("user_id")
    private UUID userId;

    @TableField("role_id")
    private UUID roleId;

    @TableField("create_time")
    private LocalDateTime createTime;

    /** 不继承 BaseEntity：此表无需 update/delete 逻辑 */
}
```

---

### 5.2 权限模块 (`com.colonel.saas.entity.auth`)

无独立实体，权限数据存储在 `SysRole.permissions` JSONB 字段中。

---

### 5.3 抖音模块 (`com.colonel.saas.entity.douyin`)

#### DouyinToken

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| appId | String | - |
| shopId | Long | `@TableField("shop_id")` |
| shopName | String | - |
| authorityId | String | - |
| authSubjectType | String | - |
| tokenType | Integer | `0=主店铺, 1=子店铺` |
| accessToken | String | TEXT |
| refreshToken | String | TEXT |
| expiresIn | Long | 秒数 |
| tokenExpireAt | LocalDateTime | - |
| refreshExpireAt | LocalDateTime | - |
| encryptOperator | String | - |
| operatorName | String | - |
| toutiaoId | String | - |
| status | Integer | - |
| extraData | `Map<String, Object>` | `@TableField(typeHandler = JacksonTypeHandler.class)` |

```java
package com.colonel.saas.entity.douyin;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("douyin_token")
public class DouyinToken extends BaseEntity {

    private String appId;

    @TableField("shop_id")
    private Long shopId;

    private String shopName;
    private String authorityId;
    private String authSubjectType;

    /** 0=主店铺, 1=子店铺 */
    private Integer tokenType;

    @TableField("access_token")
    private String accessToken;

    @TableField("refresh_token")
    private String refreshToken;

    @TableField("expires_in")
    private Long expiresIn;

    @TableField("token_expire_at")
    private LocalDateTime tokenExpireAt;

    @TableField("refresh_expire_at")
    private LocalDateTime refreshExpireAt;

    @TableField("encrypt_operator")
    private String encryptOperator;

    @TableField("operator_name")
    private String operatorName;

    @TableField("toutiao_id")
    private String toutiaoId;

    /** 1=有效, 0=失效 */
    private Integer status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraData;
}
```

---

### 5.4 活动模块 (`com.colonel.saas.entity.activity`)

#### ColonelActivity

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| activityId | String | `@TableField("activity_id")`，UNIQUE |
| activityName | String | - |
| activityType | String | - |
| shopId | Long | - |
| shopName | String | - |
| colonelBuyinId | Long | `@TableField("colonel_buyin_id")` |
| commissionRate | BigDecimal | `@TableField("commission_rate")`，`NUMERIC(5,4)` |
| serviceRate | BigDecimal | `@TableField("service_rate")`，`NUMERIC(5,4)` |
| startTime | LocalDateTime | - |
| endTime | LocalDateTime | - |
| status | String | - |
| monthsOfProtection | Integer | - |
| lastSyncAt | LocalDateTime | - |
| extraData | `Map<String, Object>` | JSONB |

```java
package com.colonel.saas.entity.activity;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("colonel_activity")
public class ColonelActivity extends BaseEntity {

    @TableField("activity_id")
    private String activityId;

    private String activityName;
    private String activityType;

    @TableField("shop_id")
    private Long shopId;

    private String shopName;

    @TableField("colonel_buyin_id")
    private Long colonelBuyinId;

    @TableField("commission_rate")
    private BigDecimal commissionRate;

    @TableField("service_rate")
    private BigDecimal serviceRate;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private String status;

    @TableField("months_of_protection")
    private Integer monthsOfProtection;

    @TableField("last_sync_at")
    private LocalDateTime lastSyncAt;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraData;
}
```

---

### 5.5 商品模块 (`com.colonel.saas.entity.product`)

#### Product

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| productId | String | UNIQUE |
| outerProductId | String | - |
| name | String | - |
| description | String | TEXT |
| marketPrice | Long | 分 |
| discountPrice | Long | 分 |
| cover | String | - |
| detailUrl | String | - |
| firstCid | Long | - |
| secondCid | Long | - |
| thirdCid | Long | - |
| fourthCid | Long | - |
| categoryDetail | `Map<String, Object>` | JSONB |
| pics | `List<Map<String, Object>>` | JSONB 数组 |
| specPrices | `Map<String, Object>` | JSONB |
| cosRatio | BigDecimal | `NUMERIC(5,2)` |
| cosFee | Long | - |
| serviceRatio | BigDecimal | `NUMERIC(5,2)` |
| status | Integer | - |
| checkStatus | Integer | - |

```java
package com.colonel.saas.entity.product;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class Product extends BaseEntity {

    @TableField("product_id")
    private String productId;

    @TableField("outer_product_id")
    private String outerProductId;

    private String name;

    /** TEXT 类型，MyBatis-Plus 自动映射为 String */
    private String description;

    /** 金额字段统一以"分"为单位存储，Long 类型 */
    private Long marketPrice;
    private Long discountPrice;

    private String cover;
    private String detailUrl;

    @TableField("first_cid")
    private Long firstCid;

    @TableField("second_cid")
    private Long secondCid;

    @TableField("third_cid")
    private Long thirdCid;

    @TableField("fourth_cid")
    private Long fourthCid;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> categoryDetail;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> pics;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> specPrices;

    @TableField("cos_ratio")
    private BigDecimal cosRatio;

    @TableField("cos_fee")
    private Long cosFee;

    @TableField("service_ratio")
    private BigDecimal serviceRatio;

    /** 1=在售, 0=下架 */
    private Integer status;

    /** 1=已审核, 0=待审核, 2=拒绝 */
    private Integer checkStatus;
}
```

---

### 5.6 团长活动商品 (`com.colonel.saas.entity.activity`)

#### ColonelActivityProduct

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| activityId | String | 索引 |
| activityName | String | - |
| productId | String | - |
| title | String | - |
| price | Long | 分 |
| cosRatio | BigDecimal | `NUMERIC(5,2)` |
| cosFee | Long | - |
| serviceRatio | BigDecimal | `NUMERIC(5,2)` |
| status | Integer | - |
| shopId | Long | - |
| shopName | String | - |
| activityStartTime | LocalDateTime | - |
| activityEndTime | LocalDateTime | - |
| promotionStartTime | LocalDateTime | - |
| promotionEndTime | LocalDateTime | - |
| monthsOfProtection | Integer | - |
| cover | String | - |
| detailUrl | String | - |
| firstCid | Long | - |
| secondCid | Long | - |
| thirdCid | Long | - |
| assigneeId | UUID | - |
| sampleRequirement | `Map<String, Object>` | JSONB |
| promotionInfo | `Map<String, Object>` | JSONB |
| auditStatus | Integer | `0=待分配,1=待审核,2=通过,3=拒绝,4=撤回` |
| auditTime | LocalDateTime | - |
| auditRemark | String | TEXT |
| minReferAmount | Long | - |
| extraData | `Map<String, Object>` | JSONB |

```java
package com.colonel.saas.entity.activity;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("colonel_activity_product")
public class ColonelActivityProduct extends BaseEntity {

    @TableField("activity_id")
    private String activityId;

    private String activityName;

    @TableField("product_id")
    private String productId;

    private String title;

    /** 价格单位：分 */
    private Long price;

    @TableField("cos_ratio")
    private BigDecimal cosRatio;

    @TableField("cos_fee")
    private Long cosFee;

    @TableField("service_ratio")
    private BigDecimal serviceRatio;

    /** 1=推广中, 0=已结束 */
    private Integer status;

    @TableField("shop_id")
    private Long shopId;

    private String shopName;

    @TableField("activity_start_time")
    private LocalDateTime activityStartTime;

    @TableField("activity_end_time")
    private LocalDateTime activityEndTime;

    @TableField("promotion_start_time")
    private LocalDateTime promotionStartTime;

    @TableField("promotion_end_time")
    private LocalDateTime promotionEndTime;

    @TableField("months_of_protection")
    private Integer monthsOfProtection;

    private String cover;
    private String detailUrl;

    @TableField("first_cid")
    private Long firstCid;

    @TableField("second_cid")
    private Long secondCid;

    @TableField("third_cid")
    private Long thirdCid;

    @TableField("assignee_id")
    private UUID assigneeId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> sampleRequirement;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> promotionInfo;

    /** 0=待分配, 1=待审核, 2=通过, 3=拒绝, 4=撤回 */
    private Integer auditStatus;

    @TableField("audit_time")
    private LocalDateTime auditTime;

    @TableField("audit_remark")
    private String auditRemark;

    @TableField("min_refer_amount")
    private Long minReferAmount;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraData;
}
```

---

### 5.7 达人模块 (`com.colonel.saas.entity.talent`)

#### Talent

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| douyinUid | String | UNIQUE |
| nickname | String | - |
| fansCount | Long | - |
| fansLevel | String | - |
| liveLevel | String | - |
| avatarUrl | String | - |
| intro | String | TEXT |
| categories | `List<String>` | JSONB |
| contactPhone | String | - |
| contactWechat | String | - |
| addrCity | String | - |
| addrDistrict | String | - |
| crawlSource | String | - |
| crawlStatus | Integer | `0=待采集,1=成功,2=失败` |
| crawlMessage | String | - |
| lastCrawlAt | LocalDateTime | - |
| status | Integer | - |
| likesCount | Long | - |
| followingCount | Long | - |
| worksCount | Long | - |
| ipLocation | String | - |
| extraData | `Map<String, Object>` | JSONB |

```java
package com.colonel.saas.entity.talent;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent")
public class Talent extends BaseEntity {

    @TableField("douyin_uid")
    private String douyinUid;

    private String nickname;

    @TableField("fans_count")
    private Long fansCount;

    @TableField("fans_level")
    private String fansLevel;

    @TableField("live_level")
    private String liveLevel;

    @TableField("avatar_url")
    private String avatarUrl;

    /** TEXT */
    private String intro;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> categories;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("contact_wechat")
    private String contactWechat;

    @TableField("addr_city")
    private String addrCity;

    @TableField("addr_district")
    private String addrDistrict;

    @TableField("crawl_source")
    private String crawlSource;

    /** 0=待采集, 1=成功, 2=失败 */
    @TableField("crawl_status")
    private Integer crawlStatus;

    @TableField("crawl_message")
    private String crawlMessage;

    @TableField("last_crawl_at")
    private LocalDateTime lastCrawlAt;

    /** 1=正常, 0=禁用 */
    private Integer status;

    @TableField("likes_count")
    private Long likesCount;

    @TableField("following_count")
    private Long followingCount;

    @TableField("works_count")
    private Long worksCount;

    @TableField("ip_location")
    private String ipLocation;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraData;
}
```

#### TalentClaim

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| talentId | UUID | - |
| talentUid | String | - |
| userId | UUID | - |
| deptId | UUID | - |
| claimType | Integer | `1=招商认领, 2=渠道认领` |
| status | Integer | `1=已认领, 0=已释放` |
| applyTime | LocalDateTime | - |
| confirmTime | LocalDateTime | - |
| expireTime | LocalDateTime | - |
| remark | String | TEXT |

```java
package com.colonel.saas.entity.talent;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_claim")
public class TalentClaim extends BaseEntity {

    @TableField("talent_id")
    private UUID talentId;

    @TableField("talent_uid")
    private String talentUid;

    @TableField("user_id")
    private UUID userId;

    @TableField("dept_id")
    private UUID deptId;

    /** 1=招商认领, 2=渠道认领 */
    @TableField("claim_type")
    private Integer claimType;

    /** 1=已认领, 0=已释放 */
    private Integer status;

    @TableField("apply_time")
    private LocalDateTime applyTime;

    @TableField("confirm_time")
    private LocalDateTime confirmTime;

    @TableField("expire_time")
    private LocalDateTime expireTime;

    /** TEXT */
    private String remark;
}
```

#### ExclusiveTalent

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| talentId | UUID | - |
| talentUid | String | - |
| userId | UUID | - |
| deptId | UUID | - |
| exclusiveType | Integer | `1=独家招商, 2=独家渠道` |
| effectiveMonth | String | `YYYY-MM` |
| serviceFee | Long | 分 |
| channelTotalFee | Long | 分 |
| serviceFeeRatio | BigDecimal | `NUMERIC(5,2)` |
| monthlySamples | Integer | - |
| startDate | LocalDate | DATE |
| endDate | LocalDate | DATE |
| status | Integer | - |
| triggerType | Integer | `1=自动, 2=人工` |
| auditUserId | UUID | - |
| remark | String | TEXT |

```java
package com.colonel.saas.entity.talent;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exclusive_talent")
public class ExclusiveTalent extends BaseEntity {

    @TableField("talent_id")
    private UUID talentId;

    @TableField("talent_uid")
    private String talentUid;

    @TableField("user_id")
    private UUID userId;

    @TableField("dept_id")
    private UUID deptId;

    /** 1=独家招商, 2=独家渠道 */
    @TableField("exclusive_type")
    private Integer exclusiveType;

    @TableField("effective_month")
    private String effectiveMonth; // YYYY-MM

    @TableField("service_fee")
    private Long serviceFee;

    @TableField("channel_total_fee")
    private Long channelTotalFee;

    @TableField("service_fee_ratio")
    private BigDecimal serviceFeeRatio;

    @TableField("monthly_samples")
    private Integer monthlySamples;

    @TableField("start_date")
    private LocalDate startDate;

    @TableField("end_date")
    private LocalDate endDate;

    private Integer status;

    /** 1=自动, 2=人工 */
    @TableField("trigger_type")
    private Integer triggerType;

    @TableField("audit_user_id")
    private UUID auditUserId;

    /** TEXT */
    private String remark;
}
```

---

### 5.8 商家模块 (`com.colonel.saas.entity.merchant`)

#### Merchant

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| merchantId | String | UNIQUE |
| merchantName | String | - |
| shopId | Long | - |
| shopName | String | - |
| sourceOrderId | String | - |
| status | Integer | - |
| extraData | `Map<String, Object>` | JSONB |

```java
package com.colonel.saas.entity.merchant;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("merchant")
public class Merchant extends BaseEntity {

    @TableField("merchant_id")
    private String merchantId;

    @TableField("merchant_name")
    private String merchantName;

    @TableField("shop_id")
    private Long shopId;

    @TableField("shop_name")
    private String shopName;

    @TableField("source_order_id")
    private String sourceOrderId;

    private Integer status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraData;
}
```

#### ExclusiveMerchant

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| merchantId | String | - |
| merchantName | String | - |
| shopId | Long | - |
| userId | UUID | - |
| deptId | UUID | - |
| effectiveMonth | String | `YYYY-MM`，UNIQUE 组合之一 |
| serviceFee | Long | - |
| businessTotalFee | Long | - |
| serviceFeeRatio | BigDecimal | `NUMERIC(5,2)` |
| startDate | LocalDate | DATE |
| endDate | LocalDate | DATE |
| status | Integer | - |
| remark | String | TEXT |

```java
package com.colonel.saas.entity.merchant;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exclusive_merchant")
public class ExclusiveMerchant extends BaseEntity {

    @TableField("merchant_id")
    private String merchantId;

    @TableField("merchant_name")
    private String merchantName;

    @TableField("shop_id")
    private Long shopId;

    @TableField("user_id")
    private UUID userId;

    @TableField("dept_id")
    private UUID deptId;

    @TableField("effective_month")
    private String effectiveMonth;

    @TableField("service_fee")
    private Long serviceFee;

    @TableField("business_total_fee")
    private Long businessTotalFee;

    @TableField("service_fee_ratio")
    private BigDecimal serviceFeeRatio;

    @TableField("start_date")
    private LocalDate startDate;

    @TableField("end_date")
    private LocalDate endDate;

    private Integer status;

    /** TEXT */
    private String remark;
}
```

---

### 5.9 归因模块 (`com.colonel.saas.entity.picksource`)

#### PickSourceMapping

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| userId | UUID | - |
| shortId | String | UNIQUE，`VARCHAR(10)` |
| uuidSeed | UUID | UUID |
| deptId | UUID | - |
| pickSource | String | UNIQUE |
| productId | String | - |
| activityId | String | - |
| sourceUrl | String | TEXT |
| convertedUrl | String | TEXT |
| clickCount | Integer | - |
| orderCount | Integer | - |
| orderAmount | Long | 分 |
| pickExtra | String | `VARCHAR(10)` |
| validFrom | LocalDateTime | - |
| validUntil | LocalDateTime | - |
| status | Integer | - |

```java
package com.colonel.saas.entity.picksource;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pick_source_mapping")
public class PickSourceMapping extends BaseEntity {

    @TableField("user_id")
    private UUID userId;

    @TableField("short_id")
    private String shortId;

    @TableField("uuid_seed")
    private UUID uuidSeed;

    @TableField("dept_id")
    private UUID deptId;

    @TableField("pick_source")
    private String pickSource;

    @TableField("product_id")
    private String productId;

    @TableField("activity_id")
    private String activityId;

    @TableField("source_url")
    private String sourceUrl;

    @TableField("converted_url")
    private String convertedUrl;

    @TableField("click_count")
    private Integer clickCount;

    @TableField("order_count")
    private Integer orderCount;

    @TableField("order_amount")
    private Long orderAmount;

    @TableField("pick_extra")
    private String pickExtra;

    @TableField("valid_from")
    private LocalDateTime validFrom;

    @TableField("valid_until")
    private LocalDateTime validUntil;

    private Integer status;
}
```

---

### 5.10 寄样模块 (`com.colonel.saas.entity.sample`)

#### SampleRequest

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| requestNo | String | UNIQUE |
| talentId | UUID | FK |
| talentUid | String | - |
| talentNickname | String | - |
| productId | UUID | FK |
| activityProductId | String | - |
| activityId | String | - |
| userId | UUID | FK（申请人/招商） |
| deptId | UUID | FK |
| channelUserId | UUID | FK（渠道对接人） |
| channelDeptId | UUID | FK（渠道部门） |
| recipientName | String | - |
| recipientPhone | String | `VARCHAR(32)` |
| recipientAddress | String | `VARCHAR(512)` |
| expectedSampleNum | Integer | - |
| actualSampleNum | Integer | - |
| logisticsCompany | String | - |
| trackingNo | String | - |
| status | Integer | `0=待审核,1=已通过,2=已拒绝,3=已寄出,4=已收货,5=已完成,6=已关闭` |
| auditRemark | String | TEXT |
| rejectReason | String | TEXT |
| sampleFee | Long | 分 |
| auditTime | LocalDateTime | - |
| shipTime | LocalDateTime | - |
| deliverTime | LocalDateTime | - |
| homeworkDeadline | LocalDateTime | - |
| completeTime | LocalDateTime | - |
| closeTime | LocalDateTime | - |
| closeReason | String | TEXT |
| remark | String | TEXT |
| extraData | `Map<String, Object>` | JSONB |

```java
package com.colonel.saas.entity.sample;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sample_request")
public class SampleRequest extends BaseEntity {

    @TableField("request_no")
    private String requestNo;

    @TableField("talent_id")
    private UUID talentId;

    @TableField("talent_uid")
    private String talentUid;

    @TableField("talent_nickname")
    private String talentNickname;

    @TableField("product_id")
    private UUID productId;

    @TableField("activity_product_id")
    private String activityProductId;

    @TableField("activity_id")
    private String activityId;

    @TableField("user_id")
    private UUID userId;

    @TableField("dept_id")
    private UUID deptId;

    @TableField("channel_user_id")
    private UUID channelUserId;

    @TableField("channel_dept_id")
    private UUID channelDeptId;

    @TableField("recipient_name")
    private String recipientName;

    @TableField("recipient_phone")
    private String recipientPhone;

    @TableField("recipient_address")
    private String recipientAddress;

    @TableField("expected_sample_num")
    private Integer expectedSampleNum;

    @TableField("actual_sample_num")
    private Integer actualSampleNum;

    @TableField("logistics_company")
    private String logisticsCompany;

    @TableField("tracking_no")
    private String trackingNo;

    /** 0=待审核, 1=已通过, 2=已拒绝, 3=已寄出, 4=已收货, 5=已完成, 6=已关闭 */
    private Integer status;

    @TableField("audit_remark")
    private String auditRemark;

    @TableField("reject_reason")
    private String rejectReason;

    @TableField("sample_fee")
    private Long sampleFee;

    @TableField("audit_time")
    private LocalDateTime auditTime;

    @TableField("ship_time")
    private LocalDateTime shipTime;

    @TableField("deliver_time")
    private LocalDateTime deliverTime;

    @TableField("homework_deadline")
    private LocalDateTime homeworkDeadline;

    @TableField("complete_time")
    private LocalDateTime completeTime;

    @TableField("close_time")
    private LocalDateTime closeTime;

    @TableField("close_reason")
    private String closeReason;

    /** TEXT */
    private String remark;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraData;
}
```

#### SampleStatusLog（追加型日志，不继承 BaseEntity，含 deleted 字段）

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| requestId | UUID | - |
| fromStatus | Integer | - |
| toStatus | Integer | - |
| operatorId | UUID | - |
| operateTime | LocalDateTime | - |
| remark | String | TEXT |

```java
package com.colonel.saas.entity.sample;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.UUID;

/**
 * 寄样状态变更日志（追加型，仅记录）
 * - 不继承 BaseEntity（无 update/delete 语义）
 * - 有 deleted 字段（数据库实际存在，支持逻辑删除）
 * - 无 create_by/update_by（操作人记录在 operator_id 中）
 */
@Data
@TableName("sample_status_log")
public class SampleStatusLog {

    @TableId(type = IdType.AUTO)
    private UUID id;

    @TableField("request_id")
    private UUID requestId;

    @TableField("from_status")
    private Integer fromStatus;

    @TableField("to_status")
    private Integer toStatus;

    @TableField("operator_id")
    private UUID operatorId;

    @TableField("operate_time")
    private LocalDateTime operateTime;

    /** TEXT */
    private String remark;

    @TableLogic
    private Integer deleted = 0;
}
```

---

### 5.11 订单模块 (`com.colonel.saas.entity.order`)

#### ColonelSettlementOrder（分区表）

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| orderId | String | 索引 |
| productId | String | - |
| productName | String | - |
| shopId | Long | - |
| shopName | String | - |
| orderAmount | Long | 分 |
| actualAmount | Long | 分 |
| colonelBuyinId | Long | - |
| colonelActivityId | String | - |
| settleColonelCommission | Long | 分 |
| settleColonelTechServiceFee | Long | 分 |
| secondColonelBuyinId | Long | - |
| secondColonelActivityId | String | - |
| settleSecondColonelCommission | Long | 分 |
| phaseId | String | - |
| orderStatus | Integer | - |
| orderType | Integer | - |
| settleTime | LocalDateTime | - |
| cursor | String | - |
| pickSource | String | - |
| channelUserId | UUID | - |
| channelDeptId | UUID | - |
| userId | UUID | - |
| deptId | UUID | - |
| createTime | LocalDateTime | 分区键，**必须插入时赋值** |
| extraData | `Map<String, Object>` | JSONB |

```java
package com.colonel.saas.entity.order;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("colonelsettlement_order")
public class ColonelSettlementOrder extends BaseEntity {

    @TableField("order_id")
    private String orderId;

    @TableField("product_id")
    private String productId;

    @TableField("product_name")
    private String productName;

    @TableField("shop_id")
    private Long shopId;

    @TableField("shop_name")
    private String shopName;

    @TableField("order_amount")
    private Long orderAmount;

    @TableField("actual_amount")
    private Long actualAmount;

    @TableField("colonel_buyin_id")
    private Long colonelBuyinId;

    @TableField("colonel_activity_id")
    private String colonelActivityId;

    @TableField("settle_colonel_commission")
    private Long settleColonelCommission;

    @TableField("settle_colonel_tech_service_fee")
    private Long settleColonelTechServiceFee;

    @TableField("second_colonel_buyin_id")
    private Long secondColonelBuyinId;

    @TableField("second_colonel_activity_id")
    private String secondColonelActivityId;

    @TableField("settle_second_colonel_commission")
    private Long settleSecondColonelCommission;

    @TableField("phase_id")
    private String phaseId;

    @TableField("order_status")
    private Integer orderStatus;

    @TableField("order_type")
    private Integer orderType;

    @TableField("settle_time")
    private LocalDateTime settleTime;

    private String cursor;

    @TableField("pick_source")
    private String pickSource;

    @TableField("channel_user_id")
    private UUID channelUserId;

    @TableField("channel_dept_id")
    private UUID channelDeptId;

    @TableField("user_id")
    private UUID userId;

    @TableField("dept_id")
    private UUID deptId;

    /**
     * 分区键，必须在 INSERT 时赋值
     * MyBatis-Plus 默认会自动填充 @TableField(fill = INSERT)
     * 但需确保 Mapper.insert() 调用时此字段非 null
     */
    @Override
    @TableField(fill = FieldFill.INSERT)
    public void setCreateTime(LocalDateTime createTime) {
        super.setCreateTime(createTime);
    }

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraData;
}
```

> **分区表注意事项**：分区键 `create_time` 必须是复合主键 `(id, create_time)` 的一部分。`BaseEntity` 的 `id` 已在父类定义，复合主键由 MyBatis-Plus 全局配置或 `@TableId` 注解协同实现。插入时 `createTime` 必须有值，否则 PostgreSQL 报错。

#### OrderDetail

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| orderId | String | UNIQUE |
| address | String | TEXT |
| province | String | - |
| city | String | - |
| district | String | - |
| recipientName | String | - |
| phoneCipher | String | TEXT |
| phonePlain | String | - |
| isVirtualTel | Integer | `0=否, 1=是` |
| phoneNoA | String | `VARCHAR(20)` |
| phoneNoB | String | `VARCHAR(20)` |
| expireTime | Long | BIGINT（Unix 秒） |
| idCardCipher | String | TEXT |
| idCardPlain | String | - |
| decryptStatus | Integer | `0=未解密, 1=成功, 2=失败` |
| decryptMsg | String | `VARCHAR(500)` |
| decryptTime | LocalDateTime | - |
| extraData | `Map<String, Object>` | JSONB |

```java
package com.colonel.saas.entity.order;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_detail")
public class OrderDetail extends BaseEntity {

    @TableField("order_id")
    private String orderId;

    private String address;
    private String province;
    private String city;
    private String district;

    @TableField("recipient_name")
    private String recipientName;

    @TableField("phone_cipher")
    private String phoneCipher;

    @TableField("phone_plain")
    private String phonePlain;

    @TableField("is_virtual_tel")
    private Integer isVirtualTel;

    @TableField("phone_no_a")
    private String phoneNoA;

    @TableField("phone_no_b")
    private String phoneNoB;

    @TableField("expire_time")
    private Long expireTime;

    @TableField("id_card_cipher")
    private String idCardCipher;

    @TableField("id_card_plain")
    private String idCardPlain;

    /** 0=未解密, 1=成功, 2=失败 */
    @TableField("decrypt_status")
    private Integer decryptStatus;

    @TableField("decrypt_msg")
    private String decryptMsg;

    @TableField("decrypt_time")
    private LocalDateTime decryptTime;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraData;
}
```

#### OrderDecryptRecord（含 deleted 字段）

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| orderId | String | - |
| dataType | Integer | `1=地址, 2=姓名, 3=手机号, 4=身份证` |
| cipherText | String | TEXT |
| decryptText | String | TEXT |
| isVirtualTel | Integer | - |
| phoneNoA | String | - |
| phoneNoB | String | - |
| expireTime | LocalDateTime | - |
| decryptStatus | Integer | `0=未解密, 1=成功, 2=失败` |
| decryptMsg | String | - |
| extraData | `Map<String, Object>` | JSONB |

```java
package com.colonel.saas.entity.order;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;

/**
 * 订单解密记录（追加型）
 * - 不继承 BaseEntity
 * - 有 deleted 字段（数据库实际存在，支持逻辑删除）
 */
@Data
@TableName("order_decrypt_record")
public class OrderDecryptRecord {

    @TableId(type = IdType.AUTO)
    private UUID id;

    @TableField("order_id")
    private String orderId;

    /** 1=地址, 2=姓名, 3=手机号, 4=身份证 */
    @TableField("data_type")
    private Integer dataType;

    @TableField("cipher_text")
    private String cipherText;

    @TableField("decrypt_text")
    private String decryptText;

    @TableField("is_virtual_tel")
    private Integer isVirtualTel;

    @TableField("phone_no_a")
    private String phoneNoA;

    @TableField("phone_no_b")
    private String phoneNoB;

    @TableField("expire_time")
    private LocalDateTime expireTime;

    /** 0=未解密, 1=成功, 2=失败 */
    @TableField("decrypt_status")
    private Integer decryptStatus;

    @TableField("decrypt_msg")
    private String decryptMsg;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableField("create_by")
    private UUID createBy;

    @TableField("update_by")
    private UUID updateBy;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraData;

    @TableLogic
    private Integer deleted = 0;
}
```

---

### 5.12 结算模块 (`com.colonel.saas.entity.commission`)

#### CommissionSettlement

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| settleMonth | String | `YYYY-MM`，与 user_id 组合 UNIQUE |
| userId | String | FK → sys_user |
| deptId | String | - |
| orderCount | Integer | - |
| totalOrderAmount | Long | 分 |
| commissionAmount | Long | 分（招商提成） |
| techServiceFee | Long | 分（技术服务费） |
| netCommission | Long | 分（净提成 = commission - tech） |
| status | Integer | `0=待确认, 1=已确认, 2=已结算` |
| confirmTime | LocalDateTime | - |
| settleTime | LocalDateTime | - |
| remark | String | TEXT |
| extraData | `Map<String, Object>` | JSONB |

```java
package com.colonel.saas.entity.commission;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("commission_settlement")
public class CommissionSettlement extends BaseEntity {

    @TableField("settle_month")
    private String settleMonth;

    @TableField("user_id")
    private UUID userId;

    @TableField("dept_id")
    private UUID deptId;

    @TableField("order_count")
    private Integer orderCount;

    @TableField("total_order_amount")
    private Long totalOrderAmount;

    @TableField("commission_amount")
    private Long commissionAmount;

    @TableField("tech_service_fee")
    private Long techServiceFee;

    @TableField("net_commission")
    private Long netCommission;

    /** 0=待确认, 1=已确认, 2=已结算 */
    private Integer status;

    @TableField("confirm_time")
    private LocalDateTime confirmTime;

    @TableField("settle_time")
    private LocalDateTime settleTime;

    /** TEXT */
    private String remark;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraData;
}
```

#### CommissionConfig

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| userId | String | nullable，null=全局配置 |
| commissionType | Integer | `1=招商提成, 2=渠道提成` |
| ratio | BigDecimal | `NUMERIC(5,4)` |
| scope | String | `global / activity / product` |
| scopeId | String | scope 非 global 时必填 |
| validFrom | LocalDateTime | - |
| validUntil | LocalDateTime | - |
| status | Integer | - |
| remark | String | TEXT |

```java
package com.colonel.saas.entity.commission;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("commission_config")
public class CommissionConfig extends BaseEntity {

    /** null = 全局配置 */
    @TableField("user_id")
    private UUID userId;

    /** 1=招商提成, 2=渠道提成 */
    @TableField("commission_type")
    private Integer commissionType;

    private BigDecimal ratio;

    /** global / activity / product */
    private String scope;

    /** scope 非 global 时必填 */
    @TableField("scope_id")
    private String scopeId;

    @TableField("valid_from")
    private LocalDateTime validFrom;

    @TableField("valid_until")
    private LocalDateTime validUntil;

    private Integer status;

    /** TEXT */
    private String remark;
}
```

---

### 5.13 系统配置 (`com.colonel.saas.entity.config`)

#### SystemConfig

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| configKey | String | UNIQUE |
| configValue | String | TEXT |
| configType | String | `numeric / int / boolean / json / varchar` |
| configGroup | String | `talent / douyin / sample / commission` |
| configName | String | - |
| sortOrder | Integer | - |
| status | Integer | - |
| remark | String | `VARCHAR(500)` |

```java
package com.colonel.saas.entity.config;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_config")
public class SystemConfig extends BaseEntity {

    @TableField("config_key")
    private String configKey;

    @TableField("config_value")
    private String configValue;

    @TableField("config_type")
    private String configType;

    @TableField("config_group")
    private String configGroup;

    @TableField("config_name")
    private String configName;

    @TableField("sort_order")
    private Integer sortOrder;

    private Integer status;

    @TableField("remark")
    private String remark;
}
```

---

### 5.14 日志模块 (`com.colonel.saas.entity.log`)

#### OperationLog（分区表）

| Java 字段 | 类型 | 注解 |
|-----------|------|------|
| userId | String | - |
| username | String | - |
| module | String | - |
| action | String | - |
| targetType | String | - |
| targetId | String | - |
| targetName | String | - |
| content | String | TEXT |
| requestMethod | String | - |
| requestUrl | String | - |
| requestParams | `Map<String, Object>` | JSONB |
| requestBody | `Map<String, Object>` | JSONB |
| responseCode | String | - |
| responseBody | `Map<String, Object>` | JSONB |
| ipAddress | String | - |
| userAgent | String | `VARCHAR(500)` |
| durationMs | Long | BIGINT |
| errorMessage | String | TEXT |

```java
package com.colonel.saas.entity.log;

import com.colonel.saas.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.UUID;

/**
 * 操作日志（分区表，按月分区）
 * create_time 为分区键，必须在 INSERT 时赋值
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("operation_log")
public class OperationLog extends BaseEntity {

    @TableField("user_id")
    private UUID userId;

    private String username;
    private String module;
    private String action;

    @TableField("target_type")
    private String targetType;

    @TableField("target_id")
    private String targetId;

    @TableField("target_name")
    private String targetName;

    private String content;

    @TableField("request_method")
    private String requestMethod;

    @TableField("request_url")
    private String requestUrl;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> requestParams;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> requestBody;

    @TableField("response_code")
    private String responseCode;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> responseBody;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("user_agent")
    private String userAgent;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("error_message")
    private String errorMessage;

    /**
     * 分区键，必须在 INSERT 时赋值
     */
    @Override
    @TableField(fill = FieldFill.INSERT)
    public void setCreateTime(java.time.LocalDateTime createTime) {
        super.setCreateTime(createTime);
    }
}
```

---

## 六、MyBatis-Plus 全局配置

在 `application.yml` 中添加：

```yaml
mybatis-plus:
  global-config:
    db-config:
      id-type: auto                 # UUID 主键由 PostgreSQL gen_random_uuid() 生成，实体类用 @TableId(type = IdType.AUTO)
      logic-delete-field: deleted   # 全局逻辑删除字段
      logic-delete-value: 1        # 删除值
      logic-not-delete-value: 0     # 未删除值
  configuration:
    map-underscore-to-camel-case: true  # 开启驼峰映射
  type-handlers-package: com.baomidou.mybatisplus.extension.handlers
```

---

## 七、包结构汇总

```
com.colonel.saas
├── common
│   └── base
│       └── BaseEntity.java            # 统一基类（com.colonel.saas.common.base）
└── entity
    ├── system
    │   ├── SysUser.java
    │   ├── SysRole.java
    │   └── SysUserRole.java
├── douyin
│   └── DouyinToken.java
├── activity
│   ├── ColonelActivity.java
│   └── ColonelActivityProduct.java
├── product
│   └── Product.java
├── talent
│   ├── Talent.java
│   ├── TalentClaim.java
│   └── ExclusiveTalent.java
├── merchant
│   ├── Merchant.java
│   └── ExclusiveMerchant.java
├── picksource
│   └── PickSourceMapping.java
├── sample
│   ├── SampleRequest.java
│   └── SampleStatusLog.java           # 含 deleted 字段
├── order
│   ├── ColonelSettlementOrder.java    # 分区表
│   ├── OrderDetail.java
│   └── OrderDecryptRecord.java        # 含 deleted 字段
├── commission
│   ├── CommissionSettlement.java
│   └── CommissionConfig.java
├── config
│   └── SystemConfig.java
└── log
    └── OperationLog.java              # 分区表
```

---

## 八、Cursor 执行要求

1. **创建所有实体类文件**，路径按第七节包结构
2. **BaseEntity** 已在 `com.colonel.saas.common.base` 包下（TASK-02 已创建，无需重复创建）
3. **pom.xml** 确认已有 `mybatis-plus-spring-boot3-starter` 和 `jackson` 依赖
4. **application.yml** 已配置 MyBatis-Plus 全局配置（TASK-02 已完成，确认 `id-type: auto`）
5. **CustomMetaObjectHandler** 已在 `com.colonel.saas.config` 包下（TASK-02 已创建，需更新 createBy/updateBy 类型为 UUID）
6. 所有实体类添加 `@Data`、`@EqualsAndHashCode(callSuper = true)`（继承 BaseEntity 的类）注解
7. 不需要创建 Mapper 接口（MyBatis-Plus 提供通用 Mapper）
8. 完成后生成**测试脚本**验证：按字段名匹配数据库表，确认无遗漏字段

---

## 九、架构方案交叉检查记录

**检查时间**：2026-04-20
**检查依据**：[技术架构方案.md](./技术架构方案.md)

### 检查结论

| 检查项 | 结论 | 说明 |
|--------|------|------|
| §2.2 模块职责 | ✅ 全部匹配 | 8大模块（系统/抖音/活动/商品/达人/寄样/订单/权限）完整对应 |
| §7.7 仓库结构 | ✅ 匹配 | `com.colonel.saas.entity.*` 包结构符合规范 |
| §1 技术栈 | ✅ 匹配 | MyBatis-Plus + JacksonTypeHandler + UUID + MetaObjectHandler |
| BaseEntity 路径 | ✅ 已修复 | 原错误：`com.colonel.saas.entity.BaseEntity` → 正确：`com.colonel.saas.common.base.BaseEntity` |

### 已修复问题

1. **BaseEntity 导入路径错误（19处）**
   - 错误：`import com.colonel.saas.entity.BaseEntity;`
   - 正确：`import com.colonel.saas.common.base.BaseEntity;`
   - 影响范围：全部继承 BaseEntity 的实体类（20个类）
   - 修复方式：全局替换所有错误导入

2. **OrderDecryptRecord 缺失 deleted 字段（B10）**
   - 添加：`@TableLogic private Integer deleted = 0;`
   - Section VII 包结构注释：`# 追加型` → `# 含 deleted 字段`
   - Section title：`（追加型，不继承 BaseEntity）` → `（含 deleted 字段）`

3. **OperationLog userId 字段类型错误（B13）**
   - 错误：`private String userId;`
   - 正确：`private UUID userId;`
   - 添加：`import java.util.UUID;` 到 OperationLog imports

4. **yml id-type 配置错误（C1）**
   - 错误：`id-type: assign_uuid`
   - 正确：`id-type: auto`，注释说明 UUID 由 PostgreSQL gen_random_uuid() 生成

5. **Section VII BaseEntity 包路径错误（C2）**
   - 错误：`com.colonel.saas.entity.BaseEntity`
   - 正确：`com.colonel.saas.common.base.BaseEntity`
   - 包结构改为：`common/base/BaseEntity.java` 在 `com.colonel.saas.common.base` 包下

### 架构备注

- **MyBatis-Plus 版本**：文档基于 3.5.6，实际版本以 pom.xml 为准
- **talent_crawl_log 表**：技术架构方案 §2.2 爬虫模块提及，当前设计未见此表（属于爬虫模块 TASK-07 范围，可在后续补充）
- **OrderDecryptRecord**：含 `deleted`、`create_by`、`update_by`（UUID 类型）
- **分区表分区键**：`colonelsettlement_order` 和 `operation_log` 的 `create_time` 已在各实体中正确声明
