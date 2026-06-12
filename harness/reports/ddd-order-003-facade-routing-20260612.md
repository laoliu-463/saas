# DDD-ORDER-003 Facade 璺敱鎶ュ憡

鏃堕棿锛?026-06-12
浠诲姟锛欱atch3 Replace 鈥?OrderController 鏌ヨ鍒?Facade

## 鍙樻洿鎽樿

| 鍘熻皟鐢ㄧ偣 | 鏂拌皟鐢ㄧ偣 | 寮€鍏?| 鍥為€€ |
|---------|---------|------|------|
| `getOrders` / `getUnattributedOrders` | `OrderDomainFacade#getOrders` | `ddd.refactor.order-application.enabled` | 鍏抽棴鏃惰蛋 Controller 鍐?legacy wrapper |
| `getOrderDetail` | `OrderDomainFacade#getOrderDetail` | 鍚屼笂 | 濮旀淳 `OrderQueryService` |
| `getStats` | `OrderDomainFacade#getStats` | 鍚屼笂 | Controller 鍐?legacy 缁熻 SQL |

## 鏂板

- `OrderDomainFacade` / `LegacyOrderDomainFacade`
- `DddOrder003RoutingTest`

## 绾︽潫

- **鏈慨鏀?* `OrderSyncService` 鍚屾涓婚摼

## 楠岃瘉

- 瀹氬悜鍗曟祴锛歅ASS锛圖ddOrder003 + OrderController + OrderSyncController锛?- 寰?agent-do 瀹氱

## 缁撹

闃舵鎬?**PASS**
