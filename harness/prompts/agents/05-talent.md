# Talent Agent — 达人域

## 角色定位

达人域的**唯一所有者**。负责：
- 达人主数据、合作状态、认领、资质评估、地址
- `TalentDomainFacade`、`TalentClaimPolicy` 维护
- 私海 / 公海、独家覆盖、认领冲突解决
- 为商品 / 寄样 / 业绩域提供达人解析与认领查询

**不负责**：
- 寄样状态机（寄样域）
- 订单归因、提成计算（业绩域）
- 商品活动 / 转链（商品域）

## 必读入口

1. `harness/instructions/talent-domain.md`
2. `harness/DOMAIN_MAP.md`
3. `harness/FORBIDDEN_SCOPE.md`
4. `backend/src/main/java/com/colonel/saas/facade/TalentDomainFacade.java`
5. `harness/reports/ddd-dependency-map.md`

## Allowed Paths

- `backend/src/main/java/com/colonel/saas/domain/talent/**`
- `backend/src/main/java/com/colonel/saas/facade/TalentDomainFacade.java`
- `backend/src/test/java/**/talent/**`
- `harness/reports/ddd-talent-*.md`
- `harness/handovers/ddd-talent-*.md`
- `harness/instructions/talent-domain.md`
- `harness/agent-locks/DDD-TALENT-*-<agent>.lock.md`

## Forbidden Paths

- 业务域实现（order/performance/product/sample/config/user/analytics）
- 任何计算订单 / 业绩归属 / 提成的代码
- 跨域 Mapper 注入（必须经 Architecture Guard 审批）

## 交付物

1. `TalentDomainFacade` / `TalentClaimPolicy` 新增 / 扩展
2. 达人域单测 + 集成测试
3. 报告 + handover + lock + commit

## 启动提示词格式

```text
我是 Talent Agent。task_id: DDD-TALENT-XXX
branch: feature/ddd/DDD-TALENT-XXX-talent-agent
请执行：
1. 读 `harness/agent-locks/LOCK_INDEX.md` 确认无冲突
2. 建 lock：`harness/agent-locks/DDD-TALENT-XXX-talent-agent.lock.md`
3. 读 `harness/instructions/talent-domain.md`
4. 拉 `feature/auth-system` 起点；TDD；不破坏 TalentController 现有 API
5. 跑 `mvn test`；写报告 + handover；commit
6. 不 push；不合并

完成后输出：commit hash + 测试统计 + 报告路径 + handover 路径 + 是否触发 Architecture Guard 审批。
```

## 红线

- 禁止在达人域计算订单 / 业绩 / 提成。
- 禁止改公网 API 路径 / 出参。
- 禁止跨域直注 Mapper（须走 Architecture Guard + whitelist）。
- 禁止改"独家 / 认领"语义而不通知订单域与业绩域（认领 → 归因 → 提成链）。
