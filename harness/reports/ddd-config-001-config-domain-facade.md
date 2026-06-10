# DDD-CONFIG-001：新增 ConfigDomainFacade

- **时间**：2026-06-10
- **环境**：本地 backend 单元测试（未重启 real-pre）
- **分支**：`feature/auth-system`
- **基线 commit**：`1addc145`（本任务在其上追加收尾提交）

## 目标

建立配置域对其他领域的统一只读入口。当前由 `LegacyConfigDomainFacade` 委派旧 `BusinessRuleConfigService` / `SysConfigService`，**不改变配置读取结果**。

## 新增接口（`ConfigDomainFacade`）

| # | 方法 | 实现要点 |
|---|------|----------|
| 1 | `getConfig(String key)` | 委派 `SysConfigService.getConfigValue` |
| 2 | `getString(key, default)` | trim；空白/缺失回退 |
| 3 | `getInt(key, default)` | `Integer.parseInt`；异常回退 |
| 4 | `getDecimal(key, default)` | `BigDecimal`；异常回退 |
| 5 | `getBoolean(key, default)` | `true/false/1/0`（大小写不敏感） |
| 6 | `getJson(key, type, default)` | Jackson 反序列化；异常回退 |
| 7 | `getCommissionRates()` | → `CommissionRatesDTO` |
| 8 | `getSampleRules()` | → `SampleRulesDTO`（含 `SampleDefaultStandardDTO`） |
| 9 | `getTalentRules()` | → `TalentRulesDTO` |
| 10 | `getPromotionTemplate()` | → `PromotionTemplateDTO` |
| 11 | `getExclusiveRules()` | → `ExclusiveRulesDTO` |

另含 DDD-CONFIG-002 寄样/达人核心阈值方法（同 Facade，本任务不重复改造消费方）。

## 专用 DTO

| DTO | 路径 |
|-----|------|
| `CommissionRatesDTO` | `domain/config/facade/dto/CommissionRatesDTO.java` |
| `SampleRulesDTO` | `domain/config/facade/dto/SampleRulesDTO.java` |
| `TalentRulesDTO` | `domain/config/facade/dto/TalentRulesDTO.java` |
| `PromotionTemplateDTO` | `domain/config/facade/dto/PromotionTemplateDTO.java` |
| `ExclusiveRulesDTO` | `domain/config/facade/dto/ExclusiveRulesDTO.java` |

## 配置键映射（任务验收命名）

| 任务键 | system_config 键 | 读取方式 | 默认 |
|--------|------------------|----------|------|
| `recruiter_commission_rate` | `commission.business_default_ratio` | `getDecimal` / `getCommissionRates().businessRatio()` | 0.05 |
| `channel_commission_rate` | `commission.channel_default_ratio` | `getDecimal` / `getCommissionRates().channelRatio()` | 0.10 |
| `sample_limit_days` | `sample.restrict_days` | `getInt` / `getSampleRules().restrictDays()` | 7 |
| `sample_limit_enabled` | `sample.restrict_enabled` | `getBoolean` / `getSampleRules().restrictEnabled()` | true |
| `talent_claim_protect_days` | `talent.protection_days` | `getInt` / `getTalentRules().protectionDays()` | 30 |
| 商家独家服务费阈值 | `merchant.exclusive.service_fee_ratio` | `getExclusiveRules().merchantServiceFeeRatio()` | 70 |

## 实现结构

```
ConfigDomainFacade (interface)
    └── LegacyConfigDomainFacade (@Service)
            ├── BusinessRuleConfigService  — 业务规则聚合、寄样/达人阈值
            └── SysConfigService           — 通用 getConfig 原始值
```

本任务收尾变更：

- `LegacyConfigDomainFacade.getExclusiveRules()` 默认回退与 `BusinessRuleConfigService` 对齐为 **70**（非 0.20）
- 移除 `BusinessRuleConfigService` 中重复的 facade bridge 方法（逻辑已集中在 Facade）
- `LegacyConfigDomainFacadeTest` 补充 `SysConfigService` mock 接线，使通用 `getConfig` 路径可测

## 禁止项核对

| 禁止项 | 状态 |
|--------|------|
| 不修改配置表数据 | ✅ 仅新增只读 Facade |
| 不改变默认配置值 | ✅ 与旧服务默认值一致 |
| 不替换业务域调用 | ✅ 本提交未改 `CommissionService` / `ProductService` 等消费方 |
| 不直接新增业务规则 | ✅ 无新规则逻辑 |

## 测试证据

### 定向测试（PASS）

```text
mvn test -Dtest=LegacyConfigDomainFacadeTest,BusinessRuleConfigServiceTest
```

覆盖项：

- string / int / decimal / bool / json 解析
- 配置缺失 fallback
- 非法值安全 fallback
- `recruiter_commission_rate` / `channel_commission_rate` 可读
- `sample_limit_days` / `sample_limit_enabled` 可读
- `talent_claim_protect_days` 可读
- 聚合 DTO：`getCommissionRates` / `getSampleRules` / `getTalentRules` / `getPromotionTemplate` / `getExclusiveRules`

### 全量测试（PARTIAL）

```text
mvn test
```

结果：`Tests run: 1858, Failures: 0, Errors: 2, Skipped: 1`

阻塞项（与 DDD-CONFIG-001 无直接关联）：

- `SysConfigServiceEventTest`：`BaseIntegrationTest` 无法解析（既有集成测试基类问题，见 DDD-CONFIG-004 范围）

## 结论

- **DDD-CONFIG-001**：`PASS` — Facade 接口、Legacy 实现、DTO、契约测试齐备；业务域调用未在本提交替换。
- **剩余风险**：全量 CI 仍有 2 个集成测试编译/加载错误；real-pre 未做容器重启（纯 Facade + 单测范围）。
