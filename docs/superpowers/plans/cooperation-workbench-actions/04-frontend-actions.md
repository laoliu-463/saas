# 分册 4：前端操作栏与风险提醒

## Task 7：类型、API 与纯交互模型

**Files:**

- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/api/sample.ts`, `frontend/src/api/sample.test.ts`
- Create: `frontend/src/api/talentComplaint.ts`
- Create: `frontend/src/views/sample/cooperation-actions.ts`, `cooperation-actions.test.ts`

- [ ] 写失败测试，固定顺序：`APPROVE,REJECT,EDIT,PROGRESS,COPY_LINK,COPY_ORDER,COMPLAIN,NOTE`。
- [ ] 断言缺少后端能力键时按钮仍显示但禁用，原因“服务端未返回该操作能力”。
- [ ] 运行 RED：

```powershell
npm --prefix frontend run test -- src/views/sample/cooperation-actions.test.ts src/api/sample.test.ts
```

- [ ] `SampleItem` 增加 `version/activityId/productSpecification/actionAvailability/complaintRisk`。
- [ ] `sample.ts` 增加编辑、推广复制、订单复制、备注、投诉 multipart 方法。
- [ ] `talentComplaint.ts` 增加风险、提醒、已读、详情和附件 Blob 方法；组件不直接拼 URL。
- [ ] 运行 GREEN并提交：`feat(frontend): add cooperation action contracts`。

## Task 8：竖排操作栏和弹窗

**Files:**

- Create: `frontend/src/views/sample/components/CooperationActionColumn.vue`
- Create: `frontend/src/views/sample/components/SampleEditModal.vue`
- Create: `frontend/src/views/sample/components/TalentComplaintModal.vue`
- Create: `frontend/src/views/sample/components/PrivateNoteModal.vue`
- Create: `frontend/src/views/sample/components/ManualCopyModal.vue`
- Create: `frontend/src/views/sample/components/CooperationActionColumn.test.ts`
- Create: `frontend/src/views/sample/components/SampleEditModal.test.ts`
- Create: `frontend/src/views/sample/components/TalentComplaintModal.test.ts`
- Create: `frontend/src/views/sample/components/PrivateNoteModal.test.ts`
- Modify: `frontend/src/views/sample/CooperationWorkbench.vue`

- [ ] 写失败测试：八个按钮固定竖排、禁用 tooltip、通过二次确认、拒绝原因必填、编辑只读字段与真实地址、投诉 0/200 和 9 张限制、备注私有提示、剪贴板失败手动复制。
- [ ] 运行 RED：

```powershell
npm --prefix frontend run test -- src/views/sample/components/CooperationActionColumn.test.ts src/views/sample/components/SampleEditModal.test.ts src/views/sample/components/TalentComplaintModal.test.ts src/views/sample/components/PrivateNoteModal.test.ts
```

- [ ] 操作容器 CSS 固定为：

```css
.cooperation-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}
```

- [ ] 操作列加宽；不可用按钮始终渲染，外层 `n-tooltip` 显示 `disabledReason`。
- [ ] `CooperationWorkbench.vue` 只持有当前行和弹窗状态，操作细节下沉组件。
- [ ] 通过调用 `actionSample(id,{action:'APPROVED'})`；拒绝调用 `{action:'REJECTED',reason}`；查看进度继续打开 `SampleDetail`。
- [ ] 保存编辑提交服务端版本；409/冲突提示“数据已更新，请刷新后重试”。
- [ ] 两种复制都调用 `tryCopyText`；失败时将完整文本放进 `ManualCopyModal`，不得丢失。
- [ ] 投诉关联商品只读，上传 `accept="image/jpeg,image/png,image/webp"`；前端预检后端仍复检。
- [ ] 备注每次打开只请求当前用户内容，关闭时清空本地状态，标题和按钮只写“备注”。
- [ ] 运行 GREEN并提交：`feat(frontend): add vertical cooperation actions`。

## Task 9：风险标签和专项提醒

**Files:**

- Create: `frontend/src/components/talent/TalentComplaintRiskTag.vue`
- Create: `frontend/src/components/talent/TalentComplaintReminderPopover.vue`
- Create: `frontend/src/components/talent/TalentComplaintReminderPopover.test.ts`
- Modify: `frontend/src/views/sample/CooperationWorkbench.vue`
- Modify: `frontend/src/views/talent/index.vue`, `frontend/src/views/talent/index.test.ts`
- Modify: `frontend/src/views/talent/components/TalentDetailModal.vue`, `TalentDetailModal.security.test.ts`

- [ ] 写失败测试：普通用户只看到“投诉记录 N 条”；提醒接收人看到未读 badge、列表和已读动作；已读后本地计数减一并重新请求。
- [ ] 运行 RED：

```powershell
npm --prefix frontend run test -- src/components/talent/TalentComplaintReminderPopover.test.ts src/views/talent/index.test.ts src/views/talent/components/TalentDetailModal.security.test.ts
```

- [ ] 合作单和达人列表分页后收集最多 100 个 `talentId`，一次批量查询并合并风险，禁止逐行 N+1。
- [ ] 普通用户只渲染风险摘要；领导从提醒入口点击后才加载投诉详情和附件。
- [ ] 合作单页、达人页 `PageHeader.actions` 复用提醒组件；未读 0 不显示红点，非接收人列表为空。
- [ ] 运行 GREEN并提交：`feat(frontend): show talent complaint risk reminders`。

## 固定展示检查

- [ ] 每行文案依次为：通过、拒绝、修改订单、查看进度、复制链接、复制订单、投诉达人、备注。
- [ ] 待交作业、已完成、已关闭时“修改订单”存在但禁用。
- [ ] 管理员人工操作可用，但没有手工“完成”或“关闭”入口。
- [ ] 备注不显示“我的备注”；投诉不自动切换合作单状态。
