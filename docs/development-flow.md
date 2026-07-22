# 日常开发流程

本文是普通开发者的唯一流程入口。管理员、值班人员和 CI/CD 维护者请看[维护者 Runbook](runbooks/)。

## 只记住五步

1. 从最新 `main` 创建短分支。
2. 修改代码或文档。
3. 运行 `harness verify`。
4. 推送分支并创建 PR。
5. `CI Gate` 通过、评审完成后合并。

## 常用命令

```bash
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c fix/<short-name>
```

Windows：

```powershell
.\harness.cmd inspect
.\harness.cmd verify
git push -u origin fix/<short-name>
```

团队 shell 将仓库根目录加入 PATH 后，可直接使用 `harness inspect` 和 `harness verify`。Harness 会根据变更自动选择 docs、backend、frontend 或 full，不要求开发者手工指定几十个参数。

## 三条红线

- 不直接推送 `main` 或 `release/real-pre`。
- 不把直接 SSH、服务器 `git pull` 或现场构建作为日常部署。
- 不提交真实环境配置、密钥、Token、密码、私钥或证书。

## 系统自动处理

CI / Harness / Jenkins 负责按变更范围选择测试、检查治理规则、生成 evidence、构建和发布制品、执行发布锁、健康检查以及回滚入口。开发者在 PR 中只填写：

- 改了什么；
- 为什么改；
- 如何验证；
- 是否涉及数据库。

数据库、认证、结算、定时任务、CI/CD 和发布基础设施变更，会由 PR 模板要求补充兼容性、回滚和故障说明。

## real-pre 发布边界

合并到 `main` 后，GitHub Actions 构建不可变后端和前端镜像；发布提升 PR 将 `main` 的源代码 SHA、镜像 digest、迁移版本和回滚引用写入 `release/real-pre.json`，再由 Jenkins 串行部署、验收、留证据或回滚。

普通开发者不直接 SSH、`git pull`、现场 `docker build` 或重启远端容器。缺少真实 Token、订单或授权样本时保留 `BLOCKED` / `PENDING`，不得改写为 `PASS`。

## 相关入口

- [贡献与 PR 规则](../CONTRIBUTING.md)
- [维护者 Runbook](runbooks/)
- [发布审查索引](release/README.md)
- [文档地图](README.md)
