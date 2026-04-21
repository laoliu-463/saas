# 闇€姹傦細鏁版嵁搴撹璁¤鑼?
**鏂囨。鐗堟湰**锛歏1.0
**鐘舵€?*锛氬凡瀹氱
**鏅鸿兘浣撳叆鍙?*锛氱洿鎺ヨ鍙栨鏂囦欢

---

## 涓€銆丳ostgreSQL 鈫?Java 绫诲瀷鏄犲皠

| PostgreSQL 绫诲瀷 | Java 绫诲瀷 | MyBatis-Plus 娉ㄨВ | 璇存槑 |
|-----------------|-----------|-------------------|------|
| `UUID` (涓婚敭) | `java.util.UUID` | `@TableId(type = IdType.AUTO)` | DB: `gen_random_uuid()` |
| `UUID` (澶栭敭) | `java.util.UUID` | `@TableField("xxx_id")` | user_id, dept_id 绛?|
| `TIMESTAMP` | `LocalDateTime` | 鑷姩鏄犲皠 | - |
| `DATE` | `LocalDate` | 鑷姩鏄犲皠 | 浠呮棩鏈熼儴鍒?|
| `BIGINT` | `Long` | 鑷姩鏄犲皠 | 閲戦锛堝垎锛夈€佹暟閲?|
| `INTEGER / INT` | `Integer` | 鑷姩鏄犲皠 | 鐘舵€佺爜銆佽鏁?|
| `SMALLINT` | `Integer` | 鑷姩鏄犲皠 | 甯冨皵鏍囪 |
| `BOOLEAN` | `Boolean` | 鑷姩鏄犲皠 | - |
| `NUMERIC(p,s)` | `BigDecimal` | 鑷姩鏄犲皠 | 姣斾緥銆侀噾棰?|
| `VARCHAR(n)` | `String` | 鑷姩鏄犲皠 | - |
| `TEXT` | `String` | 鑷姩鏄犲皠 | 闀挎枃鏈€佸瘑鏂?|
| `JSONB` | `Map<String, Object>` | `@TableField(typeHandler = JacksonTypeHandler.class)` | 閰嶇疆鏁版嵁 |
| `BYTEA` | `byte[]` | 鑷姩鏄犲皠 | 鏂囦欢浜岃繘鍒?|

---

## 浜屻€佷富閿瓥鐣?
### 2.1 UUID 涓婚敭锛堟帹鑽愮敤浜庢墍鏈変笟鍔¤〃锛?
```java
// Java 瀹炰綋绫?@TableId(type = IdType.AUTO)
private UUID id;

// 鏁版嵁搴?DDL
id UUID DEFAULT gen_random_uuid() PRIMARY KEY
```

### 2.2 澶嶅悎涓婚敭锛堝垎鍖鸿〃涓撶敤锛?
```sql
-- colonelsettlement_order 鍜?operation_log 浣跨敤澶嶅悎涓婚敭
PRIMARY KEY (id, create_time)
```

> **璀﹀憡**锛氬垎鍖洪敭 `create_time` 蹇呴』鍦?INSERT 鏃惰祴鍊硷紝鍚﹀垯 PostgreSQL 鎶ラ敊銆?
---

## 涓夈€丅aseEntity 缁熶竴鍩虹被

璺緞锛歚com.colonel.saas.common.base.BaseEntity`

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

## 鍥涖€佸垎鍖鸿〃瑙勮寖

### 4.1 鎸夋湀鍒嗗尯琛?
| 琛ㄥ悕 | 鍒嗗尯閿?| 鍒嗗尯绛栫暐 | 璇存槑 |
|------|--------|----------|------|
| `colonelsettlement_order` | `create_time` | RANGE (Monthly) | 璁㈠崟鏁版嵁锛岄噺澶?|
| `operation_log` | `create_time` | RANGE (Monthly) | 鎿嶄綔鏃ュ織 |

### 4.2 鍒嗗尯琛ㄧ害鏉?
- [ ] **蹇呴』**锛欼NSERT 鏃?`create_time` 蹇呴』鏈夊€硷紙闈?NULL锛?- [ ] **蹇呴』**锛氭煡璇㈡椂甯︿笂鏃堕棿鑼冨洿锛坄WHERE create_time BETWEEN ? AND ?`锛?- [ ] **绂佹**锛氬鍒嗗尯琛ㄨ繘琛屽叏琛ㄦ壂鎻?- [ ] **蹇呴』**锛氭寜鏈堝垱寤哄垎鍖猴紝鎻愬墠鍒涘缓涓嬪搴﹀垎鍖?
---

## 浜斻€丣SONB 鍒楀鐞?
```java
// Java 绔娇鐢?Map<String, Object>
@TableField(typeHandler = JacksonTypeHandler.class)
private Map<String, Object> permissions;

@TableField(typeHandler = JacksonTypeHandler.class)
private Map<String, Object> extraData;

// 瀛樺偍缁撴瀯绀轰緥
Map<String, Object> permissions = Map.of(
    "menus", List.of("product:list", "order:view"),
    "dataScope", 1,
    "apis", List.of("GET /api/products", "POST /api/orders")
);
```

---

## 鍏€佸璁″瓧娈佃鑼?
### 6.1 瀹屾暣瀹¤锛堢户鎵?BaseEntity锛?
閫傜敤浜庯細鎵€鏈変笟鍔″疄浣?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| create_time | TIMESTAMP | 鍒涘缓鏃堕棿 |
| update_time | TIMESTAMP | 鏇存柊鏃堕棿 |
| create_by | UUID | 鍒涘缓浜?|
| update_by | UUID | 鏇存柊浜?|
| deleted | INTEGER | 閫昏緫鍒犻櫎鏍囪 |

### 6.2 杩藉姞鍨嬫棩蹇楋紙浠呭惈 deleted锛?
閫傜敤浜庯細`sample_status_log`, `order_decrypt_record`

```java
// 涓嶇户鎵?BaseEntity锛屼粎鍚?deleted
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

## 涓冦€佺储寮曡鑼?
### 7.1 蹇呴』鍒涘缓鐨勭储寮?
| 琛ㄥ悕 | 绱㈠紩瀛楁 | 绱㈠紩绫诲瀷 | 璇存槑 |
|------|----------|----------|------|
| `sys_user` | `username` | UNIQUE | 鐧诲綍璐﹀彿鍞竴 |
| `sys_user` | `dept_id` | INDEX | 鎸夐儴闂ㄦ煡璇?|
| `colonel_activity` | `activity_id` | UNIQUE | 娲诲姩 ID 鍞竴 |
| `colonel_activity` | `shop_id` | INDEX | 鎸夊簵閾烘煡璇?|
| `product` | `product_id` | UNIQUE | 鍟嗗搧 ID 鍞竴 |
| `talent` | `douyin_uid` | UNIQUE | 鎶栭煶 UID 鍞竴 |
| `talent_claim` | `talent_id, user_id` | UNIQUE | 闃查噸澶嶈棰?|
| `pick_source_mapping` | `pick_source` | UNIQUE | 褰掑洜鍙傛暟鍞竴 |
| `pick_source_mapping` | `short_id` | UNIQUE | 鐭?ID 鍞竴 |
| `pick_source_mapping` | `uuid_seed` | INDEX | ShortID 鍙嶆煡杩樺師 |
| `sample_request` | `request_no` | UNIQUE | 鐢宠缂栧彿鍞竴 |
| `sample_request` | `talent_id, product_id, status` | INDEX | 闃查噸澶嶇敵璇?|
| `colonelsettlement_order` | `create_time` | 鍒嗗尯閿?| 鍒嗗尯琛ㄥ繀闇€ |
| `colonelsettlement_order` | `order_id` | INDEX | 鎸夎鍗曞彿鏌ヨ |
| `operation_log` | `create_time` | 鍒嗗尯閿?| 鍒嗗尯琛ㄥ繀闇€ |
| `sample_status_log` | `create_time` | INDEX | 鎸夋椂闂存煡璇?|
| `sample_status_log` | `sample_request_id` | INDEX | 鎸夌敵璇峰崟鏌ヨ |

> **琛ㄥ叧绯昏鏄?*锛歚commission_config` 瀛樺偍鎻愭垚閰嶇疆瑙勫垯锛堟瘮渚嬶級锛宍commission_settlement` 瀛樺偍缁撶畻缁撴灉锛堣绠楀悗鐨勯噾棰濓級锛屼袱鑰呴€氳繃 `order_id` 鍏宠仈銆?
---

## 鍏€佸紑鍙戠害鏉?
### 8.1 蹇呴』閬靛畧

- [ ] 鏂板瀹炰綋绫诲繀椤荤户鎵?`BaseEntity`锛堟棩蹇楄〃闄ゅ锛?- [ ] UUID 澶栭敭瀛楁蹇呴』浣跨敤 `UUID` 绫诲瀷锛岀姝?`String`
- [ ] 閲戦瀛楁浣跨敤 `Long`锛堝崟浣嶏細鍒嗭級锛岀姝?`Double`
- [ ] 鍒嗗尯琛ㄦ彃鍏ユ椂蹇呴』璁剧疆 `createTime`
- [ ] 鏌ヨ鍒嗗尯琛ㄥ繀椤诲甫鏃堕棿鑼冨洿鏉′欢

### 8.2 绂佹鍋氭硶

- [ ] 绂佹浣跨敤 `String` 绫诲瀷瀛樺偍 UUID
- [ ] 绂佹浣跨敤 `Double` 瀛樺偍閲戦锛堢簿搴﹂棶棰橈級
- [ ] 绂佹瀵瑰垎鍖鸿〃鎵ц鏃犳椂闂存潯浠剁殑鍏ㄨ〃鎵弿
- [ ] 绂佹鍦?JSONB 瀛楁瀛樺偍瓒呰繃 1MB 鐨勬暟鎹?
---

## 涔濄€侀獙鏀舵爣鍑?
1. **缂栬瘧閫氳繃**锛氭墍鏈夊疄浣撶被姝ｇ‘缁ф壙 BaseEntity
2. **鍗曞厓娴嬭瘯**锛歎UID 涓婚敭鐢熸垚銆丣SONB 搴忓垪鍖?鍙嶅簭鍒楀寲姝ｅ父
3. **鍒嗗尯琛ㄦ祴璇?*锛氭彃鍏ユ暟鎹椂 `create_time` 涓虹┖搴旀姏鍑烘槑纭紓甯?4. **绱㈠紩妫€鏌?*锛氭暟鎹簱杩佺Щ鑴氭湰鍖呭惈鎵€鏈夊繀闇€绱㈠紩

---

## 鍗併€佺浉鍏虫枃浠剁储寮?
| 鏂囦欢 | 璺緞 | 璇存槑 |
|------|------|------|
| BaseEntity | `backend/src/main/java/com/colonel/saas/common/base/BaseEntity.java` | 缁熶竴鍩虹被 |
| MetaObjectHandler | `backend/src/main/java/com/colonel/saas/config/CustomMetaObjectHandler.java` | 鑷姩濉厖 |
| 鏁版嵁搴?DDL | `backend/src/main/resources/db/init-db.sql` | 寤鸿〃鑴氭湰 |
| Lint 瑙勫垯 | `doc/rules/entity-constraints.md` | 瀹炰綋绫荤害鏉?|

---

## 鍗佷竴銆佸畬鏁?DDL 鍙傝€?
> 瀹屾暣 SQL 鑴氭湰浣嶄簬 `backend/src/main/resources/db/init-db.sql`锛屼互涓嬩负鏉冨▉鐗堟湰鎽樿銆?> 骞傜瓑璁捐锛歚CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` / `INSERT ... ON CONFLICT DO NOTHING`

### 11.1 琛ㄦ竻鍗曪紙22寮狅級

| 搴忓彿 | 琛ㄥ悕 | 绫诲瀷 | 璇存槑 |
|------|------|------|------|
| 1 | `sys_user` | 鏅€?| 鐢ㄦ埛锛屽惈 `channel_code`锛坧ick_extra 閫忎紶锛?|
| 2 | `sys_role` | 鏅€?| 瑙掕壊锛屽惈 `permissions JSONB`銆乣menu_config JSONB` |
| 3 | `sys_user_role` | 鏅€?| 鐢ㄦ埛-瑙掕壊鍏宠仈 |
| 4 | `douyin_token` | 鏅€?| 鎶栭煶 API Token锛屽惈 `extra_data JSONB` |
| 5 | `colonel_activity` | 鏅€?| 鍥㈤暱娲诲姩锛屽惈 `colonel_buyin_id`銆乣commission_rate`銆乣service_rate` |
| 6 | `product` | 鏅€?| 鍟嗗搧锛屽惈 `cos_ratio`銆乣cos_fee`銆乣service_ratio`锛圴1.3锛?|
| 7 | `colonel_activity_product` | 鏅€?| 娲诲姩鍟嗗搧鍏宠仈锛屽惈 `assignee_id`銆乣min_refer_amount`锛圴1.3锛?|
| 8 | `talent` | 鏅€?| 杈句汉锛屽惈 `crawl_status`銆乣crawl_message`锛圴1.2锛?|
| 9 | `talent_claim` | 鏅€?| 杈句汉璁ら锛屽惈 `expire_time`锛堜繚鎶ゆ湡锛?|
| 10 | `exclusive_talent` | 鏅€?| 鐙杈句汉锛屽惈 `trigger_type`銆乣audit_user_id` |
| 11 | `exclusive_merchant` | 鏅€?| 鐙鍟嗗 |
| 12 | `merchant` | 鏅€?| 鍟嗗锛屽惈 `status`锛圴1.3锛?|
| 13 | `pick_source_mapping` | 鏅€?| 褰掑洜鏄犲皠锛屽惈 `short_id`锛堟柟妗圔锛夈€乣uuid_seed`锛圴1.3锛?|
| 14 | `sample_request` | 鏅€?| 瀵勬牱鐢宠锛屽惈鏀朵欢浜轰俊鎭紙V1.2锛?|
| 15 | `sample_status_log` | 鏅€?| 鐘舵€佸彉鏇存棩蹇?|
| 16 | `colonelsettlement_order` | **鍒嗗尯** | 璁㈠崟锛堟寜鏈?RANGE锛宍create_time`锛?|
| 17 | `commission_settlement` | 鏅€?| 鍒嗕剑缁撶畻 |
| 18 | `commission_config` | 鏅€?| 鎻愭垚閰嶇疆锛堝惈鍏ㄥ眬/涓汉/娲诲姩/鍟嗗搧绾у埆锛?|
| 19 | `order_detail` | 鏅€?| 璁㈠崟瑙ｅ瘑璇︽儏锛堝惈铏氭嫙鍙峰鐞嗭級 |
| 20 | `order_decrypt_record` | 鏅€?| 瑙ｅ瘑鎿嶄綔璁板綍 |
| 21 | `system_config` | 鏅€?| 绯荤粺閰嶇疆 |
| 22 | `operation_log` | **鍒嗗尯** | 鎿嶄綔鏃ュ織锛堟寜鏈?RANGE锛宍create_time`锛?|

### 11.2 鍒嗗尯琛ㄨ鑼?
```sql
-- 鍒嗗尯閿細create_time锛堝繀椤伙級
-- 鍒嗗尯绛栫暐锛歊ANGE (Monthly)
CREATE TABLE colonelsettlement_order (
    id         UUID,
    order_id   VARCHAR(50) NOT NULL,
    ...
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, create_time)
) PARTITION BY RANGE (create_time);

-- 瀛愬垎鍖哄懡鍚嶏細cso_YYYY_MM锛堣鍗曪級銆乷p_log_YYYY_MM锛堟棩蹇楋級
-- 褰撳墠宸插垱寤猴細2026-04 ~ 2027-03 鍏?2涓湀鍒嗗尯
```

### 11.3 鏂规B褰掑洜瀛楁锛圴1.3锛?
```sql
pick_source_mapping 琛ㄦ柊澧烇細
- short_id   VARCHAR(10) UNIQUE  -- 8浣岯ase36閫忎紶鍊硷紙鈮?0瀛楃锛?- uuid_seed  UUID                 -- 鍘熷UUID锛屽弽鏌ヨ繕鍘?- pick_extra VARCHAR(10)         -- 瀹為檯閫忎紶鍊?short_id
```

### 11.4 鑷姩鍒嗗尯绠＄悊

```sql
-- 姣忔湀1鍙疯嚜鍔ㄥ垱寤轰笅鏈堝垎鍖猴紙寤鸿鐢?pg_cron 璋冪敤锛?SELECT create_next_month_partitions();
```

### 11.5 鍏抽敭淇璁板綍锛圴1.2/V1.3锛?
| 淇 | 绾у埆 | 璇存槑 |
|------|------|------|
| `sys_user.channel_code` | P0 | UUID 瓒?pick_extra 20瀛楃闄愬埗锛屾柊澧炴笭閬撶煭鐮?|
| `pick_source_mapping.short_id` + `uuid_seed` | P0 | 鏂规B ShortID 閫忎紶鏈哄埗 |
| `pick_source_mapping.pick_source` VARCHAR(128) | P2 | API 瀹為檯杩斿洖鍙兘杈冮暱 |
| `colonelsettlement_order.order_amount/actual_amount` | P0 | 璁㈠崟閲戦瀛楁锛堝垎锛?|
| `product.cos_ratio/cos_fee/service_ratio` | P0 | 鍟嗗搧绾у垎浣ｅ瓧娈?|
| `merchant.status` | P0 | 鍟嗗鍚敤/绂佺敤鎺у埗 |
| `douyin_token.app_id` | P1 | 鎶栧簵搴旂敤ID鏍囪瘑 |
| `colonel_activity_product.min_refer_amount` | P1 | 鏈€浣庢帹骞块噾棰濋棬妲涳紙鍒嗭級 |
| `talent.crawl_status/crawl_message` | P1 | 閲囬泦鐘舵€佽拷韪?|
| `sample_request` 鏀朵欢浜哄瓧娈?| P1 | recipient_name/phone/address |
| `sys_role.permissions/menu_config JSONB` | P1 | 鎿嶄綔鏉冮檺+鑿滃崟鍙厤缃?|

---

## 鍗佷簩銆佺浉鍏虫枃浠剁储寮?
| 鏂囦欢 | 璺緞 | 璇存槑 |
|------|------|------|
| 瀹屾暣 DDL | `backend/src/main/resources/db/init-db.sql` | V1.3 鍏ㄩ儴 20 琛?+ 鍒嗗尯 + 绉嶅瓙鏁版嵁 |
| BaseEntity | `backend/src/main/java/com/colonel/saas/common/base/BaseEntity.java` | 缁熶竴鍩虹被 |
| MetaObjectHandler | `backend/src/main/java/com/colonel/saas/config/CustomMetaObjectHandler.java` | 鑷姩濉厖 |
| Lint 瑙勫垯 | `doc/rules/entity-constraints.md` | 瀹炰綋绫荤害鏉?|
| 鍒嗗尯绾︽潫 | `doc/rules/partition-table.md` | 鍒嗗尯琛ㄥ己鍒剁害鏉?|
