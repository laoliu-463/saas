# 部署文档入口

## 当前发布事实

日常流程固定为：

```text
短分支 -> GitHub PR / CI Gate -> main -> GitHub immutable images
       -> release/real-pre promotion PR -> Jenkins saas-real-pre-cd
```

- `main` 是唯一集成主线。
- `release/real-pre` 是唯一远端 real-pre 部署来源。
- GitHub Actions 负责检查和在 `main` 合并后构建一次镜像。
- Jenkins 负责拉取发布清单中的镜像摘要、锁定部署、迁移、验收、证据和回滚。
- 普通 Agent 不直接 SSH、不在服务器 `git pull`、不在服务器构建镜像。

## 核心入口

- Compose：`docker-compose.real-pre.yml`
- 环境示例：`.env.real-pre.example`
- 发布清单说明：`release/README.md`
- 发布清单校验：`python3 scripts/verify-real-pre-release.py release/real-pre.json`
- Jenkins CD：`Jenkinsfile`
- Jenkins 规则：[07-Jenkins自动化部署规划.md](07-Jenkins自动化部署规划.md)
- 回滚排障：[06-回滚与故障排查.md](06-回滚与故障排查.md)
- 环境参数：[08-real-pre参数开关契约.md](08-real-pre参数开关契约.md)

## Jenkins 前置条件

- Jenkins 节点可以访问容器仓库并使用受控凭据 `saas-container-registry`。
- Jenkins 配置 Lockable Resources 资源 `saas-real-pre-deploy`。
- 服务器环境文件由服务器受控保存：`/opt/saas/env/.env.real-pre`。
- real-pre 的真实开关必须保持真实模式；缺少 Token、订单或上游样本时记录 `BLOCKED` / `PENDING`。

## 破例恢复

SSH、服务器源码更新、现场构建和手工 Compose 仅作为审批后的 Break-glass 紧急恢复，并且必须使用与 Jenkins 相同的锁、备份、健康检查和证据流程。它不是日常发布入口，恢复后必须通过正常 PR / 镜像 / Jenkins 链路收敛。

## 禁止事项

- 不提交 `.env.real-pre`、密码、Token、私钥或服务器连接信息。
- 不执行 `docker compose down -v`，不删除 PostgreSQL / Redis volume。
- 不使用 `latest`、短 SHA 或无 digest 的浮动镜像。
- 不把 `BLOCKED`、`PENDING` 或 `PARTIAL` 写成 `PASS`。
