# P-FIX-002 商品库数量不足修复：5分钟同步、默认启动参数、同步冲突修复与远端对齐

## 1. 任务概述

| 项目 | 值 |
| --- | --- |
| 任务编号 | P-FIX-002 |
| 执行时间 | 2026-06-03 12:01 ~ 12:22 |
| 环境 | 本地 real-pre |
| 分支 | feature/auth-system |
| commit hash | 1ac7796f |
| 是否修改 Java | 是 |
| 是否修改配置 / Docker / runbook | 是 |
| 是否修改数据库 | 否 |
| 是否重启容器 | 否 |
| 是否部署远端 | 否 |
| 标准 evidence | `harness/reports/evidence-20260603-122021.md` |
| 最终状态 | `DONE_CONFIG_READY`（任务口径）/ `PARTIAL`（Completion Gate 口径） |

说明：用户任务明确要求本轮不执行 real-pre 写库、不默认重启容器、不直接远端部署。因此本报告只能证明代码、配置和部署前检查已准备完成，不能证明新 jar 已在运行态加载。

## 2. Harness 与图谱读取

已读取主要文件：

- `AGENTS.md`
- `CLAUDE.md`
- `docs/README.md`
- `harness/AGENT_CONTRACT.md`
- `harness/TASK_ROUTING.md`
- `harness/FORBIDDEN_SCOPE.md`
- `harness/COMPLETION_GATES.md`
- `harness/CURRENT_STATE.md`
- `harness/state/DOMAIN_STATUS.md`
- `harness/state/KNOWN_ISSUES.md`
- `harness/state/DECISIONS.md`
- `harness/HARNESS_CHANGELOG.md`
- `harness/instructions/product-domain.md`
- `harness/instructions/config-domain.md`
- `harness/skills/product-library.skill.md`
- `harness/skills/real-pre-debug.skill.md`
- `harness/skills/ddd-boundary-check.skill.md`
- `harness/skills/domain-alignment.skill.md`
- `harness/skills/evidence-report.skill.md`
- `harness/plans/DDD_OPTIMIZATION_ROADMAP.md`
- `harness/plans/DDD_DOMAIN_TASK_MATRIX.md`
- `harness/reports/p-diag-002-product-library-count-sync-remote-20260603-114742.md`
- `harness/reports/p-fix-002a-product-sync-5min-config-20260603-120100.md`
- `harness/runbooks/remote-deploy.md`
- `harness/commands/deploy-remote.ps1`

用户任务中列出的 `harness/skills/backend-domain-change.md`、`real-pre-safe-operation.md`、`docker-restart-validate.md`、`post-task-gc.md` 在当前仓库不存在，本轮未伪造读取结论。

code-review-graph 已先行使用：

- `get_minimal_context_tool`
- `semantic_search_nodes_tool`
- `build_or_update_graph_tool`
- `detect_changes_tool`

图谱后续影响半径受当前大量 dirty 工作区污染，不能作为纯 P-FIX-002 的唯一结论来源。

## 3. Git 工作区分类

本任务相关变更：

- `.env.real-pre.example`
- `backend/src/main/java/com/colonel/saas/job/ProductActivitySyncJob.java`
- `backend/src/main/java/com/colonel/saas/service/ProductDisplayRuleService.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-real-pre.yml`
- `backend/src/test/java/com/colonel/saas/service/ProductDisplayRuleServiceTest.java`
- `docker-compose.real-pre.yml`
- `harness/commands/deploy-remote.ps1`
- `harness/runbooks/remote-deploy.md`
- `harness/CURRENT_STATE.md`
- `harness/state/DOMAIN_STATUS.md`
- `harness/state/KNOWN_ISSUES.md`
- `harness/HARNESS_CHANGELOG.md`
- `harness/reports/evidence-20260603-122021.md`
- 本报告

非本任务 dirty 仍存在，包括用户域 U-2.5-B / TEST-1、商品卡片 UI、商品库分页、Harness Gate/report 归档等前序变更。本轮未提交、未推送，避免把非本任务变更混入提交。

## 4. P-FIX-002A：5 分钟同步与默认启动参数

当前配置口径：

| 位置 | 配置 |
| --- | --- |
| `ProductActivitySyncJob @Scheduled` 默认 cron | `0 */5 * * * ?` |
| `application.yml` 默认 cron | `0 */5 * * * ?` |
| `application.yml` 默认 enabled | `${PRODUCT_ACTIVITY_SYNC_ENABLED:false}` |
| `application-real-pre.yml` enabled | `${PRODUCT_ACTIVITY_SYNC_ENABLED:true}` |
| `application-real-pre.yml` cron | `${PRODUCT_ACTIVITY_SYNC_CRON:0 */5 * * * ?}` |
| `docker-compose.real-pre.yml` enabled | `${PRODUCT_ACTIVITY_SYNC_ENABLED:-true}` |
| `docker-compose.real-pre.yml` cron | `${PRODUCT_ACTIVITY_SYNC_CRON:-0 */5 * * * ?}` |
| `.env.real-pre.example` | `PRODUCT_ACTIVITY_SYNC_ENABLED=true`、`PRODUCT_ACTIVITY_SYNC_CRON=0 */5 * * * ?` |
| test profile | `spring.task.scheduling.enabled: false`，未启用调度 |

新增启动日志：`ProductActivitySyncJob` 通过 `@PostConstruct` 记录 enabled、cron、batchSize、whitelist，便于重启后确认运行态参数。

## 5. P-FIX-002B：唯一索引冲突修复

冲突对象：

- 索引：`uk_pos_one_displaying_per_product`
- 约束：同一个 `product_id` 在 `deleted = 0 AND display_status = 'DISPLAYING'` 下只能有一条记录
- 方法：`ProductDisplayRuleService.applyNormalDisplayDedup`

已修复代码路径：

1. 先计算全部 `DisplayDecision`，不立即写库。
2. 第一轮持久化：先处理 `currentStatus == DISPLAYING && nextStatus != DISPLAYING`，把旧 DISPLAYING 降级。
3. 第二轮持久化：处理其他非 DISPLAYING 决策。
4. 第三轮持久化：最后处理新 winner 的 DISPLAYING 决策。

该顺序避免同一事务内短暂出现两条 DISPLAYING。唯一索引继续保留，不通过删除约束绕过数据完整性。

TDD 说明：新增严格顺序测试时，工作区已存在前序 P-FIX-002B 修复草稿，因此未能观察到新增测试红灯。本轮在 green 基础上把决策结构收口为 typed `DisplayDecision record`，并执行定向与全量测试验证。

## 6. P-FIX-002C：本地 real-pre 只读对账

本轮未执行任何数据库写操作。

| 对账项 | 结果 |
| --- | --- |
| `product_snapshot` 总数 | 7284 |
| `product_operation_state` 总数 | 7284 |
| DISPLAYING | 1963 |
| HIDDEN | 4566 |
| PENDING | 755 |
| 多 DISPLAYING 冲突 | 0 |
| 推广中但未 DISPLAYING | 716 |
| `/api/products?page=1&size=1` total | 1963 |

阶段性结论：本地 real-pre API total 与 SQL DISPLAYING 数量一致；当前数据库没有重复 DISPLAYING 冲突。代码修复尚未通过容器重启加载到运行态。

## 7. P-FIX-002D：远端对齐准备

已完成：

- `docker-compose.real-pre.yml` 已显式传递同步参数。
- `.env.real-pre.example` 已补齐同步参数。
- `harness/runbooks/remote-deploy.md` 已增加部署前 / 部署后参数检查。
- `harness/commands/deploy-remote.ps1` 已增加远端 env、compose config、容器 env、后端日志检查。
- 本地 compose config 解析通过，安全 grep 仅显示 `PRODUCT_ACTIVITY_SYNC_ENABLED=true` 与 `PRODUCT_ACTIVITY_SYNC_CRON=0 */5 * * * ?`。

未完成：

- 未执行远端部署。
- 未重启远端容器。
- 未复核远端商品库数量。

## 8. 验证结果

| 命令 / 检查 | 结果 |
| --- | --- |
| `mvn -f backend/pom.xml "-Dtest=ProductDisplayRuleServiceTest" test` | PASS，31 tests / 0 failures / 0 errors |
| `mvn -f backend/pom.xml "-Dtest=*Product*Sync*,*Product*Display*,*ActivityProduct*,ProductActivitySyncJobTest" test` | PASS，49 tests / 0 failures / 0 errors |
| `mvn -f backend/pom.xml -DskipTests package` | PASS，BUILD SUCCESS |
| `mvn -f backend/pom.xml test` | PASS，1675 tests / 0 failures / 0 errors |
| `git diff --check` | PASS |
| `safety-check.ps1 -Env real-pre -Scope code -DryRun` | FAIL，当前脚本 Scope 仅支持 `backend/frontend/full/docs` |
| `safety-check.ps1 -Env real-pre -Scope full -DryRun` | PASS |
| `docker compose --env-file .env.real-pre -f docker-compose.real-pre.yml config` | PASS，解析通过；完整输出包含 env 展开，未在报告中粘贴 |
| Docker compose ps | backend / frontend / postgres / redis 均 healthy |
| 后端健康检查 | `/api/system/health` = `UP` |
| 前端健康检查 | `/healthz` = `200` |
| backend 容器 env grep | `PRODUCT_ACTIVITY_SYNC_ENABLED=true`、`PRODUCT_ACTIVITY_SYNC_CRON=0 */5 * * * ?` |
| backend logs tail | 仍可见旧运行态同步失败日志，符合“未重启，新 jar 未加载”的预期 |

## 9. 旧内容维护

已执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\retire-content.ps1 -Action Plan -DryRun
```

DryRun 仅输出候选，不生成 `content-retire-20260603-122021.md` 文件。

## 10. 剩余风险

1. 未重启容器，Java 修复未加载到当前运行态。
2. 未部署远端，远端 `PRODUCT_ACTIVITY_SYNC_*` 参数和新 jar 未生效。
3. 本地 real-pre 仍存在 716 个推广中但未 DISPLAYING 的商品，需要后续 repair dry-run。
4. 当前工作区包含大量非本任务 dirty，不适合直接提交 / 推送。
5. `safety-check -Scope code` 与当前脚本 ValidateSet 不一致，已作为 Harness 缺口记录。
6. 用户任务列出的部分 Harness skill 路径不存在，后续可补齐或更新任务模板。

## 11. 下一步建议

1. 先执行本地 real-pre 后端重启验证，确认日志出现 `ProductActivitySyncJob config: enabled=true, cron=0 */5 * * * ?`，并观察同步不再因唯一索引回滚。
2. 用户明确授权后，再执行 P-FIX-002D 远端部署验证。
3. 远端部署后执行 P-VERIFY-002，复核远端商品库 DISPLAYING 数量。
4. 对 716 个推广中但未 DISPLAYING 商品，另起 P-FIX-002E repair 任务，先 dry-run，再决定是否写库。

## 12. 最终状态

任务口径：`DONE_CONFIG_READY`

Completion Gate 口径：`PARTIAL`

原因：代码、配置、测试、构建、只读对账和部署前检查已完成；但用户明确限制本轮不重启、不远端部署，所以不能把运行态和远端状态写成 `DONE`。
