# Evidence: 业绩域 legacy retire (#124) - BLOCKED

## Basic Info

- Time: 2026-06-28 14:49:12 Asia/Shanghai
- Issue: #124 业绩域 legacy retire
- Type: retire
- Status: **BLOCKED**

## 阻塞原因

需灰度 100% + N 周回归

按 SAAS DDD 政策 'Legacy 保留不动；DDD 旁路新增；灰度开关默认 OFF':
- Legacy code 已薄壳委派壳 (DDD Application Service 自包含业务)
- 18 Feature Flag 全部默认 OFF
- 灰度切换需 prod 环境 N 周回归
- 缺真实上游/真实样本只能标记 BLOCKED

## 已完成 (前置条件)

- DDD Application Service 已自包含业务逻辑
- 端口/Policy/Facade 完整 (按 V4 sprint 路径)
- mvn test 通过 (3273+ tests baseline)
- vitest 通过 (657+ tests baseline)

## 解除 BLOCKED 条件

1. 需灰度 100% + N 周回归
2. 真实样本采集或 mock 抖音 API 测试通过
3. N 周 prod 灰度观察

## 总结

#124 当前 BLOCKED by 环境依赖。业务代码已 DDD 化完成, 仅缺真实样本验证。
