# 文档总览（2026-04-21）

本目录已按“当前代码实况”更新。

## 1. 主源与参考

- 主源需求：`doc/requirements/*.md`
- 主源规则：`doc/rules/*.md`
- 执行计划：`doc/DEVELOPMENT-PLAN.md`
- SDK 协作：`doc/DOUYIN_SDK_INTEGRATION.md`
- 每日记录：`doc/DAILY-PROGRESS.md`

## 2. 当前项目状态

- 后端：V1.0 到 M1.5 已实现（含寄样自动闭环）
- 前端：已完成核心页面对接
- 测试：`mvn test` 全绿
- 未完成：第三方 SDK 真实环境联调与验收

## 3. 下一步建议

1. 完成抖音 SDK 真联调（Token + activity/product/order 三接口）
2. 完成 M1.3 真数据链路验收（订单入库、归因、寄样自动完成）
3. 推进 M1.6 数据看板真实口径与 M1.7 部署验证

## 4. 历史文档处理原则

- 历史文档保留用于追溯，不作为执行依据
- 执行请以 `doc/requirements/`、`doc/rules/`、`doc/DEVELOPMENT-PLAN.md` 为准
