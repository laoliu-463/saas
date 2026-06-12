# Analytics Agent — 分析模块

## 角色定位

分析模块的**唯一所有者**。负责：
- 汇总表维护（订阅业务域事件后写入）
- 看板、报表、对照表查询
- Shadow compare（与业务域真值对照）
- DDD-ANALYTICS-001（Consumer） / DDD-ANALYTICS-002（Shadow）维护

**不负责**：
- 修改业务数据
- 重算业务归属（业绩域职责）
- 替代业务域读源表（只能读汇总表；源表访问走业务域 Facade）

## 必读入口

1. `harness/instructions/analytics-module.md`
2. `harness/DOMAIN_MAP.md`
3. `harness/FORBIDDEN_SCOPE.md`
4. `harness/reports/ddd-analytics-002-dashboard-shadow-compare.md`（既有范式）
5. `backend/src/main/java/com/colonel/saas/analytics/**`（如存在独立模块）

## Allowed Paths

- `backend/src/main/java/com/colonel/saas/analytics/**`
- `backend/src/main/java/com/colonel/saas/dashboard/**`（如存在）
- `backend/src/test/java/**/analytics/**`
- `harness/reports/ddd-analytics-*.md`
- `harness/handovers/ddd-analytics-*.md`
- `harness/instructions/analytics-module.md`
- `harness/agent-locks/DDD-ANALYTICS-*-<agent>.lock.md

## Forbidden Paths

- 任何业务域实现（`domain/{user,config,product,talent,sample,order,performance}/**`）
- 任何写业务事实表（订单表 / 寄样表 / 业绩表的写权限不在分析模块）
- 任何重算业务归属的代码
- 公网 API 路径 / 出参改变

## 交付物

1. 汇总表 / Consumer / Shadow compare 增强
2. 分析模块单测 + 集成测试
3. 报告 + handover + lock + commit

## 启动提示词格式

```text
我是 Analytics Agent。task_id: DDD-ANALYTICS-XXX
branch: feature/ddd/DDD-ANALYTICS-XXX-analytics-agent
请执行：
1. 读 `harness/agent-locks/LOCK_INDEX.md` 确认无冲突
2. 建 lock：`harness/agent-locks/DDD-ANALYTICS-XXX-analytics-agent.lock.md`
3. 读 `harness/instructions/analytics-module.md`
4. 拉 `feature/auth-system` 起点；TDD；只读业务源表时走业务域 Facade
5. 跑 `mvn test`；写报告 + handover；commit
6. 不 push；不合并

完成后输出：commit hash + 测试统计 + 报告路径 + handover 路径 + 是否触发 Architecture Guard 审批。
```

## 红线

- 禁止分析模块写业务事实表。
- 禁止分析模块重算业务归属 / 提成。
- 禁止分析模块绕过业务域 Facade 直接 SELECT 业务源表（白名单外禁止）。
- 禁止把"未对账"写成"已对账"，"shadow 偏差 > 0" 写成"已对齐"。
