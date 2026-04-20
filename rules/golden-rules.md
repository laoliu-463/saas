# 规则：黄金规则（Golden Rules）

**版本**：V1.0
**状态**：强制执行
**适用范围**：全局，所有开发者必须遵守

---

## 一、金规则列表

### 规则 1：分区表必须带时间查询

```java
// ✅ 正确：所有 orders 查询必须带时间范围
public List<Order> listByCreateTime(LocalDateTime start, LocalDateTime end) {
    return orderMapper.selectList(Wrappers.<Order>lambdaQuery()
        .between(Order::getCreateTime, start, end));
}

// ❌ 违反规则：分区表无时间查询
public List<Order> listAll() {
    return orderMapper.selectList(null); // 违反！全表扫描
}
```

### 规则 2：敏感数据不得持久化

```java
// ✅ 正确：解密数据仅展示，不存储
public OrderVO getDecryptedOrder(String orderId) {
    DecryptResponse response = decryptService.decrypt(orderId);
    return OrderVO.builder()
        .phone(response.getPhone()) // 仅返回，不存储
        .build();
}

// ❌ 违反规则：存储敏感数据
order.setPhone(response.getPhone()); // 违反！
```

### 规则 3：必须通过归因映射获取 channel_id

```java
// ✅ 正确：必须通过 PickSourceMapping 归因
UUID channelId = attributionService.resolve(pickSource);

// ❌ 违反规则：直接从 pick_source 解析
String channelId = pickSource.split("_")[0]; // 违反！
```

### 规则 4：提成比例必须引用配置

```java
// ✅ 正确：从配置服务获取
BigDecimal ratio = systemConfig.getCommissionRatio(talentLevel);

// ❌ 违反规则：硬编码比例
BigDecimal ratio = new BigDecimal("0.15"); // 违反！
```

### 规则 5：爬虫必须遵循安全间隔

```python
# ✅ 正确：使用基类
class MyCrawler(CrawlerBase):
    def crawl(self):
        html = self.http_get(url)  # 自动 3-6s 间隔

# ❌ 违反规则：无间隔请求
requests.get(url)  # 违反！
```

### 规则 6：业务表必须继承 BaseEntity

```java
// ✅ 正确
public class Product extends BaseEntity { }

// ❌ 违反规则
public class Product {  // 违反！缺少审计字段
    private UUID id;
}
```

---

## 二、违规检测

### 2.1 CI/CD 检测规则

| 规则 | 检测方式 | 违规处理 |
|------|----------|----------|
| 分区表无时间查询 | SpotBugs 规则 | **BLOCK** CI |
| 敏感数据存储 | SonarQube 规则 | **BLOCK** CI |
| 归因逻辑绕过 | 单元测试 | **BLOCK** PR |
| 硬编码比例 | SpotBugs 规则 | **WARN** |
| 爬虫无间隔 | 集成测试 | **BLOCK** PR |
| 未继承 BaseEntity | 编译检查 | **BLOCK** 编译 |

### 2.2 SpotBugs 规则配置

```xml
<!-- SpotBugs 自定义规则：禁止分区表无时间查询 -->
<Match classRegex="com\.colonel\.saas\..*Order.*">
    <Bug pattern="PARTITION_TABLE_WITHOUT_TIME_RANGE"/>
</Match>

<!-- 禁止存储敏感字段 -->
<Match classRegex="com\.colonel\.saas\.entity\..*">
    <Field name="phone" type="String"/>
    <Bug pattern="SENSITIVE_DATA_FIELD"/>
</Match>
```

---

## 三、违规处理流程

```
1. 开发阶段 → 代码审查时发现违规
2. CI 阶段 → SpotBugs/SonarQube 检测到违规
3. BLOCK → 禁止合并，需修复
4. 修复后 → 重新审查
```

---

## 四、规则索引

| 规则编号 | 规则名称 | 详细约束文件 |
|----------|----------|--------------|
| G1 | 分区表时间约束 | `rules/partition-table.md` |
| G2 | 敏感数据保护 | `rules/api-security.md` |
| G3 | 归因逻辑 | `rules/attribution-logic.md` |
| G4 | 配置引用 | `rules/exclusive-triggers.md` |
| G5 | 爬虫安全 | `rules/crawler-safety.md` |
| G6 | 实体基类 | `rules/entity-constraints.md` |
