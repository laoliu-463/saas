# DDD-PRODUCT-001 — ProductDomainFacade

时间：2026-06-12  
环境：local / `mvn test`  
分支：`feature/auth-system`（工作区未提交）

## 目标

商品域只读门面，供跨域读取商品主数据与快照，不搬业务逻辑。

## 变更

| 文件 | 说明 |
|------|------|
| `ProductDomainFacade` | 只读接口 |
| `LegacyProductDomainFacade` | 委派 `ProductMapper` / `ProductSnapshotMapper` |
| `ProductReadDTO` / `ProductSnapshotReadDTO` | 跨域最小字段集 |
| `LegacyProductDomainFacadeTest` | 契约单测（3） |

## 方法

- `findProductById` / `findProductByExternalId`
- `findSnapshotById`
- `existsById`
- `loadProductNamesByIds`

## 验证

| 用例 | 结果 |
|------|------|
| `LegacyProductDomainFacadeTest` | PASS（3） |

## 后续（Batch 3）

寄样域 `SampleApplicationService` 等仍直注 Mapper；替换在跨域调用替换批次进行。

## 结论

**PARTIAL** — Facade 已抽取 + 单测绿；尚未替换现有跨域 Mapper 注入。
