# 贡献与 Git 协作指南

普通开发者只需按[日常开发流程](./docs/development-flow.md)操作。本文件只补充 PR、风险和边界规则；CI、Harness 和 Jenkins 的实现细节不在这里重复。

## 基本规则

- 从最新 `origin/main` 创建短分支和独立 worktree。
- 一个任务对应一个分支和一个 PR；不要混入其他任务的修改。
- 不直接推送 `main` 或 `release/real-pre`，不把 SSH 作为日常部署方式。
- 不提交 `.env`、凭证、Token、密码、私钥、证书、构建产物或临时文件。
- PR 使用仓库模板；系统负责收集 CI、Harness 和 evidence，开发者只需填写业务说明和验证方式。

## 按风险填写 PR

| 风险 | 示例 | 额外说明 |
| --- | --- | --- |
| 低 | 文案、样式、文档、非核心页面、单元测试 | 修改、验证、PR、CI |
| 中 | 普通业务逻辑、API、权限、佣金、数据查询 | 补充影响范围和验证方式 |
| 高 | 数据库、CI/CD、认证、结算、定时任务、发布基础设施 | 补充兼容性、回滚和故障处理 |

数据库变更必须说明迁移版本、历史数据兼容和回滚方式。高风险路径由 `CODEOWNERS` 指定评审人。

## 合并与发布

- CI 的稳定入口是 `CI Gate`；未通过或未完成项不能写成 `PASS`。
- 普通开发 PR 只提交候选变更，不自行合并或部署。
- 合并后的 real-pre 发布必须通过 `release/real-pre` 和 Jenkins 唯一发布队列。
- 详细操作只在[维护者 Runbook](./docs/runbooks/)和 [Harness 规则](./harness/README.md)中维护。
