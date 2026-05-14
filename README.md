# 抖音团长 SaaS V2.2

本项目是一套面向抖音电商“团长”业务的全链路管理系统。

当前默认运行口径：

- 环境：`test`
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

- 当前主导航链路：
  - [04-开发进度](./docs/04-开发进度.md)
  - [10-上线前验收清单](./docs/10-上线前验收清单.md)
  - [11-real-pre证据索引](./docs/11-real-pre证据索引.md)
- [docs/README](./docs/README.md)
- [00-项目总览](./docs/00-项目总览.md)
- [01-业务闭环](./docs/01-业务闭环.md)
- [02-架构设计](./docs/02-架构设计.md)
- [03-Test与Real网关契约](./docs/03-Test与Real网关契约.md)
- [04-开发进度](./docs/04-开发进度.md)
- [05-接口与数据模型](./docs/05-接口与数据模型.md)
- [06-部署与对接计划](./docs/06-部署与对接计划.md)
- [09-真实SDK联调准备清单](./docs/09-真实SDK联调准备清单.md)
- [10-上线前验收清单](./docs/10-上线前验收清单.md)
- [11-real-pre证据索引](./docs/11-real-pre证据索引.md)
- [10-V2.2场景覆盖矩阵](./docs/10-V2.2场景覆盖矩阵.md)
- [archive/README](./docs/archive/README.md)
- [README-e2e](./README-e2e.md)（根目录 Playwright）

## 快速上手

1. 测试环境：`docker compose --env-file .env.test --project-name saas-test -f docker-compose.test.yml up -d`
2. 打开前端：`http://localhost:3000`
3. 登录后访问 `/dev/test` 进行 reset / seed / 造数调试。

## 当前标准启动格局

以后本机启动统一按下面这套执行，不再接受混合占用：

| 用途 | 前端 | 后端 | 数据库 | Redis | 启动来源 |
| --- | --- | --- | --- | --- | --- |
| `test` 人工联调 / 自动化基线 | `3000` | `8080` | `5432` | `6379` | `test` compose + 本机单前端 |
| `real-pre` 浏览器回归 | `3001` | `8081` | `5433` | `6380` | `real-pre` compose |

强制要求：

- `3000` 只保留一个本机前端进程
- `3001` 只保留 `real-pre` 前端来源，不允许本机再起第二个 `vite --port 3001`
- `8080` 只对应 `test` backend
- `8081` 只对应 `real-pre` backend
- 非明确需要时，不单独启动本机 `redis-server` 占用 `6379`

real-pre 回归口径：

- 后端：`http://localhost:8081/api`
- 前端：`http://localhost:3001`
- 适用于页面级 E2E 回归、权限验收、部署形态验证和 `/api/actuator/health` 健康检查
- 当前 `real-pre` 是独立端口/容器拓扑，使用 `SPRING_PROFILES_ACTIVE=real`、`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`
- `test` 是 Mock 联调和回归基线，旧 `local-mock` 口径保留为历史脚本、报告和回滚参考

当前基线：

- `backend mvn test`：以 `docs/04-开发进度.md` 最近一次全量为准（2026-05-09：`652 tests, 0 failures, 0 errors`）
- `frontend npm.cmd run build`：通过
- real-pre 浏览器回归报告：`runtime/qa/out/e2e-20260503-1353/report.md`，2026-05-03 全路径回归 `45/45` 通过
- local-mock 补充验收报告：`runtime/qa/out/local-mock-supplement-20260503-1430/report.md`，2026-05-03 交互级补充验收 `5/5` 通过
- QA 脚本入口：`runtime/qa/full-browser-e2e.cjs`、`runtime/qa/local-mock-supplement.cjs`、`runtime/qa/data-gap4-visible.cjs`
- 根目录 Playwright：`README-e2e.md`（`npm run e2e`、`npm run e2e:real-pre`）
- QA 一键命令：`powershell -ExecutionPolicy Bypass -File .\scripts\run-real-pre-e2e.ps1`、`powershell -ExecutionPolicy Bypass -File .\scripts\run-local-mock-supplement.ps1`、`powershell -ExecutionPolicy Bypass -File .\scripts\run-data-gap4-visible.ps1`
- QA 串行总入口：`powershell -ExecutionPolicy Bypass -File .\scripts\run-qa-all.ps1`
- 拓扑检查命令：`powershell -ExecutionPolicy Bypass -File .\scripts\check-env-topology.ps1`

补充说明：

- 抖店 SDK 依赖不再使用 `systemPath`
- 项目通过 `backend/lib/maven-repo/` 加载 `com.doudian:open-sdk:1.1.0`

## Environment Secret Policy

- Real credentials must not be stored in tracked repository files.
- Repository example files may contain field names and placeholders only, never live values.
- Local real-integration credentials must live in untracked env files, CI/CD secrets, or server environment variables.
- If a real Douyin secret was ever shared in a working tree, screenshots, or prior commits, rotate `DOUYIN_CLIENT_SECRET` and related credentials.

## Local Startup Checklist

1. Use `docker compose --env-file .env.test --project-name saas-test -f docker-compose.test.yml up -d` for isolated `test`.
2. Start only one local frontend for `3000`; do not start another local Vite on `3001`.
3. Use `docker compose --env-file .env.real-pre --project-name saas -f docker-compose.real-pre.yml up -d --build backend-real-pre frontend-real-pre` for `real-pre` browser regression.
4. Do not use `dev` as the default local walkthrough profile.
5. Do not store real Douyin credentials in tracked files.
6. Use `/api/test/**` only in `test` or `real-pre`.
7. If `3001` is already occupied by a local Node/Vite process, stop it before starting `real-pre`.
8. If `6379` is already occupied by a standalone local Redis, stop it unless you explicitly need it.

## 开发规范

- 统一使用 **UTF-8** 编码。
- 业务逻辑面向 **Gateway 契约** 开发，确保 Test 与 Real 环境平滑切换。
- 前端组件遵循 **Naive UI** 与 **Vue 3** 最佳实践。

---

> **核心价值**：流量分发有闭环，业绩归因有回流。
