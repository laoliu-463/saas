# Retro — user-soft-deleted-restore

- 结论：本次代码修复已通过用户域定向测试、PostgreSQL Mapper 集成测试、后端构建、real-pre 后端容器重启和健康检查；完整 real-pre 业务流未通过，因为 preflight 使用的 QA 管理员凭据连续返回 HTTP 401。
- 可执行改进：维护 real-pre QA 管理员凭据与数据库种子数据的一致性，修复或更新 `QA_ADMIN_USER` / `QA_ADMIN_PASSWORD` 后重新运行 `npm run e2e:real-pre:p0:preflight`，再用管理员 token 执行一次真实新建用户正向验证。
- 本次未修改认证配置、密码哈希或数据库业务数据，避免把认证阻塞误混入用户创建根因修复。
