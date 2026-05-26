# Java / Spring Boot

[V1 必做] 当前后端事实为 Spring Boot，不是 FastAPI。

[V1 必做] 重点检查 Controller、Service、Gateway、Mapper、Job、Aspect、Listener。

[V1 必做] 业务层不能直接依赖真实抖音 SDK 字段；真实差异由 Gateway 吸收。

[V1 必做] 定时与异步以 Spring `@Scheduled` / Job / Redis 锁 / Outbox 为准，不写 Celery 当前事实。

[V1 必做] 权限检查同时关注 `@RequireRoles`、`@DataScope`、Service 对象级校验。

