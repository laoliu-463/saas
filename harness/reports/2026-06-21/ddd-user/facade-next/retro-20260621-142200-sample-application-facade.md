# Retro: DDD-USER-SAMPLE-APPLICATION-FACADE

## What Changed

- 寄样创建只需要当前用户主组织单元，已改为负责人归属引用。
- 寄样状态日志、导出、详情和看板只需要用户展示标签，已改为显示标签标量出口。
- 没有改动寄样状态机、权限数据范围、物流流程或历史数据。

## Evidence

- RED boundary test confirmed the old dependency existed.
- Focused subset: 19 PASS.
- Full Sample focused set: 88 PASS.
- Expanded regression: 303 PASS.
- Package, backend restart, local health, graph refresh all PASS.

## Boundary Notes

- 用户域负责解析用户归属与展示标签。
- 寄样域只消费寄样流程所需的归属引用和展示文本。
- `ProductService` 仍是唯一剩余完整用户 DTO 消费者，需要单独拆分，不应混入本片。

## Follow-Up

- U-7 下一步继续处理 `ProductService` 5 处 `getUserById/getUsersByIds` 调用。
- 本次无需 Harness 升级；现有边界测试已能防止 `SampleApplicationService` 回退到完整用户 DTO。
