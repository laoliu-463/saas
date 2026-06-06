# Evidence Report — DOUYIN-SECRET-RUNTIME-VERIFY-002 + ORDER-SYNC-FRESHNESS-SLA-VERIFY-001

## 基本信息

| 项目 | 值 |
|---|---|
| 时间 | 2026-06-06T17:13:12+08:00 |
| 环境 | local real-pre |
| 分支 | feature/auth-system |
| commit | 696cc902 |
| 工作区 | dirty (热同步相关文件已有变更) |

## 1. DOUYIN-SECRET-RUNTIME-VERIFY-002 — 运行态秘钥验证

### 1.1 问题发现

容器重建后 `signature-invalid` 持续出现，6468 / 2704 / 商品接口 / token refresh 全部失败。

### 1.2 根因定位

| 来源 | DOUYIN_CLIENT_SECRET 前缀 |
|---|---|
| `.env.real-pre` 文件 | `7bc053a4****` |
| `.env` 文件 (默认) | `28973aaf****` (旧值) |
| 容器运行态 (重建前) | `28973aaf****` (旧值) |

**根因**: Docker Compose v5.0.2 自动加载项目根目录 `.env` 文件，当 `.env` 和 `env_file: .env.real-pre` 同时定义相同变量时，`.env` 的值覆盖了 `.env.real-pre` 的值。

### 1.3 修复操作

```powershell
# 使用 --env-file 显式指定，确保 .env.real-pre 优先
docker compose --env-file .env.real-pre -f docker-compose.real-pre.yml up -d --force-recreate backend-real-pre
```

### 1.4 修复验证

| 检查项 | 结果 |
|---|---|
| `.env.real-pre` DOUYIN_CLIENT_SECRET | `7bc053a4****` (最新) |
| 容器运行态 DOUYIN_CLIENT_SECRET | `7bc053a4****` (已一致) |
| Token refresh | SUCCESS |
| 6468 API | SUCCESS (`buyin.instituteOrderColonel`) |
| 2704 API | SUCCESS (`buyin.colonelMultiSettlementOrders`) |
| signature-invalid (过去 10 分钟) | **0 次** |

## 2. ORDER-SYNC-FRESHNESS-SLA-VERIFY-001 — 热同步 SLA 观察

### 2.1 观察窗口

`09:02 ~ 09:12 UTC`（共 11 轮，连续 10 分钟）

### 2.2 每轮 Freshness Lag

| 轮次 | 时间 (UTC) | Orders | Inserted | Updated | Lag (s) | stopReason |
|---|---|---|---|---|---|---|
| 1 | 09:02 | 48 | 48 | 0 | 31 | EMPTY_PAGE |
| 2 | 09:03 | 30 | 7 | 23 | 30 | EMPTY_PAGE |
| 3 | 09:04 | 30 | 11 | 19 | 30 | EMPTY_PAGE |
| 4 | 09:05 | 21 | 3 | 18 | 41 | EMPTY_PAGE |
| 5 | 09:06 | 29 | 14 | 15 | 30 | EMPTY_PAGE |
| 6 | 09:07 | 24 | 5 | 19 | 34 | EMPTY_PAGE |
| 7 | 09:08 | 27 | 8 | 19 | 50 | EMPTY_PAGE |
| 8 | 09:09 | 25 | 11 | 14 | 35 | EMPTY_PAGE |
| 9 | 09:10 | 33 | 14 | 19 | 34 | EMPTY_PAGE |
| 10 | 09:11 | 39 | 14 | 25 | 31 | EMPTY_PAGE |
| 11 | 09:12 | 39 | 11 | 28 | 31 | EMPTY_PAGE |

### 2.3 SLA 统计

```
排序后: 30, 30, 30, 31, 31, 31, 34, 34, 35, 41, 50
P50 = 31s
P95 = 50s
P99 ≈ 50s (样本量有限)
MAX = 50s
MIN = 30s
```

**P95 = 50s ≤ 120s → PASS**

### 2.4 数据完整性 (SQL 验收)

| 指标 | 值 | 要求 | 结果 |
|---|---|---|---|
| 订单总数 | 12,176 | — | — |
| 业绩总数 | 12,176 | = 订单数 | PASS |
| anti-join | 0 | = 0 | PASS |
| 重复业绩 | 0 | = 0 | PASS |
| freshness_lag (DB) | 85s | ≤ 120s | PASS |

> 注：DB freshness_lag 使用 `(NOW() AT TIME ZONE 'UTC' + INTERVAL '8 hours') - MAX(pay_time)` 计算，因 pay_time 存储为 UTC+8 无时区。

## 3. 验收标准检查

| 验收项 | 要求 | 实际 | 结论 |
|---|---|---|---|
| 热同步每分钟触发 | 连续 ≥ 10 轮 | 11 轮连续 | PASS |
| freshnessLagSeconds P95 | ≤ 120s | 50s | PASS |
| anti-join | = 0 | 0 | PASS |
| dupPerformance | = 0 | 0 | PASS |
| latest_pay_time 持续推进 | 每轮更新 | 持续递增 | PASS |
| signature-invalid | = 0 | 0 (10min 窗口) | PASS |
| 独立锁 | 使用 ORDER_SYNC_INSTITUTE_HOT | 已验证 | PASS |
| 独立水位 | 失败不推进 | 已验证 (47 测试 PASS) | PASS |
| hot lag = 30s | 不用全局 lag | 已验证 | PASS |
| 窗口 300s + overlap 120s | 配置正确 | 已验证 | PASS |
| maxPages=10 / maxOrders=1000 | 安全上限 | 已验证 | PASS |
| 10 分钟 INSTITUTE_RECENT 补偿保留 | 保留 | 已验证 | PASS |
| 后端构建 | BUILD SUCCESS | 已验证 | PASS |
| 容器健康 | /api/system/health = UP | UP | PASS |

## 4. 结论

**PASS**

ORDER-SYNC-FRESHNESS-OPTIMIZE-001 全部验收项通过。

## 5. 根因总结

此前 PARTIAL 的唯一阻塞项是运行态秘钥不一致：
- `.env` (旧值 `28973aaf`) 被 Docker Compose 自动加载
- `.env.real-pre` (新值 `7bc053a4`) 的 `env_file` 属性被覆盖
- 修复：使用 `--env-file .env.real-pre` 显式指定优先级

## 6. 剩余风险

1. **docker compose 启动命令需始终带 `--env-file .env.real-pre`**：否则 `.env` 旧值会再次覆盖。建议更新 `harness/commands/agent-do.ps1` 或 `harness/environment/` 中的启动脚本。
2. **热同步与补偿同步共用熔断器**：高频 hot 失败仍可能影响其他同步任务，建议后续隔离。
3. **pay_time 时区存储**：`colonelsettlement_order.pay_time` 以 UTC+8 无时区方式存储，查询需显式处理偏移。
