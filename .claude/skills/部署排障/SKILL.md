# 部署排障

description: 排查 Docker Compose、profile、端口、数据库、Redis、real-pre 启动问题。

## 步骤

[V1 必做] 读取 `docs/10-部署运行总览.md` 与 `.claude/lsp/诊断规则.md`。

[V1 必做] 确认只能运行一套目标环境，不能同时混起 test 与 real-pre 后端/前端。

[V1 必做] 检查 compose、`.env.*`、容器健康、端口、数据库名、Redis DB。

[V1 必做] 输出现象、证据、推论、阶段性结论和回滚方式。

