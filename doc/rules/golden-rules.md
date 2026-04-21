# 瑙勫垯锛氶粍閲戣鍒欙紙Golden Rules锛?
**鐗堟湰**锛歏1.0
**鐘舵€?*锛氬己鍒舵墽琛?**閫傜敤鑼冨洿**锛氬叏灞€锛屾墍鏈夊紑鍙戣€呭繀椤婚伒瀹?
---

## 涓€銆侀噾瑙勫垯鍒楄〃

### 瑙勫垯 1锛氬垎鍖鸿〃蹇呴』甯︽椂闂存煡璇?
```java
// 鉁?姝ｇ‘锛氭墍鏈?orders 鏌ヨ蹇呴』甯︽椂闂磋寖鍥?public List<Order> listByCreateTime(LocalDateTime start, LocalDateTime end) {
    return orderMapper.selectList(Wrappers.<Order>lambdaQuery()
        .between(Order::getCreateTime, start, end));
}

// 鉂?杩濆弽瑙勫垯锛氬垎鍖鸿〃鏃犳椂闂存煡璇?public List<Order> listAll() {
    return orderMapper.selectList(null); // 杩濆弽锛佸叏琛ㄦ壂鎻?}
```

### 瑙勫垯 2锛氭晱鎰熸暟鎹笉寰楁寔涔呭寲

```java
// 鉁?姝ｇ‘锛氳В瀵嗘暟鎹粎灞曠ず锛屼笉瀛樺偍
public OrderVO getDecryptedOrder(String orderId) {
    DecryptResponse response = decryptService.decrypt(orderId);
    return OrderVO.builder()
        .phone(response.getPhone()) // 浠呰繑鍥烇紝涓嶅瓨鍌?        .build();
}

// 鉂?杩濆弽瑙勫垯锛氬瓨鍌ㄦ晱鎰熸暟鎹?order.setPhone(response.getPhone()); // 杩濆弽锛?```

### 瑙勫垯 3锛氬繀椤婚€氳繃褰掑洜鏄犲皠鑾峰彇 channel_id

```java
// 鉁?姝ｇ‘锛氬繀椤婚€氳繃 PickSourceMapping 褰掑洜
UUID channelId = attributionService.resolve(pickSource);

// 鉂?杩濆弽瑙勫垯锛氱洿鎺ヤ粠 pick_source 瑙ｆ瀽
String channelId = pickSource.split("_")[0]; // 杩濆弽锛?```

### 瑙勫垯 4锛氭彁鎴愭瘮渚嬪繀椤诲紩鐢ㄩ厤缃?
```java
// 鉁?姝ｇ‘锛氫粠閰嶇疆鏈嶅姟鑾峰彇
BigDecimal ratio = systemConfig.getCommissionRatio(talentLevel);

// 鉂?杩濆弽瑙勫垯锛氱‖缂栫爜姣斾緥
BigDecimal ratio = new BigDecimal("0.15"); // 杩濆弽锛?```

### 瑙勫垯 5锛氱埇铏繀椤婚伒寰畨鍏ㄩ棿闅?
```python
# 鉁?姝ｇ‘锛氫娇鐢ㄥ熀绫?class MyCrawler(CrawlerBase):
    def crawl(self):
        html = self.http_get(url)  # 鑷姩 3-6s 闂撮殧

# 鉂?杩濆弽瑙勫垯锛氭棤闂撮殧璇锋眰
requests.get(url)  # 杩濆弽锛?```

### 瑙勫垯 6锛氫笟鍔¤〃蹇呴』缁ф壙 BaseEntity

```java
// 鉁?姝ｇ‘
public class Product extends BaseEntity { }

// 鉂?杩濆弽瑙勫垯
public class Product {  // 杩濆弽锛佺己灏戝璁″瓧娈?    private UUID id;
}
```

---

## 浜屻€佽繚瑙勬娴?
### 2.1 CI/CD 妫€娴嬭鍒?
| 瑙勫垯 | 妫€娴嬫柟寮?| 杩濊澶勭悊 |
|------|----------|----------|
| 鍒嗗尯琛ㄦ棤鏃堕棿鏌ヨ | SpotBugs 瑙勫垯 | **BLOCK** CI |
| 鏁忔劅鏁版嵁瀛樺偍 | SonarQube 瑙勫垯 | **BLOCK** CI |
| 褰掑洜閫昏緫缁曡繃 | 鍗曞厓娴嬭瘯 | **BLOCK** PR |
| 纭紪鐮佹瘮渚?| SpotBugs 瑙勫垯 | **WARN** |
| 鐖櫕鏃犻棿闅?| 闆嗘垚娴嬭瘯 | **BLOCK** PR |
| 鏈户鎵?BaseEntity | 缂栬瘧妫€鏌?| **BLOCK** 缂栬瘧 |

### 2.2 SpotBugs 瑙勫垯閰嶇疆

```xml
<!-- SpotBugs 鑷畾涔夎鍒欙細绂佹鍒嗗尯琛ㄦ棤鏃堕棿鏌ヨ -->
<Match classRegex="com\.colonel\.saas\..*Order.*">
    <Bug pattern="PARTITION_TABLE_WITHOUT_TIME_RANGE"/>
</Match>

<!-- 绂佹瀛樺偍鏁忔劅瀛楁 -->
<Match classRegex="com\.colonel\.saas\.entity\..*">
    <Field name="phone" type="String"/>
    <Bug pattern="SENSITIVE_DATA_FIELD"/>
</Match>
```

---

## 涓夈€佽繚瑙勫鐞嗘祦绋?
```
1. 寮€鍙戦樁娈?鈫?浠ｇ爜瀹℃煡鏃跺彂鐜拌繚瑙?2. CI 闃舵 鈫?SpotBugs/SonarQube 妫€娴嬪埌杩濊
3. BLOCK 鈫?绂佹鍚堝苟锛岄渶淇
4. 淇鍚?鈫?閲嶆柊瀹℃煡
```

---

## 鍥涖€佽鍒欑储寮?
| 瑙勫垯缂栧彿 | 瑙勫垯鍚嶇О | 璇︾粏绾︽潫鏂囦欢 |
|----------|----------|--------------|
| G1 | 鍒嗗尯琛ㄦ椂闂寸害鏉?| `doc/rules/partition-table.md` |
| G2 | 鏁忔劅鏁版嵁淇濇姢 | `doc/rules/api-security.md` |
| G3 | 褰掑洜閫昏緫 | `doc/rules/attribution-logic.md` |
| G4 | 閰嶇疆寮曠敤 | `doc/rules/exclusive-triggers.md` |
| G5 | 鐖櫕瀹夊叏 | `doc/rules/crawler-safety.md` |
| G6 | 瀹炰綋鍩虹被 | `doc/rules/entity-constraints.md` |
