# Batch 3 — Replace（调用替换）

## 目标

把原 Service / Controller 中的**直调实现**逐步切换到 **Facade → Policy** 路径：
- 每次只切**一个方向**（如 OrderController → OrderDomainFacade → Policy）
- 切完跑 `mvn test` + E2E
- 旧实现保留为委派层（不删）
- 开关：`ddd.refactor.<domain>.use-facade=true` 控制

## 入口任务

| task_id | Agent | 状态 | 备注 |
|---|---|---|---|
| DDD-ORDER-003 | Order | 等 BATCH 2 | Controller 切 Facade |
| DDD-PERF-003 | Performance | 等 BATCH 2 | QueryController 切 Facade |
| DDD-PRODUCT-003 | Product | 等 BATCH 2 | QuickSample 切 Facade |
| DDD-SAMPLE-007 | Sample | 等 BATCH 2 | SampleController 切 Facade |
| DDD-TALENT-003 | Talent | 等 BATCH 2 | TalentController 切 Facade |

## 文件冲突矩阵

每个 Controller 由对应域 Agent **独占**。跨 Controller 无共享文件。

## 启动提示词

```text
我是 Coordinator。任务：启动 Batch 3（调用替换）。
请执行：
1. 读 `harness/agent-locks/LOCK_INDEX.md` 确认无冲突
2. 读 `harness/reports/ddd-base-001-refactor-switches.md` 确认开关命名
3. 对每个域，串行（**一个一个域**）启动 Replace：
   - 一次只切一个 Controller
   - 切完跑 mvn test + e2e:v1-p0
   - 全绿后才进下一个域
4. 每个 Agent 写：原调用点 / 新调用点 / 开关 / 回退路径
5. 收齐 commit + 测试 + 报告
6. 更新看板
```

## 退出条件

- [ ] 业务 Controller 全部走 Facade
- [ ] 开关全 `true` 且 `mvn test` 全绿
- [ ] E2E V1-P0 通过
- [ ] 旧 Service 委派层不删

## 串行依赖

- Batch 2 全完
- 每个域内部串行（不跨域并发）
