# Local Development Environment

## 适用场景

用于本机静态审查、文档/Harness 调整、轻量构建和本地脚本验证。默认不代表业务闭环已经通过。

## 当前事实

| 项 | 当前证据 | 说明 |
| --- | --- | --- |
| 工作目录 | `D:\Projects\SAAS` | 当前 Codex workspace |
| 后端 | `backend/pom.xml` | Spring Boot 3.2.5 / Java 17 |
| 前端 | `frontend/package.json` | Vue 3 / Vite / Pinia / Naive UI / TypeScript |
| 根 E2E | `package.json` | Playwright 与 `runtime/qa/*.cjs` |
| Compose | `docker-compose.test.yml`、`docker-compose.real-pre.yml` | test 与 real-pre 两套 |
| 环境文件 | `.env.example`、`.env.test.example`、`.env.real-pre.example` | 示例可读；真实 `.env*` 不输出值 |

## 常用命令

```powershell
npm --prefix frontend run build
mvn -f backend/pom.xml test
npm run e2e:v1-p0
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun
```

## 待确认

- 本机 Java、Maven、Node 版本以实际命令输出为准；本文不硬编码。
- `frontend/pnpm-lock.yaml` 存在，但当前根脚本和 Harness 入口使用 `npm`。如切换包管理器，必须同步更新执行脚本和本环境说明。

## 禁止事项

- 不把本地 `.env.real-pre` 内容复制到文档、报告或远端。
- 不用本机临时端口进程替代 Compose 健康检查。
- 不把 docs-only 结构验证写成代码构建或业务闭环通过。
