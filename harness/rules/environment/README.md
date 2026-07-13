# Environment System

本目录只记录 Agent 执行环境入口和安全边界；业务部署事实以 `docs/10-部署运行总览.md` 和 `docs/deploy/README.md` 为主源。

## 阅读路径

| 场景 | 必读 |
| --- | --- |
| 本机静态验证 | `envs/local-dev-env.md` |
| test/mock 回归 | `envs/test-env.md` |
| 本地 real-pre | `envs/real-pre-env.md`、`envs/docker-compose-map.md` |
| 远端 real-pre | `envs/remote-real-pre-env.md`、`../runbooks/remote-deploy.md` |
| Docker 排障 | `envs/docker-compose-map.md`、`../runbooks/governance/docker-compose-operations.md` |

## 环境口径

- 默认工程修改环境：本地 `real-pre`。
- `test`：仅用于用户明确要求或专项 mock 回归。
- 远端 `real-pre`：仅在用户明确要求部署时操作。
- 旧 `dev`、`local-mock`、`real`、`prod` 入口不作为当前事实。

## 禁止事项

- 不输出真实 `.env*` 的值。
- 不执行 `docker compose down -v`。
- 不删除 PostgreSQL / Redis volume。
- 不把 real-pre 改成 test/mock。
- 不用 mock 结果证明 real-pre 真实闭环。
