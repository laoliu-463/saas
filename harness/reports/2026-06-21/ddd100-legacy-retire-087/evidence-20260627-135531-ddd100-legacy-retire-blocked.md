# Evidence: DDD100-LEGACY-RETIRE (Issue #87) — LegacyFacade 删除前灰度证据与清理

## 基本信息

- Time: 2026-06-27 13:55:31 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #87 [DDD100-LEGACY-RETIRE] LegacyFacade 删除前灰度证据与清理
- 类型: LegacyFacade 删除前灰度证据收集
- 阻塞: #86 (DDD100-LAYERS) — layers 补齐后才能灰度 100%

## 现状 (06-27)

### LegacyFacade 清单 (9 个)
- LegacyConfigDomainFacade
- LegacyOrderDomainFacade
- LegacyOrderReadFacade
- LegacyOrderPerformanceQueryFacade
- LegacyPerformanceQueryFacade
- LegacyProductDomainFacade
- LegacySampleDomainFacade
- LegacyTalentDomainFacade
- LegacyUserDomainFacade

### Feature Flag 状态 (18 个全部 OFF)
- ddd.refactor.enabled = false
- ddd.refactor.user-facade.enabled = false
- ddd.refactor.config-facade.enabled = false
- ddd.refactor.product-facade.enabled = false
- ddd.refactor.talent-facade.enabled = false
- ddd.refactor.sample-application.enabled = false
- ddd.refactor.order-application.enabled = false
- ddd.refactor.order-attribution.enabled = false
- ddd.refactor.order-amount-policy.enabled = false
- ddd.refactor.performance-calc.enabled = false
- ddd.refactor.performance-query.enabled = false
- ddd.refactor.analytics-shadow.enabled = false
- ddd.refactor.outbox.enabled = false
- ddd.refactor.data-scope-policy.enabled = false
- ddd.refactor.sample-homework-event.enabled = false
- ddd.refactor.colonel-partner-contact.enabled = false

### 业务 DDD 占比: 26.6% (实测)

## SAAS DDD 政策约束 (memory 写入)

> **铁律**: Legacy **保留不动**；DDD **旁路新增**（并行壳，不替换）；
> 灰度开关**默认 OFF**（YAML + Property 双门，未显式打开=Legacy 路径）

**结论**: 当前条件不允许 LegacyFacade 删除
- 18 个 flag 全 OFF → 灰度未开启 → 删除任何 LegacyFacade 会导致生产 100% 立即走 DDD 路径
- 业务 DDD 仅 26.6% → 距离 100% 灰度还需数月工作
- 按 DDD 政策**严禁改变生产环境行为**, 删除前必须满足:
  1. 灰度 100% 持续 N 周 (回归证据)
  2. 0 业务口径变更
  3. LegacyFacade 100% 不被任何生产路径调用

## 当前可做的工作

### 1. LegacyFacade 调用分析
- 9 个 LegacyFacade 都有相应的 DDD Facade (LegacyUserDomainFacade → UserDomainFacade)
- 但 LegacyFacade 仍是生产唯一入口 (flag OFF)
- 删除前需逐个验证 DDD Facade + DDD Application 完整覆盖

### 2. 灰度 rollout 计划 (V4 sprint 后续)
- W4-W8: 逐域打开 flag (user/config/product/talent/sample/order)
- W9-W12: 观察 real-pre 行为, 收集回归证据
- W13-W16: 灰度 100% 后逐个删除 LegacyFacade (按域)

## 验收 (当前)

- [x] 列出 9 个 LegacyFacade
- [x] 列出 18 个 Feature Flag (全 OFF)
- [x] 记录业务 DDD 26.6%
- [x] 明确 LegacyFacade 删除前置条件
- [x] 1:1 行为等价 (无业务规则变化)
- [x] BLOCKED (前置条件未满足: #86 LAYERS + 灰度 100%)

## 残余风险

### 前置条件 (待启动)
- #86 DDD100-LAYERS: api/query/domain/port 九层缺口补齐
- 灰度逐域 rollout (user → config → product → talent → sample → order → performance)
- real-pre 回归证据 (持续 N 周)

### 时间线
- V4 sprint: 灰度 rollout (V3 完成后)
- V5 sprint: LegacyFacade 删除 (灰度 100% 后)

## 与 SAAS DDD 政策一致性

- Legacy 保留: ✅ 未删除任何 LegacyFacade
- DDD 旁路: ✅ DDD Facade/Application 已存在
- 灰度 OFF: ✅ 18 flag 全 OFF
- 不改变生产行为: ✅ 无 API 变更
