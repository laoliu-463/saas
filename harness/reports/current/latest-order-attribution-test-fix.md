# 订单归属修复测试证据

- 时间：2026-07-25（Asia/Shanghai）
- 环境：测试服务器 `my-second-brain-server`（192.168.101.220）
- Compose：`saas-active`，目录 `/home/caojianing/saas-production`
- 生产环境：未连接、未修改
- 分支：`codex/test-order-attribution-status-20260725`
- 提交：`ad614b71`（包含前一提交 `f14f7c21`）
- 工作树：干净

## 根因

订单回放服务 `OrderAttributionReplayService` 重复写入归属用户 ID，但没有同步渠道和招商两个归属状态字段。结果是用户已经分开，状态仍显示渠道未归属。

## 代码修复

- `OrderDefaultAttributionPolicy` 的旧桥接补写双维度状态。
- `OrderAttributionReplayService` 的回放路径同步补写双维度状态。
- 新增旧桥接和回放路径回归断言。

## 本地验证

- `git diff --check`：通过
- Maven 定向测试：通过
  - `OrderDefaultAttributionPolicyTest`
  - `OrderDefaultAttributionResolverTest`
  - `OrderAttributionReplayServiceTest`
  - `PerformanceRecordSyncListenerTest`
- Maven 构建：`mvn -f backend/pom.xml -DskipTests package` 通过
- Harness 门禁：`TASK_GATE=PASS`
- code-review-graph：已完成增量更新和影响审查；变更影响包含订单同步、归因路由、回放控制器和业绩监听路径。

## 测试服务器部署

- 本地构建产物 SHA-256：`F166561EAF9F940419524CAFD8C4B24B5B1763FBEA4AB84CA2C2B7044BB5ABA3`
- 运行后端镜像：`colonel-saas/backend:local-attribution-status-ad614b71`
- 镜像 ID：`sha256:26bc0e7309d4d3062ea349c2b35071deaa965be984ed4202e0a0aca94819f673`
- 应用健康检查：`UP`
- 应用版本：`local-attribution-status-ad614b71`
- 前端、PostgreSQL、Redis：均保持原容器并健康
- 运行安全开关：`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`
- 调度：`APP_SCHEDULING_ENABLED=true`

由于仓库没有 `harness/scripts/commands/remote-verify.ps1`，本次按用户指定的测试服务器使用受控 SSH + Compose 仅替换后端容器；未执行任何 volume 删除或 `down -v`。

## 业务验证

目标：活动 `3916506`、商品 `3829691670191014167`、订单 `6928228692853423377`。

- 壮云：角色为 `biz_staff / 招商专员`
- 商品负责人：壮云
- 两条活动商品映射：均归属 `渠道专员测试 / channel_staff`
- 订单回放：`scanned=1`、`attributed=1`、`updated=1`、`nativeKeyMatched=1`、`safeToUpdate=1`
- 订单最终结果：
  - 渠道：渠道专员测试，`CHANNEL_ATTRIBUTED`
  - 招商：壮云，`RECRUITER_ATTRIBUTED`
  - 聚合：`ATTRIBUTED`
- `performance_records`：
  - `final_channel_user_id`：渠道专员测试
  - `final_recruiter_user_id`：壮云

## 结论

**PASS：本次订单归属修复范围已通过本地单元测试、测试服务器 API 回放和数据库逐字段核对。**生产服务器未修改。

未覆盖项：浏览器 Playwright 全流程和正式 Jenkins 发布；它们不影响本次后端归属修复的 API/SQL 结论，需在后续验收中单独执行。

## Retro

- 发现：归因逻辑存在两套写入路径，新增策略修复不能自动覆盖回放服务的重复实现。
- 改进：以后修改订单归属字段时，必须同时检索所有 `setChannelUserId`、`setColonelUserId` 和双维度状态写入点，并为每条入口保留回归测试。
- 验证：本次已补齐策略桥接和回放服务两条路径，并用同一订单回放核对订单表与业绩表。

## 仓库协议缺口

- `harness/INDEX.md`：当前工作树不存在。
- `harness/rules/`：当前工作树不存在。
- `harness/scripts/commands/remote-verify.ps1`：当前工作树不存在。
- `harness` 门禁结果：`REPOSITORY_HEALTH=PARTIAL`，原因是既有 `harness/reports` 根目录历史债务；本次 `TASK_GATE=PASS`。
