# 需求：样品管理（寄样台）

**文档版本**：V1.0
**来源**：V2.2 定稿文档 §3.5
**智能体入口**：直接读取此文件

---

## 一、功能定位

寄样台是渠道申请寄样、招商审核、运营发货的全流程管理系统，覆盖申请 → 审核 → 发货 → 交作业 → 完成/关闭的完整链路。

---

## 二、状态机

```
┌─────────┐    招商审核     ┌─────────┐   录入单号    ┌─────────┐
│ 待审核   │ ────通过────→ │ 待发货   │ ────────→   │ 快递中   │
└─────────┘               └─────────┘               └─────────┘
     │                          │                         │
     │ 拒绝                     │ 30天超时关闭            │ 签收
     ↓                         ↓                         ↓
┌─────────┐               ┌─────────┐               ┌─────────┐
│ 已拒绝   │               │ 已关闭   │               │ 待交作业 │
└─────────┘               └─────────┘               └─────────┘
                                                           │
                              ←───────── 出单（≥1单） ─────┘
                                                           ↓
                                                      ┌─────────┐
                                                      │ 已完成   │
                                                      └─────────┘
```

| 状态 | 触发操作 | 说明 |
|------|----------|------|
| 待审核 | 渠道提交 | 等待招商审核 |
| 待发货 | 招商通过 | 等待运营录入物流 |
| 快递中 | 录入物流 | 商家已发货 |
| 待交作业 | 签收确认 | 等待该渠道该商品出单 |
| 已完成 | 订单入库 | 该达人在该渠道产生 ≥1 单 |
| 已拒绝 | 招商拒绝 | 渠道可重新申请（不受7天限制） |
| 已关闭 | 超时关闭 | 30 天未出单自动关闭 |

---

## 三、申请寄样

### 3.1 申请入口

- 渠道在商品库页面，选择商品后点击"申请寄样"
- 或在达人详情页申请

### 3.2 申请参数

```java
public class SampleRequestCreateDTO {
    @NotNull private UUID talentId;      // 达人ID
    @NotNull private UUID productId;     // 商品ID
    @NotNull private String receiverName;  // 收货人姓名
    @NotNull private String receiverPhone; // 收货人电话
    @NotNull private String receiverAddress; // 收货地址
    private String reason;                 // 申请原因（不满足寄样标准时必填）
}
```

### 3.3 申请校验

```java
public void apply(SampleRequestCreateDTO dto) {
    // 1. 达人是否符合商品寄样标准
    Product product = productService.getById(dto.getProductId());
    Talent talent = talentService.getById(dto.getTalentId());

    if (!talentService.meetsSampleStandard(talent, product)) {
        if (dto.getReason() == null || dto.getReason().isBlank()) {
            throw new BusinessException("该达人不满足寄样标准，请填写申请原因");
        }
    }

    // 2. 寄样限制校验（7天规则）
    sampleRequestService.checkDuplicate(
        getCurrentUserId(), dto.getTalentId(), dto.getProductId());

    // 3. 创建申请
    SampleRequest request = SampleRequest.builder()
        .requestNo(generateRequestNo())  // 格式：SM + yyyyMMdd + 6位序号
        .talentId(dto.getTalentId())
        .productId(dto.getProductId())
        .userId(getCurrentUserId())  // 申请渠道
        .status(SampleStatus.PENDING_REVIEW)
        .receiverName(dto.getReceiverName())
        .receiverPhone(dto.getReceiverPhone())
        .receiverAddress(dto.getReceiverAddress())
        .reason(dto.getReason())
        .build();
    sampleRequestMapper.insert(request);
}
```

### 3.4 申请编号规则

```
格式：SM + yyyyMMdd + 6位序号
示例：SM20260420000001
存储位置：sample_request.request_no（UNIQUE 索引）
```

---

## 四、招商审核

### 4.1 审核操作

| 操作 | 结果状态 | 影响 |
|------|----------|------|
| 通过 | 待发货 | 进入发货流程 |
| 拒绝 | 已拒绝 | 渠道可重新申请（不受7天限制） |

### 4.2 拒绝原因

```java
public void reject(UUID requestId, String reason) {
    SampleRequest request = sampleRequestMapper.selectById(requestId);
    request.setStatus(SampleStatus.REJECTED);
    request.setRejectReason(reason);
    request.setRejectTime(LocalDateTime.now());
    sampleRequestMapper.updateById(request);

    // 记录状态变更日志
    sampleStatusLogService.log(requestId, SampleStatus.REJECTED, reason);
}
```

---

## 五、运营发货

### 5.1 V1.0 阶段

- 手动录入物流单号
- 录入后状态变更为"快递中"

### 5.2 V2.0 阶段（规划）

- 对接第三方物流 API（快递鸟/快递100）
- 自动更新物流状态

```java
// V2.0 物流更新（规划）
public void updateExpressStatus(String trackingNo) {
    LogisticsInfo info = logisticsApi.query(trackingNo);
    sampleRequestMapper.updateStatusByTrackingNo(trackingNo, mapStatus(info.getStatus()));
}
```

---

## 六、交作业判断

### 6.1 判断规则

> 该达人通过**该渠道**的链接产生**该商品**的订单（≥1 单）即视为完成

### 6.2 实现逻辑

```java
// SampleRequestService.checkDeliverComplete()
public void checkDeliverComplete(UUID requestId) {
    SampleRequest request = sampleRequestMapper.selectById(requestId);

    // 条件：同一达人 + 同一商品 + 同一渠道 + 订单状态有效
    long orderCount = orderMapper.countByTalentProductChannel(
        request.getTalentId(),
        request.getProductId(),
        request.getUserId()  // 渠道
    );

    if (orderCount >= 1) {
        request.setStatus(SampleStatus.COMPLETED);
        request.setCompleteTime(LocalDateTime.now());
        sampleRequestMapper.updateById(request);
    }
}
```

### 6.3 超时自动关闭

```java
// 定时任务：每天检查超时申请
@Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨2点
public void autoCloseTimeoutRequests() {
    LocalDateTime deadline = LocalDateTime.now().minusDays(30);
    List<SampleRequest> timeouts = sampleRequestMapper.selectTimeout(deadline);
    for (SampleRequest r : timeouts) {
        r.setStatus(SampleStatus.CLOSED);
        r.setCloseReason("超时30天未出单");
        sampleRequestMapper.updateById(r);
    }
}
```

---

## 七、筛选与批量操作

| 功能 | 说明 |
|------|------|
| 按状态筛选 | 待审核/待发货/快递中/待交作业等 |
| 按商品筛选 | 商品名称/ID |
| 按达人筛选 | 达人昵称/UID |
| 按申请时间筛选 | 时间范围 |
| 按招商负责人筛选 | 对应商品的招商 |
| 批量审核 | 招商组长可批量通过 |
| 批量导出 | 导出用于同步商家的寄样数据 |

---

## 八、业务约束

| 约束 | 文件 | 级别 |
|------|------|------|
| 寄样限制必须校验 | `requirements/05-talent-crm.md` | HIGH |
| 申请编号必须唯一 | `rules/entity-constraints.md` | HIGH |
| 状态流转必须记录日志 | 业务逻辑 | HIGH |

---

## 九、相关文件索引

| 文件 | 路径 |
|------|------|
| 寄样申请实体 | `backend/src/main/java/com/colonel/saas/entity/SampleRequest.java` |
| 状态枚举 | `backend/src/main/java/com/colonel/saas/enums/SampleStatus.java` |
| 寄样服务 | `backend/src/main/java/com/colonel/saas/service/SampleRequestService.java` |
| 状态日志实体 | `backend/src/main/java/com/colonel/saas/entity/SampleStatusLog.java` |
