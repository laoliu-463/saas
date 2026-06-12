# DDD-ORDER-002 — OrderAmountMapperPolicy + 开关委派

时间：2026-06-12  
环境：local / `mvn test`  
分支：`feature/auth-system`（工作区未提交）

## 目标

- 订单域金额映射抽到 `OrderAmountMapperPolicy`（Batch 2 Policy）
- 通过 `ddd.refactor.order-amount-policy.enabled` 安全开关委派，默认 legacy 行为 1:1

## 变更摘要

| 组件 | 说明 |
|------|------|
| `OrderAmountMapperPolicy` | raw → 双轨金额映射、merge/apply 纯函数 |
| `OrderAmountMappingRouter` | 根开关 + 子开关路由 legacy / policy |
| `OrderSyncService` | `mapOrder` 经 router 解析/落库 |
| `OrderSyncPersistenceService` | 重复同步 merge 经 router |
| `SampleController` | 查询/命令委派修复（DDD-SAMPLE-005-FIX 合入范围） |

## 开关口径

```yaml
ddd:
  refactor:
    enabled: false          # 根开关
    order-amount-policy:
      enabled: false        # 子开关；两者同时为 true 才走 Policy
```

## 验证

| 用例 | 结果 |
|------|------|
| `OrderAmountMapperPolicyTest` | PASS（28） |
| `OrderAmountMappingRouterTest` | PASS（3） |
| `OrderDualTrackAmountResolverTest` | PASS |
| `OrderSyncPersistenceServiceTest` | PASS |
| `DddConfig003ConfigRoutingTest` | PASS |
| `ColonelSaasApplicationTests.contextLoads` | PASS |

## 已知阻塞 / 风险

- `INSTITUTE_SETTLEMENT` 在 Policy 开关开启时仍走 legacy `resolveInstituteSettlement`（1603 口径尚未迁入 Policy）
- 全量 `mvn test` 建议 Integration Agent 合入前跑完

## 验证（2026-06-12 复验）

| 用例 | 结果 |
|------|------|
| `OrderSyncServiceTest` | PASS（39） |
| 其余见上表 | PASS |

## 结论

**PARTIAL** — ORDER-002 Policy + Router 已落地且目标单测绿；工作区待合入 commit。
