# DDD-USER-003 — DataScopeAspect 归位用户域

更新时间：2026-06-12
状态：READY / WIP 以 `ddd-multi-agent-board.md` 为准
来源：`C:\Users\caojianing\.claude\plans\flickering-orbiting-thompson.md`

## 目标

将顶层 `DataScope` 注解与 `DataScopeAspect` 切面归位到用户域，使 `@DataScope` 查询统一消费 `UserDomainFacade.resolveDataScope(currentUserId)` 返回的 `userIds`，减少跨域重复实现。

## 当前证据

| 证据 | 现状 |
| --- | --- |
| 唯一消费方 | `SampleRequestMapper.findPageWithScope` 使用 `@DataScope(userField = "sr.channel_user_id")` |
| Facade | `UserDomainFacade.resolveDataScope(UUID userId)` 已存在 |
| DTO | `UserDataScopeResponse(scope, code, userIds)`，scope 为 `self/group/all` |
| 旧入口 | `com.colonel.saas.annotation.DataScope` 与 `com.colonel.saas.aspect.DataScopeAspect` |
| Jacoco | `backend/pom.xml` 仍排除旧 FQN |

## 决策

- 注解目标包：`com.colonel.saas.domain.user.api.DataScope`。
- 切面目标包：`com.colonel.saas.domain.user.infrastructure.aspect.DataScopeAspect`。
- 新增 `CurrentUserProvider`，让切面不直接读取 `RequestContextHolder`。
- `null currentUserId`、`null scope`、`null/empty userIds` 均 no-op。
- `self/group` 通过 `AbstractWrapper<?, ?, ?>.in(userField, userIds)` 注入过滤。
- `all` 表示 `userIds` 为空，切面不追加条件。
- `DEPT` 路径不迁入本切面；旧枚举暂不删除，避免影响 `SysUser`、JWT 等其他路径。

## 计划修改

| 类型 | 文件 | 动作 |
| --- | --- | --- |
| 新增 | `domain/user/api/CurrentUserProvider.java` | 暴露 `UUID currentUserId()` |
| 新增 | `domain/user/api/RequestAttributeCurrentUserProvider.java` | 从 request attribute 解析 `userId` |
| 新增 | `domain/user/api/DataScope.java` | 迁移注解，保留最小 Javadoc |
| 新增 | `domain/user/infrastructure/aspect/DataScopeAspect.java` | 委托 `UserDomainFacade` 并注入 `IN` 条件 |
| 修改 | `mapper/SampleRequestMapper.java` | import 新注解 FQN |
| 修改 | `backend/pom.xml` | Jacoco exclude 更新为新 FQN |
| 修改 | `docs/领域/用户域.md` | 补数据范围切面合同 |
| 删除 | 旧注解、旧切面、旧测试 | 删除顶层实现和旧测试 |
| 新增 | 新 `DataScopeAspectTest` | Mockito 单测覆盖核心分支 |

## 验证清单

| 项目 | 命令 / 证据 |
| --- | --- |
| 编译 | `mvn -f backend/pom.xml compile` |
| 目标单测 | `mvn -f backend/pom.xml -Dtest=DataScopeAspectTest test` |
| Facade 回归 | `mvn -f backend/pom.xml -Dtest=LegacyUserDomainFacadeTest test` |
| 残留扫描 | `rg "com\\.colonel\\.saas\\.(annotation\\.DataScope\|aspect\\.DataScopeAspect)" backend/src` 应无旧引用 |
| Jacoco | `rg "DataScopeAspect" backend/pom.xml` 仅保留新 FQN |
| 业务验证 | real-pre 使用 self/group/all 账号验证寄样分页范围 |

## 风险

- `group` 行为从旧 `dept_id` 过滤改为 `sr.channel_user_id IN (...)`，需用寄样分页数据复验。
- `IN ()` 空集合必须 no-op，避免 PostgreSQL 语法错误。
- `ColonelsettlementOrderMapper.findPageWithScope` 不应新增 `@DataScope`，避免与 controller 手动 scope 双重过滤。
- 本任务未完成验证前只能作为实施卡，不能写成完成报告。
