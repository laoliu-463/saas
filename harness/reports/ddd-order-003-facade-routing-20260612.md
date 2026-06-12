# DDD-ORDER-003 Facade 路由报告

时间：2026-06-12
任务：Batch3 Replace — OrderController 查询切 Facade

## 变更摘要

| 原调用点 | 新调用点 | 开关 | 回退 |
|---------|---------|------|------|
| `getOrders` / `getUnattributedOrders` | `OrderDomainFacade#getOrders` | `ddd.refactor.order-application.enabled` | 关闭时走 Controller 内 legacy wrapper |
| `getOrderDetail` | `OrderDomainFacade#getOrderDetail` | 同上 | 委派 `OrderQueryService` |
| `getStats` | `OrderDomainFacade#getStats` | 同上 | Controller 内 legacy 统计 SQL |

## 新增

- `OrderDomainFacade` / `LegacyOrderDomainFacade`
- `DddOrder003RoutingTest`

## 约束

- **未修改** `OrderSyncService` 同步主链

## 验证

- 定向单测：PASS（DddOrder003 + OrderController + OrderSyncController）
- 待 agent-do 定稿

## 结论

阶段性 **PASS**
