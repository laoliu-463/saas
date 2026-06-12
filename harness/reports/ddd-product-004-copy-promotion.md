# DDD-PRODUCT-004 Evidence

- 时间：2026-06-12 16:32
- 环境：local real-pre toolchain
- 分支：feature/ddd/DDD-SAMPLE-002-eligibility-policy
- 任务：CopyPromotionApplicationService + DouyinConvertPort

## 变更摘要

- 新增 `DouyinConvertPort` 端口与 `DouyinPromotionGatewayConvertAdapter` 适配器。
- 新增 `CopyPromotionApplicationService`；`ColonelActivityProductController` 转链接口改走应用层。
- `ProductService` 转链内部改依赖 `DouyinConvertPort`，不再直接注入 `DouyinPromotionGateway`。

## 测试

```text
mvn test -Dtest=CopyPromotionApplicationServiceTest,ColonelActivityProductControllerCopyPromotionTest,ProductServicePromotionPortArchitectureTest,DouyinPromotionGatewayConvertAdapterTest
Tests run: 4, Failures: 0, Errors: 0
BUILD SUCCESS
```

## 结论

PARTIAL — 单元测试 PASS；全量 `mvn test` / agent-do 本轮未跑完。

## 剩余风险

- Spring 上下文集成测试与 E2E 转链链路待 DDD-VERIFY-001 覆盖。
- Batch 6 CLEAN 任务尚未移除 legacy 跨域 Mapper。
