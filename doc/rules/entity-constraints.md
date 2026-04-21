# 瑙勫垯锛氬疄浣撶被绾︽潫

**鐗堟湰**锛歏1.0
**鐘舵€?*锛氬己鍒舵墽琛?**閫傜敤鑼冨洿**锛氭墍鏈?Java 瀹炰綋绫?
---

## 涓€銆佷富閿害鏉?
### 1.1 UUID 涓婚敭锛堜笟鍔¤〃锛?
```java
// 鉁?姝ｇ‘锛氫娇鐢?UUID 绫诲瀷涓婚敭
@Data
@TableName("product")
public class Product extends BaseEntity {
    // id 缁ф壙鑷?BaseEntity锛屽凡閰嶇疆 UUID 涓婚敭
}

// 鉂?閿欒锛氫娇鐢?Long 涓婚敭
private Long id; // 绂佹锛佷笟鍔¤〃搴斾娇鐢?UUID
```

### 1.2 澶嶅悎涓婚敭锛堝垎鍖鸿〃锛?
```java
// colonelsettlement_order 鍜?operation_log 浣跨敤澶嶅悎涓婚敭
@TableName("colonelsettlement_order")
@EqualsAndHashCode(callSuper = true)
public class Order extends BaseEntity {
    // 鍒嗗尯琛ㄥ鍚堜富閿湪 DDL 涓畾涔夛細PRIMARY KEY (id, create_time)
}
```

---

## 浜屻€佸瓧娈电被鍨嬬害鏉?
### 2.1 UUID 澶栭敭

```java
// 鉁?姝ｇ‘锛氫娇鐢?UUID 绫诲瀷
private UUID userId;
private UUID deptId;
private UUID talentId;
private UUID channelId;

// 鉂?閿欒锛氫娇鐢?String 绫诲瀷
private String userId; // 绂佹锛?```

### 2.2 閲戦瀛楁

```java
// 鉁?姝ｇ‘锛氫娇鐢?Long锛堝崟浣嶏細鍒嗭級
private Long commissionFee;    // 鏈嶅姟璐癸紙鍒嗭級
private Long productPrice;    // 鍟嗗搧浠锋牸锛堝垎锛?private Long settlementAmount; // 缁撶畻閲戦锛堝垎锛?
// 鉂?閿欒锛氫娇鐢?Double
private Double commissionFee; // 绂佹锛佺簿搴﹂棶棰?```

### 2.3 姣斾緥瀛楁

```java
// 鉁?姝ｇ‘锛氫娇鐢?BigDecimal
private BigDecimal commissionRatio; // 鎻愭垚姣斾緥锛?.15 琛ㄧず 15%锛?private BigDecimal discountRate;    // 鎶樻墸鐜?
// 鉂?閿欒锛氫娇鐢?Double 瀛樺偍姣斾緥
private Double ratio; // 绂佹锛?```

---

## 涓夈€丣SONB 鍒楃害鏉?
### 3.1 JSONB 瀛楁澹版槑

```java
// 鉁?姝ｇ‘锛氫娇鐢?Map<String, Object> + JacksonTypeHandler
@TableField(typeHandler = JacksonTypeHandler.class)
private Map<String, Object> extraData;

@TableField(typeHandler = JacksonTypeHandler.class)
private Map<String, Object> permissions;
```

### 3.2 JSONB 澶у皬闄愬埗

- [ ] **绂佹**锛氬瓨鍌ㄨ秴杩?1MB 鐨?JSONB 鏁版嵁
- [ ] 寤鸿鍦ㄥ疄浣撳眰娣诲姞澶у皬鏍￠獙

```java
@PrePersist
public void validateJsonbSize() {
    if (extraData != null) {
        String json = JsonUtils.toJson(extraData);
        if (json.getBytes().length > 1024 * 1024) {
            throw new BusinessException("JSONB 瀛楁瓒呰繃 1MB 闄愬埗");
        }
    }
}
```

---

## 鍥涖€佸璁″瓧娈电害鏉?
### 4.1 蹇呴』缁ф壙 BaseEntity

```java
// 鉁?姝ｇ‘锛氱户鎵?BaseEntity
@Data
@TableName("product")
public class Product extends BaseEntity {
    // 鑷姩鑾峰緱锛歩d, createTime, updateTime, createBy, updateBy, deleted
}

// 鉂?閿欒锛氫笉缁ф壙
@Data
@TableName("product")
public class WrongProduct {
    // 绂佹锛佺己灏戝璁″瓧娈?}
```

### 4.2 鏃ュ織琛ㄩ櫎澶?
```java
// 杩藉姞鍨嬫棩蹇楄〃涓嶇户鎵?BaseEntity锛屼粎鍚?deleted
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

## 浜斻€佺姝㈠仛娉曟眹鎬?
| 搴忓彿 | 绂佹 | 姝ｇ‘鍋氭硶 |
|------|------|----------|
| 1 | String 瀛樺偍 UUID | 浣跨敤 UUID 绫诲瀷 |
| 2 | Double 瀛樺偍閲戦 | 浣跨敤 Long锛堝垎锛?|
| 3 | Double 瀛樺偍姣斾緥 | 浣跨敤 BigDecimal |
| 4 | 涓氬姟琛ㄤ娇鐢?Long 涓婚敭 | 缁ф壙 BaseEntity锛圲UID锛?|
| 5 | JSONB 瓒呰繃 1MB | 鏍￠獙澶у皬锛岃秴杩囧垯鎷掔粷 |
| 6 | 涓嶇户鎵?BaseEntity | 涓氬姟琛ㄥ繀椤荤户鎵?|

---

## 鍏€侀獙鏀舵祴璇?
```java
@Test
void shouldRejectStringUuidInEntity() {
    // 姝よ鍒欑敱浠ｇ爜瀹℃煡寮哄埗鎵ц锛屾棤鑷姩娴嬭瘯
    // 瀹℃煡鏃跺簲妫€鏌ュ疄浣撶被瀛楁绫诲瀷
}

@Test
void shouldRejectDoubleForMoney() {
    // 姝よ鍒欑敱浠ｇ爜瀹℃煡寮哄埗鎵ц
    // 瀹℃煡鏃跺簲妫€鏌ラ噾棰濆瓧娈电被鍨?}
```

---

## 涓冦€佺浉鍏虫枃浠剁储寮?
| 鏂囦欢 | 璇存槑 |
|------|------|
| `BaseEntity.java` | 缁熶竴鍩虹被 |
| `CustomMetaObjectHandler.java` | 鑷姩濉厖澶勭悊鍣?|
| `Product.java` | 鍟嗗搧瀹炰綋锛堢ず渚嬶級 |
| `Order.java` | 璁㈠崟瀹炰綋锛堝垎鍖鸿〃绀轰緥锛?|
| 闇€姹傚叆鍙?| `doc/requirements/02-data-schema.md` |
