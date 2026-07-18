# Cooperation Workbench Actions Implementation Plan

**Goal:** 在现有合作单表格右侧固定竖排提供“通过、拒绝、修改订单、查看进度、复制链接、复制订单、备注”七项操作。

**Architecture:** 合作单继续复用寄样域；寄样域负责操作能力、受限编辑、订单文本和私有备注，商品域负责真实推广文本，达人域负责有效认领地址和橱窗销量。

## 分册

1. [后端数据与边界](./cooperation-workbench-actions/01-backend-foundation.md)
2. [合作单编辑与复制](./cooperation-workbench-actions/02-sample-product-actions.md)
3. [前端操作栏](./cooperation-workbench-actions/04-frontend-actions.md)
4. [迁移、测试与 Harness 验收](./cooperation-workbench-actions/05-verification-harness.md)

## 不变量

- `SampleVO.actionAvailability` 固定按 `APPROVE, REJECT, EDIT, PROGRESS, COPY_LINK, COPY_ORDER, NOTE` 返回七项。
- 管理员拥有全部人工权限，但系统专属完成/关闭流转不可人工触发。
- 地址更新原子双写合作单和申请人的有效 `talent_claim`。
- 私有备注按 `(sample_request_id,user_id)` 隔离。
- 近 30 天橱窗销量只读取已定义的销量别名，缺失显示 `---`。
- 真实转链失败时返回明确失败原因，不伪造链接。
- 未获用户明确授权时不执行远端部署。

## 完成标准

- 七项操作顺序、竖排样式和禁用原因正确。
- 编辑、两种复制、进度、审核和私有备注均通过针对性测试。
- 后端与前端构建通过，本地 `real-pre` 重启、健康和业务验证有 evidence。
