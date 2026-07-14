# 商品寄样设置侧边栏 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将商品管理页寄样设置改为系统字号的右侧侧边栏，并让已确认的寄样字段通过现有 GET/PUT API 正确读取、校验、持久化和审计。

**Architecture:** 前端继续通过内部 `/api/products/{relationId}/sample-setting` 读写，不直连第三方；打开侧边栏先读取 `product_snapshot.id` 对应的后端事实，失败时只用列表缓存做明确降级展示。后端继续将设置合并到 `product_operation_state.audit_payload`，保留审核补充字段、乐观锁和 `SAMPLE_SETTING` 日志；截图中两个无标签输入框只保留为不可提交的视觉占位。

**Tech Stack:** Vue 3、TypeScript、Naive UI、Vitest、Spring Boot、Java 17、JUnit 5、Mockito、Maven。

---

### Task 1: 固化前端字段模型与序列化规则

**Files:**
- Modify: `frontend/src/views/product/sample-setting.ts`
- Test: `frontend/src/views/product/sample-setting.test.ts`

- [ ] **Step 1: 写失败测试，先锁定截图字段和等级范围**

在 `sample-setting.test.ts` 增加以下行为断言：

```ts
it('uses the screenshot defaults without sample box fields', () => {
  expect(normalizeSampleSetting()).toMatchObject({
    supportFreeSample: true,
    hasSampleThreshold: true,
    minSales30d: 50000,
    minTalentLevel: 1
  })
  expect(normalizeSampleSetting()).not.toHaveProperty('sampleBoxCount')
  expect(normalizeSampleSetting()).not.toHaveProperty('sampleQuantity')
})

it('normalizes LV values from the API and submits numeric compatibility fields', () => {
  expect(normalizeSampleSetting({ sampleThresholdLevel: 'LV3' }).minTalentLevel).toBe(3)
  expect(toSampleSettingPayload({
    supportFreeSample: true,
    hasSampleThreshold: true,
    minWindowSales30d: null,
    minSales30d: 50000,
    minFans: null,
    minTalentLevel: 1
  })).toMatchObject({ sampleThresholdLevel: 1, sampleType: 'FREE' })
})

it('clears threshold values when the threshold switch is off', () => {
  expect(toSampleSettingPayload({
    supportFreeSample: false,
    hasSampleThreshold: false,
    minWindowSales30d: 100,
    minSales30d: 50000,
    minFans: 1000,
    minTalentLevel: 1
  })).toMatchObject({
    minWindowSales30d: null,
    minSales30d: null,
    minFans: null,
    minTalentLevel: null,
    sampleThresholdSales: null,
    sampleThresholdLevel: null
  })
})
```

- [ ] **Step 2: 运行测试，确认它因旧模型不满足而失败**

Run: `npm --prefix frontend run test -- src/views/product/sample-setting.test.ts --run`

Expected: FAIL，失败原因包含旧的 `sampleBoxCount` / `sampleQuantity` 默认字段或等级 `LV3` 无法归一化。

- [ ] **Step 3: 实现最小字段模型**

在 `sample-setting.ts`：

1. 从 `ProductSampleSettingForm` 和默认值中移除 `sampleBoxCount`、`sampleQuantity`。
2. 将 `minTalentLevel` 默认值设为 `1`，以匹配截图的 `LV1`；已有保存值优先。
3. 新增 `TALENT_LEVEL_OPTIONS`，值为 `0..7`，label 为 `LV0..LV7`。
4. 归一化 `minTalentLevel` 时支持数字、数字字符串和 `LVn` 字符串。
5. 提交时保留 `supportFreeSample/freeSample/sampleType` 和门槛兼容字段；门槛关闭时将四个门槛值和 `sampleThreshold*` 值发送为 `null`。

- [ ] **Step 4: 运行测试确认通过**

Run: `npm --prefix frontend run test -- src/views/product/sample-setting.test.ts --run`

Expected: PASS，目标文件 0 failures。

### Task 2: 改造寄样设置组件为右侧侧边栏

**Files:**
- Modify: `frontend/src/views/product/components/SampleSettingModal.vue`
- Test: `frontend/src/views/product/components/SampleSettingModal.test.ts`
- Modify: `frontend/src/api/productManage.ts` only if the existing GET response shape cannot be consumed without changing the public path.

- [ ] **Step 1: 写失败组件测试，覆盖读取、字段和提交**

更新 `SampleSettingModal.test.ts`：mock `fetchSampleSetting` 返回 `{ data: { sampleThresholdLevel: 1, sampleThresholdSales: 50000 } }`，并增加断言：

```ts
expect(wrapper.find('[data-testid="sample-setting-drawer"]').exists()).toBe(true)
expect(wrapper.text()).toContain('是否支持免费寄样')
expect(wrapper.text()).toContain('是否有寄样门槛')
expect(wrapper.text()).toContain('近30天橱窗销量')
expect(wrapper.text()).toContain('达人带货等级')
expect(wrapper.text()).toContain('LV1')
expect(wrapper.text()).toContain('快速申样不进行该标准的判断')
expect(wrapper.text()).not.toContain('样品盒数')
expect(wrapper.text()).not.toContain('每次寄样数量')
expect(fetchSampleSetting).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111')
```

保留点击确定后对 `updateSampleSetting` 的请求断言，并补充 GET 失败时出现“寄样设置读取失败”且仍使用行缓存的负例。

- [ ] **Step 2: 运行组件测试，确认它因仍是 `n-modal` 且未读取 GET 而失败**

Run: `npm --prefix frontend run test -- src/views/product/components/SampleSettingModal.test.ts --run`

Expected: FAIL，失败原因包含缺少 `sample-setting-drawer` 或 `fetchSampleSetting` 未调用。

- [ ] **Step 3: 实现侧边栏和交互状态**

在 `SampleSettingModal.vue`：

1. 将 `n-modal` 替换为 `n-drawer` / `n-drawer-content`，`placement="right"`，桌面宽度 548px，小屏使用 `calc(100vw - 24px)`。
2. 使用自定义 header 和 footer：标题左侧红色竖线，关闭按钮触发 `updateShow(false)`，footer 包含“取消”和“确定”。
3. 按截图顺序渲染两个 radio 行、四个标准行、两个不可提交的“请输入”视觉占位和三行说明。
4. 销量/销售额/粉丝数使用可清空的数字输入；达人等级使用 `TALENT_LEVEL_OPTIONS` 的 `n-select`。
5. 监听 `show` 与 `row`：先用列表行缓存初始化，再调用 `fetchSampleSetting` 覆盖为服务端事实；维护 `loading`、`submitting` 和读取错误提示。
6. 只调用 `toSampleSettingPayload(form)`，不得提交样品盒数/数量或无标签占位字段。
7. 使用现有 `notifyApiFailure`、`resolveProductRelationId` 和消息体系；不引入第三方 API 调用。
8. 用 scoped CSS 维护 14–18px 系统字号、抽屉内滚动、底部 footer 固定和小屏宽度。

- [ ] **Step 4: 运行组件测试确认通过**

Run: `npm --prefix frontend run test -- src/views/product/components/SampleSettingModal.test.ts --run`

Expected: PASS，读取、显示、失败降级和提交断言全部通过。

### Task 3: 后端规范化达人等级并移除误加字段写入

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/service/ProductSampleSettingService.java`
- Modify: `backend/src/main/java/com/colonel/saas/controller/ProductController.java`
- Test: `backend/src/test/java/com/colonel/saas/service/ProductSampleSettingServiceTest.java`
- Test: `backend/src/test/java/com/colonel/saas/controller/ProductControllerTest.java` only if the OpenAPI method signature or annotation assertion changes.

- [ ] **Step 1: 写失败后端测试，先锁定等级边界**

在 `ProductSampleSettingServiceTest` 增加：

```java
@Test
void update_shouldAcceptTalentLevelBetweenZeroAndSeven() {
    UUID relationId = UUID.randomUUID();
    ProductSnapshot snapshot = snapshot(relationId);
    ProductOperationState state = new ProductOperationState();
    state.setId(UUID.randomUUID());
    state.setActivityId(snapshot.getActivityId());
    state.setProductId(snapshot.getProductId());
    state.setVersion(1);
    state.setBizStatus("APPROVED");
    when(snapshotMapper.selectById(relationId)).thenReturn(snapshot);
    when(operationStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(state);
    when(operationStateMapper.updateById(state)).thenReturn(1);

    Map<String, Object> request = Map.of("supportFreeSample", true, "minTalentLevel", 7);
    assertThat(service.update(relationId, request, null, null))
            .containsEntry("minTalentLevel", 7L)
            .containsEntry("sampleThresholdLevel", 7L);
}

@Test
void update_shouldRejectTalentLevelAboveSeven() {
    UUID relationId = UUID.randomUUID();
    when(snapshotMapper.selectById(relationId)).thenReturn(snapshot(relationId));
    Map<String, Object> request = Map.of("supportFreeSample", true, "minTalentLevel", 8);
    assertThatThrownBy(() -> service.update(relationId, request, null, null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("达人带货等级");
}
```

同时把已有测试中的 `sampleBoxCount` / `sampleQuantity` 请求和断言移除，并增加断言保存结果不包含这两个字段。

- [ ] **Step 2: 运行目标 Maven 测试确认等级测试失败**

Run: `mvn -f backend/pom.xml -Dtest=ProductSampleSettingServiceTest test`

Expected: FAIL，`LV8` 当前不会被拒绝，或保存结果仍含样品盒数/数量。

- [ ] **Step 3: 实现最小后端修改**

1. 将 `applyOptionalNumber` 扩展为接受最大值参数；当值超过最大值时抛出带字段中文名的参数异常。
2. 对 `minTalentLevel` 及兼容别名 `sampleThresholdLevel` / `talentLevelRequirement` 使用最小值 0、最大值 7。
3. 从 `update` 主流程移除 `applySampleQuantity` 调用及其持久化写入，其他审核字段合并逻辑不变。
4. 更新 `ProductController` 的接口描述和示例，只展示截图已确认字段；路径和 operator context 不变。

- [ ] **Step 4: 运行目标 Maven 测试确认通过**

Run: `mvn -f backend/pom.xml -Dtest=ProductSampleSettingServiceTest,ProductControllerTest test`

Expected: PASS，等级边界、门槛关闭、审核字段保留、操作日志和 Controller 委托测试全部通过。

### Task 4: 更新 API 契约并执行静态回归

**Files:**
- Modify: `docs/05-API契约总表.md`
- Modify: `frontend/src/types/productManage.ts` only if TypeScript still exposes removed primary fields in the sample-setting form type.

- [ ] **Step 1: 更新契约文本**

把寄样设置条目补充为：GET 打开侧边栏读取服务端事实；PUT 只写免费寄样开关、门槛、四类达人标准；等级范围为 0–7；写入 `audit_payload`、保留既有审核字段、使用乐观锁和 `SAMPLE_SETTING` 日志；不改变审核/上架/展示状态。明确两个无标签视觉占位不是 API 字段。

- [ ] **Step 2: 检查契约和代码引用**

Run: `rg -n "sampleBoxCount|sampleQuantity|sample-setting|sampleThresholdLevel|minTalentLevel" frontend/src backend/src docs/05-API契约总表.md`

Expected: 主流程不再引用 `sampleBoxCount` / `sampleQuantity`；历史兼容代码若仍存在必须有明确注释和测试，不得由新组件发送。

### Task 5: 完成验证与证据

**Files:**
- Generate: `harness/reports/current/latest-product-sample-setting-drawer.md`
- Modify: `harness/rules/state/snapshots/DOMAIN_STATUS.md` only if the fixed entry requires a domain status update for this task.

- [ ] **Step 1: 运行前端目标测试和构建**

Run: `npm --prefix frontend run test -- src/views/product/sample-setting.test.ts src/views/product/components/SampleSettingModal.test.ts --run`

Expected: PASS。

Run: `npm --prefix frontend run build`

Expected: exit code 0。

- [ ] **Step 2: 运行后端目标测试和构建**

Run: `mvn -f backend/pom.xml -Dtest=ProductSampleSettingServiceTest,ProductControllerTest test`

Expected: PASS。

Run: `mvn -f backend/pom.xml -DskipTests package`

Expected: exit code 0。

- [ ] **Step 3: 使用仓库唯一入口执行 real-pre 本地验证**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -ReportKey product-sample-setting-drawer -OwnedFiles 'frontend/src/views/product/components/SampleSettingModal.vue;frontend/src/views/product/sample-setting.ts;frontend/src/views/product/components/SampleSettingModal.test.ts;frontend/src/views/product/sample-setting.test.ts;backend/src/main/java/com/colonel/saas/service/ProductSampleSettingService.java;backend/src/main/java/com/colonel/saas/controller/ProductController.java;backend/src/test/java/com/colonel/saas/service/ProductSampleSettingServiceTest.java;backend/src/test/java/com/colonel/saas/controller/ProductControllerTest.java;docs/05-API契约总表.md' -Message "feat: align product sample setting drawer with reference UI"
```

Expected: 记录 build、容器重启、health、API smoke 和业务验证的真实结果；未采集项必须写明原因，不得写成 PASS。

- [ ] **Step 4: 检查视觉与工作区证据**

使用本地页面验证商品管理页打开寄样设置后是右侧抽屉、字号正常、底部按钮可用、API 请求 relationId 正确；执行 `git diff --check`、Harness 限制检查并读取 evidence report。

- [ ] **Step 5: 只提交本任务文件并推送**

逐文件执行 `git add -- <path>`，确认 staged scope 只包含本计划文件；创建符合规范的提交并推送当前分支上游。保留已有 report-only dirty，不混入本任务提交。
