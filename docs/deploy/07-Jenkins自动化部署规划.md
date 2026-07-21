# Jenkins real-pre 发布规则

## 目标

日常发布固定为：开发分支 -> GitHub PR / CI Gate -> `main` -> 镜像产物 -> `release/real-pre` 提升 PR -> Jenkins `saas-real-pre-cd`。

开发人员不需要登录服务器、执行 `git pull` 或现场 `docker build`。Jenkins 也不从服务器源码构建镜像，只消费 `release/real-pre.json` 中的不可变镜像摘要。

## GitHub 产物

合并到 `main` 后，GitHub Actions 在 `CI Gate` 通过后执行：

1. 后端打包并构建镜像。
2. 前端构建镜像。
3. 使用完整 `github.sha` 写入 OCI revision。
4. 推送 `ghcr.io/laoliu-463/saas/backend:<sha>` 和 frontend 对应镜像。
5. 记录两个 `repository@sha256:digest` 到 `image-release.json` artifact 和 Job Summary。

PR 到 `release/real-pre` 必须包含 `release/real-pre.json`，并通过 `scripts/verify-real-pre-release.py`。该清单还必须固定迁移版本、迁移输入摘要和上一版本回滚引用。

## Jenkins 前置条件

- Job 名称：`saas-real-pre-cd`。
- 唯一部署分支：`release/real-pre`。
- Jenkins 节点拥有 Docker Compose、Python 3、Node / pnpm、curl 和 Git。
- Jenkins 凭据中配置 `saas-container-registry`，类型为 username/password，密码只用于读取容器仓库。
- `/opt/saas/env/.env.real-pre` 由服务器受控保存，不进入 Git 或 Jenkins 日志。
- Jenkins Lockable Resources 配置全局资源 `saas-real-pre-deploy`。

## Pipeline 顺序

```text
Checkout release/real-pre
  -> Preflight manifest / main ancestry / migration fingerprint
  -> registry login + docker pull repository@digest
  -> docker compose config（禁止 build）
  -> saas-real-pre-deploy 全局锁
  -> release order + migration diff
  -> backup / Flyway / schema precheck（仅迁移输入变化时）
  -> backend（暂停调度）+ readiness
  -> frontend
  -> P0 smoke + 多角色 E2E
  -> 恢复调度 + readiness
  -> evidence + 原子更新 current.json
```

任何 readiness、smoke、E2E 或证据核对失败，都必须保持发布失败状态；若已有上一份不可变镜像，Jenkins 尝试按部署前引用回滚，并记录回滚结果。

## 发布清单

路径：`release/real-pre.json`。字段要求见 [release/README.md](../../release/README.md)。服务器归档：

```text
/opt/saas/releases/<sourceMainSha>/release.json
/opt/saas/releases/current.json
/opt/saas/releases/previous.json
```

`current.json` 只有在镜像、运行 SHA、后端健康、前端版本、迁移和业务验收全部一致后才原子替换。

## 禁止事项

- 禁止 Jenkins `docker compose build`、`docker build` 或从服务器源码重新生成镜像。
- 禁止使用 `latest`、短 SHA、浮动 tag 或未带 digest 的镜像。
- 禁止普通 Agent 直接 SSH、服务器 `git pull` 或修改 `/opt/saas/app`。
- 禁止 `docker compose down -v`、删除 PostgreSQL / Redis volume 或用 mock 配置证明 real-pre 通过。
