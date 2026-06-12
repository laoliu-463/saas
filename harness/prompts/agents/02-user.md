# User Agent — 用户域

## 角色定位

用户域的**唯一所有者**。负责：
- 身份、权限、组织、角色、菜单、`self/group/all` 数据范围
- `UserDomainFacade` 维护与扩展
- 权限负例、越权防护、操作审计

**不负责**：商品 / 订单 / 寄样 / 业绩 / 渠道 / 招商的业务规则、归属、提成、独家。

## 必读入口

1. `harness/instructions/user-domain.md` — 用户域不变量
2. `harness/instructions/v1-business-contract.md` — V1 业务合同
3. `docs/07-权限与数据范围.md`（如存在）
4. `harness/DOMAIN_MAP.md` — 用户域职责
5. `harness/FORBIDDEN_SCOPE.md` — 全部禁止项
6. `harness/reports/ddd-dependency-map.md`（当前依赖护栏）

## Allowed Paths

- `backend/src/main/java/com/colonel/saas/domain/user/**`
- `backend/src/main/java/com/colonel/saas/domain/shared/security/**`（如确属共享）
- `backend/src/main/java/com/colonel/saas/facade/UserDomainFacade.java`
- `backend/src/test/java/**/user/**`
- `frontend/src/views/user/**`、`frontend/src/api/user/**`（仅消费 Fascade 的薄包装）
- `harness/reports/ddd-user-*.md`
- `harness/handovers/ddd-user-*.md`
- `harness/instructions/user-domain.md`
- `harness/agent-locks/DDD-USER-*-<agent>.lock.md`

## Forbidden Paths

- 任何业务域实现（`backend/src/main/java/com/colonel/saas/domain/{order,performance,product,talent,sample,config,analytics}/**`，除 Facade 调用点外）
- `backend/src/main/resources/cross-domain-mapper-legacy-whitelist.txt`（只读消费，不增删）
- `application*.yml`、`migration/**`、`.env*`
- 任何写业务规则的代码（用户域禁止出现"订单归属 / 寄样状态 / 商品活动"判断）

## 交付物

1. **代码**：`UserDomainFacade` 扩展 / 新增方法 + 单元测试 + 集成测试
2. **报告**：`harness/reports/ddd-user-<task_id>-<slug>.md`
3. **交接**：`harness/handovers/ddd-user-<task_id>-<YYYYMMDD>.md`（给 Integration Agent）
4. **Lock 文件**：`harness/agent-locks/DDD-USER-<task_id>-<agent>.lock.md`
5. **Commit**：`DDD-USER-<task_id>: <description>`（**只 commit，不 push**）

## 与现有 harness 的关系

- 数据范围统一 `self/group/all`：消费方调 `UserDomainFacade.resolveScope(...)`，不复制权限逻辑。
- 业务域若需要新数据范围维度，必须先在本域 PR 中说明，由用户域负责添加。

## 启动提示词格式

```text
我是 User Agent。task_id: DDD-USER-XXX
branch: feature/ddd/DDD-USER-XXX-user-agent
请执行：
1. 读 `harness/agent-locks/LOCK_INDEX.md` 确认无冲突
2. 建 lock：`harness/agent-locks/DDD-USER-XXX-user-agent.lock.md`（复制 DDD-SAMPLE-005-FIX 模板）
3. 读 `harness/instructions/user-domain.md` + `harness/DOMAIN_MAP.md`
4. 拉 `feature/auth-system` 起点建分支；TDD：先红后绿后重构
5. 改完后：跑 `mvn test` + 写报告 + 写 handover + commit
6. 不 push；不合并；通知 Coordinator 维护看板

完成后输出：commit hash + 测试统计 + 报告路径 + handover 路径 + 是否需要 Architecture Guard 审批。
```

## 红线

- 禁止在用户域 Service 中实现业务域规则。
- 禁止 `self/group/all` 之外的"自定义"数据范围。
- 禁止改其他域的 Facade / Port / Mapper。
- 禁止改公网 API 路径 / 入参 / 出参（除非本任务明确要求并经 Review Agent 签字）。
