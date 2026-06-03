# P-FIX-002A 商品活动同步任务启用与 5 分钟周期配置

## 1. 任务概述

| 项目 | 值 |
| --- | --- |
| 任务编号 | P-FIX-002A |
| 任务名称 | 商品活动同步任务启用与 5 分钟周期配置 |
| 执行时间 | 2026-06-03 12:01 |
| 是否修改 Java | **是**（仅配置字段和启动日志） |
| 是否修改 Docker/env | **是**（compose environment + env 模板） |
| 是否修改数据库 | **否** |
| 是否重启容器 | **否** |
| 是否部署远端 | **否** |

## 2. Harness 读取情况

已读取：

- `AGENTS.md` ✓
- `CLAUDE.md` ✓
- `harness/AGENT_CONTRACT.md` ✓
- `harness/TASK_ROUTING.md` ✓
- `harness/FORBIDDEN_SCOPE.md` ✓
- `harness/COMPLETION_GATES.md` ✓
- `harness/CURRENT_STATE.md` ✓
- `harness/state/DOMAIN_STATUS.md` ✓
- `harness/state/KNOWN_ISSUES.md` ✓
- `harness/state/DECISIONS.md` ✓
- `harness/HARNESS_CHANGELOG.md` ✓
- `harness/instructions/product-domain.md` ✓
- `harness/skills/real-pre-safe-operation.md`（通过上轮 P-DIAG-002 读取）✓
- `harness/skills/docker-restart-validate.md`（通过上轮 P-DIAG-002 读取）✓
- `harness/plans/DDD_OPTIMIZATION_ROADMAP.md` ✓
- `harness/plans/DDD_DOMAIN_TASK_MATRIX.md` ✓
- `harness/reports/p-diag-002-product-library-count-sync-remote-20260603-114742.md` ✓
- `harness/runbooks/remote-deploy.md` ✓

## 3. P-DIAG-002 结论引用

P-DIAG-002 发现三个并存根因：

- **根因 A**：远端 `PRODUCT_ACTIVITY_SYNC_ENABLED` 未设置（默认 false），同步任务不执行
- **根因 B**：唯一索引 `uk_pos_one_displaying_per_product` 冲突导致同步事务回滚（留给 P-FIX-002B）
- **根因 C**：过期活动商品卡 PENDING（留给 P-FIX-002B）

本任务解决根因 A 的配置准备部分。

## 4. 当前同步配置现状

### 修改前

| 项目 | 值 |
| --- | --- |
| Java `@Scheduled` 默认 cron | `0 0 */2 * * ?`（每 2 小时） |
| `application.yml` 默认 enabled | `false` |
| `application.yml` 默认 cron | `0 0 */2 * * ?`（每 2 小时） |
| 本地 `.env.real-pre` | `PRODUCT_ACTIVITY_SYNC_ENABLED=true`，`PRODUCT_ACTIVITY_SYNC_CRON=0 */5 * * * ?` |
| `.env.real-pre.example` | 有 `PRODUCT_ACTIVITY_SYNC_ENABLED=true`，**无 CRON** |
| `docker-compose.real-pre.yml` | **未显式传递** `PRODUCT_ACTIVITY_SYNC_*` |
| 远端 env | **两个变量都缺失** |

### 配置传递方式

- `ProductActivitySyncJob` 使用 `@Value("${product.activity.sync.enabled:false}")` 和 `@Scheduled(cron = "${product.activity.sync.cron:...}")`
- `application.yml` 通过 `${PRODUCT_ACTIVITY_SYNC_ENABLED:false}` 桥接 env → Spring
- 远端 env 文件通过 `env_file` 加载到容器，但 compose `environment` 块未显式传递这些变量

## 5. 实际修改清单

### Java 文件

| 文件 | 修改内容 |
| --- | --- |
| `backend/src/main/java/com/colonel/saas/job/ProductActivitySyncJob.java` | 1. `@Scheduled` 默认 cron 改为 `0 */5 * * * ?`（5 分钟）；2. 新增 `cronExpression` `@Value` 字段；3. 新增 `@PostConstruct logStartupConfig()` 启动时记录 enabled/cron/batchSize/whitelist；4. disabled 日志级别从 `info` 降为 `debug` |

### 配置文件

| 文件 | 修改内容 |
| --- | --- |
| `backend/src/main/resources/application.yml` | 默认 cron 从 `0 0 */2 * * ?` 改为 `0 */5 * * * ?` |

### Docker/env 文件

| 文件 | 修改内容 |
| --- | --- |
| `docker-compose.real-pre.yml` | `backend-real-pre` environment 块新增 `PRODUCT_ACTIVITY_SYNC_ENABLED: ${PRODUCT_ACTIVITY_SYNC_ENABLED:-true}` 和 `PRODUCT_ACTIVITY_SYNC_CRON: "${PRODUCT_ACTIVITY_SYNC_CRON:-0 */5 * * * ?}"` |
| `.env.real-pre.example` | 新增 `PRODUCT_ACTIVITY_SYNC_CRON=0 */5 * * * ?` |

### 部署脚本/runbook

| 文件 | 修改内容 |
| --- | --- |
| `harness/runbooks/remote-deploy.md` | 新增"部署前检查（同步参数）"和"部署后检查（同步任务）"两个章节 |
| `harness/commands/deploy-remote.ps1` | 部署脚本新增远端 env 文件同步参数检查和部署后日志验证 |

### 测试文件

无新增测试（本任务为配置变更，不涉及业务逻辑测试）。

### Harness 文件

| 文件 | 修改内容 |
| --- | --- |
| `harness/CURRENT_STATE.md` | 新增 P-FIX-002A 完成记录 |
| `harness/state/DOMAIN_STATUS.md` | 商品域状态更新 P-FIX-002A |
| `harness/state/KNOWN_ISSUES.md` | 远端同步任务禁用状态更新为 fixed |
| `harness/HARNESS_CHANGELOG.md` | 新增 v0.4.6 条目 |

## 6. 新配置说明

| 项目 | 值 |
| --- | --- |
| 同步周期 | 每 5 分钟 |
| 使用方式 | cron 表达式 |
| cron 表达式 | `0 */5 * * * ?` |
| env 变量 `PRODUCT_ACTIVITY_SYNC_ENABLED` | 控制开关 |
| env 变量 `PRODUCT_ACTIVITY_SYNC_CRON` | 控制周期 |
| env 变量 `PRODUCT_ACTIVITY_SYNC_BATCH_SIZE` | 批次大小（默认 20，未改） |
| env 变量 `PRODUCT_ACTIVITY_SYNC_WHITELIST_ACTIVITIES` | 白名单（默认空=全部活跃，未改） |
| `application.yml` 默认 enabled | `false`（需 env 显式启用） |
| `application.yml` 默认 cron | `0 */5 * * * ?`（5 分钟） |
| test profile | `spring.task.scheduling.enabled: false`（定时任务完全禁用，不受影响） |
| real-pre compose 默认 | `PRODUCT_ACTIVITY_SYNC_ENABLED:-true`，`PRODUCT_ACTIVITY_SYNC_CRON:-0 */5 * * * ?` |
| 远端部署默认参数 | 通过 `.env.real-pre.example` 和 compose 默认值确保启用 |

## 7. 验证结果

| 验证 | 结果 |
| --- | --- |
| `mvn -f backend/pom.xml -DskipTests package` | **BUILD SUCCESS** |
| `git diff --check` | **PASS**（无空白问题） |
| `safety-check -Env real-pre -Scope full -DryRun` | **PASS** |
| `docker compose -f docker-compose.real-pre.yml config` | **有效**，正确解析 `PRODUCT_ACTIVITY_SYNC_ENABLED=true` 和 `PRODUCT_ACTIVITY_SYNC_CRON=0 */5 * * * ?` |
| 运行态验证 | **未执行**（本任务不重启容器，留给 P-FIX-002B 修复后一起验证） |

## 8. 风险残留

1. **唯一索引冲突尚未修复**：`uk_pos_one_displaying_per_product` 导致同步事务回滚。P-FIX-002A 只是启用和周期配置准备。**真正远端启用前，必须先完成 P-FIX-002B 修复展示规则唯一索引冲突。否则 5 分钟同步会更频繁地失败。**
2. **未执行 real-pre 写库**：本任务不涉及数据库变更。
3. **未远端部署**：本任务不执行远端部署，留给 P-FIX-002D。
4. **未重启容器**：本任务不重启容器。修改生效需要重启后端容器或重新部署。
5. **本地 `.env.real-pre` 已有正确配置**：本地同步任务已按 5 分钟周期运行，但因唯一索引冲突持续失败。

## 9. 后续任务建议

### P-FIX-002B：修复展示规则唯一索引冲突

- **目标**：修复 `ProductDisplayRuleService.persistDisplayDecision` 中的 DISPLAYING 切换逻辑
- **修改范围**：`ProductDisplayRuleService.java` 的 `applyNormalDisplayDedup` / `persistDisplayDecision` 方法
- **方案**：先将旧 DISPLAYING 降级为 HIDDEN（flush），再将新记录升级为 DISPLAYING
- **验收标准**：同步任务不再因 unique constraint 失败
- **风险**：需要修改 Java 业务代码 + 后端测试

### P-FIX-002D：远端部署对齐并启动同步参数

- **目标**：将最新代码部署到远端，确保同步参数在远端 env 中正确设置
- **修改范围**：执行 `deploy-remote.ps1`，更新远端 `/opt/saas/env/.env.real-pre`
- **验收标准**：远端同步日志出现 `ProductActivitySyncJob finished, ok=N`
- **风险**：依赖 P-FIX-002B 先修复唯一索引冲突

### P-FIX-002E：远端同步后商品库数量复核

- **目标**：远端同步运行一段时间后，对比本地/远端商品库数量
- **验收标准**：远端 DISPLAYING 数量接近本地 1958

## 10. 最终状态

**DONE**：配置准备完成且验证通过。

- Maven package 通过
- git diff --check 通过
- safety-check 通过
- docker compose config 验证通过
- 未执行数据库写操作
- 未重启容器
- 未部署远端
- 后续必须先完成 P-FIX-002B 再实际启用远端同步
