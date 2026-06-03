# P-FIX-002D 本地 real-pre 运行态验证：商品同步修复加载与 5 分钟周期观察

## 1. 任务概述

| 项目 | 值 |
| --- | --- |
| 任务编号 | P-FIX-002D |
| 任务名称 | 本地 real-pre 重启加载商品同步修复并验证 5 分钟同步任务 |
| 执行时间 | 2026-06-03 12:24 ~ 12:34 |
| 是否重启容器 | **是**（backend-real-pre 重建并启动） |
| 是否执行手工数据库写操作 | **否** |
| 是否远端部署 | **否** |
| 分支 | feature/auth-system |
| commit hash | 1ac7796f（P-FIX-002 代码未提交，dirty 状态） |

## 2. Harness 读取情况

已读取：

- `AGENTS.md` ✓
- `CLAUDE.md` ✓
- `harness/CURRENT_STATE.md` ✓
- `harness/state/DOMAIN_STATUS.md` ✓
- `harness/state/KNOWN_ISSUES.md` ✓
- `harness/HARNESS_CHANGELOG.md` ✓
- `harness/reports/p-fix-002-product-sync-display-5min-20260603-121257.md` ✓
- `harness/commands/restart-compose.ps1` ✓
- `harness/skills/real-pre-safe-operation.md` ✓（前轮已读）
- `harness/skills/docker-restart-validate.md` ✗（不存在，KNOWN_ISSUES 已记录）

## 3. P-FIX-002 基线复述

P-FIX-002 包含四阶段，均已在上一轮完成：

- **A) 同步周期配置**：`@Scheduled` 默认 cron 改为 `0 */5 * * * ?`，compose environment 传递 `PRODUCT_ACTIVITY_SYNC_ENABLED=true` 和 `PRODUCT_ACTIVITY_SYNC_CRON`
- **B) 唯一索引冲突修复**：`ProductDisplayRuleService.applyNormalDisplayDedup` 改为两遍处理（Pass 1 降级 → Pass 2 升级），3 个新测试通过
- **C) 只读对账**：7284 快照 / 1958 展示中 / 无重复 DISPLAYING / 715 推广未展示
- **D) 远端参数对齐**：配置已准备，待独立部署

状态：`DONE_CONFIG_READY`，未重启容器，新 jar 未加载。

## 4. Git 工作区状态

### P-FIX-002 相关变更

| 文件 | 来源 |
| --- | --- |
| `backend/src/main/java/com/colonel/saas/job/ProductActivitySyncJob.java` | P-FIX-002A |
| `backend/src/main/java/com/colonel/saas/service/ProductDisplayRuleService.java` | P-FIX-002B |
| `backend/src/test/java/com/colonel/saas/service/ProductDisplayRuleServiceTest.java` | P-FIX-002B |
| `backend/src/main/resources/application.yml` | P-FIX-002A |
| `docker-compose.real-pre.yml` | P-FIX-002A |
| `.env.real-pre.example` | P-FIX-002A |
| `harness/commands/deploy-remote.ps1` | P-FIX-002A |
| `harness/runbooks/remote-deploy.md` | P-FIX-002A |
| `harness/CURRENT_STATE.md` | 状态回写 |
| `harness/state/DOMAIN_STATUS.md` | 状态回写 |
| `harness/state/KNOWN_ISSUES.md` | 状态回写 |
| `harness/HARNESS_CHANGELOG.md` | 状态回写 |

### 其他任务残留变更

| 文件 | 来源 |
| --- | --- |
| `frontend/src/components/product/ProductSelectionCard.test.ts` | FUNC-001 |
| `frontend/src/components/product/ProductSelectionCard.vue` | FUNC-001 |
| `frontend/src/views/product/ProductLibrary.vue` | P-FIX-001C |
| `frontend/src/views/product/ProductLibrary.test.ts` | P-FIX-001C (untracked) |
| `tests/e2e/03b-product-library-drawer-fields.spec.ts` | E2E |

### 其他 untracked 报告文件

| 文件 | 来源 |
| --- | --- |
| `harness/reports/func-001-*.md` | FUNC-001 |
| `harness/reports/p-diag-002-*.md` | P-DIAG-002 |
| `harness/reports/p-fix-001c-*.md` | P-FIX-001C |
| `harness/reports/p-fix-002-*.md` | P-FIX-002 |
| `harness/reports/p-fix-002a-*.md` | P-FIX-002A |

**结论**：P-FIX-002 代码未提交（dirty），本轮不提交，建议后续 GIT-P-FIX-002 安全拆分提交。

## 5. 构建结果

| 项目 | 值 |
| --- | --- |
| Maven 命令 | `mvn -f backend/pom.xml -DskipTests package` |
| jar 路径 | `backend/target/colonel-saas.jar` |
| jar 大小 | 79,932,391 bytes (79.9 MB) |
| jar 时间 | 2026-06-03 12:15 (本地) / Jun 3 04:15 (容器内 UTC) |
| 结果 | **BUILD SUCCESS** |

## 6. 容器重启过程

| 项目 | 值 |
| --- | --- |
| 使用脚本 | `harness/commands/restart-compose.ps1 -Env real-pre -Scope backend` |
| 底层命令 | `docker compose --env-file .env.real-pre -f docker-compose.real-pre.yml up -d --build backend-real-pre` |
| 重启容器 | backend-real-pre（Recreated + Started） |
| 其他容器 | postgres（Running）、redis（Running）、frontend（Running） |
| 是否使用 down -v | **否** |
| 是否删除 volume | **否** |
| Safety check | **PASSED** |

### 重启后容器状态

| 容器 | 状态 |
| --- | --- |
| saas-active-backend-real-pre-1 | Up (healthy) |
| saas-active-frontend-real-pre-1 | Up 52 minutes (healthy) |
| saas-active-postgres-real-pre-1 | Up 23 hours (healthy) |
| saas-active-redis-real-pre-1 | Up 23 hours (healthy) |

## 7. 运行态验证

### 7.1 后端健康检查

```
GET http://127.0.0.1:8081/api/system/health → {"status":"UP"}
```

### 7.2 新 jar 加载确认

```
/app/app.jar  79932391 bytes  Jun 3 04:15 (UTC)
```

与本地构建时间 12:15 (UTC+8) 一致，新 jar 已加载。

### 7.3 环境变量确认

```
PRODUCT_ACTIVITY_SYNC_ENABLED=true
PRODUCT_ACTIVITY_SYNC_CRON=0 */5 * * * ?
```

### 7.4 ProductActivitySyncJob 启动日志

```
2026-06-03T04:23:39.225Z INFO ProductActivitySyncJob config:
  enabled=true, cron=0 */5 * * * ?, batchSize=20, whitelist=(all active)
```

## 8. 5 分钟同步观察

### 观察时间

12:24 ~ 12:31（覆盖 04:25 和 04:30 两个 5 分钟边界）

### 第一周期：04:25 UTC

```
04:25:26 activity synced, activityId=3920684, syncedProductCount=370,
  libraryEntryCount=1, createdCount=1, updatedCount=369, skippedCount=0
04:26:15 activity synced, activityId=3916506, syncedProductCount=809,
  libraryEntryCount=22, createdCount=38, updatedCount=778, skippedCount=0
04:26:30 activity synced, activityId=3891192, syncedProductCount=251,
  libraryEntryCount=0, createdCount=0, updatedCount=251, skippedCount=0
04:26:30 finished, ok=3, fail=0
```

### 第二周期：04:30 UTC

```
04:30:11 finished, ok=0, fail=0
```

（无变更需要同步，符合预期）

### 关键检查结果

| 检查项 | 结果 |
| --- | --- |
| DuplicateKeyException | **无** |
| uk_pos_one_displaying_per_product 冲突 | **无** |
| 限流 / token 错误 | **无** |
| API 瞬时错误 | 1 次 `isp.service-error:256`（抖音服务端瞬时错误，不影响整体同步） |
| 同步 start/end 正常记录 | **是** |
| 商品数量合理变化 | **是**（新增 39 快照，419 商品新增为 DISPLAYING） |

## 9. 只读 SQL 对账

### 对账结果

| 指标 | P-FIX-002C（修复前） | P-FIX-002D（重启后同步） | 变化 |
| --- | --- | --- | --- |
| product_snapshot 总数 | 7284 | **7323** | +39 |
| product_operation_state 总数 | 7284 | 7323 | +39 |
| DISPLAYING | 1958 | **2377** | +419 |
| HIDDEN | 4571 | 4575 | +4 |
| PENDING | 755 | 371 | -384 |
| 重复 DISPLAYING | 0 | **0** | 无冲突 |

### 分析

- **+39 新快照**：3 个活动同步拉取了 39 个新商品
- **+419 DISPLAYING**：同步后展示规则重算将符合条件的商品正确设为 DISPLAYING
- **-384 PENDING**：大量此前卡 PENDING 的商品被正确处理（转为 DISPLAYING 或 HIDDEN）
- **两遍处理生效**：唯一索引零冲突，证明 Pass 1 降级 → Pass 2 升级方案有效

## 10. 商品库 API 验证

| 请求 | 结果 |
| --- | --- |
| `GET /api/products?page=1&pageSize=100&selectedToLibrary=true` | code=200, **total=2377** |
| `GET /api/products?page=1&pageSize=200&selectedToLibrary=true` | code=200, **total=2377** |
| SQL DISPLAYING 数量 | **2377** |
| API vs SQL 一致性 | **完全一致** |

### 前端页面验证

未执行浏览器验证。原因：本轮为 backend 容器重启验证，frontend 容器未重启且未变化。记录为未验证。

## 11. 风险残留

| 风险 | 说明 |
| --- | --- |
| 远端未部署 | 远端仍为旧 jar，同步仍禁用，数据仍未对齐 |
| 远端数据偏差 | 本地 2377 DISPLAYING vs 远端 420（P-DIAG-002 数据），差距显著 |
| 684→371 PENDING | 仍有 371 商品卡 PENDING，其中可能有过期活动商品，需 P-FIX-002E repair |
| P-FIX-002 代码未提交 | 所有变更在 dirty 状态，需 GIT-P-FIX-002 安全拆分提交 |
| 抖音 API 瞬时错误 | `isp.service-error:256` 出现 1 次，系统正常容错，非阻塞 |
| 前端页面未验证 | 本轮未打开浏览器验证商品库页面 |

## 12. 最终状态

**DONE_RUNTIME_VERIFIED**

- 本地 real-pre 新 jar 已加载（app.jar Jun 3 04:15 UTC）
- 同步配置生效（`enabled=true, cron=0 */5 * * * ?`）
- 两个 5 分钟周期正常执行（ok=3 + ok=0，fail=0）
- 零唯一索引冲突
- API total=2377 与 SQL DISPLAYING=2377 完全一致
- 可以进入远端部署对齐阶段

## 13. 下一步建议

1. **P-FIX-002D-remote（远端部署对齐）**：将新 jar 部署到远端 real-pre，验证同步参数生效
2. **GIT-P-FIX-002（安全拆分提交）**：将 P-FIX-002 相关变更从 dirty 工作区中安全拆分提交
3. **P-FIX-002E（PENDING 商品 repair）**：处理剩余 371 个 PENDING 商品
