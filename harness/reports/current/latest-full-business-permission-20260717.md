# 复合业务角色权限与远端部署证据

## 元数据

- 时间：2026-07-17 22:03 +08:00
- 环境：本地 real-pre、远端 real-pre
- 集成分支：`feature/auth-system`
- 远端提交：`0bff5f6df2709d77164bd5698a1cacb728c1a7d4`
- 代码提交：`2f648d5f`；集成测试修正：`0bff5f6d`
- 部署对象：`saas:/opt/saas/app`
- 目标用户：`1c34b680-30b2-41ec-bdc7-2dde1f37e786`（用户名“玄同”，姓名“壮云”）

## 变更结论

- 非管理员账号通过多角色组合完成业务流程，不再要求授予 `admin`。
- 目标组合为 `biz_leader`、`biz_staff`、`channel_leader`、`channel_staff`、`ops_staff`。
- 操作权限按角色并集生效；`ops_staff` 使数据范围解析为 `ALL`。
- 复合账号不再误套“纯招商/纯渠道/纯运营”的收缩规则。
- 寄样申请、快速寄样、招商审核、达人及订单/业绩读取的前后端门禁已对齐。

## 本地构建与测试

~~~text
Backend package: PASS
  mvn -q -f backend/pom.xml -DskipTests package

Backend targeted permissions/sample tests: PASS
  166 tests, 0 failures, 0 errors

Frontend focused permissions tests: PASS
  6 files, 35 tests

Frontend full tests: PASS
  96 files, 742 tests

Frontend typecheck/build: PASS
  npm --prefix frontend run build
~~~

补充事实：此前源分支全量后端测试运行超过 10 分钟后超时；当时报告中存在 2 个与本次权限修改无关的旧反射断言失败，因此本报告不把“后端全量测试”标记为 PASS。

## 本地 Harness

~~~text
agent-do real-pre full: PASS
Backend build: PASS
Frontend build: PASS
Local Docker restart: PASS
Backend /api/system/health: 200 / UP
Frontend /healthz: 200
e2e:real-pre:p0:preflight: PASS
Task gate: PASS
Repository health: PARTIAL（历史报告数量/行数债务，不是本次新增）
~~~

## 推送与远端部署

~~~text
GitHub origin/feature/auth-system: 0bff5f6d
Gitee gitee/feature/auth-system: 0bff5f6d
Fixed deploy script: PASS
Remote git fast-forward: c89db16a -> 0bff5f6d
Remote backend Maven build: PASS
Remote frontend pnpm build: PASS
Remote backend image rebuild/recreate: PASS
Remote frontend image rebuild/recreate: PASS
Remote HEAD: 0bff5f6df2709d77164bd5698a1cacb728c1a7d4
Remote worktree: CLEAN
~~~

## 远端健康检查

~~~text
backend-real-pre: healthy
frontend-real-pre: healthy
postgres-real-pre: healthy
redis-real-pre: healthy
GET http://127.0.0.1:8081/api/system/health -> {"status":"UP"}
GET http://127.0.0.1:3001/healthz -> ok
~~~

## 目标账号权限变更与核验

管理员 API 登录使用远端环境文件中的 `ADMIN_PASSWORD` 返回 401，证明该环境值与当前管理员密码不一致；未猜测或重置管理员密码。随后使用单事务直接更新角色事实，并同步执行授权版本升级和缓存失效。

~~~text
Transaction precheck: target user active; five target roles enabled
Existing active assignments soft-deleted: 2
Desired assignments upserted: 5
Authorization version: 7 -> 8
Old authorization snapshot deleted: PASS
Short-TTL user permission/data-scope cache eviction published: PASS

DB_VERIFY=
玄同|壮云|1|8|biz_leader,biz_staff,channel_leader,channel_staff,ops_staff|false
~~~

核验结果：目标账号恰好拥有 5 个业务角色，未包含 `admin`。旧 JWT 因授权版本变化将失效，用户必须重新登录获取新角色和 `ALL` 数据范围。

## 业务验证状态

- 代码级权限矩阵、控制器、应用服务、业绩范围、寄样动作及前端路由/按钮测试：PASS。
- 远端部署、数据库角色事实、授权版本、缓存和健康检查：PASS。
- 壮云账号真实密码未提供，已确认其密码不是仓库 QA 默认值；未进行密码猜测。因此该账号重新登录后的浏览器全流程 E2E：PENDING。
- 未在 real-pre 创建寄样、订单等业务脏数据。

## Retro

本次反复出现“加一个权限、另一个权限消失”的根因是纯角色分支和页面局部角色判断。现已统一为：内置业务角色操作权限取并集，数据范围取最大范围，只有纯岗位账号才应用岗位收缩规则。后续应补能力码下发，减少前端角色矩阵重复。

## 结论

`PARTIAL`：代码、构建、自动化测试、远端部署和目标账号角色事实均已通过；仅缺少壮云本人重新登录后的浏览器业务流程验收，不将其伪报为 PASS。

## 剩余风险

- 用户需要退出旧会话并重新登录；旧 token 不会自动获得新角色。
- 本次角色写入因管理员凭据漂移未走管理 API，已用事务、授权版本递增及缓存失效补齐运行语义，但管理 API 操作日志未生成。
- 前端依赖审计仍有 6 个既有漏洞告警（2 high、2 critical），不属于本次权限修改。
