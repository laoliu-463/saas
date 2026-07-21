# 日常开发流程

本文是普通开发者的唯一流程入口。管理员、值班人员和 CI/CD 维护者请看[维护者 Runbook](runbooks/)。

## 只记住五步

1. 从最新 `main` 创建短分支。
2. 修改代码或文档。
3. 运行统一验证入口。
4. 推送分支并创建 PR。
5. `CI Gate` 通过、评审完成后合并。

## 常用命令

```bash
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c fix/<short-name>

# 目标统一入口：由 Harness 按变更范围选择检查
harness inspect
harness verify

git push -u origin fix/<short-name>
```

当前 Gate 0 基线尚未合并 `harness inspect/verify` CLI。CLI 可用前，Windows 的兼容入口是：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 `
  -Env real-pre -Scope docs -ReportKey task-key `
  -OwnedFiles 'path1;path2' -Message "说明本次修改"
```

应用代码请按 [CONTRIBUTING.md](../CONTRIBUTING.md) 的风险分级选择验证；不要自行复制 Jenkins、锁、digest 或发布参数。

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

## 相关入口

- [贡献与 PR 规则](../CONTRIBUTING.md)
- [维护者 Runbook](runbooks/)
- [发布审查索引](release/README.md)
- [文档地图](README.md)
