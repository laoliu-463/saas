# Retro: DDD-USER-EXCLUSIVE-MERCHANT-APPLICATION-FACADE

## What Changed

- 业绩域独家商家评估只需要 `recruiterUserId -> deptId` 映射，不需要完整用户选项 DTO。
- 已复用用户域 `UserOwnershipReference`，避免再为单一归属字段泄漏 `UserOptionResponse`。

## Evidence

- RED boundary test confirmed the old dependency existed.
- Focused tests: 17 PASS.
- Expanded regression: 227 PASS.
- Package, backend restart, local health, graph refresh all PASS.

## Boundary Notes

- 用户域负责解析用户身份与主组织单元引用。
- 业绩域只消费负责人归属组织单元，用于独家商家覆盖评估。
- 本片未改变独家商家覆盖规则、金额归集、服务费比例或历史数据。

## Follow-Up

- U-7 下一步继续处理 `ProductService` 5 处与 `SampleApplicationService` 2 处 `getUserById/getUsersByIds` 调用。
- 本次无需 Harness 升级；现有 TDD + evidence + retro + graph update 流程足够覆盖该类 facade 小切片。
