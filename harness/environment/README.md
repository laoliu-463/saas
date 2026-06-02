# Environment System

## 作用

本目录记录 Agent 执行任务前必须确认的环境事实。业务部署细节仍以 `docs/10-部署运行总览.md` 和 `docs/deploy/README.md` 为主源，本目录只做环境入口和安全边界。

## 阅读路径

| 场景 | 必读 |
| --- | --- |
| 本机开发或静态验证 | `local-dev-env.md` |
| test/mock 回归 | `test-env.md` |
| 本地 real-pre 联调 | `real-pre-env.md`、`docker-compose-map.md` |
| 远端 real-pre 部署 | `remote-real-pre-env.md`、`../runbooks/remote-deploy.md` |
| Docker 服务排障 | `docker-compose-map.md`、`../runbooks/docker-compose-operations.md` |

## 当前环境口径

- 默认工程修改环境：本地 `real-pre`。
- `test`：只用于用户明确要求或专项 mock 回归。
- 远端 `real-pre`：只在用户明确要求部署时操作。
- 不再维护独立 `dev`、`local-mock`、`real`、`prod` 入口；旧文档出现这些词时必须回查当前事实源。

## 禁止事项

- 不输出 `.env`、`.env.real-pre`、`.env.test` 的值。
- 不执行 `docker compose down -v`。
- 不删除 PostgreSQL / Redis volume。
- 不把 real-pre 改成 test/mock。
- 不用 mock 结果证明 real-pre 真实闭环。
