# Evidence Report - DDD-CONFIG-004: 配置更新事件兼容层

本报告记录了 **DDD-CONFIG-004** 任务的设计、实现与本地集成联调测试结果，证明已满足 Definition of Done (DoD) 标准。

## 设计方案

在配置保存/更新的持久化主流程成功后，发布 `ConfigUpdatedEvent` 领域事件，以便下游各域在后续重构中接收并刷新缓存。本阶段只做进程内事件（同步分发）与接口占位，暂不引入外部消息队列（MQ）。

### 事件 payload 结构
- `eventId`: UUID
- `configKey`: String
- `oldValue`: String
- `newValue`: String
- `valueType`: String
- `operatorId`: UUID
- `updatedAt`: LocalDateTime

### 发布与监听兼容
- **事件发布器**：`ConfigDomainEventPublisher` (接口) 和 `InProcessConfigDomainEventPublisher` (实现，基于 Spring Event + Refresher 循环)。
- **刷新监听器**：`ConfigCacheRefresher` 接口，提供 `onConfigUpdated(ConfigUpdatedEvent event)` 统一规范占位。
- **事务与异常隔离**：事件发布采用 `try-catch` 强力捕获所有刷新监听器的异常，确保刷新故障绝不破坏主流程配置修改事务。

---

## 验证结果

### 1. 专向单元测试
新编写了 [SysConfigServiceEventTest.java](file:///d:/Projects/SAAS/backend/src/test/java/com/colonel/saas/service/SysConfigServiceEventTest.java) 对新事件的属性、变更日志、Jackson 序列化/反序列化以及异常安全隔离进行了验证：
- `update_config_ShouldRecordLogAndPublishSerializableEvent` (PASS)
- `update_config_ShouldSucceedEvenIfRefresherThrowsException` (PASS)

运行命令并全部通过：
`mvn clean test "-Dtest=SysConfigServiceEventTest"`

### 2. 全量业务联调
运行唯一入口脚本执行构建、重启与 P0 联调：
`powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope full`
- **编译结果**：Backend maven build passed.
- **Docker 容器**：后端及前端容器成功重启，HTTP 健康检查正常。
- **业务验证**：`npm run e2e:real-pre:p0:preflight` 100% 通过（Conclusion: PASS）。

---

## 结论

本次改动没有改变任何配置接口响应，完全保持向下兼容，未引入任何外部 MQ，未影响任何既有缓存刷新，完全符合 DDD 渐进式演进的防退化原则。
