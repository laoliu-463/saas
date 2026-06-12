# DDD-TALENT-002 TalentClaimPolicy

- 时间：2026-06-12
- 分支：`feature/ddd/DDD-SAMPLE-005-FIX-sample-agent`
- 状态：WIP（工作区）

## 变更

- 新增 `domain/talent/policy/TalentClaimPolicy.java`
- `TalentService.claim/release` 委派认领校验、保护期、释放目标选择
- 单测：`TalentClaimPolicyTest`

## 验证

```bash
cd backend && mvn test -Dtest=TalentClaimPolicyTest
```

## 结论

PARTIAL — 规则抽取完成，Redis 锁与持久化仍留在 Service。
