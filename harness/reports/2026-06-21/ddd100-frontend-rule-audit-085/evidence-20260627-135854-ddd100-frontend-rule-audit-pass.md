# Evidence: DDD100-FRONTEND-RULE-AUDIT (Issue #85) — 前端不硬编码业务规则审计

## 基本信息

- Time: 2026-06-27 13:58:54 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #85 [DDD100-FRONTEND-RULE-AUDIT] 前端不硬编码业务规则审计
- 类型: 前端架构审计
- 阻塞: #82 / #83 / #84 (前端各域化, 待启动)

## 审计结果 (实测)

### 扫描维度
1. hardcoded status number
2. hardcoded permission string
3. hardcoded role_code string
4. selectAll API 调用

### 结果
| 维度 | 命中数 |
|---|---|
| status number (1=启用) | 11 (query param 默认值, 非业务规则) |
| permission string | 0 |
| role_code string | 0 |
| selectAll | 0 |

### 11 个 status number 详情 (query param 默认值, 非硬编码业务规则)
- product/index.vue: status: 0
- system/CommissionRuleList.vue: status: 1 (启用筛选)
- system/ConfigList.vue: status: 1
- system/DeptList.vue: status: 1
- system/RoleList.vue: status: 1
- system/UserList.vue: status: 1

**结论**: 这些都是 query param 默认值 (用户筛选"启用/禁用"), 不是业务规则硬编码。

## 前端架构现状 (已正确分离)

### 状态/显示分离 (集中管理)
- frontend/src/views/product/activity-list-display.ts
  - ACTIVITY_STATUS_TABS
- frontend/src/views/product/activity-product-status-display.ts
  - OFFICIAL_STATUS_VIEWS
- frontend/src/views/product/product-library-display.ts
  - DISPLAY_STATUS_LABELS
- frontend/src/views/product/product-operation-log-display.ts
  - BIZ_STATUS_LABELS
- frontend/src/views/talent/constants.ts
  - CLAIM_STATUS_OPTIONS / TALENT_CONTACT_STATUS_OPTIONS

### Stores (Pinia)
- frontend/src/stores/

### API client (按域)
- frontend/src/api/

## 验证证据

- vitest run: **657/657 PASS** (87 文件)
- 业务规则测试:
  - activity-list-display.test.ts
  - product-page-data-source.test.ts (8 tests, #26 #27 evidence)
  - product-page-data-source 等

## 与 #82/#83/#84 关系

- #82 DDD100-FRONTEND-BOUNDARY: API client/store 收口 (待启动)
- #83 DDD100-FRONTEND-PRODUCT-DATA: 商品/订单/分析页面领域化 (待启动)
- #84 DDD100-FRONTEND-SAMPLE-TALENT: 寄样/达人页面领域化 (待启动)

## 验收 (当前)

- [x] 扫描 4 个维度 (status/permission/role/selectAll)
- [x] 11 个 status number 是 query 默认值, 非硬编码业务规则
- [x] 0 个 permission/role 硬编码
- [x] 0 个 selectAll 调用
- [x] 状态/显示分离架构已建立
- [x] vitest 657/657 PASS
- [x] 1:1 行为等价 (无业务规则变化)
- [x] PARTIAL (审计通过, 完整 boundary 待 #82-#84)

## 残余风险

### 当前已通过
- 前端业务规则无硬编码
- 状态/显示分离架构

### 待 #82-#84 完善
- API client/store 边界收口
- 商品/订单/分析页面领域化
- 寄样/达人页面领域化
