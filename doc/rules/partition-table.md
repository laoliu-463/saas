# 瑙勫垯锛氬垎鍖鸿〃绾︽潫

**鐗堟湰**锛歏1.0
**鐘舵€?*锛氬己鍒舵墽琛?**閫傜敤鑼冨洿**锛氭墍鏈夊垎鍖鸿〃锛坄colonelsettlement_order`, `operation_log`锛?
---

## 涓€銆佸垎鍖鸿〃璇嗗埆

| 琛ㄥ悕 | 鍒嗗尯閿?| 鍒嗗尯绫诲瀷 | 鐗圭偣 |
|------|--------|----------|------|
| `colonelsettlement_order` | `create_time` | RANGE (Monthly) | 璁㈠崟鏁版嵁锛岄噺澶?|
| `operation_log` | `create_time` | RANGE (Monthly) | 鎿嶄綔鏃ュ織 |

---

## 浜屻€両NSERT 绾︽潫

### 2.1 create_time 蹇呴』璧嬪€?
```java
// 鉁?姝ｇ‘锛欼NSERT 鍓嶈缃?createTime
@PrePersist
public void prePersist() {
    if (this.createTime == null) {
        this.createTime = LocalDateTime.now();
    }
}

// 鉂?閿欒锛氬厑璁?createTime 涓?NULL
public void wrongSave(Order order) {
    orderMapper.insert(order); // createTime 鍙兘涓?NULL锛?}
```

### 2.2 鍒嗗尯涓嶅瓨鍦ㄦ椂鑷姩鍒涘缓

```java
// 鍒嗗尯涓嶅瓨鍦ㄦ椂鑷姩鍒涘缓
public void ensurePartitionExists(LocalDateTime createTime) {
    String partitionName = "p" + createTime.format(DateTimeFormatter.ofPattern("yyyyMM"));
    if (!partitionService.exists(partitionName)) {
        partitionService.createMonthlyPartition(partitionName, createTime);
    }
}
```

### 2.3 鍒嗗尯棰勫垱寤?
- [ ] 姣忔搴旂敤鍚姩鏃舵鏌ュ苟鍒涘缓涓嬪搴﹀垎鍖?- [ ] 瀹氭椂浠诲姟姣忔湀 25 鏃ヨ嚜鍔ㄥ垱寤轰笅鏈堝垎鍖?- [ ] 鍒嗗尯鍛藉悕鏍煎紡锛歚p{yyyyMM}`

---

## 涓夈€佹煡璇㈢害鏉?
### 3.1 蹇呴』甯︽椂闂磋寖鍥?
```java
// 鉁?姝ｇ‘锛氬甫鏃堕棿鑼冨洿
public List<Order> listOrders(LocalDateTime start, LocalDateTime end) {
    return orderMapper.selectList(Wrappers.<Order>lambdaQuery()
        .between(Order::getCreateTime, start, end)
        .eq(Order::getChannelId, channelId));
}

// 鉂?閿欒锛氭棤鏃堕棿鑼冨洿锛堝叏琛ㄦ壂鎻忥級
public List<Order> wrongListOrders() {
    return orderMapper.selectList(null); // 绂佹锛佸叏琛ㄦ壂鎻?}
```

### 3.2 绱㈠紩浣跨敤妫€鏌?
```java
// 鉁?姝ｇ‘锛氬埄鐢ㄥ垎鍖鸿鍓?// 鏌ヨ 2024-03 鐨勮鍗曟椂锛孭ostgreSQL 鍙壂鎻?p202403 鍒嗗尯
.query("""
    SELECT * FROM colonelsettlement_order
    WHERE create_time >= '2024-03-01' AND create_time < '2024-04-01'
    AND channel_id = ?
    """)
```

---

## 鍥涖€佺姝㈠仛娉?
- [ ] **绂佹**锛氬鍒嗗尯琛ㄦ墽琛屾棤鏃堕棿鏉′欢鐨勫叏琛ㄦ壂鎻?- [ ] **绂佹**锛欼NSERT 鏃?`create_time` 涓?NULL
- [ ] **绂佹**锛氳法鍒嗗尯鑱氬悎鏌ヨ鏃朵笉鎸囧畾鏃堕棿鑼冨洿
- [ ] **绂佹**锛氬垹闄ゅ垎鍖烘暟鎹紙浣跨敤閫昏緫鍒犻櫎 `deleted=1`锛?
---

## 浜斻€佸垎鍖虹淮鎶?
### 5.1 鍒嗗尯鏌ョ湅

```sql
-- 鏌ョ湅鍒嗗尯淇℃伅
SELECT
    child.relname AS partition_name,
    pg_get_expr(child.relpartbound, child.oid) AS partition_bounds
FROM pg_inherits
JOIN pg_class parent ON inhparent = parent.oid
JOIN pg_class child ON inhrelid = child.oid
WHERE parent.relname = 'colonelsettlement_order';
```

### 5.2 鍒嗗尯鍋ュ悍妫€鏌?
```java
// 瀹氭椂妫€鏌ュ垎鍖哄畬鏁存€?@Scheduled(cron = "0 0 2 * * *") // 姣忓ぉ鍑屾櫒2鐐?public void checkPartitionHealth() {
    List<String> missingPartitions = partitionService.findMissingPartitions();
    if (!missingPartitions.isEmpty()) {
        alertService.alert("鍒嗗尯缂哄け: " + missingPartitions);
    }
}
```

---

## 鍏€佺浉鍏虫枃浠剁储寮?
| 鏂囦欢 | 璇存槑 |
|------|------|
| `PartitionService.java` | 鍒嗗尯绠＄悊鏈嶅姟 |
| `Order.java` | 璁㈠崟瀹炰綋 |
| `OperationLog.java` | 鎿嶄綔鏃ュ織瀹炰綋 |
| 闇€姹傚叆鍙?| `doc/requirements/02-data-schema.md` |
