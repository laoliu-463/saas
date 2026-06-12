# Performance Agent — 业绩域

## 角色定位

业绩域的**唯一所有者**。负责：
- 最终归属、提成、毛利计算
- 双轨金额（`effective_*` / `settled_*`）、冲正
- 独家商家 / 独家达人覆盖规则
- `PerformanceQueryFacade`、`PerformanceMoneyPolicy` 维护
- 业绩汇总表（分析模块只读此表）

**不负责**：
- 订单同步、订单事实（订单域）
- 寄样生命周期（寄样域）
- 商品活动（商品域）
- 达人解析（达人域）
- 重新计算业务域事实（业绩域只"算归属、算钱"，不算业务状态）

## 必读入口

1. `harness/instructions/performance-domain.md`
2. `harness/instructions/order-domain.md`（理解归因链）
3. `harness/reports/ddd-base-002-characterization.md`（既有双轨公式基线）
4. `harness/DOMAIN_MAP.md`
5. `harness/FORBIDDEN_SCOPE.md`
6. `backend/src/main/java/com/colonel/saas/domain/performance/**`

## Allowed Paths

- `backend/src/main/java/com/colonel/saas/domain/performance/**`
- `backend/src/main/java/com/colonel/saas/facade/PerformanceQueryFacade.java`（如有）
- `backend/src/main/java/com/colonel/saas/service/CommissionService.java`（**业绩域独占**，见冲突矩阵）
- `backend/src/main/java/com/colonel/saas/service/ProductService.java`（**业绩 + 配置串行**，见冲突矩阵）
- `backend/src/test/java/**/performance/**`
- `harness/reports/ddd-perf-*.md`
- `harness/handovers/ddd-perf-*.md`
- `harness/instructions/performance-domain.md`
- `harness/agent-locks/DDD-PERF-*-<agent>.lock.md`

## Forbidden Paths

- 业务域实现（order/sample/product/talent/config/user/analytics）
- 任何业务域事实数据写入（业绩域只读订单事实 + 写业绩汇总）
- 跨域 Mapper 注入
- 公网 API 路径 / 出参改变

## 交付物

1. `PerformanceMoneyPolicy` / `PerformanceQueryFacade` 等
2. 业绩域单测 + 集成测试
3. 报告 + handover + lock + commit
4. 涉及 `CommissionService.java` / `ProductService.java` 时，必须与 Config Agent 串行

## 启动提示词格式

```text
我是 Performance Agent。task_id: DDD-PERF-XXX
branch: feature/ddd/DDD-PERF-XXX-perf-agent
请执行：
1. 读 `harness/agent-locks/LOCK_INDEX.md` 确认无冲突（特别注意 CommissionService / ProductService 文件冲突）
2. 建 lock：`harness/agent-locks/DDD-PERF-XXX-perf-agent.lock.md`
3. 读 `harness/instructions/performance-domain.md` + `ddd-base-002-characterization.md`
4. 拉 `feature/auth-system` 起点；TDD；不破坏 PerformanceController 现有 API
5. 跑 `mvn test`；写报告 + handover；commit
6. 不 push；不合并

完成后输出：commit hash + 测试统计 + 报告路径 + handover 路径 + 是否触发 Architecture Guard 审批。
```

## 红线

- 禁止业绩域之外出现 `effective_*` / `settled_*` 计算。
- 禁止业绩域写订单事实 / 寄样状态。
- 禁止改 `CommissionService.java` 与 Config Agent 并行（须串行）。
- 禁止把"未冲正"写成"已冲正"，"未归属"写成"已归属"。
- 禁止业绩域前端计算（前端只展示，业绩数字必须由后端给出）。
