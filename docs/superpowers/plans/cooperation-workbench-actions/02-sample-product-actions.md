# 分册 2：合作单编辑与复制

## Task 3：操作能力、编辑事务和私有备注

**Files:**

- Modify: `backend/src/main/java/com/colonel/saas/entity/SampleRequest.java`
- Modify: `backend/src/main/java/com/colonel/saas/vo/sample/SampleVO.java`
- Create: `backend/src/main/java/com/colonel/saas/vo/sample/SampleActionAvailabilityVO.java`, `SampleEditContextVO.java`, `SampleCopyTextVO.java`, `SamplePrivateNoteVO.java`
- Create: `backend/src/main/java/com/colonel/saas/dto/sample/SampleCooperationUpdateRequest.java`, `SamplePrivateNoteRequest.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleCooperationActionPolicy.java`, `SampleRemarkPolicy.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/sample/application/SampleCooperationApplicationService.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/sample/application/SampleApplicationService.java`, `SampleApplicationPortImpl.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java`
- Modify: `backend/src/main/java/com/colonel/saas/controller/SampleController.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/sample/policy/SampleCooperationActionPolicyTest.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/sample/application/SampleCooperationApplicationServiceTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java`

- [ ] 写操作矩阵失败测试：八键固定；通过/拒绝仅待审核且管理员/招商专员；编辑仅申请人/管理员且状态为待审核、待发货、快递中、已拒绝；系统专属状态不增加人工动作。
- [ ] 写编辑与备注失败测试：地址更新使用 `sample.user_id`；版本冲突；禁止状态；地址失败导致事务回滚；管理员读不到他人备注；日志不含备注正文。
- [ ] 运行 RED：

```powershell
mvn -f backend/pom.xml -Dtest=SampleCooperationActionPolicyTest,SampleCooperationApplicationServiceTest,SampleControllerTest test
```

- [ ] `SampleRequest` 映射已有 `activity_id/activity_product_id`；新建普通/快速寄样时保存活动 ID。
- [ ] `SampleVO` 增加 `version/activityId/productSpecification/actionAvailability/complaintRisk`；历史活动 ID 从商品快照读取，不写回。
- [ ] `SampleRemarkPolicy` 优先 `extra_data.applyReason`；历史快速寄样剥离 `规格: {specification}；` 前缀；保存时同步写 `remark` 和 `extra_data.applyReason`。
- [ ] `GET /samples/{id}/edit-context` 先复用 `SampleQueryApplicationService.getSampleById` 做数据范围校验，再按 `sample.user_id` 查真实认领地址；不存在返回 `addressAvailable=false`，不回退其他认领人地址。
- [ ] `PUT /samples/{id}/cooperation-details` 请求严格为：

```java
public record SampleCooperationUpdateRequest(
        @NotNull Integer version,
        @Size(max = 200) String remark,
        @Size(max = 100) String recipientName,
        @Size(max = 32) String recipientPhone,
        @Size(max = 512) String recipientAddress) {}
```

- [ ] 同一 `@Transactional(rollbackFor=Exception.class)` 内先带版本更新样本并 `requireUpdated`，再调用达人门面更新申请人的认领地址。
- [ ] 私有备注按样本+当前用户查询。空内容软删除当前用户行；非空 trim 后 upsert，最多 200 字。没有管理员跨用户入口。
- [ ] 领域 `SampleApplicationService` 在列表/详情返回前注入动作能力；保留旧测试构造器，Spring 使用带 policy 的构造器。
- [ ] 运行 GREEN：同一 Maven 命令 PASS。
- [ ] 精确提交上述文件，提交信息：`feat(sample): add cooperation edit and private notes`。

## Task 4：商品域精确推广复制

**Files:**

- Create: `backend/src/main/java/com/colonel/saas/domain/product/facade/ProductPromotionFacade.java`, `LegacyProductPromotionFacade.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/product/facade/dto/ProductPromotionCopyDTO.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/product/application/CopyPromotionApplicationService.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/product/policy/CopyTextPolicy.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/sample/application/SampleCooperationApplicationService.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/product/policy/CopyTextPolicyDouyinShareTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/domain/product/application/CopyPromotionApplicationServiceTest.java`, `backend/src/test/java/com/colonel/saas/domain/sample/application/SampleCooperationApplicationServiceTest.java`

- [ ] 写失败测试，断言精确文本且不包含 `【库存】`：

```text
【抖音】鲜飘飘生椰牛乳椰子牛奶夏季夏天必备家庭装饮品饮料1L*2
【店铺名称】鲜飘飘饮料
【售价】29.9元
【佣金率】15%
【投放期佣金】5%
【奖励说明】出视频就投，控roi1.2 混剪不一定能投，要看投手评估审核
【开始时间】2026-07-16 00:00:00
【结束时间】2027-07-31 23:59:59
【推广链接】
https://haohuo.jinritemai.com/ecommerce/trade/detail/index.html?id=3820194249627009436
```

- [ ] 运行 RED：

```powershell
mvn -f backend/pom.xml -Dtest=CopyTextPolicyDouyinShareTest,CopyPromotionApplicationServiceTest,SampleCooperationApplicationServiceTest test
```

- [ ] 新增 `CopyTextPolicy.renderDouyinShare`，保留现有 `render` 行为；投放期佣金按 `activityAdCosRatio` 既有基点口径格式化；奖励和时间从快照/审核载荷读取。
- [ ] `ProductPromotionFacade.copyForSample(activityId,productId,userId,deptId,talentId,idempotencyKey)` 委托商品应用服务；寄样域不注入商品 Mapper。
- [ ] 接入 `POST /samples/{id}/promotion-copy`；返回 `text/promotionLinkGenerated/promotionLink/fallbackReason`。转链关闭或失败时最后一行 `未生成`。
- [ ] 运行 GREEN并提交：`feat(product): add sample promotion copy format`。

## Task 5：复制订单文本

**Files:**

- Create: `backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleOrderCopyPolicy.java`
- Modify: `backend/src/main/java/com/colonel/saas/domain/sample/application/SampleCooperationApplicationService.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/sample/policy/SampleOrderCopyPolicyTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/domain/sample/application/SampleCooperationApplicationServiceTest.java`

- [ ] 写失败测试：逐行匹配用户格式；`68000 -> 6.8W`；规格缺失 `---`；橱窗销量缺失 `---`，不能读金额字段。
- [ ] 运行 RED：

```powershell
mvn -f backend/pom.xml -Dtest=SampleOrderCopyPolicyTest,SampleCooperationApplicationServiceTest test
```

- [ ] `SampleOrderCopyPolicy.render` 只接收值对象；`GET /samples/{id}/order-copy` 校验可见性后由商品、达人门面和样本快照组装事实。
- [ ] 输出字段固定：商品名称、商品ID、店铺、申请数量、商品规格、申样备注、达人昵称、抖音号、粉丝数、近30天橱窗销量、收货人、收货电话、收货地址。
- [ ] 运行 GREEN并提交：`feat(sample): add cooperation order copy`。
