# Handover: DDD-PERF-005

- **Task ID**: DDD-PERF-005
- **Developer**: Antigravity Agent
- **Status**: COMPLETED

## 交付与修改内容
1. 新增了 `ExclusiveMerchantPolicy` 判定策略与 `ExclusiveMerchantRepository` 数据仓库接口（含 adapter 实现）。
2. 在业绩域成功增加了 `ExclusiveMerchantApplicationService` 作为独立评估应用服务，完成了商家独家资格的编排。
3. 提取并设计了 `PerformanceAttributionPolicy`，实现当存在独家商家归属时优先覆盖最终招商的业务规则。
4. 单元测试 `ExclusiveMerchantApplicationServiceTest` 等共 9 项测试全部 PASS。
