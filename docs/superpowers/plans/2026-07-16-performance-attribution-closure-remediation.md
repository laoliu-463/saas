# 业绩域归因闭环修复实施计划

> **执行说明：** 本计划以 `docs/领域/订单域.md`、`docs/领域/业绩域.md`、ADR-003、ADR-004 为主源。`docs/流程/订单归因链路.md` 与 `docs/流程/业绩计算链路.md` 中“业绩域回查映射/活动/达人并重新判定归属”的旧表述与领域合同冲突，本轮会同步更正为“业绩域仅消费订单归因事实”。

**目标：** 让招商链接订单按订单中的招商归因事实归属到创建链接的招商账号；该账号能查询对应订单和业绩；普通订单同步不因当前映射或活动负责人变化漂移历史归因；显式单笔归因重放通过订单事实事件可靠触发业绩域 upsert。

**范围：** 订单列表/统计/详情的角色化数据范围、归因事实冻结、显式重放事件、业绩消费、相应文档和真实环境验证。不会改动订单金额、结算状态、提成公式或已有角色审计事实。

**非本轮自行决定的业务口径：** 招商组长的历史部门范围是按订单归因时部门快照，还是按当前组织成员关系。本轮个人可见性不依赖该口径；组长历史部门快照、字段迁移和历史回填将在取得业务确认后另行实施。

**技术路径：** Spring Boot / Java 17、MyBatis-Plus、PostgreSQL、Spring 本地事件 + Outbox、Maven、Docker Compose real-pre。

---

### 任务 1：建立订单查询的角色化归因访问策略

**文件：**
- 新建：`backend/src/main/java/com/colonel/saas/domain/order/policy/OrderAccessContext.java`
- 新建：`backend/src/main/java/com/colonel/saas/domain/order/policy/OrderAccessScope.java`
- 修改：`backend/src/main/java/com/colonel/saas/controller/OrderController.java`
- 修改：`backend/src/main/java/com/colonel/saas/domain/order/facade/OrderDomainFacade.java`
- 修改：`backend/src/main/java/com/colonel/saas/domain/order/facade/LegacyOrderDomainFacade.java`
- 修改：`backend/src/main/java/com/colonel/saas/service/OrderService.java`
- 修改：`backend/src/main/java/com/colonel/saas/service/OrderQueryService.java`
- 修改：`backend/src/main/java/com/colonel/saas/domain/order/application/OrderDetailQueryApplicationService.java`
- 测试：`backend/src/test/java/com/colonel/saas/domain/order/policy/OrderAccessScopeTest.java`
- 测试：`backend/src/test/java/com/colonel/saas/service/OrderServiceTest.java`
- 测试：`backend/src/test/java/com/colonel/saas/domain/order/application/OrderDetailQueryApplicationServiceTest.java`

- [ ] **步骤 1：先写失败测试。** 覆盖纯 `biz_staff` 的列表/统计 SQL 必须使用 `colonel_user_id = currentUserId`，详情必须允许 `colonel_user_id = currentUserId`；纯渠道角色继续使用 `channel_user_id`；双角色个人范围为两个归因维度的 OR；无角色上下文保留现有兼容路径。运行：
  `mvn -f backend/pom.xml -Dtest=OrderAccessScopeTest,OrderServiceTest,OrderDetailQueryApplicationServiceTest test`
  预期：新测试因尚无角色化策略失败。
- [ ] **步骤 2：最小实现。** 从受认证请求传递 `roleCodes`，在订单域应用策略解释 `self/group/all`；个人招商只能以订单 `colonel_user_id` 查询，个人渠道只能以 `channel_user_id` 查询。保留 Controller 只做参数传递、用户域负责角色标准化、订单域只解释订单事实字段。
- [ ] **步骤 3：扩展详情与统计同一策略。** 不允许列表、统计、详情三条路径出现互相矛盾的数据范围；未知/缺失上下文维持现有拒绝或兼容语义，不放宽为全量。
- [ ] **步骤 4：验证转绿。** 重跑任务测试，确认旧的 ADMIN/ALL 与无角色兼容测试仍通过。

### 任务 2：冻结已归因订单事实，限定普通同步与人工纠正的写入边界

**文件：**
- 修改：`backend/src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java`
- 修改：`backend/src/main/java/com/colonel/saas/service/OrderAttributionReplayService.java`
- 测试：`backend/src/test/java/com/colonel/saas/service/OrderSyncPersistenceServiceTest.java`
- 测试：`backend/src/test/java/com/colonel/saas/service/OrderAttributionReplayServiceTest.java`

- [ ] **步骤 1：先写失败测试。** 构造已 `ATTRIBUTED` 的历史订单与当前映射变化后的同步输入，断言普通 `persistOrder` 保留渠道/招商用户、名称、部门桥接字段、两个 source、状态和备注；断言显式单笔重放仍可改写这些归因事实。运行：
  `mvn -f backend/pom.xml -Dtest=OrderSyncPersistenceServiceTest,OrderAttributionReplayServiceTest test`
  预期：普通同步覆盖已归因字段的断言失败。
- [ ] **步骤 2：最小实现。** 在持久化服务显式区分 `NORMAL_SYNC` 与 `ATTRIBUTION_REPLAY`；仅普通同步对已归因决定复制现存归因快照。保留支付、结算、状态、上游原始载荷和金额字段的正常同步，且让未归因订单仍可在后续真实同步补齐归因。
- [ ] **步骤 3：验证转绿。** 重跑定向测试，检查乐观锁、双轨金额合并、订单同步事件和单笔 replay 审计不回归。

### 任务 3：用可重试、版本化的订单归因更正事件驱动业绩重算

**文件：**
- 新建：`backend/src/main/java/com/colonel/saas/domain/order/event/OrderAttributionReplayedEvent.java`
- 修改：`backend/src/main/java/com/colonel/saas/constant/OrderDomainEventTypes.java`
- 修改：`backend/src/main/java/com/colonel/saas/domain/order/event/OrderDomainEventPublisher.java`
- 修改：`backend/src/main/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisher.java`
- 修改：`backend/src/main/java/com/colonel/saas/service/OrderAttributionReplayService.java`
- 修改：`backend/src/main/java/com/colonel/saas/listener/PerformanceRecordSyncListener.java`
- 测试：`backend/src/test/java/com/colonel/saas/service/OrderAttributionReplayServiceTest.java`
- 测试：`backend/src/test/java/com/colonel/saas/listener/PerformanceRecordSyncListenerTest.java`
- 测试：`backend/src/test/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisherTest.java`

- [ ] **步骤 1：先写失败测试。** 针对同一订单、不同乐观锁版本的归因更正，断言生成不同 outbox key；重复同版本只追加一次；重放服务不再直接调用业绩 Repository/Application Service，而是发布更正事实事件；业绩监听器读取最新订单事实并 upsert。
- [ ] **步骤 2：最小实现。** 增加只表达“订单归因事实已更正”的版本化事件，key 使用 `orderId + 持久化后版本`。事件在订单写入事务内落 Outbox；业绩域监听该事件并在异常时向 Outbox dispatcher 抛出，供既有 FAILED/retry/DEAD 机制处理。订单域不再直接写 `performance_records`。
- [ ] **步骤 3：验证转绿。** 运行：
  `mvn -f backend/pom.xml -Dtest=OrderAttributionReplayServiceTest,PerformanceRecordSyncListenerTest,InProcessOrderDomainEventPublisherTest,DomainEventDispatcherJobTest,OutboxEventAppenderTest test`
  确认第一次更正、同版本重复和消费失败重试路径均有测试证据。

### 任务 4：修正领域合同和验收口径

**文件：**
- 修改：`docs/流程/订单归因链路.md`
- 修改：`docs/流程/业绩计算链路.md`
- 修改：`docs/04-事件契约总表.md`
- 修改：`docs/领域/订单域.md`
- 修改：`docs/领域/业绩域.md`

- [ ] **步骤 1：对齐文档。** 移除业绩域回查映射、活动、达人或当前角色重判归因的表述；登记“订单归因事实已更正”事件及版本化幂等键。
- [ ] **步骤 2：明确未决项。** 在“冲突与待确认”记录招商组长部门历史快照需业务确认，避免把当前部门查询误写为历史审计快照。
- [ ] **步骤 3：文档自检。** `rg` 检查领域合同、流程和事件总表不再给出冲突职责。

### 任务 5：构建、受控部署和真实订单核验

**文件：**
- 修改/生成：`harness/reports/current/latest-performance-attribution-closure-remediation.md`

- [ ] **步骤 1：本地回归。** 执行针对订单、业绩、事件、权限的 Maven 测试集；执行 `mvn -f backend/pom.xml test` 和前端生产构建（若前端未修改，记录为回归验证）。
- [ ] **步骤 2：提交并推送。** 只暂存本计划列出的本轮文件；不触碰现有三个未跟踪文件。
- [ ] **步骤 3：固定入口部署。** 使用 `agent-do.ps1 -Env real-pre -Scope full -DeployRemote true` 构建、重启、健康检查和归档证据。
- [ ] **步骤 4：远端核验。** 读取订单 `6927995582750227729` 与相应业绩明细，确认壮云仍为 final recruiter、招商组长无该单招商归属、金额与非归因维度不变；如需触发新更正事件，只能执行该订单的 dry-run 后经显式确认单笔 apply。
- [ ] **步骤 5：结论。** 生成 evidence report，分开写明代码/部署/真实数据结果，以及因“招商组长部门历史快照”未决而保留的范围。

---

## 执行顺序与回滚

1. 所有生产变更均遵循先失败测试、再最小实现、再转绿验证。
2. 任务 1、2、3 在本地测试完整通过后才进入任务 4、5。
3. 出现构建、测试、远端健康或真实核验失败时，停止推进并在 evidence 标 `FAIL` / `PARTIAL`；不以手工改库掩盖问题。
4. 回滚为部署前 Git commit；不删除 real-pre 数据卷，不清库；已经产生的单笔审计事件保留并通过受控重放入口处理。
