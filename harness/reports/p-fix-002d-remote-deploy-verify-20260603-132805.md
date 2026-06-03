# P-FIX-002D-REMOTE 远端部署对齐：商品同步修复验证

## 1. 任务概述

| 项目 | 值 |
| --- | --- |
| 任务编号 | P-FIX-002D-REMOTE |
| 任务名称 | 远端部署对齐商品同步修复并验证 5 分钟同步任务 |
| 执行时间 | 2026-06-03 13:10 ~ 13:28 |
| 远端服务器 | VM-0-12-ubuntu (SSH alias: saas) |
| 是否部署远端 | **是** |
| 是否执行手工数据库写操作 | **否** |
| 是否清库 | **否** |
| 分支 | feature/auth-system |
| 远端 commit | **dea06e4c** |

## 2. Harness 读取情况

已读取：

- `AGENTS.md` ✓
- `CLAUDE.md` ✓
- `harness/CURRENT_STATE.md` ✓
- `harness/state/DOMAIN_STATUS.md` ✓
- `harness/state/KNOWN_ISSUES.md` ✓
- `harness/HARNESS_CHANGELOG.md` ✓
- `harness/runbooks/remote-deploy.md` ✓
- `harness/commands/deploy-remote.ps1` ✓
- `harness/reports/p-fix-002-product-sync-display-5min-20260603-121257.md` ✓
- `harness/reports/p-fix-002d-real-pre-runtime-verify-20260603-123411.md` ✓

## 3. 本地基线确认

| 项目 | 值 |
| --- | --- |
| 本地 HEAD | `dea06e4c` |
| 本地 branch | `feature/auth-system` |
| 远端 origin (GitHub) | `dea06e4cadadd7835b724ffdf4b7afe25c6094d7` |
| 远端 gitee | 部署前 `1ac7796` → 推送后 `dea06e4c` |
| 本地 dirty | 106 文件（其他任务残留，未影响远端部署） |

**关键操作**：发现远端服务器从 Gitee 拉取（非 GitHub），先执行 `git push gitee feature/auth-system` 同步 `dea06e4c` 到 Gitee，再在远端 `git pull --ff-only gitee`。

## 4. 远端部署前状态

| 项目 | 值 |
| --- | --- |
| 远端路径 | `/opt/saas/app` |
| 远端 branch | `feature/auth-system` |
| 远端 commit（部署前） | `bab9f15e` |
| 远端 dirty | 无 |
| 远端 remote | `gitee` (https://gitee.com/cao-jianing463/saas.git) |
| 远端容器 | 4 个均 healthy |

## 5. 远端代码对齐

| 步骤 | 结果 |
| --- | --- |
| `git fetch gitee feature/auth-system` | 成功 |
| 第一次 `git pull --ff-only` | 到 `1ac7796`（Gitee 未有 dea06e4c） |
| 本地 `git push gitee feature/auth-system` | 成功推送 `1ac7796f..dea06e4c` |
| 第二次 `git pull --ff-only` | 到 `dea06e4`（12 files changed, +1237/-5） |
| **最终 commit** | **`dea06e4`** |
| **是否等于 dea06e4c** | **是** |

## 6. 远端 env 参数

### 部署前检查

远端 `/opt/saas/env/.env.real-pre` **缺失** `PRODUCT_ACTIVITY_SYNC_*` 参数。

### 补齐操作

追加到远端 env 文件：

```env
# P-FIX-002A: product activity sync (5 min)
PRODUCT_ACTIVITY_SYNC_ENABLED=true
PRODUCT_ACTIVITY_SYNC_CRON=0 */5 * * * ?
```

### compose config 确认

```
PRODUCT_ACTIVITY_SYNC_CRON: 0 */5 * * * ?
PRODUCT_ACTIVITY_SYNC_ENABLED: "true"
```

### 容器内 printenv 确认

```
PRODUCT_ACTIVITY_SYNC_CRON=0 */5 * * * ?
PRODUCT_ACTIVITY_SYNC_ENABLED=true
```

## 7. 构建与重启过程

| 步骤 | 命令 / 结果 |
| --- | --- |
| Maven 构建 | `docker run maven:3.9.10-eclipse-temurin-17 mvn -f backend/pom.xml -DskipTests package` |
| jar 大小 / 时间 | 77MB / Jun 3 05:17 (UTC) |
| 构建结果 | **BUILD_DONE** |
| 重启命令 | `docker compose up -d --build backend-real-pre frontend-real-pre` |
| backend-real-pre | Recreated + Started + **Healthy** |
| frontend-real-pre | Recreated + Started + **Healthy** |
| postgres-real-pre | Running + Healthy |
| redis-real-pre | Running + Healthy |
| 是否使用 down -v | **否** |

## 8. 运行态日志验证

### 后端健康检查

```
GET http://127.0.0.1:8081/api/system/health → {"status":"UP"}
```

### 前端健康检查

```
GET http://127.0.0.1:3001/healthz → ok
```

### jar 时间

```
/app/app.jar  79952999 bytes  Jun 3 05:17 (UTC)
```

### ProductActivitySyncJob 启动日志

```
2026-06-03T05:18:41.249Z INFO ProductActivitySyncJob config:
  enabled=true, cron=0 */5 * * * ?, batchSize=20, whitelist=(all active)
```

## 9. 5 分钟同步周期观察

### 观察时长

05:18 UTC ~ 05:26 UTC（覆盖 05:20 和 05:25 两个 5 分钟边界）

### 第一周期：05:20 UTC

```
05:20:09 activity synced, activityId=3929906, syncedProductCount=157,
  libraryEntryCount=0, createdCount=134, updatedCount=23
05:20:19 activity synced, activityId=3929905, syncedProductCount=136,
  libraryEntryCount=115, createdCount=90, updatedCount=46
05:20:39 activity synced, activityId=3920684, syncedProductCount=371,
  libraryEntryCount=40, createdCount=12, updatedCount=359
05:21:43 finished, ok=5, fail=0
```

### 第二周期：05:25 UTC

```
05:25:00 finished, ok=0, fail=0
```

### 关键检查

| 检查项 | 结果 |
| --- | --- |
| DuplicateKeyException | **无** |
| uk_pos_one_displaying_per_product 冲突 | **无** |
| API 瞬时错误 | 1 次 `isp.service-error:256`（抖音服务端，不影响整体同步） |
| ERROR 总数 | 1（仅上述瞬时错误） |

## 10. 远端 SQL 对账

### 对账结果

| 指标 | 部署前 (P-DIAG-002) | 部署后 (P-FIX-002D-REMOTE) | 变化 |
| --- | --- | --- | --- |
| product_snapshot 总数 | 3601 | **3846** | +245 |
| DISPLAYING | 420 | **604** | +184 |
| HIDDEN | — | 1114 | — |
| PENDING | — | 2128 | — |
| 重复 DISPLAYING | — | **0** | 无冲突 |

### 与本地 real-pre 对比

| 指标 | 本地 real-pre | 远端 real-pre |
| --- | --- | --- |
| product_snapshot | 7323 | 3846 |
| DISPLAYING | 2377 | 604 |
| HIDDEN | 4575 | 1114 |
| PENDING | 371 | 2128 |

远端快照少于本地（3846 vs 7323），因为远端历史同步活动较少。远端 PENDING 较多（2128），因为新同步的商品尚未被展示规则处理（需后续同步周期逐步处理或 P-FIX-002E repair）。

## 11. 远端商品库 API 验证

| 请求 | 结果 |
| --- | --- |
| pageSize=100 | code=200, **total=604** |
| pageSize=200 | code=200, **total=604** |
| SQL DISPLAYING | **604** |
| API vs SQL | **完全一致** |

## 12. 风险残留

| 风险 | 说明 |
| --- | --- |
| 远端 PENDING=2128 | 新同步商品尚未被展示规则处理，后续 5 分钟周期会逐步处理；若长时间不减少，需 P-FIX-002E repair |
| 远端快照少于本地 | 3846 vs 7323，远端历史活动较少，属正常差异 |
| 远端 env 手工修改 | 追加了 `PRODUCT_ACTIVITY_SYNC_ENABLED=true` 和 `PRODUCT_ACTIVITY_SYNC_CRON` 到 `/opt/saas/env/.env.real-pre`，需纳入文档 |
| 抖音 API 瞬时错误 | 1 次 `isp.service-error:256`，不影响整体同步 |
| Gitee 同步 | 远端服务器从 Gitee 拉取，需确保每次推送同时 push 到 gitee |

## 13. 最终状态

**DONE_REMOTE_VERIFIED**

- 远端 commit 对齐 `dea06e4c` ✓
- 同步参数生效（`enabled=true, cron=0 */5 * * * ?`）✓
- 两个 5 分钟周期正常执行（ok=5 + ok=0, fail=0）✓
- 零唯一索引冲突 ✓
- API total=604 与 SQL DISPLAYING=604 完全一致 ✓
- 远端商品库从 420 DISPLAYING 增长到 604 DISPLAYING（+184）✓

## 14. 下一步建议

1. **P-VERIFY-002**：远端同步稳定后（建议 1-2 小时）复核商品库数量，确认 PENDING 是否持续减少
2. **P-FIX-002E**：如远端 PENDING 长期不减少，需 repair 任务处理
