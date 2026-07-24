# 订单归属修复验证

- 时间：2026-07-24 23:27:43 +08:00
- 环境：测试服务器 `my-second-brain-server:/home/caojianing/saas-production`
- 分支：`codex/e2e-suite-refactor`
- Commit：`eb2b24340e82dca93d36e85f1d7f8e188a389df6`
- 工作树：干净，已推送到上游

## 根因

生产订单 `6928228692853423377` 的渠道字段为空，`pick_source` 为空，但订单保留了原生 `colonel_buyin_id`。同活动、同商品下存在渠道映射；招商负责人字段来自商品操作状态，因此显示为“招商组长测试”。

旧归因路径只读原始 payload 的团长字段，不回退读取已持久化订单字段，原生映射未命中后留下 `NO_PICK_SOURCE`。生产容器运行版本 `8827b2a…`，早于当前原生订单归因修复。

## 代码修复

- legacy 归因回退读取订单中的主/副 `colonel_buyin_id` 和副活动 ID。
- 增加按真实订单条件覆盖的 DDD 回归测试：buyin 不一致时使用唯一活动+商品原生映射。
- 保留渠道归属与招商归属分离：商品负责人只写入招商维度。

## 本地验证

- 定向 Maven 测试：`AttributionServiceTest`、`OrderDefaultAttributionResolverTest` 通过。
- `mvn -DskipTests package`：通过。
- `git diff --check`：通过。
- 本地 Docker 不可用，因此使用本地 Maven 生成的 JAR，不在测试服务器重复编译镜像。

## 测试服务器部署

- 本地 JAR 上传后 SHA-256 与测试容器 `/app/app.jar` 一致。
- 仅重启测试后端容器 `saas-active-backend-real-pre-1`。
- 未执行 `down`、未删除 volume、未改测试前端或数据库结构。
- 后端健康：`/api/system/health` 返回 `UP`。
- 前端健康：`/healthz` 返回 HTTP 200。
- PostgreSQL、Redis、后端、前端均为 healthy。
- 测试库 `admin` 登录密码按既有测试约定重置为 `admin123`，仅影响测试库。

## 业务验证

同样的原生归因场景订单 `6928194636077432731` dry-run 结果：

```text
scanned=1
attributed=1
unattributed=0
nativeKeyMatched=1
colonelBuyinIdMismatch=1
safeToUpdate=1
updated=0
```

用户指定订单 `6928228692853423377` 在测试库的 dry-run 结果为 `attributed=0`。原因不是运行错误，而是测试库没有该商品/活动对应的“壮云”原生映射；生产只读证据已确认该映射存在。dry-run 未修改该订单，订单仍保持原状态。

## 生产安全

- 生产服务器仅执行容器版本、数据库记录和日志的只读检查。
- 未上传 JAR、未重启容器、未写数据库、未改生产配置。

## 结论

`PARTIAL`：修复代码、定向测试、测试服务器部署和同形态业务 dry-run 均通过；指定订单在测试库缺少对应映射，不能在测试服务器直接宣称该订单已归属“壮云”。等待用户在测试服务器验证；确认后再进入生产发布流程。

## Retro

后续真实订单验收必须同时准备订单、活动、商品和原生映射四件样本，不能只复制订单表。验收脚本应先检查映射存在且归属用户可解析，再执行 dry-run，避免把测试数据缺失误判为代码回归。

## 工具限制

code-review-graph 可用但当前后端 Java 图谱覆盖不足；本次使用图谱结果做范围确认，并通过源码手工追踪归因路由、映射回退和回放服务。未声称完成完整 Java 图谱覆盖。
