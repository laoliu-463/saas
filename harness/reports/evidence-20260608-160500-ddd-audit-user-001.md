# Evidence — DDD-AUDIT-USER-001

| 字段 | 值 |
| --- | --- |
| 时间 | 2026-06-08 16:05:00 |
| 环境 | 本地只读 |
| 分支 | feature/auth-system |
| commit | 90701c73 |
| 工作区 | 无 staged 业务变更；untracked 为其他 harness reports |
| Scope | docs / audit-only |
| 构建 | 未执行（只读审查） |
| Docker | 未重启 |
| 健康检查 | 未执行 |
| 业务验证 | 未执行（只读源码审查） |
| 远端部署 | 否 |
| 结论 | **PASS**（审查任务范围内） |

## 执行的验证命令

```text
git status --short
git branch --show-current  → feature/auth-system
git log -1 --oneline       → 90701c73 docs: complete DDD-AUDIT-SAMPLE-001 audit report
git diff --check           → PASS
```

## KB 路径验证

| 路径 | Test-Path |
| --- | --- |
| plans/ddd-refactor/audits/ddd-audit-user-001.md | 已创建 |
| plans/ddd-refactor/domains/user-ddd-plan.md | 已更新 |
| plans/ddd-refactor/tasks/ddd-audit-user-001.md | 已创建 |
| plans/ddd-refactor/tasks/00-task-index.md | 已更新 |
| plans/ddd-refactor/03-execution-order.md | 已更新 |

## 源码只读扫描摘要

| 类 | 结论 |
| --- | --- |
| AuthService | 登录/刷新/登出 + Redis 黑名单 + 锁定 |
| JwtTokenProvider | access/refresh claims 含 dataScope |
| JwtAuthenticationFilter | 写入 userId/deptId/dataScope/roleCodes |
| DataScopeAspect | Mapper AOP 三级过滤 |
| SysUserService.applyDataScopeFilter | Service 手动 scope |
| PerformanceAccessScope | 业绩域平行 SQL scope |
| UserDomainService | CurrentUser + permissions 合并 |
| DeptType | dept_type 标准常量 |

## 未执行项

- mvn test / build（无代码变更）
- 容器重启 / real-pre 业务验证
- git commit / push（用户未要求）

## 剩余风险

- 三套 dataScope 实现不一致可能导致越权或漏数
- JWT dataScope 与 DB 角色变更滞后
- PerformanceAccessScope 组长成员校验简化
- 跨域 SysUserMapper 注入面广，Facade 前任何用户表变更影响面大
