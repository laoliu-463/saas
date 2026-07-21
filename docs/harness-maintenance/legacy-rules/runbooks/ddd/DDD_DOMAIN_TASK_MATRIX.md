# DDD Domain Task Matrix

> 本文件是 V1 DDD 优化的总览任务矩阵。详细任务清单已拆分为：
> - `DDD_DOMAIN_TASK_MATRIX_core.md`：核心业务域（订单/商品/达人/寄样）
> - `DDD_DOMAIN_TASK_MATRIX_cross.md`：跨域（用户/配置/业绩/分析/Outbox/前端/E2E+GC）
>
> 任务 ID：HARNESS-DOC-GC-OPTIMIZE-003 Step 2
> 拆分时间：2026-06-12

## 使用规则

- 本矩阵是后续任务卡索引，不代表任务已经完成。
- 每次只执行一个任务卡，执行前读取对应领域 instruction 和领域合同。
- 任务结果必须进入 evidence report 和 `docs/harness-maintenance/legacy-rules/state/snapshots/DOMAIN_STATUS.md`。

## 域优化顺序（固定）

1. 用户域
2. 配置域
3. 订单域
4. 业绩域
5. 分析模块
6. 商品域
7. 达人域
8. 寄样域
9. Outbox 事件
10. 前端领域化
11. E2E 验收
12. 垃圾回收

## 总览

| 域 | 任务数 | 文件 |
|---|---:|---|
| 核心业务：订单 | 19 | [[DDD_DOMAIN_TASK_MATRIX_core]] § 订单域 |
| 核心业务：商品 | 22 | [[DDD_DOMAIN_TASK_MATRIX_core]] § 商品域 |
| 核心业务：达人 | 18 | [[DDD_DOMAIN_TASK_MATRIX_core]] § 达人域 |
| 核心业务：寄样 | 22 | [[DDD_DOMAIN_TASK_MATRIX_core]] § 寄样域 |
| 用户域 | 15 | [[DDD_DOMAIN_TASK_MATRIX_cross]] § 用户域 |
| 配置域 | 14 | [[DDD_DOMAIN_TASK_MATRIX_cross]] § 配置域 |
| 业绩域 | 19 | [[DDD_DOMAIN_TASK_MATRIX_cross]] § 业绩域 |
| 分析模块 | 15 | [[DDD_DOMAIN_TASK_MATRIX_cross]] § 分析模块 |
| Outbox 事件 | 15 | [[DDD_DOMAIN_TASK_MATRIX_cross]] § Outbox 事件 |
| 前端领域化 | 11 | [[DDD_DOMAIN_TASK_MATRIX_cross]] § 前端领域化 |
| E2E 与 GC | 8 | [[DDD_DOMAIN_TASK_MATRIX_cross]] § E2E 与 GC |

合计 178 任务卡。

## 关联

- `DDD_DOMAIN_TASK_MATRIX_core.md`：核心业务域（订单/商品/达人/寄样）
- `DDD_DOMAIN_TASK_MATRIX_cross.md`：跨域（用户/配置/业绩/分析/Outbox/前端/E2E+GC）
- `Git history/`：本拆分所属 manifest 目录
