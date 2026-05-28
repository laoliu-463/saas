# 上线前全量审查总览与 GO/NO-GO 判定

审查日期：2026-05-27 | 审查基线：feature/auth-system @ cfd7236
审查人：上线前全量审查负责人 + 高级后端架构师 + QA Lead + DevOps 审计员

## 一、项目概况

| 指标 | 值 |
|------|-----|
| Java 源文件 | 216 |
| Java 测试文件 | 54 |
| TypeScript 文件 | 111 |
| Vue 文件 | 52 |
| 前端测试文件 | 54 |
| SQL 迁移脚本 | 47 |
| Docker Compose | test + real-pre |
| CI/CD | Jenkinsfile（real-pre 生产形态，含环境守卫和迁移串联） |
| 业务域 | 7（用户/配置/商品/达人/寄样/订单/业绩） |

## 二、审查范围

| 序号 | 审查域 | 报告 | 状态 |
|------|--------|------|------|
| 01 | 用户域 | [01-用户域审查报告.md](01-用户域审查报告.md) | PARTIAL |
| 02 | 配置域 | [02-配置域审查报告.md](02-配置域审查报告.md) | CRITICAL |
| 03 | 商品域 | [03-商品域审查报告.md](03-商品域审查报告.md) | PASS |
| 04 | 达人域 | [04-达人域审查报告.md](04-达人域审查报告.md) | CRITICAL |
| 05 | 寄样域 | [05-寄样域审查报告.md](05-寄样域审查报告.md) | PASS |
| 06 | 订单域与业绩域 | [06-订单域与业绩域审查报告.md](06-订单域与业绩域审查报告.md) | PASS |
| 07 | 三方联调 | [07-三方联调审查报告.md](07-三方联调审查报告.md) | PARTIAL |
| 08 | 运维部署 | [08-运维部署审查报告.md](08-运维部署审查报告.md) | CRITICAL |
| 09 | 问题清单 | [09-P0P3问题清单与修复策略.md](09-P0P3问题清单与修复策略.md) | — |

## 三、问题统计

> 下表为 2026-05-27 原始审查发现数量；最新修复/验证状态以本文件“2026-05-27 复核后状态”和 `09-P0P3问题清单与修复策略.md` 的复核表为准。

| 优先级 | 数量 | 阻断上线 |
|--------|------|----------|
| P0 | 8 | 是 |
| P1 | 19 | 强烈建议修复 |
| P2 | 12 | 否 |
| P3 | 5 | 否 |
| **合计** | **44** | |

### P0 分布

| 领域 | 数量 | 关键问题 |
|------|------|----------|
| 运维部署 | 6 | 原独立 prod / real / local-mock 入口与 real-pre 并存，部署口径分裂 |
| 寄样域 | 2 | God Controller（2736 行）、@Transactional 在 Controller |

### P1 分布

| 领域 | 数量 | 关键问题 |
|------|------|----------|
| 用户域 | 3 | JWT 占位值、DEPT 越权、Token 无轮换 |
| 达人域 | 8 | Provider 占位、测试失败、非公开 API、无 LIMIT |
| 运维部署 | 5 | Guard 校验不足、profile 不完整、Nginx 无安全头 |
| 跨域/三方 | 3 | OAuth HTTP、分区过期、非幂等迁移 |

## 四、测试状态

| 测试类型 | 状态 | 说明 |
|---------|------|------|
| 后端单元测试 | PASS（定向核心回归） | 2026-05-27：`mvn clean '-Dtest=SampleControllerTest,SampleLifecycleServiceTest,OrderSyncServiceTest,RealProdEnvironmentGuardTest,WebConfigTest,SecurityConfigTest,JwtAuthenticationFilterTest,JwtAuthInterceptorTest,TestControllerSecurityTest,DataControllerTest,SysUserServiceTest,TalentServiceTest,TalentQueryServiceTest' test`，255/255 PASS |
| 后端构建 | PASS | `mvn compile` 通过 |
| 前端构建 | PASS | `npm run build` 通过 |
| 前端单元测试 | — | 待确认 |
| E2E (test/mock) | — | 待确认 |
| E2E (real-pre) | — | 待确认 |

## 五、安全审查摘要

| 检查项 | 状态 | 说明 |
|--------|------|------|
| JWT 密钥管理 | PASS | protected profile 下默认/空 JWT secret fail-fast |
| 密码存储 | PASS | BCrypt 哈希 |
| RBAC 权限 | PASS | @RequireRoles + RoleGuardAspect |
| 数据权限 | PARTIAL | DEPT 越权已修复；订单分页 mapper 已移除 `@DataScope` 双重过滤，Sample/SysUser 等注解链路仍作为专项治理保留 |
| CORS 配置 | PASS | real-pre 默认值已环境变量化，Guard/Jenkins 禁止占位域名 |
| HTTPS | PARTIAL | Nginx/compose/Jenkins 已配置 443、证书挂载和 HTTP 跳转；真实证书运行待部署验证 |
| SQL 注入 | PASS | MyBatis-Plus 参数化查询 |
| XSS | PASS | Vue 前端自动转义 |
| CSRF | — | JWT 无状态，不适用 |
| 速率限制 | PASS | Nginx API rate limit 已配置 |
| 安全头 | PASS | Nginx 安全头已配置 |
| 敏感信息泄露 | PARTIAL | /system/env 依赖手动权限检查 |

## 六、架构审查摘要

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 分层架构 | PASS（Controller 阻断项） | `SampleController` 已降至 374 行，`DataController` 已降至 204 行；HTTP 入口保留在 `controller` 包，业务实现下沉到 `service.sample.SampleApplicationService` 与 `service.data.DataApplicationService` |
| 事务边界 | PASS | Controller 目录未检出 `@Transactional`；寄样写事务由 `SampleWriteTransactionService` 承担 |
| JWT 校验 | PASS | 运行时只注册 `JwtAuthenticationFilter`；`JwtAuthInterceptor` 不再作为 Bean/MVC 拦截器注册 |
| 数据权限 | PARTIAL | 订单链路已统一为显式 wrapper 数据范围；其他历史注解链路仍需专项梳理 |
| 配置管理 | PASS | 当前只保留 test 与 real-pre；real/prod/local-mock/dev 配置已合并或删除，real-pre 使用环境变量和 Guard |
| 部署可重现性 | PARTIAL | 已有版本化镜像、回滚 stage 和迁移脚本；仍缺真实生产流水线执行证据 |

## 七、GO/NO-GO 判定

### 判定结果：**NO-GO**

### 2026-05-27 复核后状态

本轮已修复并验证一批可闭环问题：生产 Compose 结构、生产配置补全、生产流水线版本化镜像逻辑、Redis/DB/业务开关 Guard、JWT/DEPT/Refresh Token、`/system/env` protected profile 保护、SysConfig `@Valid`、业绩存在性查询、OAuth HTTPS 默认值、前端 Nginx 安全头/限流/镜像锁定、寄样 Controller 事务下沉、订单同步本地断路器、JWT 双重校验移除、Redis 连接池、测试 Redis 密码、达人公共池 SQL 上限、管理员重置后强制改密等。

- `AuthServiceTest,SysUserServiceTest,RealProdEnvironmentGuardTest,RuntimeExposurePolicyTest,SystemEnvControllerTest,JwtAuthenticationFilterTest`：81/81 PASS
- `PerformanceMetricsQueryServiceTest,DashboardServiceTest,SysConfigControllerTest,RealPreMigrationContractTest,TalentDataProviderTest,TalentProfileSyncServiceTest,ProductDisplayAuditServiceTest`：35/35 PASS
- `ProductDisplayAuditServiceTest,TalentProfileSyncServiceTest,TalentDataProviderTest`：14/14 PASS
- `mvn clean '-Dtest=SampleControllerTest,SampleLifecycleServiceTest,OrderSyncServiceTest,RealProdEnvironmentGuardTest,WebConfigTest,SecurityConfigTest,JwtAuthenticationFilterTest,JwtAuthInterceptorTest,TestControllerSecurityTest,DataControllerTest,SysUserServiceTest,TalentServiceTest,TalentQueryServiceTest' test`：255/255 PASS
- `mvn clean '-Dtest=DataScopeAspectTest,DataControllerTest,OrderAttributionControllerTest,TalentDataProviderTest,RealProdEnvironmentGuardTest,TalentEnrichModeGuardTest' test`：67/67 PASS
- `mvn '-Dtest=RoleGuardAspectTest,SampleControllerTest,DataControllerTest' test`：108/108 PASS
- `SampleController.java` 行数：374；`DataController.java` 行数：204
- `docker compose --env-file .\.env.test.example -f .\docker-compose.test.yml config --quiet`：PASS
- `docker compose --env-file .\.env.real-pre.example -f .\docker-compose.real-pre.yml config --quiet`：PASS
- `mvn clean "-Dtest=RealPreMigrationContractTest,RealProdEnvironmentGuardTest,RuntimeExposurePolicyTest,TalentEnrichModeGuardTest,SwaggerConfigTest,TestControllerSecurityTest,SystemEnvControllerTest,StartupEnvironmentLoggerTest" test`：45/45 PASS
- `bash -n scripts/run-real-pre-db-migrations.sh`：PASS
- `mvn package -DskipTests`（backend）：PASS
- `docker compose --env-file .\.env.real-pre.example -f .\docker-compose.real-pre.yml build backend-real-pre frontend-real-pre`：PASS

仍不能改为 GO 的阻断项：

1. R-05/OPS-16 已补正式迁移脚本和 Jenkins 串联，但仍缺真实生产流水线执行记录与迁移日志表落库证据。
2. T-02/T-03/R-10 仍缺真实第三方 endpoint/凭证与官方替代源确认；当前只能证明代码通道、配置和 protected profile Guard 已闭环。

### 判定依据

**阻断因素（必须消除）：**

1. **real-pre 生产形态执行证据不足**（R-05/OPS-16）：迁移工具和分区维护已接入 Jenkins，但仍缺 real-pre 生产形态执行记录
2. **外部数据源/非公开采集风险**（T-02/T-03/R-10）：第三方 HTTP 通道已接入配置化 Provider，真实 endpoint/凭证与官方替代源仍需外部确认

**不阻断但强烈建议修复：**

- 达人 Enrich Provider 真实源配置（T-02）— 代码通道已接入，仍需真实 endpoint/凭证验证
- 非公开 crawler 替代源（T-03/R-10）— protected profile 误启已禁止，官方替代源仍需外部权限

### GO 条件（必须全部满足）

| 序号 | 条件 | 当前状态 | 目标 |
|------|------|----------|------|
| 1 | 保留 real-pre docker-compose 并删除独立 prod 入口 | 已合并，配置解析通过 | 有且验证通过 |
| 2 | Jenkinsfile 收口到 real-pre 生产形态 | 已合并，含环境守卫和迁移串联 | 有且含守卫 |
| 3 | 实现部署回滚机制 | 已有版本化镜像与 rollback stage | 版本化镜像 + 一键回滚 |
| 4 | 合并 prod/real 配置到 application-real-pre.yml | 已合并 | 所有配置节完整 |
| 5 | 替换 real-pre CORS 占位域名 | 已改环境变量注入并加守卫 | 真实域名 |
| 6 | 密码环境变量化 | 已改为环境变量注入 | 环境变量注入 |
| 7 | 配置 HTTPS | 配置/compose/Jenkins 守卫已补，真实证书运行待验证 | SSL 证书 + 强制重定向 |
| 8 | JWT fail-fast | 已修复 | 未设置时拒绝启动 |
| 9 | 后端测试全部通过 | 定向核心回归 255/255 + 67/67 PASS，未跑全量 `mvn test` | 0 Errors |
| 10 | SampleController 拆分 | 已完成：`SampleController` 374 行，`DataController` 204 行；Controller 包内保留 HTTP 入口和 `@RequireRoles` 切面执行点 | <500 行 Controller |

### 预计修复周期

| 阶段 | 内容 | 人天 |
|------|------|------|
| real-pre 执行取证 | real-pre 迁移流水线、`schema_migration_log` 落库、证书运行证据 | 1-2 |
| 外部依赖取证 | 达人真实 endpoint/凭证或官方替代源确认 | 依赖外部 |
| P2 改进 | 容器资源限制、JVM 容器参数等非阻断治理 | 1-3 |
| **合计** | | **本地代码项已收口，剩余取决于外部环境证据** |

### 建议

1. **补 real-pre 生产形态执行证据**，重点是迁移流水线、迁移日志表落库与分区维护记录
2. **补外部数据源证据**，确认达人真实 HTTP endpoint/凭证或官方替代源
3. **real-pre 迁移流水线执行**需补真实 Jenkins 记录与 `schema_migration_log` 落库证据
4. **外部达人数据源**需补真实 endpoint/凭证或官方替代源确认
5. **P2 可排入上线后第一个迭代**
