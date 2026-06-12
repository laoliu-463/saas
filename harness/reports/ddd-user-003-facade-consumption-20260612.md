# DDD-USER-003 证据报告

时间：2026-06-12  
环境：real-pre（本地）  
分支：feature/auth-system  
Scope：backend（WIP 未 commit）

## 变更摘要

扩展 `UserDomainFacade` 消费，移除 3 处跨域 `SysUserMapper` 注入：

| 类 | 变更 |
|---|---|
| `OperationLogService` | `UserDomainFacade.getUserById` 反查 username |
| `ExclusiveMerchantQueryService` | 同上解析招商员名 |
| `SampleFilterOptionsService` | `getUsersByIds` / `getUserById` 构建筛选 label |

同步更新 `cross-domain-mapper-legacy-whitelist.txt`（移除已迁移项）。

## 测试

| 套件 | 结果 |
|---|---|
| `OperationLogServiceTest` | PASS |
| `ExclusiveMerchantQueryServiceTest` | PASS |
| `SampleFilterOptionsServiceTest` | PASS |
| `DddCrossDomainMapperGuardTest` | PASS |

## 结论

**PARTIAL** — 代码与单测绿；合入 commit 与 Docker 健康检查待 agent-do 补证。

## 剩余

- `MerchantService` / `SampleApplicationService` / `TalentQueryService` 等待 Batch 3
- `DataController` 仍直注 `SysUserMapper`（非本卡范围）
