# 本地 real-pre 管理员登录锁定排障证据

## 元数据

- 时间：2026-07-15（Asia/Shanghai）
- 环境：本地 `real-pre`，Compose project `saas-active`
- 分支：`codex/ddd-user-role-application`
- 当前 commit：`311e14ef`
- 工作区：已有其他任务的代码修改与未跟踪报告；本轮未触碰这些文件
- 远端部署：未执行

## 现象与复现

- `POST http://127.0.0.1:8081/api/auth/login` 使用仓库现行 QA 默认凭据复现为 HTTP 401。
- 响应消息为“登录失败次数过多，请15分钟后再试”。
- Redis 存在 `auth:login:lock:admin`，TTL 取证约 3 分钟；配置表中锁定阈值为 5 次、锁定时长为 15 分钟。

## 证据链

1. `sys_user` 中 `admin` 为 `status=1`、`deleted=0`，具备 `admin` 角色和全量数据范围；不是数据库用户被禁用或软删除。
2. 数据库只读 BCrypt 比对显示：数据库密码哈希匹配用户指定的恢复密码，不匹配仓库现行 QA 默认密码。报告不记录任何明文密码。
3. 2026-07-15 的 `operation_log` 连续记录多轮 `admin` 登录失败，随后记录“登录锁定”；失败原因均为“用户名或密码错误”。
4. `.env.real-pre` 中的 QA 管理员配置与数据库实际密码不一致；本轮只做内存比对，未输出配置值。
5. 当前代码的锁定实现位于 `AuthService`：Redis key 为 `auth:login:fail:{normalizedUsername}` 和 `auth:login:lock:{normalizedUsername}`，达到配置阈值后写入锁定 key。

## 处置与验证

- 仅删除 `admin` 的 Redis 登录锁和失败计数 key，未修改 PostgreSQL 用户密码、角色或业务数据。
- 删除结果：1 个锁 key 被删除；成功登录后再次确认两个相关 key 均不存在。
- 使用数据库当前有效密码登录：HTTP 200、业务码 200、返回管理员 JWT；未记录或输出 JWT。
- 后端健康：`GET /api/system/health` 返回 HTTP 200，`{"status":"UP"}`。
- 前端健康：`GET http://127.0.0.1:3001/healthz` 返回 HTTP 200。
- Docker：backend、frontend、postgres、redis 均为 healthy。

## 结论

`PARTIAL`：本地管理员账号已恢复可登录，当前不需要重置密码；直接根因是 QA 配置/脚本密码与数据库实际密码漂移，反复使用旧密码触发 Redis 临时锁。数据库没有专用的密码变更时间/操作者字段，近期审计也没有管理员重置密码事件，因此无法确认当前密码具体由谁、何时写入。

## 未执行项与剩余风险

- 未执行构建、单元测试和容器重启：本轮未修改代码、配置或镜像；不把它们写成 PASS。
- 未执行远端部署：用户未要求，且本轮只处理本地 real-pre。
- 现有 QA 脚本仍使用旧凭据，后续自动 preflight 可能再次锁定管理员。需要维护者决定统一凭据来源；不建议把明文密码继续硬编码到脚本或文档。
- Git commit/push 未执行：工作区存在其他任务的 dirty 和 unknown 文件，本轮不混合提交。

## Retro

- 可执行改进：为本地 real-pre QA 引入不落库、不入 Git 的凭据来源，并让 preflight 使用同一来源；责任人：仓库维护者；验证方式：清理旧锁后运行 `npm run e2e:real-pre:p0:preflight`，确认管理员登录 PASS 且不再产生连续失败锁定。

## 证据命令范围

- Docker Compose 状态：本地 `saas-active` real-pre 服务状态。
- Redis：仅查询/删除 `admin` 登录锁与失败计数 key，不输出 Redis 密码。
- PostgreSQL：只读查询 `sys_user`、`sys_user_role`、`system_config`、`operation_log`，不输出密码哈希全文。
- API：登录复现、恢复登录、后端/前端健康检查；未输出 Token。
