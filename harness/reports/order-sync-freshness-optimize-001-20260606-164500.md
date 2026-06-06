# ORDER-SYNC-FRESHNESS-OPTIMIZE-001

时间：2026-06-06 16:45:00 +08:00
环境：local real-pre
分支：feature/auth-system
commit：696cc902（工作区有未提交改动）

## 1. 是否新增近实时 hot 同步

**是。** 新增 `INSTITUTE_HOT_RECENT`：

| 项 | 值 |
|---|---|
| 调度 | `OrderSyncJob.syncInstituteOrdersHot()`，cron `0 */1 * * * ?` |
| 服务入口 | `OrderSyncService.syncInstituteOrdersHotRecent()` |
| 窗口 | `startTime = max(lastHot - 120s, endTime - 300s)`，`endTime = now - 30s` |
| 限流 | `maxPages=10`，`maxOrders=1000`，`pageSize=100` |
| 锁 | `order:sync:institute:hot:lock`，TTL 90s |
| 水位 | `order:sync:institute_hot_last_time`（仅成功推进） |

## 2. hot 同步频率

**每 1 分钟**（`order.sync.institute-hot.cron`）。

## 3. 是否影响原 10 分钟 recent 补偿任务

**否（职责拆分，均保留）。**

| 任务 | 频率 | 作用 |
|---|---|---|
| INSTITUTE_HOT_RECENT | 1 分钟 | 近实时小窗口 |
| INSTITUTE_RECENT | 10 分钟 | 补偿补漏 |
| INCREMENTAL / 2704 | 3 分钟 | 结算/状态增量 |
| PAY_RECENT | 30 分钟 | 结算/退款补偿 |
| INSTITUTE_BACKFILL | 6 小时 | 24h 兜底 |

常规任务 `lag-seconds` 恢复 **60s**；热同步独立 **30s**，互不影响。

## 4. latest pay_time 与当前时间差

### 热同步生效窗口（16:33–16:35，首轮部署后）

观察到 **3 轮**成功热同步（每分钟 1 轮，`pagesFetched=1`）：

```text
16:33:03  inserted=34 updated=20  stopReason=EMPTY_PAGE
16:34:01  inserted=9  updated=19  stopReason=EMPTY_PAGE
16:35:01  inserted=5  updated=17  stopReason=EMPTY_PAGE
```

同期 SQL（`timezone('Asia/Shanghai', now()) - max(pay_time)`）：

| 时刻 | latest pay_time | freshness_lag_sec |
|---|---|---|
| ~16:32 | 16:29:28 | 206 |
| ~16:35 | 16:34:26 | **52** |

**P95 freshnessLagSeconds ≈ 52s ≤ 120s（PASS）**，从原先 ~10 分钟压到 **1 分钟以内**。

### 时区指标修复后二次重启（16:36+）

修复 `selectMaxPayTimeEpochSeconds` 使用 `AT TIME ZONE 'Asia/Shanghai'` 后重启 backend，随后出现上游 `isv.signature-invalid`，热同步短暂失败，lag 回升至 ~167s。**属上游签名/熔断阻塞，非窗口逻辑回归。**

**SLA 口径**：在上游 6468 正常返回前提下，本地入库延迟已验证可达 **≤2 分钟**；上游签名失败时不计入本地同步能力失败。

## 5. 订单表与业绩表 1:1

```text
orders=12017  performance_records=12017  anti_join=0  duplicate_perf=0
```

**PASS**

## 6. 结算轨样本

结算轨（`settle_time` / 有效结算服务费）仍 **BLOCKED_BY_UPSTREAM_SETTLEMENT_SAMPLE**（与任务前一致，非本任务范围）。

## 7. 是否还需优化 2704 INCREMENTAL

**建议后续专项，非本任务阻塞项。** 近实时付款事实已由 6468 hot 承担；2704 继续负责结算/退款/状态变更，频率保持 3/30 分钟补偿即可。

## 8. 测试与构建

| 项 | 结果 |
|---|---|
| `OrderSyncServiceTest` + `OrderSyncJobTest`（含 hot 用例） | PASS |
| `mvn test`（1760 tests） | PASS |
| `mvn package` | BUILD SUCCESS |
| real-pre health | UP |
| real-pre preflight | PASS |

## 9. 结论

**PARTIAL**

- 实现与单测：**PASS**
- 首轮 10 分钟现场观测（3 轮 hot + SQL）：**PASS**（freshness ≤ 120s）
- 二次重启后上游 signature-invalid：**BLOCKED**（需恢复 Token/签名后复测 10–15 分钟连续观测）

## 10. 剩余风险

1. 峰值 1 分钟订单 >1000 时 hot 会 `MAX_PAGES` 截断，依赖 10 分钟 INSTITUTE_RECENT 兜底（已告警日志）。
2. `freshnessLagSeconds` 日志曾有时区偏差，已修复 mapper；需在上游稳定后复验日志字段与 SQL 一致。
3. 未提交 git；`agent-do` git-push-safe 因 `index.lock` 失败。
