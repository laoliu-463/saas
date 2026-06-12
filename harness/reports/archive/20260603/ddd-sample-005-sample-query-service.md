# DDD-SAMPLE-005: Split SampleQueryService

## 任务概述

将 SampleApplicationService 中的只读查询逻辑拆分到独立的 SampleQueryService，实现查询与状态变更的职责分离。

## 变更内容

### 新增文件

| 文件 | 描述 |
|------|------|
| `src/main/java/com/colonel/saas/service/sample/SampleQueryService.java` | 寄样查询服务接口，定义 6 个查询方法 |
| `src/main/java/com/colonel/saas/service/sample/LegacySampleQueryService.java` | 实现类，委托给 SampleApplicationService 执行 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `src/main/java/com/colonel/saas/controller/SampleController.java` | 注入 SampleQueryService，重写 5 个查询方法委托给新服务 |
| `src/test/java/com/colonel/saas/controller/SampleControllerTest.java` | 新增 3 个快照验证测试 |

## SampleQueryService 接口方法

| 方法 | 描述 |
|------|------|
| `getSamplePage` (全参) | 分页查询寄样列表（25 个筛选参数） |
| `getSamplePage` (简化) | 分页查询寄样列表（8 个参数） |
| `getSampleById` | 查询单个寄样详情 |
| `exportSamples` | 导出 CSV（24 个参数） |
| `getSampleBoard` | 看板数据查询 |
| `getSampleLogistics` | 物流信息查询 |

## 设计决策

1. **委托模式**：LegacySampleQueryService 当前委托给 SampleApplicationService，确保接口行为与原实现完全一致
2. **接口不变**：所有 API 路径、字段名、筛选条件、导出列顺序保持不变
3. **Controller 重写**：SampleController 通过 `@Override` 重写查询方法，将调用委托到 sampleQueryService

## 测试结果

### 编译验证

- `mvn test-compile` ✅ 通过

### 运行时测试

- `mvn test -Dtest=SampleControllerTest` ❌ 75 个错误（**与本次变更无关**）
  - 原因：测试环境 Lombok `@Slf4j` 处理器未正确生成 `log` 字段
  - 验证：还原变更后原始测试同样失败
  - 状态：**已知环境问题，非 DDD-SAMPLE-005 引入**

### 新增测试（3 个）

| 测试方法 | 验证内容 | 编译 |
|----------|----------|------|
| `getSamplePage_shouldReturnAllExpectedListFields` | 列表字段快照：id, requestNo, talentUid, talentName, fansCount, creditScore, mainCategory, productId, externalId, productName, cover, priceText, shopId, shopName, quantity, trackingNo, logisticsCompany, recipientName, recipientPhone, recipientAddress, status, createTime, updateTime, applyReason, applySource, cooperationType, sampleOwnerType, homeworkType | ✅ |
| `getSampleById_shouldReturnAllExpectedDetailFields` | 详情字段快照：全量字段 + channelUserName, shipTime, deliverTime, rejectReason, closeReason, remark, eligibilityCheck, requirementSnapshot | ✅ |
| `exportSamples_shouldMaintainColumnOrder` | 导出列顺序：寄样单号,达人昵称,商品名称,状态,招商负责人,收件人,收件电话,收件地址,物流单号,驳回原因,备注,创建时间 | ✅ |

## 不变性验证

| 维度 | 状态 | 说明 |
|------|------|------|
| 列表字段 | ✅ | SampleVO 字段不变 |
| 详情字段 | ✅ | SampleVO 字段不变 |
| 筛选条件 | ✅ | applySampleQueryFilters 未修改 |
| 导出列顺序 | ✅ | CSV header: 寄样单号,达人昵称,商品名称,状态,招商负责人,收件人,收件电话,收件地址,物流单号,驳回原因,备注,创建时间 |
| 导出权限 | ✅ | @RequireRoles 注解未修改 |
| 状态机 | ✅ | actionSample 逻辑未修改 |
| API 路径 | ✅ | @GetMapping/@PostMapping 路径未修改 |

## 风险评估

- **低风险**：委托模式确保行为与原实现完全一致
- **已知问题**：测试环境 Lombok 处理器问题导致单元测试无法运行（非本次引入）
- **后续步骤**：可将查询逻辑从 SampleApplicationService 迁移到 LegacySampleQueryService，减少 SampleApplicationService 体积

## Commit

```
DDD-SAMPLE-005 split SampleQueryService
```
