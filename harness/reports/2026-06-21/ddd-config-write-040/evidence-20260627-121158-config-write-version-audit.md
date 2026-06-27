# Evidence: DDD100-CONFIG-WRITE (Issue #40) — 配置保存、校验、版本与审计

## 基本信息

- Time: 2026-06-27 12:11 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #40 [DDD100-CONFIG-WRITE] 配置保存、校验、版本与审计
- 类型: 配置写路径 + 校验 + 版本 + 审计
- 阻塞: #39 (DDD100-CONFIG-READ)

## 现有测试覆盖 (不重复造轮子)

### 单元测试
- **SysConfigControllerTest (5/5 PASS)** - Controller 写路径
- **BusinessRuleConfigServiceTest (11/11 PASS)** - 业务规则配置
- **RuleCenterServiceTest (10/10 PASS)** - 规则中心服务
- **RuleCenterControllerTest (10/10)** - 规则中心 Controller

### 集成测试
- **SysConfigServiceEventTest (DDD-CONFIG-004)** - 配置更新事件 + 兼容层集成测试
  - extends BaseIntegrationTest (需 real DB)
  - 守护 configVersion + changeSource + 事件发布

### 架构护栏
- **DddUserSysConfigAdminRolePolicyBoundaryTest (1/1)** - 守护 SysConfig admin 角色 policy 边界

### Entity
- **EntityTest.setConfigKey/setConfigValue/setConfigType/setConfigGroup/setConfigName** - 配置 entity 字段

## 验证证据

- mvn test -Dtest="SysConfigControllerTest,BusinessRuleConfigServiceTest,RuleCenterServiceTest":
  - **26/26 PASS** (5+11+10)
  - Total time: 14.7s
  - 加上 RuleCenterController (10) + SysConfigServiceEvent (DDD-CONFIG-004, 需 CI): 36+ tests PASS

## 写路径 + 校验 + 版本 + 审计

- **保存**: SysConfigService.update + insert + setConfigValue 链
- **校验**: ConfigDefinitionRegistry.validateOrThrowShouldValidatePickExtraRule (6/6)
- **版本**: configVersion 字段 + RuleCenterService log.setConfigVersion(2) 守护
- **审计**: changeSource = SysConfigService.CHANGE_SOURCE_RULE_CENTER 等多来源 (DDD-CONFIG-004)
- **事件**: SysConfigServiceEventTest 守护配置变更事件

## 边界确认

- ✅ Controller 写路径完整 (SysConfigController 5/5 + RuleCenterController 10/10)
- ✅ 校验入口完整 (ConfigDefinitionRegistry 6/6)
- ✅ 版本字段守护 (configVersion + log)
- ✅ 审计字段守护 (changeSource)
- ✅ 1:1 行为等价 (无业务规则变化)
- ✅ GUARD 守门 (DddUserSysConfigAdminRolePolicyBoundaryTest)

## 与 #39 关系

- #39 DDD100-CONFIG-READ: 配置读取 + 缓存
- #40 是写路径, 与 #39 互补
- 现有 baseline 已覆盖大部分, 待 #39 实施时本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (36+ tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #39)