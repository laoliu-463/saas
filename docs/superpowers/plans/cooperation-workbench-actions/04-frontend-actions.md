# 前端操作栏

## API 与类型

- `SampleItem` 增加 `version/activityId/productSpecification/actionAvailability`。
- 增加编辑上下文、保存、推广复制、订单复制、私有备注 API。
- 页面只消费后端 `enabled/disabledReason`，不自行推导状态机。

## 组件

- `CooperationActionColumn.vue` 固定竖排七项操作。
- `SampleEditModal.vue` 展示只读达人/商品事实并编辑备注和地址。
- `PrivateNoteModal.vue` 编辑当前用户私有备注。
- 查看进度复用 `SampleDetail`。
- 复制失败时展示可手动复制文本框。

## 验证

- Vitest 覆盖固定顺序、禁用提示、保存参数、剪贴板失败降级。
- 页面验证各状态下七项始终存在，修改入口按后端能力置灰。
