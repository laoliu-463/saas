# DDD-SAMPLE-007 Facade 路由报告

时间：2026-06-12  
任务：Batch3 Replace — SampleController 切 Facade/Port

## 变更摘要

| 原调用点 | 新调用点 | 开关 | 回退 |
|---------|---------|------|------|
| `SampleController` → Query/Command Service | → `SampleQueryApplicationService` / `SampleCommandApplicationService` | `ddd.refactor.sample-application.enabled` | 子开关 false 时 1:1 委派 |
| 按 ID 读/写 | 开关开启时先 `SampleDomainFacade.existsById` | 同上 | 关闭开关跳过 Facade |

## 新增

- `SampleDomainFacade` + `LegacySampleDomainFacade`
- `SampleQueryApplicationService` / `SampleCommandApplicationService`

## 验证

- `DddSample007SampleRoutingTest` / `LegacySampleDomainFacadeTest` / `SampleControllerTest`

## 结论

阶段性 **PASS**（待 agent-do 定稿）
