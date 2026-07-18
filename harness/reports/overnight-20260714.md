# 夜间工程闭环报告（2026-07-14）

## 结论

本轮结果：`PARTIAL`。活动商品同步死锁的最小修复组合、前端同步显示闭环、Apifox 本地防护已实现并通过自动化验证；真实 real-pre 业务联调因管理员登录连续返回 HTTP 401 阻塞，不能宣称真实活动 3223881/3916506 闭环通过，也不能宣称已完成整个系统。

## 基线与约束

- 初始分支：`codex/ddd-user-role-application`；初始 HEAD：`132776ae`；初始工作区已 dirty。
- 初始已有修改：`ProductService.java`、Harness 报告、时间戳 evidence/retro 文件及 JVM 崩溃日志；本轮未覆盖、删除或重置这些文件。
- 执行期间 HEAD 外部漂移至 `9db05292`，新增提交未由本轮创建，当前分支显示 ahead 13；该漂移已单独计入风险。
- 未连接、写入、删除或迁移生产数据库；未重启生产、未远端部署、未 push、未合并 main、未创建正式发布。
- `rtk` 命令在当前环境不可用，已使用等价的原生命令；没有因此降低测试标准。

## 已完成的最小切片

### P0：活动商品同步死锁

证据链：`product_operation_state` 对活动与商品存在共享行及唯一约束；两个活动对重叠商品按相反顺序更新会产生相反锁顺序，已知活动 3223881/3916506 有 101 个重叠商品。远程“20000/服务打瞌睡”不是本地死锁证据。

修改：

- 新增 `backend/src/main/java/com/colonel/saas/service/ProductActivitySyncWriteCoordinator.java`。
- `ProductService` 的活动商品同步写入路径改由协调器接管：商品 ID 稳定排序、按批次写入、每批 `REQUIRES_NEW`，远程分页结果不进入该写事务。
- 仅对 PostgreSQL `40P01` 增加默认 3 次事务级指数退避加 jitter；耗尽时抛出带批次上下文的异常并记录可观测信息，不输出商品明细或凭据。
- 保留既有幂等 upsert、人工状态保护及 `QUEUED/RUNNING/SUCCESS/PARTIAL/FAILED/LOCKED/QUEUE_FULL` 语义；未改 schema、API、权限、金额或分润规则。
- 新增 `backend/src/test/java/com/colonel/saas/service/ProductActivitySyncWriteCoordinatorTest.java`，覆盖反向顺序、批次、首次死锁后成功、重试耗尽。

限制：测试模拟了并发锁顺序和 40P01 重试，未在真实活动数据上制造 PostgreSQL deadlock；该项因 real-pre 认证阻塞仍需下一轮实库证据。

### P0：同步完成后的前端闭环

- `ActivityList.vue`、`index.vue`、`activity-sync.ts`、`activity-list-display.ts` 统一处理轮询、终态、失败/锁定/队列满提示、组件销毁清理和异常重试提示。
- 列表显示改为“已加载 X / 共 Y”，总量使用后端 `total`，推广中/待审核使用后端状态子集；已加入 1274 总量、726 推广中、121 待审核的契约测试。
- 同步时间仅读取后端活动同步时间字段，避免 `PRIORITY_1000` 错误覆盖全量时间；父级 `ProductSyncActivityDialog → POST → jobId 轮询 → 终态刷新` 链路保留。
- 前端组件销毁后不再处理已返回的轮询结果，也不再安排后续 timer。

### P1：Apifox/OpenAPI 收口

- `scripts/sync-apifox.sh`、`scripts/verify-openapi-apifox.sh` 增加 main/master 目标分支拒绝；保留开发端口、开发服务端点、OpenAPI 文件、占位符和 secret 扫描保护。
- 本地校验读取到 `paths=221`、`operations=252`、`schemas=345`、`bearerAuth=true`；main 分支 dry-run 被正确拒绝。
- 无真实凭据时未尝试云端导入；没有绕过占位符保护。

## 验证结果

| 检查 | 结果与证据 |
|---|---|
| 后端全量 Maven 测试 | PASS：37 suites，259 tests，0 failures，0 errors，0 skipped |
| 后端定向同步/DDD 测试 | PASS：83 tests，0 failures，0 errors |
| 前端全量 Vitest | PASS：93 files，705 tests |
| 前端增量回归 | PASS：3 files，23 tests；类型检查 PASS |
| 前端构建 | PASS：`npm run build`，Vite 8.0.9 |
| 代码图谱 | PASS：增量更新；15587 nodes、179011 edges，risk medium 0.60；仍提示 27 个测试缺口 |
| `git diff --check` | PASS（仅已有 Harness 报告 CRLF 提示） |
| secret 扫描 | PASS：未发现新增非占位凭据；未跟踪真实 `.env`/密钥文件 |
| 本地 real-pre 重启 | PASS：仅重建/重启 backend-real-pre、frontend-real-pre；未删除 Postgres/Redis volume |
| 本地健康检查 | PASS：backend 200 `{"status":"UP"}`；frontend `/healthz` 200 |
| real-pre P0 preflight | `BLOCKED_AUTH/FAIL`：管理员登录 5 次均 HTTP 401；无 token 时未执行业务闭环 |
| Apifox 本地验证 | PASS；云端导入未执行，原因是无真实凭据 |
| Harness 限制检查 | FAIL/PARTIAL：报告根目录在检查时 29 个文件，存在历史超 200 行报告及历史时间戳文件；未清理用户既有报告 |

## DDD、CD 与缓存

- DDD 证据矩阵未修改，完成度不变；本轮没有开始新的 ProductService DDD 小切片，避免在 P0 实库证据不足时扩大变更面。
- Jenkins/CD 只做了既有本地构建与健康检查；未登录服务器、未安装软件、未真实部署。
- 未实现缓存；没有把 Redis 变成事实源，也没有缓存权限、金额、佣金或同步任务状态。

## 未完成项与下一步

1. 用可用的本地 real-pre 认证，在非生产环境对 3223881/3916506 的 101 个重叠商品执行受控并发验证，收集 deadlock、重试、幂等和人工状态证据。
2. 对 `ProductActivityBackfillService` 与新协调器的重试/批量实现做统一边界审查，避免长期维护两套近似策略。
3. P0 实库证据通过后，再按 characterization/parity test 开始 ProductService 的一个 DDD 查询切片；失败即停止该切片。
4. 有开发凭据时，仅在 `ddd-sync`/`ai-sync` 等开发分支完成 Apifox 云端回读；不导入 main。
5. 由后续明确授权的治理任务归档 Harness 历史报告；本轮不删除或移动用户既有文件。

## Git 与 retro

- 本轮未创建 commit、未 push；原因是用户禁止 push，且工作区包含既有 dirty 修改及执行期间外部 HEAD 漂移。
- 本报告合并记录 retro summary：现有 Harness 门禁覆盖了构建、重启、健康检查、报告和安全约束，本轮无需新增规则；需改进的是环境中缺少 `rtk`、real-pre 认证预置和报告归档流程。
- 当前结论是“局部修改已验证、真实业务闭环部分阻塞”，不是生产发布结论。
