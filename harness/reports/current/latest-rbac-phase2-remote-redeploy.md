# RBAC Phase 2 远端 real-pre 重新部署证据

## 基本信息

- 时间：2026-07-15（Asia/Shanghai）
- 环境：远端 `real-pre`
- 分支：`feature/auth-system`
- 应用镜像构建源码提交：`66ddf8bda80f27d49a25a9c4ba602c0266f89ed1`
- Evidence-only 收口提交会继续 fast-forward 服务器仓库，但不重建已验证镜像
- 本轮新增业务代码提交：`82456081`（重复用户名错误映射）；`61f7f129`、`66ddf8bd` 为对应 evidence 提交
- RBAC 基础代码仍来自已部署集成链 `539fbb68` / `38689b0a`
- Completion Gate：Gate 4
- 总结论：`PARTIAL`

## Git 与并发部署事实

- 主仓库存在用户未提交修改，本轮只使用隔离工作树 `D:\Projects\SAAS\.worktrees\integrate-rbac-to-auth-system-20260714`，未修改主仓库工作区。
- 部署开始时本地、GitHub、Gitee、服务器均为 `068ef926`；部署期间 Gitee 先前进到 `61f7f129`，固定脚本实际拉取并部署该提交。
- 随后并发发布将 Gitee 与服务器推进到 `66ddf8bd`；`61f7f129..66ddf8bd` 仅更新 evidence，没有业务代码差异。
- 应用镜像构建时服务器 HEAD 为 `66ddf8bd` 且工作区干净；并发发布停止后无残留 deploy、Maven、Docker build 或 E2E 进程。
- GitHub `feature/auth-system` 在本报告生成前仍落后，需在本报告提交后与 Gitee 一并 fast-forward。

## 构建与测试

- `068ef926` 基线：后端 3426 tests，0 failures，0 errors，3 skipped；前端 94 files、710 tests 通过。
- 实际部署业务代码 `82456081`：后端重新全量验证为 3430 tests，0 failures，0 errors，3 skipped。
- `068ef926..66ddf8bd` 未修改 frontend，故前端 710 tests 对应相同前端源码树。
- 本地 backend package、frontend `npm ci`、typecheck/build、Docker 重建和本地 HTTP health 均通过。
- npm audit 仍报告 6 个既有漏洞（1 low、1 moderate、2 high、2 critical），本任务未擅自升级依赖。

## 本地 fixed-entry 阻塞与处置

- 首次按 `agent-do -Scope full -DeployRemote true` 执行：构建、容器重启、backend/frontend health 通过；在本地 preflight 阶段停止，未触发远端部署。
- 本地 preflight 失败证据：`runtime/qa/out/real-pre-preflight-20260715-130912`；admin 连续 5 次登录 HTTP 401。
- 本地只读证据显示 admin 状态为启用、未删除、`authzVersion=1`；后端日志仅记录登录 401，无授权版本拒绝。
- 同一提交、同一脚本在未变更的远端 preflight 全项通过：`/opt/saas/app/runtime/qa/out/real-pre-preflight-20260715-131014`。
- 阶段性结论：本地 admin 凭据基线漂移，不是远端认证或 RBAC 回归。本轮不修改本地账号/密码，改用 runbook 明确允许的固定 `deploy-remote.ps1`；未降低远端 preflight 门禁。

## 备份与固定部署

- 部署前备份：`/opt/saas/backups/rbac-redeploy-pre-20260715-131037.dump`。
- 备份大小：2,354,579,213 bytes。
- SHA-256：`b61a91e1898179f08f6e3d8fbc4ffa0779114188d0f8d5b570ba56a127073908`。
- 使用 `harness/scripts/commands/deploy-remote.ps1` 完成服务器拉取、既有幂等 Schema guard、服务端 Maven package、镜像重建、容器重启和健康检查。
- 服务端 Maven `BUILD SUCCESS`；JAR 81,065,134 bytes，宿主机/容器 JAR 大小 guard 通过。
- 最终 backend 镜像：`sha256:1453cd620cdfe04191cf116a4d429c33a4ef7e9c473d8c42d36cb5d6202cce33`。
- 最终 frontend 镜像：`sha256:b0eab55cfd26b780a2eb4470c9817d6809024b8d7277ed488aef5075ed110d16`。
- 旧回滚镜像：backend `sha256:b414818f9d146e8137e1b03ee36c577e97b6a79f69f9cc7a3672a817b00c917e`；frontend `sha256:9c72850133d70c7795d8c52676a327445b5ead6fa8a237f7e2ccf3e7bd012134`。

## 最终运行态验证

- backend、frontend、PostgreSQL、Redis：4/4 `running|healthy`。
- backend `/api/system/health`：`{"status":"UP"}`；frontend `/healthz`：`ok`。
- `SPRING_PROFILES_ACTIVE=real-pre`、`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`。
- Authorization 环境变量数量为 0，配置继续解析为 `LEGACY(default)`；未进入 `SHADOW/ENFORCE`。
- 未认证 `/api/users?page=1&size=1` 返回 HTTP 401。
- RBAC catalog：4 张授权表、1 个 `sys_user.authz_version` 列。
- 授权事实与非法版本：`0|0|0|0|0`；Redis `authz:*` 键数量为 0。
- 部署后 backend 致命启动错误计数为 0；商品同步任务加载 `enabled=true`、cron `0 */5 * * * ?`、parallelism 2。

## Gate 4 业务验证

### Preflight

- `PASS`：frontend、backend、admin 登录、real-pre 环境、抖音 Token、数据库 Schema、可复用推广映射、cleanup plan。
- 证据：`/opt/saas/app/runtime/qa/out/real-pre-preflight-20260715-132824`。

### P0

- 结论：`FAIL`；证据：`/opt/saas/app/runtime/qa/out/real-pre-p0-20260715-132843`。
- `PASS`：preflight、商品链、业绩看板、RBAC scope。
- `PASS_NEEDS_CLEANUP`：cleanup plan。
- `FAIL`：抖音联调页面未出现“商品 SKU 已验证”，与上一轮失败模式一致。
- `PENDING`：真实订单全部未归因，不能证明归因链；无真实成交订单，不能证明寄样自动完成链。

### 六角色业务流

- 结论：`FAIL`，5/13 steps 通过；证据：`/opt/saas/app/runtime/qa/out/real-pre-role-business-e2e-20260715-133515`。
- `PASS`：环境守卫、六角色登录与角色/数据范围上下文、Token preflight、活动分配、六账号登出后旧 Token 拒绝。
- 首个失败：管理员 SKU 页面断言超时。
- 独立失败：招商组长商品同步返回 `QUEUED`，测试未等待异步终态。
- 后续 6 步因 `productId`、`sampleId`、`sampleRequestNo` 未生成而级联失败，不单独解释为 RBAC 权限拒绝。

## Retro 与剩余风险

- 并发发布会使“测试提交”和“实际部署提交”漂移；本轮通过 HEAD 断言发现并补测实际业务代码。后续部署脚本应支持显式 `ExpectedCommit`，pull 后不一致即停止。
- 本地与远端 real-pre 账号凭据基线不一致会阻断总入口；应只修复本地 QA 凭据管理，不得把远端凭据复制到本地或文档。
- SKU UI 请求链仍需专项诊断；当前证据不足以确认最终根因。
- 订单归因、寄样自动完成缺真实样本；不得记为 PASS。

## 结论

`PARTIAL`：远端代码同步、备份、构建、镜像重建、容器健康、真实环境 guard、RBAC Schema/Redis/LEGACY 验证均已完成；但 P0 与六角色完整业务流仍为 `FAIL`，订单归因和寄样自动完成仍为 `PENDING`。服务器可继续用于受控 real-pre 验证，不得声明正式全量上线，不得切换 `SHADOW/ENFORCE`。
