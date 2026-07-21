# 开发与 real-pre 发布流程

本文是仓库开发、合并和 real-pre 发布的唯一流程入口。业务事实仍以 `docs/` 主文档和代码为准；部署参数、凭证和服务器登录信息不写入仓库。

## 黄金路径

```text
Issue / 任务
  -> 从 main 创建短期分支和独立 worktree
  -> 本地按变更范围验证
  -> Draft PR -> GitHub Actions CI
  -> 合并到 main
  -> 发布提升 PR：main -> release/real-pre
  -> Jenkins saas-real-pre-cd 校验并串行部署
```

约束如下：

- `main` 是默认集成分支，任务分支不承担 real-pre 发布职责。
- `release/real-pre` 是唯一 real-pre 发布分支；发布候选必须先进入 `main`，再通过发布提升 PR 进入该分支。
- GitHub Actions 负责变更范围内的测试、构建和治理检查；提交状态由稳定名称 `CI Gate` 汇总。
- Jenkins 只从 `release/real-pre` 发布 real-pre，并校验完整 40 位 SHA、发布顺序、迁移计划、健康检查和回滚证据。
- 普通 Agent 不直接 SSH、`git pull`、现场构建或重启远端 real-pre。

## 本地验证

根据任务范围执行：

```bash
# 后端
cd backend && mvn test

# 前端
cd frontend && pnpm install --frozen-lockfile && pnpm test && pnpm typecheck && pnpm build

# test/mock P0
npm run e2e:v1-p0
```

real-pre 验收使用 [real-pre 联调手册](验收/real-pre联调手册.md)。缺少真实 Token、订单或授权样本时必须保留 `BLOCKED` / `PENDING`，不能改写为 `PASS`。

## real-pre 发布

标准入口是 Jenkins job `saas-real-pre-cd`。发布前必须确认：

- 目标 commit 已在 `release/real-pre`，且对应 `main` 的可追溯提交。
- 镜像、迁移、当前发布和回滚目标均有证据。
- 数据库备份、兼容迁移、backend readiness、前端健康和 P0 / 多角色验证均完成。
- 发布失败时记录回滚动作和剩余风险。

服务器地址、SSH 用户、IdentityFile、环境文件路径和凭证只保存在私有 Runbook 或密码管理系统中。

## Break-glass 紧急恢复

手工 SSH、服务器上的 `git pull`、现场 `docker build` 和 Compose 重启只允许用于经批准的紧急恢复，不是日常部署路径。执行时必须：

1. 记录审批人、目标 SHA、原因和预计影响。
2. 获取与 Jenkins 相同的 real-pre 主机锁。
3. 使用受控脚本完成备份、部署、健康检查和回滚。
4. 将完整证据补录到私有运行记录，并在后续通过正常 PR 路径收敛差异。

不得执行 `docker compose down -v`，不得删除 PostgreSQL / Redis volume，不得用 mock 配置证明 real-pre 通过。

## 相关入口

- CI 工作流：`.github/workflows/ci.yml`
- Jenkins CD：`Jenkinsfile`
- real-pre 运行总览：[docs/10-部署运行总览.md](10-部署运行总览.md)
- 部署文档入口：[docs/deploy/README.md](deploy/README.md)
