# Batch D 审查报告：配置与缓存

审查日期：2026-05-24
审查批次：D（配置与缓存）
对照文档：
- `docs/archive/audits/17-V1全域代码审查清单.md` 配置与缓存相关项
- `docs/04-上线验收清单.md` P0-3（配置可追溯、规则影响业务）、E2E-CONFIG-01

---

## 一、审查范围与方法

### 代码锚点

| 文件 | 行数 | 核心职责 |
|------|------|----------|
| `SysConfigController.java` | 99 | 系统配置 CRUD API，角色守卫 |
| `SysConfigService.java` | 392 | 配置写入、校验、缓存失效、操作日志、变更日志 |
| `ConfigDefinitionRegistry.java` | 283 | 16 个配置定义，类型/范围校验器 |
| `BusinessRuleConfigService.java` | 225 | 配置读取，ShortTtlCache 缓存，5 分钟 TTL |
| `ShortTtlCacheService.java` | 108 | ConcurrentHashMap 本地缓存，TTL/容量管理，Redis 前缀驱逐 |
| `ShortTtlCacheRedisInvalidationConfig.java` | 32 | Redis 订阅前缀驱逐消息 |
| `ConfigChangedCacheInvalidationListener.java` | 30 | 本地缓存失效（ApplicationEvent） |
| `ConfigChangedEventRouter.java` | 69 | 跨实例事件路由，幂等消费日志 |
| `ConfigChangedEventConsumer.java` | 12 | 跨实例消费接口 |
| `ConfigChangedEventFactory.java` | 105 | 配置变更事件工厂，附加 consumerDomain |
| `TalentConfigChangedConsumer.java` | 41 | 达人域跨实例缓存失效 |
| `SampleConfigChangedConsumer.java` | 43 | 寄样域跨实例缓存失效 |
| `PerformanceConfigChangedConsumer.java` | 42 | 绩效域跨实例缓存失效 |
| `ProductConfigChangedConsumer.java` | 42 | 产品域跨实例缓存失效 |
| `UserConfigChangedConsumer.java` | 40 | 用户域跨实例缓存失效 |
| `RuleCenterController.java` | 108 | 规则中心 API，schema/validate/save/history |
| `RuleCenterSchemaRegistry.java` | 243 | 6 组 16 项规则定义 |
| `SystemConfigKeys.java` | 28 | 16 个配置键常量 |
| `ConfigConsumerDomain.java` | 19 | 5 个消费域枚举 |
| `RuntimeExposurePolicy.java` | 85 | 生产环境 URL 暴露策略 |
| `RuntimeExposurePolicyTest.java` | 57 | 暴露策略单元测试，4 个测试方法 |

### 方法

按 `17-V1全域代码审查清单` 配置与缓存相关项逐项断言，对照当前代码实际行为输出"符合/矛盾/待补证"判定。重点关注：
- 配置写入后缓存失效机制（本地 + 跨实例）
- 配置变更可追溯性
- 默认值与文档一致性
- 生产环境安全暴露策略

---

## 二、逐项审查结果

### 2.1 配置写入权限

**文档期望**：配置修改仅限管理员角色

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| SysConfigController 类级 `@RequireRoles({ADMIN})` | `SysConfigController.java` | 符合 |
| RuleCenterController 类级 `@RequireRoles({ADMIN})` | `RuleCenterController.java` | 符合 |
| 读取端点开放更多角色 | `SysConfigController`: CHANNEL_LEADER/BIZ_STAFF/OPS_STAFF 可读 | 符合（读写分离合理） |

**判定**：**全部符合**

---

### 2.2 配置值校验（类型/范围）

**文档期望**：配置写入时进行类型和范围校验

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 类型校验 | `ConfigDefinitionRegistry`: 支持 INTEGER, DECIMAL, BOOLEAN, JSON, STRING | 符合 |
| 范围校验 | 每个定义附带 `RangeValidator`，如 `SAMPLE_RESTRICT_DAYS` 范围 [1, 90] | 符合 |
| 校验入口 | `SysConfigService.validateValue()` → `definition.validate(value)` | 符合 |
| RuleCenter 校验 | `RuleCenterController.POST /validate` → 同一校验链 | 符合 |
| 佣金警告 | 招商 + 渠道比例 > 1.0 时返回 WARNING（不阻断） | 符合 |
| 敏感字段标记 | `ConfigDefinition.sensitive = true` 标记敏感配置 | 符合 |
| 敏感字段脱敏 | 读取时 `maskedValue` 替换真实值 | 符合 |

**判定**：**全部符合**

---

### 2.3 配置变更可追溯性

**文档期望**（清单 P0-3）：配置变更可追溯，规则影响业务行为

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 操作日志 | `SysConfigService.save()`: `operationLogService.recordAction(SYSTEM_CONFIG, "UPDATE", ...)` | 符合 |
| 变更日志表 | `SysConfigService.save()`: `systemConfigChangeLogMapper.insert(...)` 记录 oldValue/newValue/operatorId | 符合 |
| 版本追踪 | `SysConfigService.save()`: `version = COALESCE(version, 0) + 1` | 符合 |
| 规则中心变更日志 | `RuleCenterController.GET /change-logs` | 符合 |
| 变更历史查询 | `GET /api/sys-config/logs?key=xxx` | 符合 |
| V1 简化对齐 | 验收清单："配置变更历史: 可查最近变更即可" | 符合 |

**判定**：**全部符合**

---

### 2.4 缓存失效机制（本地）

**文档期望**：配置更新后缓存失效

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 写入触发事件 | `SysConfigService.publishConfigChanged()`: 发布 `ApplicationEvent` | 符合 |
| 本地监听器 | `ConfigChangedCacheInvalidationListener`: AFTER_COMMIT 触发，逐 key 失效 | 符合 |
| 失效路径 | `listener` → `businessRuleConfigService.invalidate(key)` → `shortTtlCacheService.evict(key)` → ConcurrentHashMap remove | 符合 |
| RuleCenter 同路径 | `RuleCenterService.saveGroup()` → `sysConfigService.publishConfigChanged()` | 符合 |

**判定**：**全部符合**

---

### 2.5 缓存失效机制（跨实例）

**文档期望**：多实例部署时配置变更传播到所有实例

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| DB Outbox 写入 | `SysConfigService.publishConfigChanged()` → `domainEventOutboxService.saveConfigChangedEvent()` | 符合 |
| Outbox 轮询 | 各实例独立轮询 outbox 表 | 符合 |
| 事件路由 | `ConfigChangedEventRouter.dispatch()` → 按 consumerDomain 分发 | 符合 |
| 幂等消费 | `ConfigChangedEventRouter`: consume 日志表防止重复消费 | 符合 |
| 5 个域消费者 | SAMPLE, TALENT, PERFORMANCE, PRODUCT, USER | 符合 |
| 消费者模式统一 | 所有消费者：`supports()` → 检查 consumerDomain 匹配 → `consume()` → 逐 key invalidate | 符合 |

**覆盖矩阵**（SystemConfigKeys 全 16 键 vs 消费者）：

| 配置键 | 消费者 | 覆盖 |
|--------|--------|------|
| `sample.restrict_days` | SampleConfigChangedConsumer | 符合 |
| `sample.restrict_enabled` | SampleConfigChangedConsumer | 符合 |
| `sample.timeout_homework_days` | SampleConfigChangedConsumer | 符合 |
| `sample.timeout_pending_ship_days` | SampleConfigChangedConsumer | 符合 |
| `sample.default_standard` | SampleConfigChangedConsumer + ProductConfigChangedConsumer | 符合 |
| `talent.protection_days` | TalentConfigChangedConsumer | 符合 |
| `talent.exclusive_ratio` | TalentConfigChangedConsumer | 符合 |
| `talent.exclusive_monthly_samples` | TalentConfigChangedConsumer | 符合 |
| **`talent.preset_tags`** | **无消费者处理** | **矛盾** |
| `commission.business_default_ratio` | PerformanceConfigChangedConsumer | 符合 |
| `commission.channel_default_ratio` | PerformanceConfigChangedConsumer | 符合 |
| `merchant.exclusive_service_fee_ratio` | PerformanceConfigChangedConsumer | 符合 |
| `promotion.copy_brief_template` | ProductConfigChangedConsumer | 符合 |
| `promotion.pick_extra_rule` | ProductConfigChangedConsumer | 符合 |
| `auth.login_max_failures` | UserConfigChangedConsumer | 符合 |
| `auth.login_lock_minutes` | UserConfigChangedConsumer | 符合 |

**判定**：**部分矛盾（MEDIUM）**

---

### 2.6 PRESET_TALENT_TAGS 跨实例失效缺口（D-1）

**代码事实**：

- `PRESET_TALENT_TAGS = "talent.preset_tags"` 定义于 `SystemConfigKeys.java`
- `RuleCenterSchemaRegistry` 注册在 "talent" 组，`consumerDomain = [TALENT]`
- `TalentConfigChangedConsumer.KEYS` 仅包含：
  - `TALENT_PROTECTION_DAYS`
  - `TALENT_EXCLUSIVE_RATIO`
  - `TALENT_EXCLUSIVE_MONTHLY_SAMPLES`
- **不包含** `PRESET_TALENT_TAGS`

**影响分析**：
- **本地实例**：变更后立即失效（通过 `ConfigChangedCacheInvalidationListener`），无影响
- **其他实例**：outbox 事件会路由到 `TalentConfigChangedConsumer`，`supports()` 返回 true（因为事件包含 TALENT 域 key），但 `consume()` 只 invalidate 已知的 3 个 key，**遗漏 `PRESET_TALENT_TAGS`**
- **实际影响**：`PRESET_TALENT_TAGS` 的跨实例缓存失效需等待 5 分钟 TTL 自然过期
- **严重度评估**：V1 阶段 preset_tags 变更频率极低（预设达人标签为初始化配置），5 分钟延迟不构成业务风险。**MEDIUM**

---

### 2.7 短期缓存服务

**文档期望**：缓存有 TTL 和容量控制

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| TTL 控制 | `ShortTtlCacheService`: 默认 5 分钟 | 符合 |
| 容量控制 | `maxEntries = 1024`，超限 evict oldest | 符合 |
| Redis 前缀驱逐 | `evictByPrefix()` 发布 Redis pub/sub → `ShortTtlCacheRedisInvalidationConfig` 订阅 | 符合 |
| 配置读取路径 | `BusinessRuleConfigService.getConfigValue(key)` → cache miss → DB 查询 → cache put | 符合 |
| 默认值 | DB 无值时返回代码内置默认值 | 符合 |

**判定**：**全部符合**

---

### 2.8 配置默认值与文档一致性

**文档期望**（验收清单 E2E-CONFIG-01）：`sample.restrict_days=7`, `sample.restrict_enabled=true`, `talent.protection_days=30`

**代码事实**（`BusinessRuleConfigService` 默认值）：

| 配置键 | 代码默认值 | 文档期望 | 结论 |
|--------|-----------|----------|------|
| `sample.restrict_days` | 7 | 7 | 符合 |
| `sample.restrict_enabled` | true | true | 符合 |
| `talent.protection_days` | 30 | 30 | 符合 |
| `sample.timeout_homework_days` | 30 | — | 合理 |
| `sample.timeout_pending_ship_days` | 15 | — | 合理 |
| `auth.login_max_failures` | 5 | — | 合理 |
| `auth.login_lock_minutes` | 15 | — | 合理 |

**判定**：**全部符合**

---

### 2.9 生产环境安全暴露策略

**文档期望**：生产环境隐藏调试端点（swagger、env）

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 生产环境隐藏 swagger | `RuntimeExposurePolicy.publicSecurityPatterns()`: prod 时不添加 DOC_PUBLIC_PATTERNS | 符合 |
| 生产环境隐藏 /system/env | `RuntimeExposurePolicy.publicSecurityPatterns()`: prod 时不添加 ENV_PUBLIC_PATTERNS | 符合 |
| health 端点始终公开 | `BASE_PUBLIC_PATTERNS` 包含 `/system/health`, `/api/system/health` | 符合 |
| OAuth 回调始终公开 | `BASE_PUBLIC_PATTERNS` 包含 `/douyin/oauth/callback` | 符合 |
| env 端点需 admin | `requiresAdminForSystemEnv()`: prod 环境返回 true | 符合 |

**测试覆盖**：

| 测试 | 覆盖场景 | 结论 |
|------|----------|------|
| `shouldBypassAuthentication_shouldAllowDouyinOAuthCallback` | OAuth 回调路径（real-pre） | 符合 |
| `shouldBypassAuthentication_shouldKeepDocsAndSystemEnvPublicOutsideProdOnly` | real-pre 环境 swagger/env 公开；prod 环境拒绝 | 符合 |
| `shouldBypassAuthentication_shouldKeepHealthAndOAuthCallbackPublicInProd` | prod 环境 health + OAuth 公开 | 符合 |
| `shouldBypassAuthentication_shouldRejectBlankAndUnknownPaths` | null/空白/未知路径拒绝 | 符合 |

**判定**：**全部符合（含测试覆盖）**

---

### 2.10 规则中心 Schema 与配置注册一致性

**文档期望**：规则中心展示的配置项与系统配置键一一对应

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| Schema 注册 6 组 16 项 | `RuleCenterSchemaRegistry`: sample(5), talent(4), exclusive(1), commission(3), promotion(2), security(2) = 17 项 | **注意** |
| SystemConfigKeys 16 键 | `SystemConfigKeys.java` | 符合 |
| `SAMPLE_DEFAULT_STANDARD` 双域注册 | Sample 组 + Product 组均包含，消费者也双覆盖 | 符合（有意设计） |
| V2 预留项 `enabled=false` | `SAMPLE_TIMEOUT_HOMEWORK_DAYS`, `TALENT_EXCLUSIVE_RATIO`, `TALENT_EXCLUSIVE_MONTHLY_SAMPLES`, `MERCHANT_EXCLUSIVE_SERVICE_FEE_RATIO` | 符合 |
| 消费域映射 | 每项注册 `consumerDomain`，与 5 个消费者对应 | 符合（除 D-1 缺口） |

**判定**：**符合**

---

### 2.11 配置变更事件工厂

**文档期望**：配置变更事件携带正确的消费域信息

**代码事实**：

| 断言 | 代码位置 | 结论 |
|------|----------|------|
| 事件创建 | `ConfigChangedEventFactory.createEvent()` → 遍历变更 key，查询 `RuleCenterSchemaRegistry` 获取 consumerDomain | 符合 |
| 域聚合 | 相同 consumerDomain 的 key 聚合为同一事件 payload | 符合 |
| 数据库持久化 | `domainEventOutboxService.save(event)` | 符合 |
| 变更来源标记 | `CHANGE_SOURCE_API = "SYS_CONFIG_API"`, `CHANGE_SOURCE_RULE_CENTER = "RULE_CENTER"` | 符合 |

**判定**：**全部符合**

---

## 三、矛盾汇总与风险评级

| # | 矛盾项 | 严重度 | 审查清单断言 | 代码实际行为 |
|---|--------|--------|-------------|-------------|
| D-1 | **PRESET_TALENT_TAGS 跨实例缓存失效遗漏** | **MEDIUM** | 所有配置键变更应传播到所有实例 | `TalentConfigChangedConsumer.KEYS` 不含 `PRESET_TALENT_TAGS`，跨实例失效依赖 5 分钟 TTL 自然过期 |

---

## 四、建议

### MEDIUM — D-1：PRESET_TALENT_TAGS 跨实例缓存失效遗漏

**当前状态**：`TalentConfigChangedConsumer` 仅处理 3 个达人域 key（`TALENT_PROTECTION_DAYS`, `TALENT_EXCLUSIVE_RATIO`, `TALENT_EXCLUSIVE_MONTHLY_SAMPLES`），遗漏 `PRESET_TALENT_TAGS`。本地实例通过 `ConfigChangedCacheInvalidationListener` 正常失效，但其他实例需等 5 分钟 TTL 过期。

**建议**（二选一）：

1. **选项 A（推荐）**：在 `TalentConfigChangedConsumer.KEYS` 中补充 `PRESET_TALENT_TAGS`：
   ```java
   private static final Set<String> KEYS = Set.of(
           SystemConfigKeys.TALENT_PROTECTION_DAYS,
           SystemConfigKeys.TALENT_EXCLUSIVE_RATIO,
           SystemConfigKeys.TALENT_EXCLUSIVE_MONTHLY_SAMPLES,
           SystemConfigKeys.PRESET_TALENT_TAGS);
   ```
   侵入最小，一行改动。

2. **选项 B**：将 `ConfigChangedEventConsumer` 改为通用实现，根据事件 payload 中的 key 集合直接 invalidate，无需每个消费者维护 KEYS 集合。但需评估是否所有 key 都走同一失效路径。

**影响评估**：V1 阶段 `preset_tags` 为初始化配置，变更频率极低，5 分钟延迟不构成业务风险。建议在 V2 启用达人标签管理功能前修复。

---

## 五、通过项汇总

| 审查项 | 结论 | 备注 |
|--------|------|------|
| 配置写入仅限管理员 | **通过** | SysConfig + RuleCenter 双 Controller |
| 配置值类型/范围校验 | **通过** | ConfigDefinitionRegistry 16 项定义 |
| 配置变更操作日志 | **通过** | SysConfigService.operationLogService |
| 配置变更审计日志 | **通过** | system_config_change_log 表 |
| 配置版本追踪 | **通过** | version 字段自增 |
| 敏感字段脱敏 | **通过** | sensitive flag + maskedValue |
| 本地缓存即时失效 | **通过** | ApplicationEvent + AFTER_COMMIT |
| 跨实例缓存传播 | **通过（除 D-1）** | DB Outbox + 5 域消费者 |
| 缓存 TTL 控制 | **通过** | ShortTtlCacheService 5 分钟 |
| 缓存容量控制 | **通过** | maxEntries 1024 |
| 配置默认值一致 | **通过** | 7 个默认值与文档对齐 |
| 生产环境隐藏调试端点 | **通过** | RuntimeExposurePolicy |
| 生产环境隐藏策略有测试 | **通过** | 4 个测试方法 |
| 规则中心 Schema 一致 | **通过** | 6 组 16+1 项 |
| 规则中心委托 SysConfig | **通过** | saveGroup → sysConfigService |
| 佣金比例警告 | **通过** | 招商 + 渠道 > 1.0 返回 WARNING |
| V2 预留项 disabled | **通过** | 4 项 enabled=false |
| 变更事件工厂域映射 | **通过** | RuleCenterSchemaRegistry 查询 consumerDomain |
| 幂等消费防重 | **通过** | consume 日志表 |
| Redis 前缀驱逐 | **通过** | pub/sub + listener |

---

## 六、后续批次预告

| 批次 | 目标 | 状态 |
|------|------|------|
| A | 权限与数据范围防回归 | 已完成（2026-05-22） |
| B | 订单归因与金额双轨 | 已完成（2026-05-24） |
| C | 达人/寄样状态机 | 已完成（2026-05-24） |
| **D** | **配置与缓存** | **本报告** |
| E | 性能与可维护性 | 待执行 |
