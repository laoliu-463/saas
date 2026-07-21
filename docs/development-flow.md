# 开发与 real-pre 发布流程

```text
Issue / 任务
  -> main 短分支与独立 worktree
  -> 本地按变更范围验证
  -> Draft PR -> GitHub Actions CI Gate
  -> 合并 main -> GitHub 构建不可变镜像
  -> 发布提升 PR：main -> release/real-pre
  -> Jenkins 串行部署、验收、留证据或回滚
```

- `main` 是唯一代码真相，`release/real-pre` 是唯一远端部署来源。
- GitHub Actions 负责变更范围检查和 main 镜像产物；Jenkins 不在服务器构建镜像。
- `release/real-pre.json` 显式固定 source SHA、两个镜像 digest、迁移版本和上一版本回滚引用。
- 普通 Agent 不直接 SSH、`git pull`、现场 `docker build` 或重启远端容器。
- 缺少真实 Token、订单或授权样本时保留 `BLOCKED` / `PENDING`，不得改写为 `PASS`。

相关入口：`.github/workflows/ci.yml`、`Jenkinsfile`、`release/README.md`、`docs/deploy/07-Jenkins自动化部署规划.md`。
