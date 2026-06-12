# DDD-TALENT-001 证据报告

时间：2026-06-12  
环境：real-pre（本地）  
分支：feature/auth-system  
Scope：backend（WIP 未 commit）

## 变更摘要

新增达人域只读门面（零行为变更，委派既有 `TalentMapper`）：

- `TalentDomainFacade` / `LegacyTalentDomainFacade`
- `TalentReadDTO`（id、douyinUid、douyinNo、nickname、fansCount、status）
- 方法：`findTalentById`、`findByDouyinUid`、`existsById`、`loadNicknamesByIds`

`MapperDomainRegistry` 补充 `.domain.talent.*` 归属，避免 Facade 被误判为跨域注入。

## 测试

| 套件 | 结果 |
|---|---|
| `LegacyTalentDomainFacadeTest` (3) | PASS |
| `DddCrossDomainMapperGuardTest` | PASS |

## 结论

**PARTIAL** — Facade 已落地；Batch 3 跨域替换（`SampleApplicationService` 等）未开始。

## 剩余

- 寄样 / 归因域改用 `TalentDomainFacade` 替换直注 `TalentMapper`
- `INSTITUTE_SETTLEMENT` 等 ORDER-002 后续项独立推进
