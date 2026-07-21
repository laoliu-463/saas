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

- `test` 用于 mock 回归；`real-pre` 用于真实上游和部署验证。
- 不提交 `.env`、`.env.test`、`.env.real-pre`、Token、密码、私钥或证书。
- 不直接向 `main` 或 `release/real-pre` 推送。
- SSH、现场构建和远端重启不是日常发布方式；紧急情况只能按 Break-glass Runbook 执行。

## 文档入口

- [文档地图](./docs/README.md)：事实、领域、接口、验收和部署文档。
- [部署文档](./docs/deploy/README.md)：详细部署材料的兼容入口。
- [发布审查](./docs/release/README.md)：real-pre 发布审查和证据目录。
- [Playwright 验收说明](./README-e2e.md)。
