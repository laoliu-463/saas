# Final Global Check Before Remote Deploy

## 结论
PASS_TO_REMOTE_BACKUP_AND_CONFIG_CHECK

## 证据
- **分支**: `feature/ddd/DDD-VERIFY-001`
- **本地提交**: `6a2b1140`
- **工作区**: 本报告生成前有本轮报告归档、retro 和本报告待提交；无业务代码未确认差异。
- **安全检查**: `git diff --check` PASS；未跟踪 `.env/.key/.pem`；`git ls-files .env .env.real-pre .env.test *.pem *.key` 无输出。
- **Harness 限制**: `harness/scripts/check-harness-limits.ps1` PASS；`harness/reports` 已归档至 4 个文件后再生成本报告。
- **后端测试**: 首次 `mvn -f backend/pom.xml test` 失败于 `DataControllerTest` 旧订单费率期望；根因是测试仍期望“服务费收入/订单额”。已只修正测试期望为“服务费收益/订单额”。复验：最小用例 2/2 PASS；全量 `2191 tests, 0 failures, 0 errors, 3 skipped`。
- **前端测试**: 关键订单/看板测试 `7 files, 58 tests` PASS；全量 Vitest `84 files, 640 tests` PASS。
- **构建**: `agent-do.ps1 -Env real-pre -Scope full` 中后端 `mvn -DskipTests package` PASS；前端 `npm ci && npm run build` PASS。npm audit 报 6 个依赖漏洞（4 high, 2 critical），未作为本次部署阻塞，但需后续治理。
- **本地容器**: `backend-real-pre`、`frontend-real-pre`、`postgres-real-pre`、`redis-real-pre` 均 healthy。
- **本地健康**: `GET http://127.0.0.1:8081/api/system/health` 返回 `{"status":"UP"}`；`HEAD http://127.0.0.1:3001/login` 返回 200。
- **real-pre preflight**: `npm run e2e:real-pre:p0:preflight` PASS，证据目录 `runtime/qa/out/real-pre-preflight-20260614-150405/`。
- **本地配置**: 实际表为 `system_config`，不是附件中的 `system_configs`；`commission.business_default_ratio=0.15`、`commission.channel_default_ratio=0.15`。`commissions` count=0，`commission_config` count=0。
- **本地 2026-06-08 到 2026-06-13 SQL 复验**: 达人、团长、商品数、订单数、支付金额、服务费收入、技术服务费、服务费支出、服务费收益、商品费率、订单费率均与任务基准一致；结算金额为 `0.00`。
- **日志**: backend/frontend/redis 无持续 ERROR；PostgreSQL 近 5 分钟 ERROR 来自本轮按旧表名/旧字段执行的诊断探针，非应用运行错误。

## 远端部署允许条件
- 本地 P0/P1 门禁已通过。
- 远端部署前必须先完成 PostgreSQL 备份和环境配置备份。
- 远端必须查询实际配置表；若仍为 15%+15%，按用户确认改为招商 10% + 渠道 10%，并保留变更日志或 SQL 证据。
- 不执行历史全库重算；历史毛利是否变化取决于实时计算、快照或受控重算链路。

## 回滚计划
- 代码回滚：记录远端部署前 commit，异常时 `git checkout <pre_deploy_commit>` 后重新 `docker compose up -d --build`。
- 数据回滚：优先保留故障现场日志；数据库破坏性问题才使用部署前 `pg_dump -Fc` 备份恢复。
- 配置回滚：恢复 `system_config` 旧值，写入 `system_config_change_log`，清理配置缓存并重启 backend。

## 风险
- 本地配置仍显示旧默认提成 15%+15%；远端必须以真实 DB 核验为准。
- 06-14 为滚动日，只记录快照，不作为阻塞。
- npm audit 依赖漏洞需独立治理；本次未升级依赖，避免引入部署范围外变更。
