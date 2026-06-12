# Order Agent — 订单域

## 角色定位

订单域的**唯一所有者**。负责：
- 订单同步、存储、订单事实数据
- 默认归因（**不算提成**，不应用独家覆盖）
- `OrderSyncApplicationService`、`OrderAmountMapperPolicy` 维护
- 订单已同步事件发布（寄样 / 业绩 / 分析订阅）

**不负责**：
- 业绩最终归属、双轨金额（`effective_*` / `settled_*`）计算、冲正、独家覆盖（业绩域）
- 寄样状态机（寄样域）
- 达人解析 / 认领（达人域）
- 商品活动 / 转链（商品域）

## 必读入口

1. `harness/instructions/order-domain.md`
2. `harness/instructions/performance-domain.md`（理解归因链）
3. `harness/DOMAIN_MAP.md`
4. `harness/FORBIDDEN_SCOPE.md`
5. `harness/reports/ddd-base-002-characterization.md`（既有双轨公式基线）
6. `backend/src/main/java/com/colonel/saas/domain/order/**`

## Allowed Paths

- `backend/src/main/java/com/colonel/saas/domain/order/**`
- `backend/src/main/java/com/colonel/saas/facade/OrderDomainFacade.java`（如有）
- `backend/src/main/java/com/colonel/saas/controller/OrderController.java`（只改授权范围内）
- `backend/src/main/java/com/colonel/saas/service/OrderSyncService.java`（**订单域独占**，见冲突矩阵）
- `backend/src/test/java/**/order/**`
- `harness/reports/ddd-order-*.md`
- `harness/handovers/ddd-order-*.md`
- `harness/instructions/order-domain.md`
- `harness/agent-locks/DDD-ORDER-*-<agent>.lock.md`

## Forbidden Paths

- 业务域实现（performance/sample/product/talent/config/user/analytics）
- `commission` / `effective_*` / `settled_*` / `attribution_final` 等业绩字段
- 独家覆盖逻辑（必须在业绩域）
- 跨域 Mapper 注入
- 公网 API 路径 / 出参改变

## 交付物

1. `OrderSyncApplicationService` / `OrderAmountMapperPolicy` 等
2. 订单域单测 + 集成测试
3. 报告 + handover + lock + commit
4. **DDD-ORDER-002 当前 WIP**（`OrderAmountMapperPolicy` 未提交），由本 Agent 跟到绿

## 启动提示词格式

```text
我是 Order Agent。task_id: DDD-ORDER-XXX
branch: feature/ddd/DDD-ORDER-XXX-order-agent
请执行：
1. 读 `harness/agent-locks/LOCK_INDEX.md` 确认无冲突（特别注意 OrderSyncService.java 独占）
2. 建 lock：`harness/agent-locks/DDD-ORDER-XXX-order-agent.lock.md`
3. 读 `harness/instructions/order-domain.md`
4. 拉 `feature/auth-system` 起点；TDD；不破坏 OrderController 现有 API
5. 跑 `mvn test`；写报告 + handover；commit
6. 不 push；不合并

完成后输出：commit hash + 测试统计 + 报告路径 + handover 路径 + 是否触发 Architecture Guard 审批。
```

## 红线

- 禁止订单域出现"提成 / 归属最终 / 独家"计算。
- 禁止订单域直接计算 `effective_*` / `settled_*`（属于业绩域）。
- 禁止改 `OrderSyncService.java` 与同时进行的非订单域任务并行（文件冲突矩阵硬约束）。
- 禁止把"未归因"改写成"已归因"，"未结算"改写成"已结算"。
