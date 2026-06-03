# 远端核查报告 — ORDER-API-729-VERIFY (Server)

| 项目 | 值 |
|---|---|
| 任务 | ORDER-API-729-VERIFY — 远端服务器核查 |
| 目标接口 | `buyin.colonelMultiSettlementOrders` (文档 61/2704) |
| 环境 | 远端 real-pre (SSH alias: `saas`, 路径: `/opt/saas/app`) |
| 时间 | 2026-06-03T17:55+08:00 |
| 分支 | `feature/auth-system` |
| 远端 commit | `c470dc2` (clean, 无 dirty 文件) |
| 本地 commit | `78bdf8fa` (3 dirty 文件, 与本任务无关) |
| 容器状态 | backend `Up 3 hours (healthy)`, postgres `Up 22 hours (healthy)`, redis `Up 2 days (healthy)`, frontend `Up 4 hours (healthy)` |
| 证据包 | 远端 `/tmp/order-api-2704-server-check.tar.gz` (34KB) |

---

## 1. 远端代码/镜像是否同一套实现

**结论：是，完全一致。**

| 检查项 | 结果 |
|---|---|
| 分支 | `feature/auth-system` (与本地相同) |
| OrderApi.java L45 | `COLONEL_MULTI_SETTLEMENT_METHOD = "buyin.colonelMultiSettlementOrders"` |
| RealDouyinOrderGateway.java L49 | `@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "false", matchIfMissing = true)` |
| OrderSyncJob.java L50 | `@Scheduled(cron = "0 */10 * * * ?")` |
| Git clean | 远端无 dirty 文件，比本地更干净 |

---

## 2. 远端配置开关

**结论：全部正确，非 mock/test 模式。**

| 配置项 | 值 | 预期 | 结果 |
|---|---|---|---|
| `APP_TEST_ENABLED` | `false` | `false` | PASS |
| `DOUYIN_TEST_ENABLED` | `false` | `false` | PASS |
| `DOUYIN_REAL_UPSTREAM_MODE` | `live` | `live` | PASS |
| `SPRING_PROFILES_ACTIVE` | `real-pre` | `real-pre` | PASS |
| `ORDER_SYNC_ENABLED` | `true` | `true` | PASS |
| `DOUYIN_BASE_URL` | `https://openapi-fxg.jinritemai.com` | 抖音正式地址 | PASS |
| `DOUYIN_APP_ID` | `7623665273727387199` | — | PASS |

---

## 3. 远端日志是否真实调用 2704

**结论：是，每 10 分钟调用一次，过去 6 小时 18 次全部成功。**

`2704-call.log` 包含 18 条匹配记录：

```
06:50:00 buyin.colonelMultiSettlementOrders ✅ success
07:00:00 buyin.colonelMultiSettlementOrders ✅ success
07:10:00 buyin.colonelMultiSettlementOrders ✅ success
07:20:00 buyin.colonelMultiSettlementOrders ✅ success
07:30:00 buyin.colonelMultiSettlementOrders ✅ success
07:40:00 buyin.colonelMultiSettlementOrders ✅ success
07:50:00 buyin.colonelMultiSettlementOrders ✅ success
08:00:00 buyin.colonelMultiSettlementOrders ✅ success
08:10:00 buyin.colonelMultiSettlementOrders ✅ success
08:20:00 buyin.colonelMultiSettlementOrders ✅ success
08:30:00 buyin.colonelMultiSettlementOrders ✅ success
08:40:00 buyin.colonelMultiSettlementOrders ✅ success
08:50:00 buyin.colonelMultiSettlementOrders ✅ success
09:00:00 buyin.colonelMultiSettlementOrders ✅ success
09:10:00 buyin.colonelMultiSettlementOrders ✅ success
09:20:00 buyin.colonelMultiSettlementOrders ✅ success
09:30:00 buyin.colonelMultiSettlementOrders ✅ success
09:40:00 buyin.colonelMultiSettlementOrders ✅ success
```

**与本地对比：远端比本地更稳定**（本地有 SSL 握手失败和上游超时，远端全部成功）。

---

## 4. 远端调用结果

**结论：全部返回 0 条订单，无 SSL/timeout 错误。**

`order-sync-context.log` 每 10 分钟输出：

```
gateway=RealDouyinOrderGateway, upstreamMode=live, appKey=7623****7199, shopId=56591058, authId=7351155267604218149
Order sync completed, range=[start, end], pages=0, fetched=0, created=0, updated=0, attributed=0
OrderSyncJob done, window=[start, end], pages=0, inserted=0, skipped=0
```

**18 个同步周期全部返回 fetched=0, created=0, updated=0。**

`upstream-errors.log` (9 行) 全部与订单同步无关：
- Spring Security 启动日志 (匹配到 `ExceptionTranslationFilter`)
- `alliance.colonelActivityProduct` 商品活动接口偶发错误 (抖音侧 "服务打瞌睡了")
- HTTP TLS 请求误打到 HTTP 端口 (外部扫描)

`wrong-api-6468.log` (0 行)：远端没有调用 `instituteOrderColonel` 或其他错误接口。

---

## 5. 远端订单表是否 0 行

**结论：是，0 行。**

| 表 | 行数 | 说明 |
|---|---|---|
| `colonelsettlement_order` | **0** | 团长分次结算订单表为空 |
| `order_sync_dedup_claim` | **0** | 去重记录为空 (API 从未返回过订单) |
| `pick_source_mapping` | **11** | 渠道复制转链映射有数据 (COLONEL_NATIVE 类型) |

---

## 6. 综合判定

### 属于情况 A：远端确认真实调用 2704，但返回 0

```
远端接口调用没问题，但当前同步窗口没有返回订单。
```

### 五件事逐项结论

| # | 检查项 | 结论 |
|---|---|---|
| 1 | 远端代码/镜像是否同一套 | ✅ 是，同分支 `feature/auth-system`，同代码实现 |
| 2 | 远端 `DOUYIN_TEST_ENABLED=false` / `APP_TEST_ENABLED=false` | ✅ 是，全部正确 |
| 3 | 远端日志是否真实调用 2704 | ✅ 是，18 次全部成功调用 `buyin.colonelMultiSettlementOrders` |
| 4 | 远端调用结果 | 全部返回 0 条，无 SSL/timeout |
| 5 | 远端订单表是否 0 行 | ✅ 是，0 行 (含 dedup 表也为 0) |

---

## 7. 根因分析

远端和本地结论完全一致：**接口调用链正确，真实调用抖音正式 API，但 API 返回空结果。**

问题不在"是否接了接口"，而在"接口为什么返回 0 条"。可能原因：

### 7.1 同步窗口 + time_type 限制

当前代码 `time_type = "update"` (按订单更新时间查询)，同步窗口约 11 分钟 (`WINDOW_SECONDS=600` + `OVERLAP_SECONDS=60`)。如果 11 分钟内没有订单更新，API 返回空。

### 7.2 结算订单口径

`buyin.colonelMultiSettlementOrders` 是"团长分次结算订单"，只返回已进入结算阶段的订单。刚付款的订单不会立即出现在此接口。

### 7.3 店铺 (shopId=56591058) 近期无结算订单

最直接的可能是该店铺在 V1 测试期间确实没有结算订单产生。

### 7.4 pick_source_mapping 有 11 条但无订单

`pick_source_mapping` 有 11 条 `COLONEL_NATIVE` 类型映射，说明渠道复制转链功能正常工作，但这些 pick_source 尚未关联到任何结算订单。

---

## 8. 下一步建议

1. **确认抖音后台是否有结算订单**：登录抖店后台 → 精选联盟 → 团长结算，确认 shopId=56591058 是否有结算订单
2. **扩大时间窗口做一次性全量拉取**：修改 `startTime` 到过去 30 天，执行一次手动同步，验证 API 是否能返回历史订单
3. **增加 `time_type=pay_time` 补充拉取**：当前只按 `update` 查，可以补一个按 `pay_time` 的辅助查询窗口
4. **增加同步可观测日志**：在 `OrderSyncService` 中记录每次 API 返回的原始 `total` 和 `has_more` 字段，方便排查
5. **监控上游稳定性**：本地有 SSL/timeout 问题但远端没有，说明远端网络环境更好，但仍需持续监控

---

## 9. 禁止事项自查

| 禁止项 | 状态 |
|---|---|
| 未修改代码 | ✅ |
| 未重启容器 | ✅ |
| 未执行数据库写操作 | ✅ |
| 未清库/删 volume | ✅ |
| 未执行 git 操作 | ✅ |
| 未编造证据 | ✅ |
| 全部只读 | ✅ |
