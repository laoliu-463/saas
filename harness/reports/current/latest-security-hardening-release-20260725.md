# 测试服务器加固与生产发布证据

- 时间：2026-07-25
- 结论：PARTIAL
- 说明：测试服务器加固和目标修复验证通过；生产发布经过 Jenkins 真实验收后因寄样链失败自动回滚，生产未切换到新版本。

## 代码与发布输入

- 目标代码：`main@1285624e05f7b38a002556674e13f4d0d323d140`
- 发布分支：`release/real-pre@9c3c2a42da4e1e358367da98d8d21f6abb069441`
- 后端镜像：`ghcr.io/laoliu-463/saas/backend@sha256:39730c8a7d6be36c227757c8871479f610f954a77e7e81572bea90a5b9d2dd89`
- 前端镜像：`ghcr.io/laoliu-463/saas/frontend@sha256:1dfb488edeeb2d0a26a6ef4c8fd1c9314a873e4858daca8ec9a5a4542a0f2c1a`
- PR #281：代码修复进入 `main`，CI Gate 通过。
- PR #282：首次发布提升，因生产历史镜像漂移被 Jenkins 安全闸门拦截。
- PR #283：将发布清单 previous 基线对齐生产实际镜像，门禁全绿后合并。

## 测试服务器加固

目标：`my-second-brain-server`（`192.168.101.220`），仅测试环境。

- SSH 已验证公钥登录；关闭密码登录、键盘交互和 root 登录。
- SSH 限制为本地端口转发，关闭 Agent forwarding、X11、GatewayPorts 和 Tunnel。
- UFW 已启用：默认拒绝入站，仅允许 TCP 22。
- SaaS Compose 端口继续绑定 loopback，外部探测不到 18081、3001、8080、8081、8848。
- `.env.real-pre` 权限为 600，Compose 文件为 640，部署目录为 750。
- `fail2ban`、`unattended-upgrades` 保持运行。
- 未修改测试数据库卷、Redis 卷或应用安全开关。

验证结果：

- 四个 SaaS 容器健康。
- `APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`。
- 目标订单回放返回 HTTP 200，`attributed=1`、`unattributed=0`、`updated=1`。
- 同订单 dry-run 返回 HTTP 200，`dryRun=true`、`updated=0`。
- 订单归属验证为：渠道用户归渠道，招商归属为“壮云”，状态同时为 `CHANNEL_ATTRIBUTED|RECRUITER_ATTRIBUTED|ATTRIBUTED`。

## 生产 Jenkins 真实验证

Jenkins 任务：`saas-real-pre-cd`。

### #63：安全拦截

- 镜像拉取和 Compose 配置检查通过。
- 发布在部署前停止，原因是清单 previous 镜像与生产实际运行内容不一致。
- 未切换生产容器。

### #64：真实发布、验收、回滚

- GitHub SHA Gate 通过，使用 `main@1285624e` 的不可变镜像。
- 数据库备份成功，约 2.9GB；迁移和核心 schema 检查通过。
- 后端、前端曾在发布锁内切换，随后进入真实 P0 验收。
- P0 结果：
  - 抖音接入：PASS
  - 商品链：PASS
  - 订单归因：PENDING；当前真实订单没有可证明的上游归因闭环
  - 寄样链：FAIL；达人认领接口 HTTP 200，但业务码为 462，样本保持 `PENDING_AUDIT`
  - 业绩看板：PASS
  - RBAC：PASS
  - 清理计划：PASS_NEEDS_CLEANUP
- 统一回滚在 `saas-real-pre-deploy` 锁仍持有时执行并完成。
- Jenkins 归档包含 `rollback-completed` 和 `schedulers-restored`。
- 最终生产容器恢复为：
  - 后端 `colonel-saas/backend@sha256:386206688a69625f3b3eb6cb9cadf6340fabc8fe2051de7ad7519dc2c6e48210`
  - 前端 `colonel-saas/frontend@sha256:c7f9be3ffe53f2d8e27a72f3e049c47c6f42afa4925c1ea98dcb42c95f6dcad2`
- 回滚后后端 readiness、前端 healthz、四个 Compose 服务均健康。
- `release-completed` 未创建，未生成新的 `current.json`。

## 未完成项

1. 先查清寄样链 HTTP 462 的业务原因，不能把 `PENDING_AUDIT` 写成成功。
2. 补充真实订单归因样本，当前订单归因仍是 PENDING。
3. 修正 Jenkins 失败兜底证据：本次归档的 `latest-jenkins-cd.md` 错误写成“Production touched: NO”，但归档状态已证明实际发生过部署并完成回滚。
4. 生产根分区发布后约剩 2.1GB（99% 使用率）；清理备份前必须由管理员确认保留策略，当前未删除任何文件。

## Retro

- 做得对：发布清单漂移先由安全闸门拦截；P0 失败后在部署锁内回滚；生产最终保持旧版本健康。
- 需要改进：失败兜底报告必须根据 `deployment-started`、`rollback-completed` 和 `schedulers-restored` 生成真实结论，不能固定写“未触碰生产”。
- 下一步验证：修正证据生成后，用一次受控失败演练确认报告、回滚状态和锁释放结果一致。
