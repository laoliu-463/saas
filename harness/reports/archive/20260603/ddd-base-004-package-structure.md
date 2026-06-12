# DDD-BASE-004: DDD 目标包结构

更新时间：2026-06-10  
结论：**PASS**

## 1. 目标

在 `com.colonel.saas.domain` 下为 9 个限界上下文预留目标包骨架，**不移动**既有 `service` / `controller` / `mapper` 代码。

## 2. 包树

```
com.colonel.saas.domain
├── user/          {application,domain,policy,event,facade,infrastructure,query,api}
├── config/        （同上 8 层）
├── product/
├── talent/
├── sample/
├── order/
├── performance/
├── analytics/
└── shared/
```

每个 BC 根目录与 8 层子包均含 `package-info.java`（职责、边界、Facade、迁移任务编号）。

## 3. 与遗留代码并存

| 遗留 | 说明 |
| --- | --- |
| `com.colonel.saas.service.*` | 当前运行主路径 |
| `domain.user.facade` | DDD-USER-001/002 已部分落地 |
| `domain.event` | Outbox 基础设施，后续迁入 `shared.infrastructure` |

## 4. 验证

```powershell
cd backend
mvn compile
mvn test "-Dtest=DddPackageStructureContractTest"
```

| 项 | 结果 |
| --- | --- |
| `mvn compile` | PASS |
| `DddPackageStructureContractTest` | 2/2 PASS |
| 全量 `mvn test` | 见 evidence（存在既有 Mapper 集成测试错误，与 BASE-004 无关） |
| API / 业务路径 | 无变更 |

## 5. 下一步

按层逐步迁移：优先 `domain.{bc}.facade` / `application`，禁止批量移动 God Service。
