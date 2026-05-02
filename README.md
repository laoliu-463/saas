# 抖音团长 SaaS V2.2

本项目是一套面向抖音电商“团长”业务的全链路管理系统。

当前默认运行口径：

- 环境：`local-mock`
- 后端端口：`8080`
- 前端接口前缀：`/api`
- 调试台页面：`/dev/test`
- 初始化接口：`/api/test/seed`
- 登录账号：`admin / admin123`

## 核心能力

- **商品主链路**：同步团长活动商品、内部初筛、招商分配、自动化转链。
- **订单归因**：基于 `pick_source` 的自动化订单业绩对账与负责人归因。
- **寄样管理**：达人选品寄样申请、审核、发货、签收、订单触发自动结算。
- **达人 CRM**：公海/私海保护期机制、达人画像补全。

## 目录导航

- [00-项目总览](./docs/00-项目总览.md)
- [01-业务闭环](./docs/01-业务闭环.md)
- [02-架构设计](./docs/02-架构设计.md)
- [03-Test与Real网关契约](./docs/03-Test与Real网关契约.md)
- [04-开发进度](./docs/04-开发进度.md)
- [05-接口与数据模型](./docs/05-接口与数据模型.md)
- [06-部署与对接计划](./docs/06-部署与对接计划.md)
- [07-Test全链路验收](./docs/07-Test全链路验收.md)
- [08-test-演示脚本](./docs/08-test-演示脚本.md)
- [12-文档编码乱码问题分析报告](./docs/12-文档编码乱码问题分析报告.md)

## 快速上手

1. 本地 Mock 联调：`docker compose up -d`
2. 自动化 / 隔离测试环境：`docker compose -f docker-compose.yml -f docker-compose.test.yml up -d`
3. 打开前端：`http://localhost:3000`
4. 登录后访问 `/dev/test` 进行 reset / seed / 造数调试。

当前基线：

- `backend mvn test`：`394 tests, 0 failures, 0 errors`
- `frontend npm.cmd run build`：通过

补充说明：

- 抖店 SDK 依赖不再使用 `systemPath`
- 项目通过 `backend/lib/maven-repo/` 加载 `com.doudian:open-sdk:1.1.0`

## Environment Secret Policy

- Real credentials must not be stored in tracked repository files.
- Repository example files may contain field names and placeholders only, never live values.
- Local real-integration credentials must live in untracked env files, CI/CD secrets, or server environment variables.
- If a real Douyin secret was ever shared in a working tree, screenshots, or prior commits, rotate `DOUYIN_CLIENT_SECRET` and related credentials.

## Local Startup Checklist

1. Use `docker compose up -d` for `local-mock`.
2. Use `docker compose -f docker-compose.yml -f docker-compose.test.yml up -d` for isolated `test`.
3. Do not use `dev` as the default local walkthrough profile.
4. Do not store real Douyin credentials in tracked files.
5. Use `/api/test/**` only in `local-mock` or `test`.

## 开发规范

- 统一使用 **UTF-8** 编码。
- 业务逻辑面向 **Gateway 契约** 开发，确保 Test 与 Real 环境平滑切换。
- 前端组件遵循 **Naive UI** 与 **Vue 3** 最佳实践。

---

> **核心价值**：流量分发有闭环，业绩归因有回流。
