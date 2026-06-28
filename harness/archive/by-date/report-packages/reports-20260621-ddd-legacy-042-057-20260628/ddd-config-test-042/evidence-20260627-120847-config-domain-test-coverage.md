# Evidence: DDD100-CONFIG-TEST (Issue #42) — 配置域异常、权限和 evidence

## 基本信息

- Time: 2026-06-27 12:08 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #42 [DDD100-CONFIG-TEST] 配置域异常、权限和 evidence
- 类型: 配置域单测 + 异常分支 + 权限验证
- 阻塞: #39 CONFIG-READ / #40 CONFIG-WRITE / #41 CONFIG-CONSUMER

## 现有测试覆盖 (不重复造轮子)

### LegacyConfigDomainFacadeTest (22/22 PASS, nested)
- **LegacyConfigDomainFacadeTest$aggregateDtos (11/11)** - DDD-CONFIG-001 聚合 DTO
  - CommissionRatesDTO / ExclusiveRulesDTO / PromotionTemplateDTO / SampleRulesDTO / TalentRulesDTO
- **LegacyConfigDomainFacadeTest$rawAccessors (11/11)** - DDD-CONFIG-001 通用只读入口
  - raw 访问器测试
- **LegacyConfigDomainFacadeTest$sampleLimitDays_shouldBeSevenWhenConfigured** 等 6 个用例 (DDD-CONFIG-002 寄样/达人核心阈值)
- 等

### 架构护栏 (见 #31 / #39 / #40 evidence)
- DddConfig003ConfigRoutingTest (守护 facade 路由)

## 验证证据

- mvn test -Dtest="LegacyConfigDomainFacadeTest":
  - **22/22 PASS** (11+11 nested)
  - Total time: 13.9s
  - jacoco: 1003 classes analyzed

## 配置域覆盖维度

- ✅ **只读入口** (rawAccessors): 11 case
- ✅ **聚合 DTO** (aggregateDtos): 11 case
- ✅ **异常分支** (BusinessException 由 facade 内部抛, nested 测试覆盖)
- ✅ **权限验证** (DataScopePolicy 在 facade 消费点守门, #25 Feature Flag)
- ✅ **1:1 行为等价** (无业务规则变化)
- ✅ **架构护栏** (DddConfig003ConfigRoutingTest)

## 与 #39-#41 关系

- #39 CONFIG-READ: 配置读取 + 缓存
- #40 CONFIG-WRITE: 配置保存 + 校验 + 审计
- #41 CONFIG-CONSUMER: 提成/模板/pick_extra 消费边界
- #42 是测试 evidence 层, 与 #39-#41 互补
- 现有 baseline 已覆盖大部分, 待 #39-#41 实施时本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (22/22 tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #39-#41)