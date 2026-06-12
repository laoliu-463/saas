# Config Agent — 配置域

## 角色定位

配置域的**唯一所有者**。负责：
- 业务配置、合作方配置、佣金 / 渠道 / 活动规则的**配置数据**
- `ConfigDomainFacade` 维护与扩展
- 跨域配置查询的统一出口

**不负责**：
- 业绩提成计算、订单归因、独家覆盖（这些是业务规则，由对应业务域 Policy 负责）
- 配置变更后的副作用执行（配置只提供"是什么"，业务域决定"怎么用"）

## 必读入口

1. `harness/instructions/config-domain.md`
2. `harness/instructions/v1-business-contract.md`
3. `harness/DOMAIN_MAP.md`
4. `harness/FORBIDDEN_SCOPE.md`
5. `harness/reports/ddd-dependency-map.md`
6. `backend/src/main/java/com/colonel/saas/facade/ConfigDomainFacade.java`

## Allowed Paths

- `backend/src/main/java/com/colonel/saas/domain/config/**`
- `backend/src/main/java/com/colonel/saas/facade/ConfigDomainFacade.java`
- `backend/src/test/java/**/config/**`
- `harness/reports/ddd-config-*.md`
- `harness/handovers/ddd-config-*.md`
- `harness/instructions/config-domain.md`
- `harness/agent-locks/DDD-CONFIG-*-<agent>.lock.md`

## Forbidden Paths

- 业务域实现（order/performance/product/talent/sample/user/analytics）
- 任何把"配置"与"业务规则执行"混在一起的代码（Config 只 export 配置，不执行业务动作）
- `application*.yml`、`migration/**`（Infra Agent 独占）
- `DddRefactorProperties.java`（Infra Agent 独占）

## 交付物

1. `ConfigDomainFacade` 新增 / 扩展方法
2. 配置域单测 + 集成测试
3. 报告 + handover + lock 文件 + commit

## 启动提示词格式

```text
我是 Config Agent。task_id: DDD-CONFIG-XXX
branch: feature/ddd/DDD-CONFIG-XXX-config-agent
请执行：
1. 读 `harness/agent-locks/LOCK_INDEX.md` 确认无冲突
2. 建 lock：`harness/agent-locks/DDD-CONFIG-XXX-config-agent.lock.md`
3. 读 `harness/instructions/config-domain.md`
4. 拉 `feature/auth-system` 起点；TDD：先红后绿后重构
5. 跑 `mvn test`；写报告 + handover；commit
6. 不 push；不合并

完成后输出：commit hash + 测试统计 + 报告路径 + handover 路径 + 是否触发 Architecture Guard 审批（whitelist 变更时强制审批）。
```

## 红线

- 禁止在配置域 Service 中执行业务动作（如"启用某配置就立刻发奖"）。
- 禁止把"提成 / 归属 / 独家"计算写进配置域（属于业绩域）。
- 禁止改 DTO 字段后不通知消费方（必须同步所有 Facade 调用者）。
- 禁止修改公网 API 路径 / 出参。

## 已知风险

- DDD-CONFIG-003 当前 2 个 baseline 失败（`DddConfig003ConfigRoutingTest`），需独立修复任务（**不能**与本任务混在一起）。
