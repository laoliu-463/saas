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

## 同步到服务器

> **2026-07-21 更新：手工 SSH 部署已退役。所有发布由 Jenkins real-pre CD 完成。**

本仓库的发布黄金路径：

```text
本地分支 → PR → main → CI → 镜像构建 → release/real-pre 提升 PR → Jenkins real-pre CD
```

请按以下顺序：

1. 从 `main` 创建 worktree + 短期分支（详见 [CONTRIBUTING.md](./CONTRIBUTING.md)）。
2. 本地完成后推分支、开 Draft PR。
3. PR CI 全绿后合入 `main`（如使用 Merge Queue 则自动）。
4. `release/real-pre` 的提升必须通过独立 PR；Jenkins 只在该 PR 合并后部署。
5. 部署后由 P0 smoke + 多角色 E2E 验证，证据归档到 `releases/<sha>/`。

**不要执行：**

- 直接 SSH 到服务器执行 `git pull` / `docker compose up`。
- 把本机 `.env.real-pre` / `.ssh` 私钥拷贝到服务器。
- 跳过 Jenkins 自定义在服务器上手工改代码或重新构建镜像。

如发生 Jenkins 不可用或环境损坏，请走 BREAK-GLASS 流程：参考 [docs/deploy/README.md](./docs/deploy/README.md) 中"⚠️ BREAK-GLASS 紧急恢复"一节，并在事后补一份事后复盘到 `releases/<sha>/break-glass-YYYYMMDD.md`。

不要执行 `docker compose down -v`，避免清空 real-pre 数据卷。

## 环境密钥规则

- 真实密钥不能提交到 Git。
- `.env.real-pre`、`.env.test`、`.env` 均被 `.gitignore` 排除。
- 示例文件只能保留字段名和占位值。
- 不要直接把本机 `.env.real-pre` 复制到服务器；远端必须基于 `.env.real-pre.example` 重新填写域名、OAuth 回调、CORS 和真实写入开关。
- 如果真实抖音密钥曾出现在提交、截图或日志中，必须轮换相关密钥。

## 核心价值

流量分发有闭环，业绩归因有回流。
