# DDD-PRODUCT-002 — ProductDisplayPolicy

**时间**: 2026-06-10  
**环境**: local / `mvn test`  
**分支**: `feature/auth-system`  
**基线 commit**: `946c7b49`（提交前）

## 目标

将商品库展示去重规则从 `ProductDisplayRuleService` 抽成纯策略 `ProductDisplayPolicy`，保持同一 `productId` 下展示结果与当前行为一致。

## 交付物

| 类型 | 路径 |
|------|------|
| 策略输入 | `domain/product/policy/ProductDisplayRelationInput.java` |
| 策略输出 | `domain/product/policy/ProductDisplayPolicyResult.java` |
| 纯策略 | `domain/product/policy/ProductDisplayPolicy.java` |
| 单元测试 | `test/.../policy/ProductDisplayPolicyTest.java`（10 场景） |
| 委派入口 | `ProductDisplayRuleService.applyNormalDisplayDedup` → `productDisplayPolicy.decide` |
| 列表排序片段 | `ProductService.compareByPromotionCommissionTime` → `compareLibraryPresentation`（不含置顶） |

## 规则覆盖（单元测试）

1. 0 条可展示 → 全部隐藏  
2. 1 条可展示 → 展示该条  
3. 多条 → 投流优先  
4. 多条 → 佣金更高优先  
5. 多条 → 服务费率更低优先  
6. 多条 → 晚上架优先  
7. 保护期内不切换  
8. 保护期内优势覆盖切换  
9. 活动过期隐藏  
10. 活动延期恢复后重新判断  

## 构建与测试

### 商品定向测试（`mvn clean test -Dtest=...`）

| 套件 | 结果 |
|------|------|
| `ProductDisplayPolicyTest` | PASS (10/10) |
| `ProductDisplayRuleServiceTest` | PASS (31/31) |
| `ProductServiceLibraryViewTest` | PASS (3/3) |
| `ProductServiceFilterTest` | PASS |
| `ProductServiceActivityAssignTest` | PASS |
| `ProductServiceColonelBuyinIdTest` | PASS |
| `ProductServiceShopScoreTest` | PASS |
| `ProductServiceActivityStatusIndependenceTest` | PASS |
| `CharacterizationBaselineTest#test02_ProductLibraryBaseline` | PASS（clean 定向跑） |

### 全量后端（`mvn test`）

- **结论**: PARTIAL  
- `Tests run: 1877, Failures: 4, Errors: 62`  
- 商品展示相关套件全部 PASS；失败集中在 Spring 上下文加载（`CharacterizationBaselineTest` 等集成基线）及仓库内其他并行 WIP（如 `SampleApplicationPort` / `ProductQuickSampleService` 未纳入本任务提交）。  
- 已知历史项：`SysConfigServiceEventTest` 集成依赖 Docker/schema。

## 未改动（按任务约束）

- 商品库 API 响应结构  
- 商品同步逻辑  
- 置顶逻辑（`compareLibraryProducts` 置顶分支保留在 `ProductService`）  
- 未一次性重写 `ProductService`

## 剩余风险

1. `ProductDisplayRuleService` 仍负责持久化、审计、领域事件；策略仅负责决策。  
2. 列表二次排序仅委派投流/佣金/时间片段；置顶仍在服务层。  
3. 工作区存在其他未提交 WIP（`ProductQuickSampleService`、sample port 等），全量 CI 需与这些变更隔离后再验。

## 结论

**PARTIAL（任务范围内 PASS）** — 展示去重策略抽取完成，定向商品测试与 `ProductDisplayRuleServiceTest` 全绿；全量套件受仓库并行 WIP 与既有集成错误影响，不记为本任务回归失败。
