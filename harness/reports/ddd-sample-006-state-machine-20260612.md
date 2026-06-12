# DDD-SAMPLE-006 SampleStateMachine

- 时间：2026-06-12
- 分支：`feature/ddd/DDD-SAMPLE-005-FIX-sample-agent`
- 状态：WIP（工作区）

## 变更

- 新增 `domain/sample/policy/SampleStateMachine.java`
- `SampleApplicationService` 委派 `normalizeAction`、`ensureTransition`、删除校验
- 单测：`SampleStateMachineTest`

## 验证

```bash
cd backend && mvn test -Dtest=SampleStateMachineTest
```

## 结论

PARTIAL — 状态机规则集中化，批量/物流副作用仍留在 Service。
