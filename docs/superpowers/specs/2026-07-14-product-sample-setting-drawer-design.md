# 商品寄样设置侧边栏设计说明

## 目标

将商品管理页的“寄样设置”从居中 Dialog 改为右侧侧边栏，并按用户提供的截图重建字段顺序、文案、颜色、底部操作区和说明文字。界面字号使用现有系统字号，不按截图像素放大。

## 已确认的现状证据

- 前端入口：`frontend/src/views/product/components/SampleSettingModal.vue`。
- 前端 API：`GET/PUT /api/products/{relationId}/sample-setting` 已存在，但打开弹窗时当前只读列表行缓存，没有调用 GET。
- 后端入口：`ProductController` 委托 `ProductSampleSettingService`。
- 持久化：寄样字段写入 `product_operation_state.audit_payload`，并保留既有审核补充字段；更新使用乐观锁并写入 `SAMPLE_SETTING` 操作日志。
- 当前实现额外显示并提交了截图中不存在的 `sampleBoxCount`、`sampleQuantity`，本次移除这两个字段的前端提交和寄样设置主流程。
- 达人等级已有当前工程口径 `LV0-LV7`；持久化兼容字段仍使用数值 `sampleThresholdLevel`，前端显示为 `LV{n}`。

## 视觉与交互设计

### 容器

- 使用 Naive UI `n-drawer`，从右侧打开。
- 桌面宽度为 548px；小屏使用 `min(548px, calc(100vw - 24px))`，不得遮挡整个页面的返回路径。
- 标题区使用自定义 header：左侧 4px 红色竖线、标题“寄样设置”、右侧关闭按钮。
- footer 固定在抽屉底部，包含“取消”和红色“确定”按钮；内容区可滚动。
- 字号遵循系统：标题 18px、正文 14px、说明 13px；控件高度约 36–48px。

### 字段

按截图顺序展示：

1. 是否支持免费寄样：支持 / 不支持。
2. 是否有寄样门槛：是 / 否。
3. 达人寄样标准：近 30 天橱窗销量、近 30 天销售额、粉丝数、达人带货等级。
4. 末行两个无标签“请输入”框必须可以编辑；由于当前仓库没有可以证明其业务含义的字段契约，本轮只保留前端临时输入，不进入请求、不写入数据库，禁止凭空命名或持久化。
5. 说明文字保持截图文案，并作为静态帮助信息展示。

门槛关闭时，四个标准控件禁用，提交时由前端将门槛值置空，后端删除兼容门槛字段；这与现有服务的状态语义一致。

## 数据流与接口

打开抽屉时：

1. 根据 `row` 解析 `product_snapshot.id`。
2. 调用 `fetchSampleSetting(relationId)`。
3. 用统一 `normalizeSampleSetting` 将 API 返回、列表缓存和历史别名归一化到表单状态。
4. GET 失败时保留列表缓存作为显示降级，但必须提示“寄样设置读取失败”，不能静默伪造默认已保存状态。

提交时只发送已确认字段：

```json
{
  "supportFreeSample": true,
  "hasSampleThreshold": true,
  "minWindowSales30d": null,
  "minSales30d": 50000,
  "minFans": null,
  "minTalentLevel": 1,
  "sampleType": "FREE",
  "sampleThresholdSales": 50000,
  "sampleThresholdLevel": 1
}
```

后端继续使用现有 `ProductSampleSettingService`：校验非负整数、兼容历史别名、保留审核补充字段、使用乐观锁和写操作日志；本次补充接口说明，明确 `minTalentLevel` 的取值范围为 0–7，并保持 `sampleThresholdLevel` 兼容输出。

## 文件边界

- 修改 `frontend/src/views/product/components/SampleSettingModal.vue`：抽屉结构、视觉样式、加载/错误/提交状态。
- 修改 `frontend/src/views/product/sample-setting.ts`：去除样品盒数/数量，补齐 LV0–LV7 选项和 API 归一化。
- 修改 `frontend/src/views/product/components/SampleSettingModal.test.ts`：覆盖抽屉结构、GET 加载、截图字段、确定提交和错误提示。
- 修改 `frontend/src/views/product/sample-setting.test.ts`：覆盖默认值、等级转换、关闭门槛时清空兼容字段。
- 修改 `backend/src/main/java/com/colonel/saas/service/ProductSampleSettingService.java`：增加达人等级 0–7 校验，移除寄样设置主流程对样品盒数/数量的写入。
- 修改 `backend/src/test/java/com/colonel/saas/service/ProductSampleSettingServiceTest.java`：覆盖等级边界和非法等级。
- 修改 `backend/src/main/java/com/colonel/saas/controller/ProductController.java`：同步 OpenAPI 注释示例，不改变路径。
- 修改 `docs/05-API契约总表.md`：补充已确认字段、读取行为和不改变商品审核/上架状态的契约。

## 验证与风险

- 前端：目标 Vitest、前端构建、页面浏览器截图；检查抽屉打开、关闭、读取失败、提交 loading 和小屏宽度。
- 后端：`ProductSampleSettingServiceTest`、相关 Controller 测试、Maven 构建；验证 `audit_payload` 保留既有审核字段、门槛关闭清空旧值、`LV0/LV7` 可保存、`LV8` 被拒绝。
- 业务：仅改变寄样设置配置，不改变商品审核、上架、展示或寄样申请状态机；仍需在 real-pre 通过现有固定入口做健康检查和 API 验证。
- 末行两个无标签框是明确的未定义业务内容，若后续要求可编辑，必须先补字段名、来源、校验和历史数据策略，再单独扩展 API。
