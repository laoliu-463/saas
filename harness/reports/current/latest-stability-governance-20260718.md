# 稳定性与工程治理收口最终报告

## 1. 结论与基线

- 生成时间：2026-07-18 11:12:46 +08:00（Asia/Shanghai）；环境：本地 `real-pre`，未部署远端。
- 执行前基线：`76a5e2e8ffc8217517d3c2969c9ec11978474344`。
- 本报告对应的代码审计提交：`72c9d6557d7ea048526a9c680e521f1880732c71`；最终运行镜像绑定的代码 SHA：`0fb9e3c834df5ddf6614d23c1bd6782479ac42f3`；分支 `codex/ddd-user-role-application` 已推送，`origin...HEAD = 0/0`。
- 总结：`PARTIAL`。P0 Schema、Flyway、readiness、CI/CD 代码门禁和本地容器验证完成；真实寄样、多角色全量回归、可归因订单样本、Jenkins 实跑和远端部署没有足够证据，不能写 `PASS`。

## 2. 提交与修改文件

关键提交：

- `4faee3f8780e9ba47fe21f823b17f3e48b629029`：P0 角色归因缺列、V001、实体/Mapper/Schema 契约。
- `62fe0a90`、`2c550301`、`1a3121b3`、`f367cd8a`、`63ed85e1`：Flyway/readiness、任务 Schema 守卫、契约测试、CI/CD 基座。
- `a8ff65d78dd91c809b4ee71a5a9c641466896ad0`：real-pre 凭证注入和 Node health 探针修复。
- `72c9d6557d7ea048526a9c680e521f1880732c71`：Flyway 单轨执行器、不可泄露的 Compose 校验、immutable CD 证据、DDD 审计刷新。

本轮关键修改文件：`backend/pom.xml`、`backend/src/main/resources/application.yml`、`backend/src/main/resources/db/migrate/V20260718_001__role_aware_attribution_schema.sql`、`V20260718_002__activity_status_sync_schema.sql`、Schema probe/health/startup guard、RequestId/异常处理、核心 Entity/Mapper/契约测试、`.github/workflows/ci.yml`、`Jenkinsfile`、`scripts/run-real-pre-db-migrations.sh`、`scripts/check-real-pre-schema.sh`、`harness/scripts/commands/deploy-remote.ps1`、`runtime/qa/real-pre-env.cjs`、`runtime/qa/real-pre-preflight.cjs`、DDD 报告和 changelog。

`git status --short` 在最终提交后为空；未覆盖并行进程的未知 dirty 文件，临时 `runtime/qa/out`、Playwright、target 产物由既有 ignore 规则隔离。

## 3. P0 根因与修复

现象是活动商品查询的订单关联 SQL 引用 `colonelsettlement_order` 的四个不存在字段。根因是 real-pre 复用 PostgreSQL volume 后，旧 `docker-entrypoint-initdb.d` 不会再次执行；角色归因变更只存在于未统一接入的旧 SQL，代码 Entity/Mapper 已先行引用。另发现 V001 在已执行后被追加 activity 字段造成 checksum drift，已恢复 V001 不变并拆出 V002。

修复：V001 幂等增加四个订单字段及 owner 字段；V002 幂等增加 `colonel_activity.activity_status_synced_at`；同步 Entity、Mapper、init/test schema 和契约测试；启动时只读检查核心表/列及订单全部分区，缺失时 readiness 失败，调度任务在不兼容时不启动。没有改 API、金额公式、归因规则或状态机。

## 4. 数据库与运行态证据

- 备份：`runtime/qa/out/schema-backup-pre-flyway-20260718.sql`，迁移前完成，可恢复性目录已校验；没有删除 volume 或清库。
- DB：`postgres:15-alpine`，运行版本 15.17；Redis `7-alpine`；数据库 `saas_real_pre`。
- Flyway：9.22.3；历史为 `20260717 pre-flyway-schema`、`20260718.001 role aware attribution schema`、`20260718.002 activity status sync schema`，均 `success=t`。
- Schema：核心 7 项字段全部存在；`colonelsettlement_order` 子分区 12 个，分区字段预检无缺失。命令：`scripts/run-real-pre-db-migrations.sh`、`scripts/check-real-pre-schema.sh`，2026-07-18 10:xx~11:xx。
- 当前镜像（本地 `saas-active`）：backend `sha256:7feb8f2f5c756ab49448ceafecb2e850f0a0fb046ff2aaa2ec550c9743a48e92`，frontend `sha256:7efab6740f2310e5f02a8b9d7f087a9721a02346e2f0c6c0045a4500371b8945`；tag 和 OCI `org.opencontainers.image.revision` 均为完整 `0fb9e3c834df5ddf6614d23c1bd6782479ac42f3`。
- 容器：PostgreSQL、Redis、backend、frontend 均 healthy。liveness/readiness 均 HTTP 200 `UP`，frontend `/healthz` 为 `ok`；readiness 响应带 `X-Request-Id`。

## 5. 验证结果

命令与原始证据：

- 后端 `mvn -B -DskipTests compile`：PASS，11:06；`mvn -B -DskipTests package`：PASS，11:08（仅 JaCoCo stale execution-data warnings）。
- Flyway/Testcontainers：`mvn -B -DforkCount=0 "-Dtest=RoleAwareAttributionSchemaContractTest,RoleAwareAttributionFlywayIntegrationTest,RequestIdFilterTest" test`：PASS，3 个目标类；覆盖新库、旧结构升级、重复 migrate 和 requestId。
- 宽口径架构/DDD/Guard/Contract：337 tests PASS、1 skipped；无失败。完整 `mvn -B -DforkCount=0 test` 曾运行 600 秒未完成，记录为 `PENDING/TIMEOUT`，不冒充 PASS。
- 前端：`npm --prefix frontend run typecheck`、`npm --prefix frontend test`（97 files/741 tests）和 `npm --prefix frontend run build` 均 PASS。
- Compose：`docker compose ... config --quiet` PASS；迁移脚本 `bash -n` PASS；PowerShell deploy dry-run PASS。
- Harness：`check-harness-limits.ps1 -BaselineRef HEAD -NoReport` 为 `TASK_GATE=PASS / REPOSITORY_HEALTH=PARTIAL`。PARTIAL 是历史 reports 数量/旧超长报告债务，不是本轮新增恶化。

## 6. 三条真实业务链

| 链 | 结果 | 证据 |
|---|---|---|
| A 商品链 | `PASS` | `runtime/qa/out/real-pre-p0-20260718-102744/steps/03-31-product-chain`；活动 `3929906`，商品链同步/快照/状态/列表/分页和前端页面通过，无运行时/CSP 错误 |
| B 合作/寄样链 | `FAIL` | 同一批次 `05-33-sample-chain`；管理员登录 HTTP 401，未继续写入寄样流程，不能证明达人→寄样→审核→物流→成交完成 |
| C 订单业绩链 | `PENDING` | `04-32-order-attribution`；同步抓取 96、created=0、updated=96、attributed=0，全部无上游可归因订单；订单列表和 dashboard 读取可用，但不等于归因闭环 |

业绩/Dashboard 只读 `34` 为 PASS：订单总量、金额、已归因/未归因汇总和公式检查通过。清理计划 `36` 为 `PASS_NEEDS_CLEANUP`，仅 PlanOnly，没有执行删除。

## 7. 角色结果

- `admin`：preflight 管理员登录 PASS；35 中页面和用户范围探针 PASS。
- `biz_leader`：35 中登录、允许/拒绝页面及 403 API 检查有 PASS 证据。
- `biz_staff`：真实密码未提供；默认值登录 401，标记 `BLOCKED_AUTH/FAIL`，未猜测或改库。
- `channel_leader`：曾取得 token 但请求出现 `ECONNRESET`，未完成全量验证。
- `channel_staff`、`ops_staff`：`.env.real-pre` 没有角色密码，测试出现登录连接失败，标记 `BLOCKED_AUTH/PENDING`。
- 真实 role password、可复用寄样样本、可归因上游订单是明早最小解阻条件。原始 RBAC 文件中的 Bearer 值已清理，不进入报告、提交或回复。

## 8. CI/CD 门禁

CI 已加入：后端 compile/test、DDD/Architecture/Guard/Contract、Testcontainers Flyway、前端 typecheck/test/build、Mapper/XML/Schema 契约、Compose config、Harness limits、clean checkout、完整 SHA 和禁止 floating image。

CD 已固定并代码化：备份与恢复目录校验 → Flyway 兼容迁移（调度暂停）→ Schema 预检 → SHA/OCI revision 镜像构建 → backend readiness → frontend health → 核心 smoke/E2E → evidence。Compose config 只做 `--quiet`，不会把渲染后的密码写入产物；证据只有在 health、迁移历史、镜像 ID/label 全部满足时才写 `PASS`。Jenkins 实跑和远端 deploy 未执行，标记 `PENDING`（缺少本轮远端授权/窗口），未伪造 CD PASS。

## 9. DDD 真实审计

详见 `harness/reports/current/latest-ddd-progress-audit-20260717.md`，基线为 `72c9d655...`，不是目录计数：

`user ROUTED`、`product ROUTED`、`order ROUTED`、`sample ROUTED`、`performance ROUTED`、`talent SHADOW`、`config ROUTED`、`analytics SHADOW`、`colonel SHADOW`、`logistics SHELL`。本轮无 `PRIMARY/VERIFIED/RETIRED`；保守权重综合真实完成度约 38%。当前 >1000 行 Service 12 个，行数未超过 baseline；红线是只降不升、禁止新增 Mapper/跨域职责。本轮只做低风险迁移/守卫模板，没有大规模重写。

## 10. 未完成、风险与回滚

- `BLOCKED/PENDING`：真实角色凭证、寄样样本、可归因订单样本、全量 Maven 测试、Jenkins 实跑、远端部署；旧 `migrate-all.sql` 历史资产尚未全部转换为 Flyway，当前由 Flyway 负责本轮正式版本，旧脚本不再被 CD 执行器直接运行。
- 回滚应用：将 `IMAGE_TAG` 切回部署前记录的完整 SHA，`docker compose ... up -d --no-build backend-real-pre frontend-real-pre`，等待 readiness 和 smoke；禁止 `latest`/浮动 tag。
- 回滚数据库：本轮仅增加列/历史，应用回滚不删列；若必须恢复事实数据，使用迁移前备份并由 DBA 审批执行 `pg_restore`。禁止 `down -v`、删卷、清库或手改服务器源码。

## 11. Retro 与明早第一优先级

本轮两个根因都来自“运行库事实与代码基线未绑定”：复用 volume 让 init SQL 失效，另一个并行修改造成 Flyway checksum drift。改进动作已落地为单一 Flyway ledger、启动 Schema guard、全 SHA/OCI label、无 secrets Compose 校验和 requestId；验证方式是重复迁移 + Testcontainers + real-pre readiness。

明早第一优先级：由业务负责人提供六角色 real-pre 凭证和一条可归因订单/寄样样本，先单独重跑 33/35/C 链；随后补完整 Maven 测试超时诊断和旧 SQL→Flyway 迁移清单。远端部署必须另行明确授权和窗口。
