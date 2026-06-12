# DDD-CONFIG-002: 寄样域和达人域配置读取改走 ConfigDomainFacade

## 概要

| 项目 | 值 |
|------|-----|
| 任务编号 | DDD-CONFIG-002 |
| 分支 | feature/auth-system |
| 日期 | 2026-06-10 |
| 结论 | **PASS** |

## 改造范围

### 寄样域
| 配置项 | 门面方法 | 消费者 |
|--------|---------|--------|
| sample_limit_days | `configDomainFacade.getSampleLimitDays()` | ProductQuickSampleService |
| sample_limit_enabled | `configDomainFacade.isSampleLimitEnabled()` | ProductQuickSampleService |
| sample_auto_close_days | `configDomainFacade.getSampleAutoCloseDays()` | SampleLifecycleService |
| sample_timeout_pending_ship_days | `configDomainFacade.getSampleRules().timeoutPendingShipDays()` | SampleLifecycleService |

### 达人域
| 配置项 | 门面方法 | 消费者 |
|--------|---------|--------|
| talent_claim_protect_days | `configDomainFacade.getTalentClaimProtectDays()` | TalentService |
| exclusive_talent_fee_ratio | `configDomainFacade.getExclusiveTalentFeeRatio()` | TalentService |
| exclusive_talent_monthly_samples | `configDomainFacade.getExclusiveTalentMonthlySamples()` | TalentService |

## 变更文件

### 生产代码
| 文件 | 变更 |
|------|------|
| SampleLifecycleService.java | 移除 BusinessRuleConfigService 依赖，pendingShip 超时改走 `configDomainFacade.getSampleRules().timeoutPendingShipDays()` |

### 测试代码
| 文件 | 变更 |
|------|------|
| DddConfig002SampleTalentConfigTest.java | 新增 10 个测试覆盖寄样/达人配置门面读取 |
| SampleLifecycleServiceTest.java | 移除 BusinessRuleConfigService mock，更新构造函数和 pendingShip 测试 |
| mapper-integration-schema.sql | 补齐 system_config 表缺失的 config_version/enabled/visible_in_rule_center 列 |

## 测试结果

### Targeted Tests
| 测试类 | 通过 | 失败 | 错误 |
|--------|------|------|------|
| DddConfig002SampleTalentConfigTest | 10 | 0 | 0 |
| SampleLifecycleServiceTest | 12 | 0 | 0 |
| LegacyConfigDomainFacadeTest | 22 | 0 | 0 |
| SysConfigServiceTest | 4 | 0 | 0 |
| **合计** | **48** | **0** | **0** |

### 全量测试
| 指标 | 值 |
|------|-----|
| Tests run | 1860 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 1 |

## 禁止项验证

- [x] 寄样限制业务规则未改变（仅改读取来源，规则逻辑不变）
- [x] 达人保护期规则未改变
- [x] 独家达人判定结果未改变
- [x] 未直接查配置 Mapper

## 剩余风险

- DDD-CONFIG-003 生产代码改动（CommissionService/ProductService 等）仍在工作区，未提交。需单独处理。
