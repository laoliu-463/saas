# 抖音团长 SaaS

抖音团长内部 SaaS，覆盖商品管理、达人 CRM、寄样、订单归因、业绩统计和看板分析。

技术栈：Spring Boot / Java 17、Vue 3 / TypeScript、PostgreSQL、Redis、Docker Compose、Playwright。

## 日常开发

普通开发者只需阅读[日常开发流程](./docs/development-flow.md)：

```text
从 main 建短分支 → 修改 → harness verify → 提 PR → CI 通过后合并
```

分支、PR 和风险分级见 [CONTRIBUTING.md](./CONTRIBUTING.md)。管理员和故障处理见 [docs/runbooks/](./docs/runbooks/)。

## 快速开始

```bash
npm install
npx playwright install
npm run start:test
```

停止本地服务：

```bash
npm run stop
```

## 常用验证

```bash
cd backend && mvn test
cd frontend && npm run build
npm run e2e:v1-p0
```

开发者统一入口（Windows）：

```powershell
.\harness.cmd inspect
.\harness.cmd verify
```

Harness 会按变更范围选择检查并生成 evidence，不提交、不推送、不 SSH、不远端部署。

## 环境边界

## 合并与 real-pre 发布

日常路径固定为：

```text
短分支 -> GitHub PR + CI Gate -> main
       -> GitHub 构建不可变后端/前端镜像
       -> release/real-pre 发布提升 PR
       -> Jenkins saas-real-pre-cd 串行部署
```

开发人员只需要在本地按变更范围验证、提交 PR、等待 GitHub 检查和评审。Jenkins 消费 `release/real-pre.json` 中的 `repository@sha256:digest`，负责锁、迁移、健康检查、P0 / 多角色验收、证据和回滚；服务器不执行源码 `git pull` 或 Docker 构建。

发布清单校验：

```bash
python3 scripts/verify-real-pre-release.py release/real-pre.json
```

服务器 SSH 仅作为批准后的 Break-glass 紧急恢复入口，不是日常发布方式。不要执行 `docker compose down -v`，不要清空 real-pre 数据卷。

## 环境密钥规则

- 真实密钥不能提交到 Git。
- `.env.real-pre`、`.env.test`、`.env` 均被 `.gitignore` 排除。
- 示例文件只能保留字段名和占位值。
- 不要直接把本机 `.env.real-pre` 复制到服务器；远端必须基于 `.env.real-pre.example` 重新填写域名、OAuth 回调、CORS 和真实写入开关。
- 如果真实抖音密钥曾出现在提交、截图或日志中，必须轮换相关密钥。

## 核心价值

流量分发有闭环，业绩归因有回流。

## 环境与文档入口

- `test` 用于 mock 回归；`real-pre` 用于真实上游和部署验证。
- 不提交 `.env`、`.env.test`、`.env.real-pre`、Token、密码、私钥或证书。
- 不直接向 `main` 或 `release/real-pre` 推送。
- SSH、现场构建和远端重启不是日常发布方式；紧急情况只能按 Break-glass Runbook 执行。

- [文档地图](./docs/README.md)：事实、领域、接口、验收和部署文档。
- [部署文档](./docs/deploy/README.md)：详细部署材料的兼容入口。
- [发布审查](./docs/release/README.md)：real-pre 发布审查和证据目录。
- [Playwright 验收说明](./docs/验收/E2E浏览器测试手册.md)。
