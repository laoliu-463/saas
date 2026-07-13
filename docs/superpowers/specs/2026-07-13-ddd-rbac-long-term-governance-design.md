# DDD RBAC 长期治理设计

**日期：** 2026-07-13

**状态：** 已批准，待实施计划

**范围：** 用户域授权能力、业务域动作策略、JWT、菜单与数据范围

## 目标

在当前 Spring Boot 模块化单体中，把固定角色判断、JSON 权限、菜单权限和全局数据范围收敛为可审计、默认拒绝、可按领域灰度迁移的授权模型。外部 API 在迁移期保持兼容，业务规则仍由各业务域负责。

## 现状证据

- `@RequireRoles` 与 `RoleGuardAspect` 以固定角色编码控制业务接口。
- `sys_role.permissions` JSON 和 `sys_menu.permission_code` 形成另一套权限来源。
- `sys_role.data_scope` 是全局范围，无法表达同一角色在不同业务域的差异。
- JWT 携带并直接提供 `roleCodes`、`dataScope`，缓存失效不能使旧令牌立即失权。
- `DataScopePolicy` / `DataScopeAspect` 存在缺少上下文时不加过滤的放行路径。
- `UserDomainFacade` 同时承担授权、用户目录和归属查询，边界过宽。

## 方案选择

采用“用户限界上下文内的 Authorization 子域能力”。不新建微服务，也不在当前阶段引入 Keycloak、OPA 或 OpenFGA。与独立授权域相比，该方案符合现有用户域对用户、角色、组织和数据范围的所有权，迁移半径更小；未来如出现跨系统统一策略需求，可把已收敛的 facade/port 替换为外部 PDP。

不保留现有角色 JSON 作为长期方案，因为它无法形成单一授权事实；不单独建立 Authorization 限界上下文，因为当前模块化单体中的用户、角色、组织和会话生命周期高度一致；不引入外部策略引擎，因为当前没有跨系统统一策略与独立运维证据。

## 统一语言

- **权限**：稳定业务能力，编码为 `resource:action`，例如 `sample:approve`。
- **角色**：权限和领域数据范围的管理集合，不是业务代码分支条件。
- **授权主体**：当前用户、组织、账号状态和授权版本组成的可信上下文。
- **授权决策**：`ALLOW` 或 `DENY`，附权限、领域、数据范围和原因码。
- **领域数据范围**：当前权限在一个业务域内的 `SELF/GROUP/ALL/DENY`。
- **业务动作策略**：业务域基于授权决策、对象归属、状态机和业务规则作最终判断。
- **菜单可见性**：授权结果的展示投影，不是安全事实来源。

## 领域边界

### 用户域

- 管理用户状态、组织归属、角色、权限目录、角色授权和领域数据范围。
- 生成授权主体、授权决策和 `authzVersion`。
- 提供窄接口 `AuthorizationFacade`；用户名称和组织目录查询迁入 `UserDirectoryFacade`。
- 不读取业务聚合，不判断寄样、订单、商品、达人或业绩状态。

### 业务域

- 声明自己拥有的权限语义，权限目录由受控迁移或代码注册，不允许后台任意造码。
- 把领域数据范围映射为本域查询条件，不接收用户域 MyBatis Wrapper 修改。
- 对命令加载聚合后执行领域动作策略；入口权限通过不等于业务动作合法。

### 接口层与前端

- 接口通过统一权限元注解和 Spring `AuthorizationManager` 执行入口校验。
- 前端消费后端权限集合过滤菜单、路由和按钮，不维护角色矩阵。
- 前端隐藏不是安全边界，后端对每个受保护请求重新决策。

## 增量数据模型

| 模型 | 关键约束与用途 |
| --- | --- |
| `sys_permission` | `permission_code` 全局唯一；记录领域、资源、动作、状态、是否需要数据范围 |
| `sys_role_permission` | 角色—权限唯一关系，只允许有效角色和权限参与决策 |
| `sys_role_domain_scope` | `(role_id, domain_code)` 唯一；值为 SELF/GROUP/ALL |
| `sys_user.authz_version` | 非空、单调递增；用于令牌和授权快照失效 |
| `sys_authz_change_log` | 追加式记录操作者、对象、脱敏后的变更前后和请求追踪信息 |

保留 `sys_user_role`。迁移期保留 `sys_role.permissions`、`data_scope`、`menu_config` 和 `sys_role_menu`，完成对账后停止写入，再通过独立迁移删除。

## 授权不变量

- 默认拒绝；用户、组织、权限、领域范围或授权存储上下文缺失时不得放行。
- 禁用用户、角色或权限不参与授权；角色查询必须过滤有效状态。
- 禁止用户直接授权、角色继承、代码级管理员绕过和业务域角色字符串判断。
- 多角色只合并实际授予当前权限的角色范围，避免无关角色扩大可见范围。
- 需要数据范围的权限若无对应领域配置，决策为 `DENY`。
- 审计日志不得记录密码、Token、密钥或其他凭证。
- 职责分离规则由团队定义冲突角色后再建模，不创建推测性规则。

## 运行时决策链

1. JWT 过滤器验证签名、类型、有效期、吊销、账号状态和 `authzVersion`。
2. JWT 长期只保留认证声明；旧 `roleCodes`、`dataScope` 仅用于兼容链。
3. 过滤器建立 `AuthorizationPrincipal`，不把角色字符串作为新链权威输入。
4. 权限元注解调用 `AuthorizationApplicationService`。
5. 服务端读取版本化授权快照，返回决策、领域范围和原因码。
6. 业务应用服务加载聚合，领域策略继续校验归属、状态、幂等和流转。
7. 查询场景由本域把 SELF/GROUP/ALL/DENY 转换为显式查询条件。

缓存键包含用户 ID 和授权版本。Redis 未命中回查 PostgreSQL；两者均不可用时返回服务不可用，禁止降级放行。角色权限等变更在同一数据库事务中递增受影响用户版本，提交后事件只负责驱逐缓存和追加审计。

## 错误语义

- `401`：令牌无效、过期、吊销、账号失效或授权版本过旧。
- `403`：身份有效，但权限、领域范围或对象访问条件不足。
- `409`：权限允许，但业务状态冲突或状态机拒绝。
- `503`：授权事实无法从缓存和数据库取得。
- 对外响应不暴露角色和内部策略细节；内部日志记录稳定原因码和 trace ID。

## 灰度迁移

1. 从角色注解、JSON 权限和菜单编码生成候选权限矩阵，由业务负责人确认。
2. 增量创建表、约束、端口、审计和授权版本，不改变旧请求决策。
3. 以 `LEGACY/SHADOW/ENFORCE` 按业务域切换；禁止“新旧任一允许即放行”。
4. 影子阶段区分 `OLD_ALLOW_NEW_DENY` 与 `OLD_DENY_NEW_ALLOW`，每项必须有结论。
5. 建议按用户域基础、寄样、达人/商品、订单/业绩、系统管理顺序强制切换。
6. 后端稳定后迁移前端权限投影，最终删除角色切面、JWT 旧声明和 JSON 权限链。

回滚优先从 `ENFORCE` 降为 `SHADOW`。新增表不通过删表回滚；回到 `LEGACY` 会恢复旧模型风险，只能作为有审计、有期限的临时止血措施。

## 验证门禁

- 单元测试覆盖权限合并、有效状态、范围绑定、缺失上下文和默认拒绝。
- 数据库集成测试覆盖唯一约束、事务回滚、版本递增和审计。
- 安全接口测试覆盖 401/403/409/503、旧令牌失效和越权负向场景。
- 领域策略测试证明权限通过后仍受归属、状态机和幂等约束。
- E2E 覆盖角色 × 权限 × 数据范围 × 业务状态矩阵。
- 并发测试证明授权变更提交后不存在旧权限继续成功的窗口。
- 架构测试禁止业务域新增 `RoleCodes`、`@RequireRoles` 和跨域 Mapper。
- 每个领域进入 `ENFORCE` 前，权限矩阵已确认、未解释差异为零，并完成 Harness evidence。

## 长期治理

- 新权限默认不授予任何角色；高风险授权变更必须审计。
- 定期检查孤儿权限、停用角色、未使用权限和异常 ALL 范围。
- 紧急访问使用独立受审计流程，不保留代码级管理员后门。
- 性能门槛以迁移前基线为依据，不在缺少测量时编造目标值。

## 参考实践

- [NIST RBAC](https://csrc.nist.gov/projects/role-based-access-control)
- [NIST SP 800-162 ABAC](https://csrc.nist.gov/pubs/sp/800/162/upd2/final)
- [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)
- [Spring Security Authorization Architecture](https://docs.spring.io/spring-security/reference/servlet/authorization/architecture.html)
- [Kubernetes RBAC](https://kubernetes.io/docs/reference/access-authn-authz/rbac/)
- [AWS IAM ABAC](https://docs.aws.amazon.com/IAM/latest/UserGuide/introduction_attribute-based-access-control.html)
- [Keycloak Authorization Services](https://www.keycloak.org/docs/latest/authorization_services/)
- [Open Policy Agent](https://www.openpolicyagent.org/docs/latest/)
- [OpenFGA Authorization Concepts](https://openfga.dev/docs/authorization-concepts)
