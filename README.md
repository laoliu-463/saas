# 抖音团长 SaaS V1

本项目是抖音团长 SaaS V1 的业务记录系统与 real-pre 联调工作台。当前事实以代码、测试、运行配置和 `docs/` 文档共同确认，旧 V2.2、FastAPI、Celery、Python 爬虫式方案只作为历史归档背景。

## 当前口径

- 技术栈：Spring Boot 3.2、Java 17、PostgreSQL、Redis、Docker Compose、Vue 3、TypeScript、Playwright。
- 当前环境只保留 `test` 和 `real-pre` 两类入口。
- `test` 是 mock 回归基线。
- `real-pre` 是真实上游 / 生产形态验证环境，不允许用 mock 数据冒充真实闭环。
- 服务器 real-pre 目标是受控部署验证，不是正式生产全量放量。

## 文档入口

- 总地图：[CLAUDE.md](./CLAUDE.md)
- 文档地图：[docs/README.md](./docs/README.md)
- 用户手册：[docs/11-用户操作手册.md](./docs/11-用户操作手册.md)
- V1 范围：[docs/01-V1交付范围与边界.md](./docs/01-V1交付范围与边界.md)
- 测试验收：[docs/09-测试验收总览.md](./docs/09-测试验收总览.md)
- 部署运行：[docs/10-部署运行总览.md](./docs/10-部署运行总览.md)
- real-pre 联调：[docs/验收/real-pre联调手册.md](./docs/验收/real-pre联调手册.md)
- 服务器部署：[docs/deploy/README.md](./docs/deploy/README.md)
- 开发与发布流程：[docs/development-flow.md](./docs/development-flow.md)
- Playwright：[README-e2e.md](./README-e2e.md)

## 环境与端口

| 环境 | 前端 | 后端 | 数据库 | Redis | 用途 |
| --- | --- | --- | --- | --- | --- |
| `test` | `3000` | `8080` | Compose 内 PostgreSQL | Compose 内 Redis | mock 回归、P0 基线 |
| `real-pre` | `3001` | `8081` | `saas_real_pre` | Compose 内 Redis | 真实上游、部署验证 |

强制约束：

- 不混起第二个 `3001` Vite。
- 不额外手工启动占用 `8080` 的后端。
- real-pre 必须保持 `APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`。
- real-pre 受控部署默认关闭真实推广写入：`DOUYIN_REAL_PROMOTION_WRITE_ENABLED=false`、`ALLOW_REAL_PROMOTION_WRITE=false`。

## 本地开发

安装根目录 E2E 依赖：

```bash
npm install
npx playwright install
```

后端回归：

```bash
cd backend
mvn test
```

前端构建：

```bash
cd frontend
npm run build
```

启动 test：

```bash
npm run start:test
```

启动 real-pre：

```bash
npm run start:real-pre
```

停止本地服务：

```bash
npm run stop
```

## 验收命令

test/mock 基线：

```bash
npm run e2e:smoke
npm run e2e:v1-p0
```

real-pre 预检和 P0：

```bash
npm run e2e:real-pre:p0:preflight
npm run e2e:real-pre:p0
npm run e2e:real-pre:roles
```

当前最近一次本地证据：

- `mvn test`：`1499 tests, 0 failures, 0 errors`
- `npm run build`：通过，仅有 Vite chunk 体积警告
- `npm run e2e:real-pre:p0:preflight`：通过，证据目录 `runtime/qa/out/real-pre-preflight-20260531-200252/`

## real-pre 发布

日常开发、合并和 real-pre 发布统一遵循[开发与 real-pre 发布流程](./docs/development-flow.md)：

```text
GitHub main -> PR + CI -> release/real-pre 发布提升 PR -> Jenkins saas-real-pre-cd
```

普通任务不得通过 SSH、`git pull` 或服务器现场构建部署。服务器地址、登录用户、密钥路径和环境文件只保存在私有 Runbook 或密码管理系统中。

手工 SSH 仅作为经批准的 Break-glass 紧急恢复流程，执行时必须记录审批、目标 SHA、主机锁、备份、健康检查、回滚和补录证据。不得执行 `docker compose down -v`，不得删除 real-pre 数据卷。

## 环境密钥规则

- 真实密钥不能提交到 Git。
- `.env.real-pre`、`.env.test`、`.env` 均被 `.gitignore` 排除。
- 示例文件只能保留字段名和占位值。
- 不要直接把本机 `.env.real-pre` 复制到服务器；远端必须基于 `.env.real-pre.example` 重新填写域名、OAuth 回调、CORS 和真实写入开关。
- 如果真实抖音密钥曾出现在提交、截图或日志中，必须轮换相关密钥。

## 核心价值

流量分发有闭环，业绩归因有回流。
