# 分册 3：投诉、附件与提醒

## Task 6：达人投诉闭环

**Files:**

- Create: `backend/src/main/java/com/colonel/saas/domain/talent/policy/TalentComplaintPolicy.java`, `ComplaintImagePolicy.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/talent/application/TalentComplaintApplicationService.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/talent/facade/TalentComplaintFacade.java`, `LegacyTalentComplaintFacade.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/talent/infrastructure/ComplaintAttachmentStorage.java`
- Create: `backend/src/main/java/com/colonel/saas/dto/talent/TalentComplaintCreateRequest.java`, `TalentComplaintRiskRequest.java`
- Create: `backend/src/main/java/com/colonel/saas/vo/talent/TalentComplaintVO.java`, `TalentComplaintReminderVO.java`
- Create: `backend/src/main/java/com/colonel/saas/controller/TalentComplaintController.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/sample/application/SampleCooperationApplicationService.java`
- Modify: `backend/src/main/java/com/colonel/saas/controller/SampleController.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `docker-compose.yml`, `docker-compose.test.yml`, `docker-compose.real-pre.yml`
- Create: `backend/src/test/java/com/colonel/saas/domain/talent/policy/ComplaintImagePolicyTest.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/talent/application/TalentComplaintApplicationServiceTest.java`
- Create: `backend/src/test/java/com/colonel/saas/controller/TalentComplaintControllerTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/domain/sample/application/SampleCooperationApplicationServiceTest.java`

### TDD 规则

- [ ] 写原因测试：只接受 `REPEATED_NO_FULFILLMENT/LOW_PRICE_RESALE/OTHER`；`OTHER` 内容必填；内容最多 200 字。
- [ ] 写附件测试：最多 9 张，单张最大 10 MB；JPG 起始 `FF D8 FF`；PNG 起始 `89 50 4E 47 0D 0A 1A 0A`；WEBP 偏移 0 为 `RIFF` 且偏移 8 为 `WEBP`；扩展名、MIME 和魔数一致。
- [ ] 写权限测试：所有可见当前合作单的用户可提交；普通用户只读风险摘要；管理员、招商组长、渠道组长可读详情与附件。
- [ ] 写提醒测试：创建时固化三类角色的启用用户并去重；未读查询和已读更新都按当前接收人过滤。
- [ ] 写回滚测试：数据库事务回滚时删除本次已落盘文件；投诉不调用寄样状态更新。
- [ ] 运行 RED：

```powershell
mvn -f backend/pom.xml -Dtest=ComplaintImagePolicyTest,TalentComplaintApplicationServiceTest,TalentComplaintControllerTest,SampleCooperationApplicationServiceTest test
```

Expected: FAIL，新服务和受控存储不存在。

### 存储与事务实现

- [ ] 在 `application.yml` 增加：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 95MB
talent:
  complaint:
    storage-root: ${TALENT_COMPLAINT_STORAGE_ROOT:/var/lib/colonel-saas/complaints}
```

- [ ] Docker 后端挂载命名卷 `/var/lib/colonel-saas/complaints`；real-pre 卷名 `complaint_evidence_real_pre`，容器重启后数据保留。
- [ ] 数据库只保存随机相对 `storage_key`、原文件名、MIME、大小和 SHA-256；不保存客户端 URL、本机绝对路径或用户输入路径。
- [ ] 下载时只接收投诉/附件 UUID，解析路径后断言 `resolvedPath.startsWith(storageRoot)`，目录不注册静态资源。
- [ ] 创建流程：样本可见性 -> 原因与全部文件验证 -> 插入投诉 -> 随机名存储并插元数据 -> 查询接收人并插提醒 -> 写不含投诉正文的操作日志。
- [ ] 注册 `TransactionSynchronization`，事务回滚删除本次文件；正常提交不删除。

### API 实现

- [ ] `POST /samples/{id}/complaints` 接受 `reason/content/files[]`，达人和商品从当前合作单解析，前端不能覆盖关联对象。
- [ ] `POST /talent-complaints/risks` 最多 100 个达人 ID，仅返回 `complaintCount/lastComplaintAt`。
- [ ] `GET /talent-complaints/reminders/unread-count` 和列表只返回当前 `recipient_user_id`。
- [ ] `PUT /talent-complaints/reminders/{id}/read` 的更新条件同时包含提醒 ID、当前用户 ID、`read_at IS NULL`。
- [ ] `GET /talent-complaints/{id}` 与附件下载调用同一个领导角色策略。
- [ ] 运行 GREEN：同一 Maven 命令 PASS。
- [ ] 精确提交上述文件，提交信息：`feat(talent): add complaints and scoped reminders`。

### 失败分支验收

- 文件数量 10：参数错误，不产生投诉行和文件。
- 单文件 10 MB 加 1 字节：参数错误。
- `.jpg` 内放 PNG：参数错误。
- 普通用户请求投诉详情/附件：Forbidden。
- 非接收人标记提醒已读：Not Found 或 Forbidden，不修改数据。
- 附件写入失败：事务回滚，无投诉、附件和提醒半成品。
