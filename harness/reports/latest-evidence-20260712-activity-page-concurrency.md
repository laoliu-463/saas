# 活动列表分页并发验证证据

- 时间：2026-07-12 19:26（Asia/Shanghai）
- 环境：本地 `real-pre`
- 分支：`codex/ddd-user-role-application`
- 基线 commit：`604ac11eaa785d8ff4692ebc3a14d2eb70667ec3`
- 工作区：不干净；包含本轮活动同步修改，也包含用户已有前端修改、报告清理、未跟踪探针等，未擅自覆盖

## 修改范围

- `ColonelActivityListSyncService`：第一页先取；后续页按固定窗口并发拉取，默认并发度 4，限制 1～8；落库仍按页顺序执行；中断时取消 futures；Bean 销毁时关闭线程池。
- `application.yml`：增加 `COLONEL_ACTIVITY_LIST_SYNC_PAGE_PARALLELISM` 配置入口。
- `ColonelActivitySyncJobLogMapper`：同步完成时写回实际拉取总数，轮询 API 不再返回虚假的 `activitiesTotal=0`。
- `ColonelActivityListSyncServiceTest`：验证页级并发发生且不超过配置上限。

## 测试与构建

- 聚焦测试：`ColonelActivityListSyncServiceTest`，3 passed，0 failures，0 errors。
- 相关回归：72 passed，0 failures，0 errors。
- 后端全量：3071 tests，0 failures，0 errors，3 skipped；排除未纳入正常套件且会对 real-pre 产生真实写入的 `AllActivitiesSyncAndInspectTest`。
- 后端打包：`mvn -f backend/pom.xml -DskipTests package`，BUILD SUCCESS。
- 前端：89 files / 685 tests passed；`typecheck:test` passed；生产 build passed。
- `git diff --check`：通过。
- `check-harness-limits.ps1`：`PASS`。

## real-pre 运行证据

- 固定脚本重启：`restart-compose.ps1 -Env real-pre -Scope full`，成功；PostgreSQL、Redis、Backend、Frontend 均 healthy。
- 健康检查：`verify-local.ps1 -Env real-pre -Scope full`，Backend `/api/system/health`=200，Frontend `/healthz`=200。
- P0 预检：`npm run e2e:real-pre:p0:preflight`，通过。
- real-pre 抖音联调 UI/API：`08-real-pre-douyin-integration.spec.ts`，1 passed，0 page runtime error。
- 最后一次活动列表同步：
  - job：`act-list-2875600f`
  - 触发接口返回：`QUEUED`，约 52ms 返回，不阻塞 HTTP
  - 最终状态：`SUCCESS`
  - 轮询 API：`activitiesTotal=24`、`activitiesSynced=24`、`activitiesFailed=0`
  - 数据库：`SUCCESS|24|24|0`，任务耗时约 811ms
  - 后端日志：`total=24, pagesFetched=2, synced=24, failed=0, parallelism=4`

## 结论

本轮“活动列表分页多线程拉取”在本地 real-pre 已通过单元、全量回归、构建、重启、健康检查和真实同步闭环验证，结论为 `PASS`（仅针对本轮修改）。远端服务器未部署，因用户尚未明确要求远端部署，本轮未执行远端部署、commit 或 push。

## 剩余风险

- 本次真实数据只有 2 页，线上实际并发重叠由单元测试覆盖；真实环境仍需关注抖音限流和上游分页稳定性。
- 当前仓库仍有历史设计残留：活动列表接口/前端展示仍可回退使用 `lastSyncAt`，未完全落实设计文档要求的 `activityStatusSyncedAt` 独立字段语义；这不属于本轮并发实现，若以“整条活动状态同步设计全部闭环”作为部署门槛，应单独修复并再验收。
