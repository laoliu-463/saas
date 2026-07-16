# 业绩订单同步事件单一消费入口设计

**日期：** 2026-07-16

**状态：** 待用户复核

**范围：** `OrderSyncedEvent` 到业绩记录生成的应用层入口治理

## 背景与证据

当前仓库存在两个与业绩域相关的 `OrderSyncedEvent` 消费入口：

- `PerformanceRecordSyncListener` 会读取订单、调用 `PerformanceCalculationApplicationService.upsertFromOrder`，并在成功后发布 `PerformanceCalculatedEvent`。其测试已覆盖成功、退款、重复事件、订单不存在和异常隔离。
- `PerformanceAggregateApplicationService.handleOrderSynced` 是后续新增的异步监听方法，目前只记录日志；注释声称失效缓存，但该服务没有缓存实现，也没有可验证的状态变更。其测试只验证空事件不抛异常。

因此，现状不是“业绩事件链尚未实现”，而是“有效链路旁新增了一个没有职责的重复监听入口”。继续保留该入口会让事件消费边界变得含糊，并为后续重复计算或双重副作用留下空间。

## 目标

1. 业绩记录生成只保留一个 `OrderSyncedEvent` 消费入口。
2. 不改变现有订单同步、业绩计算、幂等更新和后续事件发布行为。
3. 用自动化架构测试阻止重复消费入口重新进入业绩域。
4. 本轮保持小步：不新增缓存、数据库字段、接口或业务规则。

## 方案决定

采用“删除重复空监听，保留有效监听器，并增加架构守卫”的方案：

- `PerformanceRecordSyncListener` 继续作为业绩记录生成的唯一事件入口。
- 删除 `PerformanceAggregateApplicationService.handleOrderSynced` 及其仅为该方法服务的事件、异步和日志依赖。
- 删除只验证空事件不抛异常的无效测试。
- 在现有 `DddPerformanceRecordGenerationEntrypointTest` 中增加守卫：`domain/performance` 包不得直接消费 `OrderSyncedEvent`；已明确的外层监听器仍必须委托到统一的 `upsertFromOrder` 漏斗。

`PerformanceAggregateApplicationService` 继续承担业绩聚合查询与汇总应用服务职责，不承担订单同步事件编排。

## 未采用的方案

### 保留两个监听器并让聚合服务委托计算

两个监听器会响应同一事件，造成同一订单被重复触发计算。即使当前 upsert 具备幂等性，也会增加数据库写入、事件发布和未来副作用的重复风险，且没有额外业务价值。

### 为聚合服务新增缓存并执行失效

当前没有缓存端口、缓存状态或命中率证据。为解释一个空监听而引入缓存会扩大本轮范围；如果后续出现可观测的聚合查询性能问题，应另立小步，基于性能证据设计缓存及一致性策略。

### 把有效监听逻辑迁入领域包

`OrderSyncedEvent` 是跨域集成事件，读取订单并编排业绩计算属于应用边界职责。将其放入领域包会让领域模型直接依赖外部事件和应用服务，违背本轮收紧边界的目标。

## 数据流与边界

本轮完成后的链路保持为：

```text
订单同步完成
  -> OrderSyncedEvent
  -> PerformanceRecordSyncListener
  -> OrderReadFacade.findByOrderId
  -> PerformanceCalculationApplicationService.upsertFromOrder
  -> PerformanceCalculatedEvent
```

边界约束：

- 订单域只发布同步事实，不计算业绩。
- 外层监听器负责跨域事件适配与异常隔离。
- 业绩应用服务负责统一的记录生成和幂等更新。
- 业绩领域包不直接监听 `OrderSyncedEvent`。

## 错误、幂等与兼容性

- 订单不存在时保持现有跳过行为。
- 计算异常时保持监听器隔离行为，不反向破坏订单同步事务。
- 重复事件继续由现有 `upsertFromOrder` 路径保证结果幂等。
- 不改变 HTTP API、数据库结构、事件结构、权限或历史数据。
- 删除的是无副作用方法，因此无需数据迁移或兼容层。

## TDD 实施顺序

1. 先增强 `DddPerformanceRecordGenerationEntrypointTest`，断言业绩领域包不能直接消费 `OrderSyncedEvent`；测试应因当前重复监听而失败。
2. 删除 `PerformanceAggregateApplicationService` 中的重复空监听和无效测试，使架构测试转绿。
3. 运行以下定向测试：
   - `DddPerformanceRecordGenerationEntrypointTest`
   - `PerformanceRecordSyncListenerTest`
   - `PerformanceAggregateApplicationServiceTest`
4. 通过项目统一 Harness 执行构建、容器重启、健康检查、相关业务验证、安全检查和 evidence 生成。

## 验收标准

- 生产代码中只有 `PerformanceRecordSyncListener` 承担“订单同步后生成业绩记录”的职责。
- `domain/performance` 下不存在 `OrderSyncedEvent` 监听。
- 现有有效监听器的成功、退款、重复事件、缺失订单和异常隔离测试全部通过。
- 聚合查询服务现有测试通过。
- Harness 报告如实记录构建、容器、健康检查和业务验证结果；未验证项不得标记为 `PASS`。

## 风险与回滚

主要风险是误删实际副作用。当前源码和测试证据表明待删方法只有日志，没有缓存或持久化操作；实施前后仍通过源码差异和定向测试复核。

如需回滚，只恢复被删方法及测试即可，不涉及数据库、接口或历史数据回滚。若未来确需缓存失效，应以独立设计引入明确的缓存端口、失效策略和一致性测试，而不是恢复空监听。

## 非目标

- 不重构 `PerformanceCalculationApplicationService` 内部计算规则。
- 不调整归因、提成、退款或状态机口径。
- 不新增缓存。
- 不处理其他领域对 `OrderSyncedEvent` 的合法消费。
- 不执行远端 `real-pre` 部署。
