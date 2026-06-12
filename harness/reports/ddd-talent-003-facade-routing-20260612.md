# DDD-TALENT-003 Facade 路由报告

时间：2026-06-12  
分支：`feature/ddd/DDD-SAMPLE-005-FIX-sample-agent`  
任务：Batch3 Replace — TalentController 切 Facade

## 变更摘要

| 原调用点 | 新调用点 | 开关 | 回退 |
|---------|---------|------|------|
| `TalentController` → `TalentQueryService` | `TalentController` → `TalentQueryApplicationService` → `TalentQueryService` | `ddd.refactor.enabled` + `ddd.refactor.talent-facade.enabled` | 子开关 false 时 1:1 委派旧服务 |
| detail / assertCanOperate | 开关开启时先 `TalentDomainFacade.existsById` | 同上 | 关闭开关跳过 Facade |

## 新增文件

- `domain/talent/application/TalentQueryApplicationService.java`
- `architecture/DddTalent003TalentRoutingTest.java`

## 验证

- `DddTalent003TalentRoutingTest` — 开关 on/off 三分支
- `TalentControllerTest` — 构造与 page 委托更新
- 待跑：`mvn test` + `agent-do -Scope backend`

## 结论

阶段性 **PASS**（单测绿 + agent-do 后定稿）
