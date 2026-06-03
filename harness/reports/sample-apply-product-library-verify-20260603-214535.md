# 商品库 / 寄样入口申请寄样修复验证报告

## 基本信息

- 时间：2026-06-03 21:45 +08:00
- 环境：本地 real-pre
- 分支：feature/auth-system
- 相关提交：
  - `0a9a6b39 fix: sample apply from product library`
  - `b881a080 fix: quick sample manual talent fallback`
- 远端部署：未执行，用户未要求远端 real-pre 部署

## 问题复述

渠道从商品库 / 寄样入口申请寄样需要满足：

1. 申请不能因商品库快照 ID 与 `product` 主表外键不一致而失败。
2. 成功后必须写入 `sample_request`。
3. 初始状态必须为待审核。
4. 合作管理 / 招商审核列表必须可见。
5. 失败时必须返回明确原因，不能只显示“申请失败”。

## 修复摘要

- `ProductQuickSampleService`：
  - 商品库快速寄样入口传入 `product_snapshot.id` 时，先校验商品已入库且 `display_status=DISPLAYING`。
  - 在插入 `sample_request` 前，按 `product_snapshot.product_id` 确保 `product` 主表存在对应记录，避免 `sample_request.product_id` 外键失败。
  - 支持渠道私海手动达人兜底：`crawler_talent_info` 未命中时，按 `talent.douyin_uid` 构造寄样达人快照。
  - 成功落库状态仍使用寄样域现有内部状态 `PENDING_AUDIT` / 状态码 `1`，前端展示为“待审核”。`PENDING_REVIEW` 目前属于商品管理官方状态映射，不在本次跨域重命名。
- 前端：
  - `QuickSampleModal.vue` 对 `failureCount > 0` 展示 `items[].message` 明细。
  - `SampleCreateModal.vue` 改用统一 `notifyApiFailure`，避免服务端已返回明确原因后又追加泛化失败文案。

## 自动验证

- 后端 targeted 测试：
  - 命令：`mvn -f backend/pom.xml "-Dtest=QuickSampleApplyTest,SampleControllerTest" test`
  - 结果：`82 tests / 0 failures / 0 errors`
- 前端 targeted 测试：
  - 命令：`npm exec vitest run src/views/product/components/QuickSampleModal.test.ts`
  - 结果：`3 tests / 0 failures`
- Harness full：
  - 命令：`powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope full -Message "fix: quick sample manual talent fallback"`
  - 结果：`PASS`
  - Evidence：`harness/reports/evidence-20260603-214340.md`
  - real-pre preflight：`runtime/qa/out/real-pre-preflight-20260603-214338`

## real-pre API 业务验证

### 1. 快速寄样申请成功

- 调用身份：`channel_staff`
- 接口：`POST /api/products/44bc3633-e40b-363d-a9f9-d3aaf70c25de/quick-sample`
- 商品：展示中、已入库、分配给 `biz_leader`，且调用前 `product` 主表无对应行
- 达人：`channel_staff` 私海达人 `QA20260531195822_t33_1780228704077`
- 验证备注：`QA_QUICK_SAMPLE_FIX_20260603214435`

响应摘要：

```json
{
  "code": 200,
  "success": true,
  "successCount": 1,
  "failureCount": 0,
  "sampleRequestId": "66d2a162-4672-4293-af1a-fc49f0fcda0a",
  "itemSuccess": true,
  "itemMessage": "抖店外部寄样暂未接通，已创建系统内寄样申请",
  "fallbackType": "LOCAL_FALLBACK"
}
```

说明：V1 不要求外部抖店快速寄样作为必需能力；本次验证的是系统内寄样申请成功落库。

### 2. `sample_request` 落库与状态

只读 SQL 验证：

```text
id                                   | 66d2a162-4672-4293-af1a-fc49f0fcda0a
request_no                           | QS20260603218FA1A4
status                               | 1
apply_source                         | LOCAL_FALLBACK
product_id                           | 44bc3633-e40b-363d-a9f9-d3aaf70c25de
external_product_id                  | 3784565763113877972
talent_uid                           | QA20260531195822_t33_1780228704077
channel_user_id                      | 33e8493b-c964-4220-b4a6-68ac216c6111
assignee_username                    | biz_leader
remark                               | 规格: QA验证规格；QA_QUICK_SAMPLE_FIX_20260603214435
```

结论：`sample_request` 已落库，状态码 `1`，按当前寄样域契约对应 `PENDING_AUDIT` / 待审核。

### 3. 合作管理 / 招商审核列表可见

接口：`GET /api/samples?status=PENDING_AUDIT&requestNo=QS20260603218FA1A4&size=20`

结果：

```text
admin      total=1 matchedCount=1
biz_leader total=1 matchedCount=1
biz_staff  total=0 matchedCount=0
```

解释：该商品的 `product_operation_state.assignee_id` 是 `biz_leader`，因此管理员和被分配的招商主管可见；未分配该商品的 `biz_staff` 不可见，符合当前数据权限边界。

### 4. 失败原因明确

用不存在的达人调用同一快速寄样接口，响应摘要：

```json
{
  "code": 200,
  "success": false,
  "successCount": 0,
  "failureCount": 1,
  "itemSuccess": false,
  "itemMessage": "达人不存在"
}
```

结论：失败时后端返回了具体原因；前端已改为展示 `items[].message`，不会只显示“申请失败”。

## 结论

本地 real-pre 验证为 `PASS`：

- 渠道从商品库快速寄样入口申请成功。
- `sample_request` 已落库。
- 状态码为 `1`，对应寄样域 `PENDING_AUDIT` / 待审核。
- 管理员和商品分配的招商负责人审核列表可见。
- 失败原因可从后端明细返回，并由前端展示。

## 剩余风险

- 本次未执行远端 real-pre 部署。
- 本次真实 API 验证留下 1 条带 `QA_QUICK_SAMPLE_FIX_20260603214435` 备注的 real-pre 寄样记录，未清理，用作可追溯证据。
- `npm audit` 仍提示 2 个 critical 漏洞；本任务未处理依赖安全升级。
