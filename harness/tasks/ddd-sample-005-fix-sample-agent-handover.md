# Handover — DDD-SAMPLE-005-FIX (Sample Agent)

## 状态

**完成（代码 + 目标验证）** — 锁可释放；**未单独 commit**（待 Integration Agent 或用户指示提交）。

## 给 Coordinator / Integration Agent

1. **P0 阻塞已解除**：`ColonelSaasApplicationTests.contextLoads` 绿；可启动 Batch 2 并行（仍建议先跑全量 `mvn test`）。
2. **合入范围**（仅寄样查询装配）：
   - `backend/src/main/java/com/colonel/saas/service/sample/SampleQueryConfiguration.java`
   - `backend/src/main/java/com/colonel/saas/service/sample/LegacySampleQueryService.java`
   - `backend/src/main/java/com/colonel/saas/service/sample/LegacySampleCommandService.java`
   - `backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java`
3. **勿合入**：工作区中 `OrderAmountMapperPolicy`、harness GC 归档、多 Agent 提示词等无关 diff。
4. **下一任务**：`DDD-CONFIG-003-FIX`（Config Agent，串行）。

## 技术要点

- 查询 HTTP 仍走 `SampleController` → `SampleQueryService`；实现落在 **delegate**，避免覆盖方法回环。
- 单测：`new LegacySampleQueryService(new SampleApplicationService(...mocks...))`，**不要**传入 `sampleController`。

## 证据

- 任务报告：`harness/reports/ddd-sample-005-fix-sample-agent-20260610.md`
- Harness evidence：`harness/reports/evidence-20260610-203014.md`（构建/健康/preflight PASS；git push FAIL）

## 建议 commit message

```
fix(sample): break LegacySampleQueryService delegate cycle (DDD-SAMPLE-005-FIX)

Register sampleQueryApplicationDelegate so query/command services delegate to a
plain SampleApplicationService instead of SampleController overrides.
```
