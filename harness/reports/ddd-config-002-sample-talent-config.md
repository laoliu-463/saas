# DDD-CONFIG-002：寄样/达人配置改走 ConfigDomainFacade

- **时间**：2026-06-10
- **环境**：本地 backend 单元测试（未重启 real-pre）
- **分支**：`feature/auth-system`
- **任务**：寄样限制、寄样超时关闭、达人保护期、独家达人阈值统一从 `ConfigDomainFacade` 读取

## 配置键映射

| 任务键 | system_config 键 | Facade 方法 | 默认值 |
|--------|------------------|-------------|--------|
| `sample_limit_days` | `sample.restrict_days` | `getSampleLimitDays()` | 7 |
| `sample_limit_enabled` | `sample.restrict_enabled` | `isSampleLimitEnabled()` | true |
| `sample_auto_close_days` | `sample.timeout_homework_days` | `getSampleAutoCloseDays()` | 30 |
| `talent_claim_protect_days` | `talent.protection_days` | `getTalentClaimProtectDays()` | 30 |
| `exclusive_talent_fee_ratio` | `talent.exclusive.service_fee_ratio` | `getExclusiveTalentFeeRatio()` | 70 |
| `exclusive_talent_monthly_samples` | `talent.exclusive.monthly_samples` | `getExclusiveTalentMonthlySamples()` | 10 |

## 改造范围

### 新增

- `com.colonel.saas.domain.config.facade.ConfigDomainFacade`（含 CONFIG-002 核心阈值 + CONFIG-001 通用只读入口）
- `com.colonel.saas.domain.config.facade.LegacyConfigDomainFacade`（委派 `BusinessRuleConfigService`）
- `LegacyConfigDomainFacadeTest`（6 项契约：7 天限制、关闭限制、自动关闭、保护期 30、独家阈值、缺失 fallback）

### 寄样域（改为注入 `ConfigDomainFacade`）

- `SampleApplicationService` — 重复申请限制
- `ProductQuickSampleService` — 快速寄样重复限制
- `SampleLifecycleService` — 待交作业自动关闭（`getSampleAutoCloseDays`）；待发货超时仍走 `BusinessRuleConfigService`（非本任务 3 键范围）
- `SampleController` — 构造器传参对齐

### 达人域

- `TalentService` — 保护期、独家判定阈值（预设标签仍用 `BusinessRuleConfigService`）
- `ExclusiveTalentService` — 移除 JDBC 直查 `system_config`，改走 Facade
- `OrderSyncedEventListener` — 订单同步后重置保护期

### 禁止项核对

- 未改寄样限制 / 保护期 / 独家判定业务规则
- 寄样/达人消费方未新增 `SystemConfigMapper` 注入
- `ExclusiveTalentService` 已删除 `loadDecimalConfig` / `loadIntConfig` 直查 SQL

## 测试证据

定向测试（2026-06-10 本地）：

```text
mvn test -Dtest=LegacyConfigDomainFacadeTest,QuickSampleApplyTest,SampleLifecycleServiceTest,TalentServiceTest,ExclusiveTalentServiceTest,BusinessRuleConfigServiceTest,SampleControllerTest
```

结果：**PASS**（exit 0）

全量 `mvn test`：存在既有 Mockito 内联 mock / 环境类加载问题（与本次 Facade 改造无直接关联），未作为本任务 PASS 依据。

## 结论

- **CONFIG-002 改造**：`PARTIAL` → 寄样/达人 6 项配置已统一经 `ConfigDomainFacade`；`SampleLifecycleService` 待发货超时键未纳入本任务 3 键清单，仍保留旧服务读取。
- **行为**：委派 `BusinessRuleConfigService`，默认值与异常回退与改造前一致。
- **剩余风险**：全量 CI 需单独复跑确认无环境性 Mockito 失败；real-pre 未做容器重启与健康检查（纯重构 + 单测）。
