# Sample Agent — 寄样域

## 角色定位

寄样域的**唯一所有者**。负责：
- 寄样生命周期（创建 → 资质评估 → 网关 → 在途 → 签收 / 拒收）
- 订阅订单已同步事件判断"交作业完成"
- `SampleApplicationPort` 实现（被商品 / 达人 / 业绩域调用）
- `SampleStateMachine` 抽取（Batch 2 待办）

**不负责**：
- 商品活动 / 转链（商品域）
- 订单归因、提成（业绩域）
- 达人解析 / 认领（达人域，只能通过 `TalentDomainFacade` 调）

## 必读入口

1. `harness/instructions/sample-domain.md`
2. `harness/DOMAIN_MAP.md`
3. `harness/FORBIDDEN_SCOPE.md`
4. `harness/agent-locks/DDD-SAMPLE-005-FIX-sample-agent.lock.md`（既有 lock 范式）
5. `harness/reports/ddd-product-005-quick-sample-port.md`（SampleApplicationPort 消费侧范式）
6. `backend/src/main/java/com/colonel/saas/facade/SampleDomainFacade.java`（如有）
7. `backend/src/main/java/com/colonel/saas/controller/SampleController.java`

## Allowed Paths

- `backend/src/main/java/com/colonel/saas/domain/sample/**`
- `backend/src/main/java/com/colonel/saas/facade/SampleDomainFacade.java`（如有）
- `backend/src/main/java/com/colonel/saas/controller/SampleController.java`
- `backend/src/main/java/com/colonel/saas/domain/sample/api/SampleApplicationPort.java`（**唯一对外端口**）
- `backend/src/main/java/com/colonel/saas/domain/sample/application/SampleApplicationPortImpl.java`
- `backend/src/test/java/**/sample/**`
- `harness/reports/ddd-sample-*.md`
- `harness/handovers/ddd-sample-*.md`
- `harness/instructions/sample-domain.md`
- `harness/agent-locks/DDD-SAMPLE-*-<agent>.lock.md`

## Forbidden Paths

- 业务域实现（order/performance/product/talent/config/user/analytics）
- 订单域 Mapper（寄样域不直读订单，只订阅"订单已同步"事件）
- 业绩域 Mapper（寄样域不写业绩）
- 达人 Mapper 直注（必须走 `TalentDomainFacade`）

## 交付物

1. `SampleApplicationPort` 实现 / `SampleStateMachine` 抽取 / 寄样服务扩展
2. 寄样域单测 + 集成测试
3. 报告 + handover + lock + commit
4. **DDD-SAMPLE-005-FIX**（循环依赖修复）属于 P0 阻塞，必须先于其他 Batch 2+ 任务开工

## 启动提示词格式

```text
我是 Sample Agent。task_id: DDD-SAMPLE-XXX
branch: feature/ddd/DDD-SAMPLE-XXX-sample-agent
请执行：
1. 读 `harness/agent-locks/LOCK_INDEX.md` 确认无冲突
2. 建 lock：`harness/agent-locks/DDD-SAMPLE-XXX-sample-agent.lock.md`（复制 DDD-SAMPLE-005-FIX 模板）
3. 读 `harness/instructions/sample-domain.md`
4. 拉 `feature/auth-system` 起点；TDD；不破坏 SampleController 现有 API 与导出列
5. 跑 `mvn test`（全量后端，特别是 SampleControllerTest + ColonelSaasApplicationTests）
6. 写报告 + handover；commit
7. 不 push；不合并

完成后输出：commit hash + 测试统计（含全量后端）+ 报告路径 + handover 路径。
```

## 红线

- 禁止寄样域写订单表 / 业绩表。
- 禁止寄样域直注达人 Mapper（必须 `TalentDomainFacade`）。
- 禁止改 SampleController 公网 API 路径 / 出参 / 导出列。
- 禁止修复循环依赖时顺手改状态机（**只修装配**）。
- 禁止删除旧 SampleService（SampleApplicationService）— 留作委派层。
