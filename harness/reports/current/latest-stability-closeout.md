# 稳定性与工程治理收口最终报告

## 1. 结论与事实基线

- 生成时间：2026-07-18 12:20 +08:00；执行环境：本地真实 `real-pre`；远端未部署。
- 执行前完整 SHA：`76a5e2e8ffc8217517d3c2969c9ec11978474344`。
- 执行后可复核代码 SHA：`1644ea55042874fecd88214230820eed29d9dd6e`；分支：`codex/ddd-user-role-application`。
- 远端：GitHub `origin` 为主源，Gitee `gitee` 为镜像；报告写入前本地相对 origin 为 `ahead 3 / behind 0`，最终推送结果以交付消息为准。
- 总结：`PARTIAL`。P0 缺列、Schema/Flyway/readiness、订单调度 OOM 根因、CI/CD 代码门禁和本地核心验证已落地；真实寄样、多角色、可归因订单、Jenkins 实跑和远端部署仍缺凭证/样本/授权，不能宣称今晚全部完成。
- 固定报告入口：`harness/reports/current/latest-stability-closeout.md`；DDD 入口：`harness/reports/current/latest-ddd-progress-audit.md`。

## 2. Git、worktree 与 dirty 分类

- `git worktree list --porcelain` 记录了主工作树及 40 余个历史 worktree；本轮不删除、不合并，建议由各分支 owner 逐项确认后归档。
- A 用户/并发源码：报告生成时有 10 个未归属 performance/dashboard/order 文件在持续出现；均未暂存、未覆盖、未纳入本轮提交。
- B 本轮修改：均已按 DB、应用稳定、CI/CD、QA、测试治理拆分提交。
- C Harness 报告：时间戳旧报告原文移入 `harness/archive/by-date/2026-07-18/stability-closeout/`，current 改为固定入口。
- D Playwright/测试产物：`.playwright-cli/`、`runtime/qa/out/`、`backend/target/`、`frontend/dist/` 已被现有 ignore 规则隔离。
- E 环境异常：受控 `.env.real-pre` 当前缺失且被 ignore；没有用示例或运行容器中的陈旧值重建，避免覆盖真实凭证。
- 从执行前到审计 SHA 共变更 113 个文件；完整清单可复现：`git diff --name-status 76a5e2e8ffc8217517d3c2969c9ec11978474344..1644ea55042874fecd88214230820eed29d9dd6e`。关键范围为 backend 45、frontend 4、harness 36、runtime/qa 4、scripts 4、CI/CD 与证据资产 20。

## 3. 提交列表

```text
4faee3f8780e9ba47fe21f823b17f3e48b629029 fix(db): align role-aware attribution schema
62fe0a90c60d887266585c6cb40588b2bf5cfb6e feat(db): schema compatibility probe + readiness guard + baseline
2c5503015f81461da1f96715dbd9f60b59a482ba feat(app): wire scheduled sync jobs + DataScope to schema/data guards
1a3121b3292f75a02be28c506c18799bec5c52ad test: extend schema + migration + global handler coverage
f367cd8af84afad8d663de5faf2bad24902b05b3 feat(ci): governance gates + controlled real-pre CD pipeline
63ed85e104e27c80aa20ceec1c93c157a091815b feat(cd): schema guards + deploy probes + DB lifecycle scripts
06770ffb550c0db5dc9aac48152c2f8771b2d3f4 docs(harness): closeout reports + governance evidence
41fb1c28e49f8b5148c8ab2bd57daec0262d063a test(arch): enforce large-service debt redline
b20ac65e7ae80a8a998a32767d6dbb0d72bc47fb chore(test): disable Flyway in non-Container unit tests
ba3468af835a874f8104868821653a25ef72c28d docs(harness): stability closeout report
f6a6fb85356f15b843363b5c9e0286916ffe3a99 fix(order): serialize scheduled sync modes
f54b90edb9d9983e6a742b98ded3d029272ad623 test(ci): fix governance compose gate + CI-only tests
a8ff65d78dd91c809b4ee71a5a9c641466896ad0 fix(qa): stabilize credential and health probes
cb47c8d15fd10819a2c8b828fd17b1c97d43595a test(ci): provide example env to Compose gate
0e47054d0ebcdb3fcd889aa2f94edcc68827ede6 fix(harness): normalize governance paths
93c343f022cec4012b55b896e7ef57dd7a6a23de chore(cd): enforce Flyway and immutable evidence
72c9d6557d7ea048526a9c680e521f1880732c71 chore(cd): enforce single-track release sequence
58440a3e193cc3102c92f3ebc63f4f12a9c53e1e test(ci): align migration guard assertion
0fb9e3c834df5ddf6614d23c1bd6782479ac42f3 docs(harness): publish governance evidence
cde8fe271c9283919c1c73b07c7b79f41b54f728 test(ci): validate canonical V001 migration
d269914ea0041a14c5b55a49df11a70a6f975782 docs(harness): align runtime image evidence
b729fd2dc89059408c4b6d5bcf7db51341c8769c fix(qa): detect actual real-pre database container
72791f401986ded14a647a52107a6b02be1ba026 test: isolate scheduled jobs from application context tests
1644ea55042874fecd88214230820eed29d9dd6e fix(qa): preserve request IDs in failed API evidence
```

## 4. 已修复 P0 与根因

- 原 P0：Entity/Mapper 已读取 `colonelsettlement_order.channel_attribution_source`、`recruiter_attribution_source`、`channel_attribution_status`、`recruiter_attribution_status`，但复用 volume 的 real-pre 仍是旧结构；初始化 SQL 不会对既有 volume 重放，活动商品订单关联查询因此失败。
- 根因修复：正式 V001 以幂等 `ADD COLUMN IF NOT EXISTS` 补齐四列与相关 owner 字段，同步 Entity、Mapper XML、init/test schema 和契约；V002 单独补活动状态字段，避免修改已执行 V001 造成 checksum drift。
- 二次 P0：三个订单定时同步模式并发，导致堆内存耗尽、Hikari 饥饿、JDBC rollback I/O 异常与乐观锁冲突。`f6a6fb...` 增加 owner-aware Redis 共享定时互斥锁，保留手工同步、各模式 watermark 和业务语义。
- 运行观察 45 分钟：`scheduled-mutex-locked=24`；`OutOfMemoryError=0`、`Unexpected packet type=0`、乐观锁冲突 `=0`。抖音上游仍有 `isp.service-error:256`，logId 已保留，属于外部依赖错误。

## 5. 数据库迁移、备份与运行版本

- 迁移前备份：`runtime/qa/out/stability-closeout-20260718/stability-closeout-pre-restart.dump`，1,378,981,227 bytes，1343 objects，SHA-256 `8B104CF9E75CC4912034165A5438D8F8D2939E8DEC5A59B9D9DC50DD249D8C3C`；未删卷、未清库。
- Flyway ledger（12:10 只读实查）：`20260717 pre-flyway-schema t`、`20260718.001 role aware attribution schema t`、`20260718.002 activity status sync schema t`。
- 父表和 12 个子分区均含四个归因字段；重复只读 Schema 预检结果一致。正式脚本为 `scripts/run-real-pre-db-migrations.sh` 与 `scripts/check-real-pre-schema.sh`，`bash -n` 均通过。
- 当前 DB/Redis：PostgreSQL `postgres:15-alpine`、Redis `redis:7-alpine`，均 healthy；数据库 `saas_real_pre`。
- 当前本地 real-pre 镜像 revision/tag：`0fb9e3c834df5ddf6614d23c1bd6782479ac42f3`；backend image `sha256:7feb8f2f5c756ab49448ceafecb2e850f0a0fb046ff2aaa2ec550c9743a48e92`，frontend `sha256:7efab6740f2310e5f02a8b9d7f087a9721a02346e2f0c6c0045a4500371b8945`。
- 开关实查：`SPRING_PROFILES_ACTIVE=real-pre`、`APP_SCHEDULING_ENABLED=true`、`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`。

## 6. 验证矩阵（命令、时间、环境、原始证据）

| 验证 | 时间/环境 | 结果与证据 |
|---|---|---|
| `git diff --check` | 12:07，本地 | PASS；仅未归属文件的 CRLF 提示，无 whitespace error |
| 后端 compile | 11:46，本地 | PASS，`mvn -B -f backend/pom.xml -DskipTests compile` |
| 后端目标宽集 | 11:51~11:57，本地 | PASS，66 tests/0 failure/0 error；含 Schema、Flyway、Mapper、Architecture redline、requestId、调度锁 |
| DDD/Architecture/Guard/Contract | 本轮较早阶段 | PASS，337 tests/1 skipped；完整 Maven 全量曾 600s timeout，保留 `PENDING/TIMEOUT` |
| QA Node 合约 | 12:15，本地 | PASS，18/18；失败 API 保存 requestId |
| 前端 test | 12:01~12:03，本地 | PASS，97 files/741 tests |
| 前端 typecheck/build | 12:04~12:07，本地 | PASS；3703 modules，生产 build 成功 |
| 容器/HTTP | 12:08，本地 real-pre | PostgreSQL/Redis/backend/frontend healthy；liveness/readiness HTTP 200 UP；frontend HTTP 200 |
| Compose config | 12:06，本地 | `BLOCKED_ENV`：`.env.real-pre` 缺失；没有用错误凭证文件绕过 |
| Harness limits | 12:11，本地 | `TASK_GATE=PASS / REPOSITORY_HEALTH=PARTIAL`；历史 report 数量债务，旧超长 current 报告已归档 |
| 固定 `agent-do` 入口 | 12:23，本地 dry-run | `BLOCKED_ENV`：safety-check 在任何构建/写入前拒绝缺失的 `.env.real-pre`；失败证据未改写本报告 |

原始业务证据：`runtime/qa/out/real-pre-p0-20260718-102744/`；当前认证/Schema 证据：`runtime/qa/out/real-pre-preflight-20260718-requestid/`。失败登录为 HTTP 401，`requestId=d620c048-1432-4a53-9dff-559d0849a079`，根因是受控 real-pre 凭证不可用；未重置用户密码、未伪造 token。

## 7. 三条业务链与角色

| 链 | 结果 | 事实 |
|---|---|---|
| A 商品链 | `PASS` | 同日样本活动 `3929906`：同步、快照、状态、列表、分页 total、前端刷新通过 |
| B 合作/寄样链 | `BLOCKED_AUTH/FAIL` | 管理员登录 401，未继续产生写操作，不能证明达人→寄样→审核→物流→成交 |
| C 订单业绩链 | `PENDING_SAMPLE` | 同步 fetched=96、created=0、updated=96、attributed=0；订单与 Dashboard 只读可用，但没有可归因上游样本完成对账 |

当前六角色矩阵不能写 PASS：`admin=BLOCKED_AUTH`；`biz_leader`、`biz_staff`、`channel_leader`、`channel_staff`、`ops_staff` 均为 `BLOCKED_AUTH/PENDING`。旧批次的部分角色证据不替代当前 HEAD/凭证基线的全量回归。

## 8. CI/CD 门禁

- CI 已代码化：clean checkout、后端 compile/test、DDD/Architecture/Guard/Contract、Testcontainers/Flyway/Schema/Mapper 契约、前端 typecheck/test/build、Compose `config --quiet`、Harness limits、完整 SHA、禁止 dirty/floating image。
- CD 固定顺序：备份/恢复确认 → Flyway 兼容迁移 → Schema 预检 → 完整 SHA/OCI 镜像 → backend readiness → frontend → smoke → 多角色 E2E → 恢复调度 → immutable evidence。
- Jenkins 实跑 `PENDING`；远端部署 `BLOCKED_AUTHORIZATION`。远端已知运行线仍是 `feature/auth-system@2092dfa45411211257597e6a79aeb750080ef440` 且与目标分支分叉，未在 dirty 工作区或无窗口条件下部署。

## 9. DDD 真实进度

逐域：`user ROUTED`、`product ROUTED`、`order ROUTED`、`sample ROUTED`、`performance ROUTED`、`talent SHADOW`、`config ROUTED`、`analytics SHADOW`、`colonel SHADOW`、`logistics SHELL`；无 `PRIMARY/VERIFIED/RETIRED`，保守综合完成度约 38%。详情与 12 个 >1000 行 Service 红线见固定 DDD 报告。今晚 P0 优先，未为提高完成率做大规模重写；计划中的低风险 DDD 新切片标记 `PENDING`。

## 10. 回滚、未完成与明早第一优先级

- 应用回滚候选（本机已存在）：backend/frontend 完整 tag `72c9d6557d7ea048526a9c680e521f1880732c71`；切换 `IMAGE_TAG` 后仅 `up -d --no-build` 对应服务并等待 readiness/smoke。禁止浮动 `latest`、`down -v`、删卷或服务器手改源码。
- DB 变更是向前兼容加列，应用回滚不删列；只有经 DBA 审批且确认恢复点后才可用上述 dump 执行 `pg_restore`。
- 阻塞：受控 `.env.real-pre`/六角色凭证、真实寄样样本、可归因订单、Jenkins 执行器/远端授权窗口；并发出现的 10 个未归属源码文件需其 owner 收口。
- 明早第一优先级：恢复受控 `.env.real-pre`（不从 example 猜值），确认六角色凭证和同一批寄样/可归因订单样本，串行重跑 B/C/角色矩阵；之后由 release owner 决策远端分叉合并与部署窗口。

## Retro

本轮两类事故都来自“运行事实未绑定版本”：旧 volume 绕过 init SQL、多个同步模式缺少全局互斥。已用 Flyway ledger + readiness/schema guard + 调度互斥 + 完整 SHA/OCI evidence 收口。可执行改进是把受控 env 文件生命周期和 Jenkins 实跑纳入 release owner 检查；验证标准是 clean checkout 中 Compose/CI PASS、同一真实样本多角色对账和远端部署证据完整。
