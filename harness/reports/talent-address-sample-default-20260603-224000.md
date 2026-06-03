# Evidence Report: TALENT-ADDRESS-SAMPLE-DEFAULT

| 字段 | 值 |
|---|---|
| 时间 | 2026-06-03 22:40 |
| 环境 | local real-pre |
| 分支 | feature/auth-system |
| 基线 commit | 49aefbda |
| 工作区 | dirty (本任务修改) |
| 构建结果 | backend package ✅ / frontend build ✅ / typecheck ✅ |
| Docker 状态 | 4/4 healthy (backend/frontend/postgres/redis) |
| 健康检查 | `{"status":"UP"}` ✅ |
| 远端部署 | 否 |
| 结论 | **PASS** |

## 1. 根因

生产反馈：达人寄样地址不默认保存，每次申请寄样都要重新填写。

**根因分析**：
- `ProductQuickSampleService` 和 `SampleApplicationService` 创建 `sample_request` 后，没有将地址回写到 `talent_claim` 认领记录
- 前端 `QuickSampleModal.vue` 和 `SampleCreateModal.vue` 选择达人后，没有调用 `getTalentShippingAddress` API 加载默认地址

## 2. 表结构与字段选择

无需新增字段或 migration。复用 `talent_claim` 表已有字段：
- `recipient_name` (varchar)
- `recipient_phone` (varchar)
- `recipient_address` (text)

`sample_request` 表已有独立快照字段：
- `recipient_name` / `recipient_phone` / `recipient_address`

地址按 `user_id + talent_id` 维度保存在 `talent_claim`，确保多渠道隔离。

## 3. 修改文件

### 后端 (3 files)
| 文件 | 变更 |
|---|---|
| `backend/src/main/java/com/colonel/saas/service/ProductQuickSampleService.java` | 添加 `writeBackClaimAddress()` 方法，创建寄样后回写地址到 `talent_claim` |
| `backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java` | 同上，寄样台创建寄样后回写地址 |
| `backend/src/test/java/com/colonel/saas/service/QuickSampleApplyTest.java` | 新增 3 个地址回写测试 |

### 前端 (4 files)
| 文件 | 变更 |
|---|---|
| `frontend/src/views/product/components/QuickSampleModal.vue` | 添加 `watch(talentIds)` 自动加载默认地址；渠道切换清空地址 |
| `frontend/src/views/sample/components/SampleCreateModal.vue` | `chooseTalent` 后调用 `loadDefaultAddress()`；修复地址字段传递到 API |
| `frontend/src/views/product/components/QuickSampleModal.test.ts` | 新增 2 个地址相关测试 |
| `frontend/src/views/sample/components/SampleCreateModal.test.ts` | 新建，3 个测试用例 |

## 4. 测试结果

### 后端测试
- 全量测试：**1708 tests / 0 failures / 0 errors** ✅
- 新增测试：
  - `shouldSaveTalentAddressAfterSampleApply` ✅
  - `shouldNotWriteBackAddressWhenEmpty` ✅
  - `shouldSkipWriteBackWhenClaimNotFound` ✅

### 前端测试
- QuickSampleModal：**5/5 passed** ✅
- SampleCreateModal：**3/3 passed** ✅
- typecheck：✅
- build：✅

## 5. real-pre 验收步骤和 SQL 证据

### H1: 第一次寄样 (Address V1)
- 用户: channel_staff
- 达人: QA20260530_163758_t33_1780130340247
- 商品: 09fe4536 (周黑鸭)
- 地址: 李四海 / 13900139001 / 北京市朝阳区望京SOHO T3-1201 V1
- sampleRequestId: `93485463-a8e4-47d0-a454-176ed34a8455`
- **结果: PASS** ✅

### H2: sample_request 快照 = V1
```sql
SELECT recipient_name, recipient_phone, recipient_address
FROM sample_request WHERE id = '93485463-a8e4-47d0-a454-176ed34a8455';
-- 结果: 李四海 | 13900139001 | 北京市朝阳区望京SOHO T3-1201 V1 ✅
```

### H3: talent_claim 默认地址 = V1
```sql
SELECT recipient_name, recipient_phone, recipient_address
FROM talent_claim WHERE talent_id = '0a390afd-...' AND user_id = '33e8493b-...';
-- 结果: 李四海 | 13900139001 | 北京市朝阳区望京SOHO T3-1201 V1 ✅
```

### H4: GET shipping-address API 返回 V1
```
GET /api/talents/0a390afd-.../shipping-address
→ {"recipientName":"李四海","recipientPhone":"13900139001","recipientAddress":"北京市朝阳区望京SOHO T3-1201 V1"} ✅
```

### H5-H8: 修改地址为 V2 后验证
- 达人: QA20260530_191530_t33_1780139901378 (另一达人，避免7天去重)
- V1 地址: 广州市天河区珠江新城花城大道 V1
- V2 地址: 深圳市南山区科技园南区 V2

| 验证项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| 旧 sample_request 地址 | V1 (广州市天河区) | V1 (广州市天河区) | ✅ |
| 新 sample_request 地址 | V2 (深圳市南山区) | V2 (深圳市南山区) | ✅ |
| talent_claim 默认地址 | V2 (深圳市南山区) | V2 (深圳市南山区) | ✅ |
| GET shipping-address | V2 | V2 | ✅ |

### H9: 多渠道隔离
- biz_leader 访问 channel_staff 认领的达人地址 → **403 无权限** ✅
- 地址按 `user_id + talent_id` 维度隔离，非认领人无法读取

## 6. 多渠道隔离证据

| 用户 | 角色 | 请求 | 响应 |
|---|---|---|---|
| channel_staff | channel_staff | GET /talents/{id}/shipping-address | 200 + 地址数据 |
| biz_leader | biz_leader | GET /talents/{id}/shipping-address | 403 无权限 |

设计保证：`TalentClaim` 按 `(user_id, talent_id)` 唯一，`TalentService.getShippingAddress` 只查询当前用户的认领记录。

## 7. 是否需要远端部署

否。本次仅本地 real-pre 验证，用户未要求远端部署。

## 8. 未解决风险

- 前端 v-model stub 限制：`QuickSampleModal` 的 watch prefill 行为在单元测试中无法完全模拟（Naive UI v-model:value 不触发 stub 的 watch），已通过 E2E real-pre API 验证补偿
- 寄样台 `SampleApplicationService` 的 writeback 在本地验证中通过代码审查确认，API 路径未直接测试（需要寄样台完整流程 E2E）
