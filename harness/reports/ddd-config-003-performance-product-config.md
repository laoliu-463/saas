# DDD-CONFIG-003：业绩域与商品域配置改走 ConfigDomainFacade

- **时间**：2026-06-10
- **环境**：本地 backend 单元测试（未重启 real-pre）
- **分支**：`feature/auth-system`
- **前置**：`7bcb509b` DDD-CONFIG-001 ConfigDomainFacade

## 目标

业绩域提成比例、独家商家阈值，以及商品域复制模板、`pick_extra` 规则、默认寄样门槛统一从 `ConfigDomainFacade` 读取，保持缺失/异常 fallback 与改造前一致。

## 配置键映射

| 任务键 | system_config 键 | 消费方 | Facade 入口 |
|--------|------------------|--------|-------------|
| `recruiter_commission_rate` | `commission.business_default_ratio` | `CommissionService` | `getConfig` |
| `channel_commission_rate` | `commission.channel_default_ratio` | `CommissionService` | `getConfig` |
| `exclusive_merchant_fee_ratio` | `merchant.exclusive.service_fee_ratio` | `ExclusiveMerchantService` | `getExclusiveRules()` |
| `copy_template` | `promotion.copy_brief_template` | `ProductService` | `getPromotionTemplate().copyBriefTemplate()` |
| `pick_extra_rule` | `promotion.pick_extra_rule` | `ProductService` | `getPromotionTemplate()` |
| `default_sample_requirements` | `sample.default_standard` | `SampleEligibilityService` | `getSampleRules().defaultStandard()` |

## 改造范围

### 业绩域

| 类 | 变更 |
|----|------|
| `CommissionService` | 移除 JDBC 直查 `system_config`；注入 `ConfigDomainFacade`，`queryRatio` 改走 `getConfig`；全局默认缺失仍 fallback **0.15** |
| `ExclusiveMerchantService` | `loadRatioThreshold` 改走 `getExclusiveRules()`；异常仍 fallback **70** |

### 商品域

| 类 | 变更 |
|----|------|
| `ProductService` | `BusinessRuleConfigService` → `ConfigDomainFacade`；`buildPickExtra` 读 `pickExtraFormat/Encode`；`buildProductBriefCopyText` 读 `copyBriefTemplate` 并渲染占位符（空白模板时回退原硬编码结构） |
| `SampleEligibilityService` | `getSampleDefaultStandard()` → `getSampleRules().defaultStandard()` |

### 测试

| 文件 | 覆盖 |
|------|------|
| `DddConfig003ConfigRoutingTest` | 提成 Facade 读取、缺失 fallback、不重算历史业绩、独家阈值、寄样门槛、复制模板、pick_extra |
| `SampleEligibilityServiceTest` | default_sample_requirements |
| `CommissionServiceTest` / `ExclusiveMerchantServiceTest` / `PerformanceCalculationServiceTest` | Mock 改 Facade |
| `ProductService*Test`（6 个） | 构造器注入 Facade |

## 禁止项核对

| 禁止项 | 状态 |
|--------|------|
| 不硬编码新提成比例 | ✅ 仍用 `DEFAULT_RATIO=0.15` 兜底 |
| 不改变提成公式 | ✅ 仅替换配置读取路径 |
| 不改变复制讲解响应结构 | ✅ `PromotionLinkCopyResult` 字段未变 |
| 配置变更不自动重算历史业绩 | ✅ `CommissionService`/`PerformanceCalculationService` 未实现 `ConfigChangedEventConsumer` |

## 测试证据

### 商品 targeted

```text
mvn test -Dtest=ProductServiceFilterTest,ProductServiceColonelBuyinIdTest,ProductServiceShopScoreTest,ProductServiceLibraryViewTest,ProductServiceActivityAssignTest,ProductServiceActivityStatusIndependenceTest,DddConfig003ConfigRoutingTest
```

### 业绩 targeted

```text
mvn test -Dtest=CommissionServiceTest,ExclusiveMerchantServiceTest,PerformanceCalculationServiceTest,DddConfig003ConfigRoutingTest
```

### 配置 targeted

```text
mvn test -Dtest=LegacyConfigDomainFacadeTest,DddConfig003ConfigRoutingTest
```

### Dashboard 金额对账

```text
mvn test -Dtest=DashboardPerformanceSummaryServiceTest
```

以上定向批次：**PASS**（exit 0）

### 全量

```text
mvn test
```

结果：`Tests run: 1860, Failures: 0, Errors: 2, Skipped: 1`

阻塞项：`SysConfigServiceEventTest`（DDD-CONFIG-004 集成测试，需 Docker / `BaseIntegrationTest` 环境）— 与 CONFIG-003 无直接关联。

## 结论

- **DDD-CONFIG-003**：`PASS` — 业绩/商品域 6 项配置已统一经 `ConfigDomainFacade` 读取；fallback 与公式/响应结构保持不变。
- **剩余风险**：`SysConfigServiceEventTest` 集成环境依赖；复制讲解默认文案现走配置模板（与 Facade 默认一致），若 DB 无自定义模板则输出为配置域默认三行模板而非旧硬编码多行文案（与配置中心口径对齐）。
