# 分册 1：后端数据与边界

## Task 1：增量数据结构与映射

**Files:**

- Create: `backend/src/main/resources/db/migrate/V20260716_001__cooperation_workbench_actions.sql`
- Modify: `backend/src/main/resources/db/init-db.sql`
- Modify: `backend/src/main/resources/db/migrate-all.sql`
- Create: `backend/src/main/java/com/colonel/saas/entity/SamplePrivateNote.java`
- Create: `backend/src/main/java/com/colonel/saas/entity/TalentComplaint.java`
- Create: `backend/src/main/java/com/colonel/saas/entity/TalentComplaintAttachment.java`
- Create: `backend/src/main/java/com/colonel/saas/entity/TalentComplaintReminder.java`
- Create: `backend/src/main/java/com/colonel/saas/mapper/SamplePrivateNoteMapper.java`
- Create: `backend/src/main/java/com/colonel/saas/mapper/TalentComplaintMapper.java`
- Create: `backend/src/main/java/com/colonel/saas/mapper/TalentComplaintAttachmentMapper.java`
- Create: `backend/src/main/java/com/colonel/saas/mapper/TalentComplaintReminderMapper.java`
- Create: `backend/src/test/java/com/colonel/saas/config/CooperationWorkbenchActionsSchemaContractTest.java`

- [ ] 写失败测试：读取三份 SQL，断言四张表、外键和唯一索引同时存在。
- [ ] 运行 RED：

```powershell
mvn -f backend/pom.xml -Dtest=CooperationWorkbenchActionsSchemaContractTest test
```

Expected: FAIL，迁移文件或表定义不存在。

- [ ] 实现 `sample_private_note`：UUID 主键、`sample_request_id/user_id/content VARCHAR(200)/version`、软删除与审计列；建立有效行唯一索引 `uk_sample_private_note_owner(sample_request_id,user_id) WHERE deleted=0`。
- [ ] 实现 `talent_complaint`：保存 `sample_request_id/talent_id/product_id/reporter_user_id/reason_code/content/status/version`，状态默认 `SUBMITTED`。
- [ ] 实现 `talent_complaint_attachment`：保存 `complaint_id/storage_key/original_name/content_type/file_size/sha256`，`storage_key` 唯一。
- [ ] 实现 `talent_complaint_reminder`：保存 `complaint_id/recipient_user_id/read_at/version`，有效行按投诉+接收人唯一。
- [ ] 四个实体与表一致；备注、投诉、提醒继承 `VersionedEntity`，附件继承 `BaseEntity`。
- [ ] Mapper 只提供本域查询：合作单+用户备注、达人批量风险、接收人提醒、投诉附件。
- [ ] 将同一幂等 DDL 合并进 `init-db.sql` 和 `migrate-all.sql`。
- [ ] 运行 GREEN：同一 Maven 命令 PASS。
- [ ] 精确提交：

```powershell
git add backend/src/main/resources/db/migrate/V20260716_001__cooperation_workbench_actions.sql backend/src/main/resources/db/init-db.sql backend/src/main/resources/db/migrate-all.sql backend/src/main/java/com/colonel/saas/entity/SamplePrivateNote.java backend/src/main/java/com/colonel/saas/entity/TalentComplaint.java backend/src/main/java/com/colonel/saas/entity/TalentComplaintAttachment.java backend/src/main/java/com/colonel/saas/entity/TalentComplaintReminder.java backend/src/main/java/com/colonel/saas/mapper/SamplePrivateNoteMapper.java backend/src/main/java/com/colonel/saas/mapper/TalentComplaintMapper.java backend/src/main/java/com/colonel/saas/mapper/TalentComplaintAttachmentMapper.java backend/src/main/java/com/colonel/saas/mapper/TalentComplaintReminderMapper.java backend/src/test/java/com/colonel/saas/config/CooperationWorkbenchActionsSchemaContractTest.java
git commit -m "feat(sample): add cooperation action persistence"
```

## Task 2：用户接收人端口与达人门面

**Files:**

- Create: `backend/src/main/java/com/colonel/saas/domain/user/port/UserRoleRecipientLookup.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserRoleRecipientLookupAdapter.java`
- Modify: `backend/src/main/java/com/colonel/saas/mapper/SysUserMapper.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/user/facade/UserDomainFacade.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/user/facade/LegacyUserDomainFacade.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/talent/facade/dto/TalentClaimAddressDTO.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/talent/facade/dto/TalentComplaintRiskDTO.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/talent/facade/dto/TalentReadDTO.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/talent/facade/TalentDomainFacade.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/talent/facade/LegacyTalentDomainFacade.java`
- Modify: `backend/src/test/java/com/colonel/saas/domain/talent/facade/LegacyTalentDomainFacadeTest.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/user/infrastructure/SysUserRoleRecipientLookupAdapterTest.java`

- [ ] 写失败测试：仅返回未删除、启用且具有 `ADMIN/BIZ_LEADER/CHANNEL_LEADER` 的用户；地址读取使用合作单申请人；认领更新数为 0 抛冲突。
- [ ] 运行 RED：

```powershell
mvn -f backend/pom.xml -Dtest=SysUserRoleRecipientLookupAdapterTest,LegacyTalentDomainFacadeTest test
```

- [ ] `SysUserMapper.findActiveIdsByRoleCodes` 使用 `sys_user -> sys_user_role -> sys_role` 联表和 `DISTINCT`。
- [ ] `UserDomainFacade` 仅暴露 `listActiveUserIdsByRoleCodes(Collection<String>)`，投诉域不直接注入用户 Mapper。
- [ ] `TalentDomainFacade` 增加：

```java
TalentClaimAddressDTO findActiveClaimAddress(UUID talentId, UUID ownerUserId);
void updateActiveClaimAddress(UUID talentId, UUID ownerUserId,
        String recipientName, String recipientPhone, String recipientAddress);
Map<UUID, TalentComplaintRiskDTO> loadComplaintRisks(Collection<UUID> talentIds);
```

- [ ] 地址更新使用 `OptimisticLockSupport.requireUpdated`；管理员也传 `sample.user_id`，不创建管理员自己的认领地址。
- [ ] `TalentReadDTO` 增加 `windowSales30d`，只解析 `Talent.rawPayload` 的 `windowSales30d/window_sales_30d/showcaseSales30d` 数字字段；缺失为 `null`。
- [ ] 运行 GREEN：同一 Maven 命令 PASS。
- [ ] 精确提交：

```powershell
git add backend/src/main/java/com/colonel/saas/domain/user/port/UserRoleRecipientLookup.java backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserRoleRecipientLookupAdapter.java backend/src/main/java/com/colonel/saas/mapper/SysUserMapper.java backend/src/main/java/com/colonel/saas/domain/user/facade/UserDomainFacade.java backend/src/main/java/com/colonel/saas/domain/user/facade/LegacyUserDomainFacade.java backend/src/main/java/com/colonel/saas/domain/talent/facade/dto/TalentClaimAddressDTO.java backend/src/main/java/com/colonel/saas/domain/talent/facade/dto/TalentComplaintRiskDTO.java backend/src/main/java/com/colonel/saas/domain/talent/facade/dto/TalentReadDTO.java backend/src/main/java/com/colonel/saas/domain/talent/facade/TalentDomainFacade.java backend/src/main/java/com/colonel/saas/domain/talent/facade/LegacyTalentDomainFacade.java backend/src/test/java/com/colonel/saas/domain/talent/facade/LegacyTalentDomainFacadeTest.java backend/src/test/java/com/colonel/saas/domain/user/infrastructure/SysUserRoleRecipientLookupAdapterTest.java
git commit -m "feat(talent): expose complaint recipients and claim address"
```
