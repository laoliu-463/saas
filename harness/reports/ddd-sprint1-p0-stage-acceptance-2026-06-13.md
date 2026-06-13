# DDD Sprint 1 P0 阶段验收报告

| Field | Value |
| --- | --- |
| period | 2026-06-13 |
| branch | feature/ddd/DDD-PERF-004 |
| HEAD | c3ecdd25 |
| scope | DDD-PROGRESS-AUDIT / ORDER-004 / PERF-004 / PRODUCT-004 / PRODUCT-005 |
| conclusion | **PARTIAL_PASS** |

## 1. 任务与 commit

| task_id | commit | report |
| --- | --- | --- |
| DDD-PROGRESS-AUDIT | 712df1ed | harness/reports/ddd-progress-audit-2026-06-13.md |
| DDD-ORDER-004 | 95020743 | harness/reports/ddd-order-004-2026-06-12.md |
| DDD-PERF-004 | a9522ac8 (+ e5db1041 test fix) | harness/reports/ddd-perf-004-2026-06-12.md |
| DDD-PRODUCT-004 | fce4b2fb | harness/reports/ddd-product-004-copy-promotion.md |
| DDD-PRODUCT-005 | 0498b08e | （基线 commit，无独立 report） |

## 2. Targeted tests — PASS

```powershell
cd backend
mvn -Dtest=OrderDefaultAttributionPolicyTest,OrderDefaultAttributionResolverTest,OrderAttributionRouterTest,LegacyOrderPerformanceQueryFacadeTest,DataApplicationServiceOrderSummaryCacheTest,PerformanceQueryServiceTest,CopyPromotionApplicationServiceTest,DouyinPromotionGatewayConvertAdapterTest,QuickSampleApplyTest test
```

结果：exit 0，Sprint 1 相关套件全绿。

## 3. 全量测试 — FAIL（基线债务）

```powershell
mvn clean test
```

结果：Tests run: 2090, Failures: 17, Errors: 114, Skipped: 1  
与本次 Sprint 1 diff 无直接因果的 legacy 失败（如 TalentEnrichTaskVO NoClassDefFoundError）。

## 4. 业务验证要点

| 项 | 结果 |
| --- | --- |
| 订单默认归因无 exclusive | PASS（Policy/Resolver 路径） |
| 业绩 BFF 批量补全 | PASS（OrderPerformanceQueryFacade） |
| 商品快速寄样 Port 化 | PASS（SampleApplicationPort） |
| 复制讲解 ApplicationService | PARTIAL（薄委派，非 SPRINT-1-P0 完整 Port） |
| real-pre 开关 | 新路径默认 `enabled: false`，需显式开启 |

## 5. 是否进入 CLEAN

**否。** 前置：TALENT-004、PERF-005、ORDER-005/006、全量测试基线、PRODUCT-004 完整版。

## 6. 下一步

1. 恢复 stash `WIP DDD-TALENT-004` 并完成 DDD-TALENT-004  
2. DDD-PERF-005 独家商家独立化  
3. 考虑 cherry-pick `feature/ddd/SPRINT-1-P0` 中 PRODUCT-004 完整实现  
4. 第二批完成后跑 DDD-VERIFY-001

## 7. 回滚

```powershell
git checkout d317d895
```

不影响 DB migration；开关关闭即回退 legacy 路径。
