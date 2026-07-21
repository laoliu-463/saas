# real-pre 约束

## 什么时候用

修改真实上游、Docker Compose、数据库迁移、部署、回滚或 real-pre 验收时使用。本地 `real-pre` 是默认验证环境，远端 `real-pre` 只接受 Jenkins 队列发布。

## 执行原则

真实闭环必须使用 live 上游、真实配置和可追踪数据。缺 Token、授权、订单或样本时标记 `BLOCKED` / `PENDING`，不得切换 mock 后宣称通过。

## 成功标准

证据同时记录环境、镜像或代码版本、数据库迁移结果、健康检查、业务验证和远端发布结果；远端发布必须能回到 Jenkins 构建和 release 分支。

## 失败回滚

停止后续操作，保留 evidence 和容器日志；按 [`../runbooks/rollback.md`](../runbooks/rollback.md) 回滚。禁止清库、删除 volume 或绕过发布队列止血。
