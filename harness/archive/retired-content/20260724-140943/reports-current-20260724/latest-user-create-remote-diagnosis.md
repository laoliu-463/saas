# 远端 real-pre 新增用户“服务器异常”排查证据

## 任务信息

- 时间：2026-07-15（Asia/Shanghai）
- 环境：远端 real-pre，主机 `VM-0-12-ubuntu`
- 任务范围：只读日志、数据库和运行状态取证；本轮未修改代码、配置或业务数据
- 远端代码：`38689b0a`
- 远端工作区：`/opt/saas/app` 无 dirty 输出
- 本地工作区：已有其他任务留下的修改和未跟踪报告；本轮未触碰这些文件

## 现象

新增用户页面显示“服务器异常”。对应接口为 `POST /api/users`。

## 证据链

1. 远端后端日志在 2026-07-15 03:53:32Z、03:53:34Z、03:53:36Z（北京时间 11:53:32、11:53:34、11:53:36）记录了 3 次 `POST /api/users`。
2. 3 次请求均进入 `SysUserController.create` -> `SysUserService.create` -> `SysUserCRUDApplicationA.create` -> `SysUserCrudMutationStoreAdapter.insertUser`，数据库错误为：
   `duplicate key value violates unique constraint "sys_user_username_key"`，冲突值为用户名 `玄同`。
3. 同一用户记录 `1c34b680-30b2-41ec-bdc7-2dde1f37e786` 在 03:53:05Z 被 `DELETE /api/users/{id}` 软删除；数据库查询显示：`username=玄同`、`deleted=1`、`update_time=2026-07-15 03:53:05.457559`。
4. 远端数据库实际唯一索引为：`sys_user_username_key ON sys_user(username)`，没有 `WHERE deleted = 0` 条件。因此软删除记录仍然占用用户名。
5. 当前代码的创建前置校验调用 `SysUserMapper.findByUsername`，SQL 条件包含 `deleted = 0`；它不会查到上述软删除记录。随后 INSERT 被数据库唯一约束拒绝。
6. `GlobalExceptionHandler.handleGeneral` 将未匹配的数据库异常转换为 `ApiResult.of(ResultCode.SERVER_ERROR, null)`；`ResultCode.SERVER_ERROR` 的消息是“服务器异常”，前端请求拦截器按业务码非 200 展示该消息。

## 运行状态

| 检查项 | 结果 | 证据 |
|---|---|---|
| 后端健康 | PASS | `GET http://127.0.0.1:8081/api/system/health` 返回 `{"status":"UP"}` |
| 前端入口 | PASS | `http://127.0.0.1:3001/login` 返回 HTTP 200 |
| Docker 容器 | PASS | frontend/backend/postgres/redis 均为 healthy |
| 数据库连接 | PASS | 已通过 real-pre PostgreSQL 只读查询确认用户记录和索引 |
| 新增用户业务重放 | SKIP | 为避免再次写入失败日志和重复请求，本轮未主动重放 POST |
| 构建 | SKIP | 本轮未修改代码 |
| 容器重启 | SKIP | 本轮只读排查，不执行重启 |
| 远端部署 | SKIP | 用户未要求部署，本轮未部署 |
| docs safety-check | PASS | `safety-check.ps1 -Env real-pre -Scope docs -DryRun` |
| Harness limits | FAIL / BLOCKED | `TASK_GATE=FAIL`、`REPOSITORY_HEALTH=PARTIAL`；工作区已有报告类 dirty 导致 `harness/reports` 相对 `HEAD` 文件预算恶化，本轮未删除或归类用户文件 |

## 阶段性结论

当前证据足以定位：这不是服务器宕机、容器不健康或数据库不可连接，而是“软删除用户仍占用全局唯一 username”与“创建前校验只查未删除用户”之间的约束不一致。数据库异常又被兜底处理器包装成通用“服务器异常”，所以前端没有显示真实的用户名冲突原因。

本轮结论状态：`PARTIAL`。问题已定位，修复尚未实施和验证。

## 修复边界与风险

- 如果业务规则是“用户名一经使用不可复用”：保留当前全局唯一索引，新增包含软删除记录的用户名占用校验，并补充数据库唯一键异常的竞态兜底，返回明确的重复用户名业务错误。
- 如果业务规则允许“软删除后复用用户名”：需要经业务确认后再迁移为仅约束 `deleted = 0` 的部分唯一索引，并明确恢复、审计和历史登录标识规则；本轮不自行改库。
- 无论选择哪种规则，都应补充“软删除后再次创建同名用户”和“并发创建同名用户”回归测试。
- 不建议通过删除或修改现有 `sys_user` 记录临时放行；这会改变 real-pre 持久化事实，且没有业务授权和回滚证据。

## Retro

本次问题可由以下工程改进提前暴露：用户创建路径已有“活跃用户名重复”单测，但没有覆盖“软删除用户名仍受物理唯一索引约束”的集成场景。改进动作是把数据库约束语义、Mapper 查询语义和创建服务异常映射放在同一个回归矩阵中，并在 UI/API 验收中断言重复用户名的业务错误码，而不只断言弹窗文本。
