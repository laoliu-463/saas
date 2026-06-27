# Evidence: DDD100-PRODUCT-BASELINE (Issue #60) — ProductService Characterization Baseline

## 基本信息

- Time: 2026-06-27 11:31:50 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #60 [DDD100-PRODUCT-BASELINE] ProductService characterization baseline
- 类型: characterization baseline (锁定 W5-W6 拆分前行为)
- 阻塞: #61, #62, #63 (product 域后续拆分)

## 工作范围

### 新增
- `backend/src/test/java/com/colonel/saas/service/ProductServiceCharacterizationTest.java`
  - 7 个 @Test 方法
  - 锁定 5 个 ProductService 公共行为 (happy path + 关键 edge)
  - 2 个 reflection-based signature baseline (验证 25+ public method 存在性)

### 既有 baseline (不重复造轮子)
- `CharacterizationBaselineTest.java` (14 个 @Test, test01-test14):
  - test02_ProductLibraryBaseline
  - test06_DashboardSummaryBaseline
  - test08_OrderListAndActivityProductCharacterizationBaseline
  - test09_OrderDetailCharacterizationBaseline
  - test12_ProductDetailsCharacterizationBaseline
  - test14_DataExportPermissionsCharacterizationBaseline
  - 等等 (14 个端到端 baseline 测试)

- `DddSlimProduct001DisplayPolicyRoutingTest.java` (架构护栏)
- `ProductServicePromotionPortArchitectureTest.java` (Port 验证)
- `DddUserFacadeProductServiceBoundaryTest.java` (边界守护)

## 验证证据

### ProductServiceCharacterizationTest
- mvn test: Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
- Total time: 1:00 min
- jacoco: 1003 classes analyzed

### 7 个 baseline 测试覆盖
1. `listLibraryCategoriesDelegates` - 委托 listDisplayingLibraryCategoryNames
2. `hasActivitySnapshotsTrue` - selectCount > 0 → true (新发现: 实际用 selectCount 不是 countActiveRowsByActivityIds)
3. `hasActivitySnapshotsFalse` - selectCount = 0 → false
4. `getByIdReturnsNull` - 抛 BusinessException "商品不存在" (新发现: 不是返回 null)
5. `getAdminCountsReturnsNonNull` - countActiveRows 委托
6. `publicMethodCountPreserved` - 25+ public method 反射验证
7. `serviceCanBeInstantiated` - 21 依赖构造器顺序保持

## 新发现 (characterization 价值)

写 baseline 时发现 2 个行为细节:
1. **getById 抛 BusinessException 而非 null** — 之前 baseline 写 "返回 null" 错;
   characterization 测试的真正价值是**纠正预期 vs 实际**
2. **hasActivitySnapshots 用 selectCount 不是 countActiveRowsByActivityIds** — 写测试时通过
   失败反馈发现，后续 W5 拆分时要保留此 mapper 行为

## W5-W6 拆分 baseline 守则

后续拆分必须保持:
- 25+ public method 签名不变 (`publicMethodCountPreserved` 守门)
- getById 抛 BusinessException (不返回 null)
- hasActivitySnapshots 行为: selectCount > 0 → true
- listLibraryCategories 委托 listDisplayingLibraryCategoryNames
- getAdminCounts 委托 countActiveRows
- 21 依赖构造器顺序 (Slim 拆解后拆分, 后续 #61-#63 验证)

## 验收

- [x] 行为与现有 API 兼容 (7 个 baseline test PASS)
- [x] 覆盖 parity / targeted / integration 验证路径 (ProductService 直接 + CharacterizationBaselineTest 间接)
- [x] 生成 evidence report (本文件)
- [x] 更新领域状态 (记录到 DOMAIN_STATUS 后续 commit)

## 风险

- 5 个 ProductService public method 实际未单独 mock (依赖复杂 chain)
  - listActivityProductSkus / getActivityProductDetail / putIntoLibrary / recordProductDecision / getOperationLogs
  - 完整测试在 #61-#63 W5 拆分时建
- refreshActivitySnapshots 依赖 ActivityProductPaginationRunner
  - 由 ActivityProductPaginationRunner 自身测试覆盖

## 后续依赖

- #61 Product Sync Application 拆分 (DDD100-PRODUCT-SYNC)
- #62 Product Display Policy 收口 (DDD100-PRODUCT-DISPLAY)
- #63 Product Status 操作日志收口 (DDD100-PRODUCT-STATUS)
- 完成后 #64-#67 (Snapshot / Backfill / Promotion / E2E) 解锁
