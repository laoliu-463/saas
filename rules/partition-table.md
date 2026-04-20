# 规则：分区表约束

**版本**：V1.0
**状态**：强制执行
**适用范围**：所有分区表（`colonelsettlement_order`, `operation_log`）

---

## 一、分区表识别

| 表名 | 分区键 | 分区类型 | 特点 |
|------|--------|----------|------|
| `colonelsettlement_order` | `create_time` | RANGE (Monthly) | 订单数据，量大 |
| `operation_log` | `create_time` | RANGE (Monthly) | 操作日志 |

---

## 二、INSERT 约束

### 2.1 create_time 必须赋值

```java
// ✅ 正确：INSERT 前设置 createTime
@PrePersist
public void prePersist() {
    if (this.createTime == null) {
        this.createTime = LocalDateTime.now();
    }
}

// ❌ 错误：允许 createTime 为 NULL
public void wrongSave(Order order) {
    orderMapper.insert(order); // createTime 可能为 NULL！
}
```

### 2.2 分区不存在时自动创建

```java
// 分区不存在时自动创建
public void ensurePartitionExists(LocalDateTime createTime) {
    String partitionName = "p" + createTime.format(DateTimeFormatter.ofPattern("yyyyMM"));
    if (!partitionService.exists(partitionName)) {
        partitionService.createMonthlyPartition(partitionName, createTime);
    }
}
```

### 2.3 分区预创建

- [ ] 每次应用启动时检查并创建下季度分区
- [ ] 定时任务每月 25 日自动创建下月分区
- [ ] 分区命名格式：`p{yyyyMM}`

---

## 三、查询约束

### 3.1 必须带时间范围

```java
// ✅ 正确：带时间范围
public List<Order> listOrders(LocalDateTime start, LocalDateTime end) {
    return orderMapper.selectList(Wrappers.<Order>lambdaQuery()
        .between(Order::getCreateTime, start, end)
        .eq(Order::getChannelId, channelId));
}

// ❌ 错误：无时间范围（全表扫描）
public List<Order> wrongListOrders() {
    return orderMapper.selectList(null); // 禁止！全表扫描
}
```

### 3.2 索引使用检查

```java
// ✅ 正确：利用分区裁剪
// 查询 2024-03 的订单时，PostgreSQL 只扫描 p202403 分区
.query("""
    SELECT * FROM colonelsettlement_order
    WHERE create_time >= '2024-03-01' AND create_time < '2024-04-01'
    AND channel_id = ?
    """)
```

---

## 四、禁止做法

- [ ] **禁止**：对分区表执行无时间条件的全表扫描
- [ ] **禁止**：INSERT 时 `create_time` 为 NULL
- [ ] **禁止**：跨分区聚合查询时不指定时间范围
- [ ] **禁止**：删除分区数据（使用逻辑删除 `deleted=1`）

---

## 五、分区维护

### 5.1 分区查看

```sql
-- 查看分区信息
SELECT
    child.relname AS partition_name,
    pg_get_expr(child.relpartbound, child.oid) AS partition_bounds
FROM pg_inherits
JOIN pg_class parent ON inhparent = parent.oid
JOIN pg_class child ON inhrelid = child.oid
WHERE parent.relname = 'colonelsettlement_order';
```

### 5.2 分区健康检查

```java
// 定时检查分区完整性
@Scheduled(cron = "0 0 2 * * *") // 每天凌晨2点
public void checkPartitionHealth() {
    List<String> missingPartitions = partitionService.findMissingPartitions();
    if (!missingPartitions.isEmpty()) {
        alertService.alert("分区缺失: " + missingPartitions);
    }
}
```

---

## 六、相关文件索引

| 文件 | 说明 |
|------|------|
| `PartitionService.java` | 分区管理服务 |
| `Order.java` | 订单实体 |
| `OperationLog.java` | 操作日志实体 |
| 需求入口 | `requirements/02-data-schema.md` |
