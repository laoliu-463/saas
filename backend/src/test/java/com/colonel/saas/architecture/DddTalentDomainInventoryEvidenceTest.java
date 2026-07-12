package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddTalentDomainInventoryEvidenceTest {

    @Test
    void talentDomainInventoryAndMainFlowsShouldStayIndexed() throws IOException {
        String talentController = readProjectFile("backend/src/main/java/com/colonel/saas/controller/TalentController.java");
        String profileController = readProjectFile("backend/src/main/java/com/colonel/saas/controller/TalentProfileController.java");
        String profileApplication = readProjectFile("backend/src/main/java/com/colonel/saas/domain/talent/application/TalentProfileApplicationService.java");
        String talentService = readProjectFile("backend/src/main/java/com/colonel/saas/service/TalentService.java");
        String talentQueryService = readProjectFile("backend/src/main/java/com/colonel/saas/service/TalentQueryService.java");
        String matrix = readProjectFile("docs/ddd-completion-evidence-matrix.md");
        String domainStatus = readProjectFile("harness/rules/state/snapshots/DOMAIN_STATUS.md");

        assertThat(talentController)
                .contains("@RequestMapping(\"/talents\")")
                .contains("TalentQueryApplicationService")
                .contains("TalentService")
                .contains("@GetMapping")
                .contains("@GetMapping(\"/{id}\")")
                .contains("@GetMapping(\"/status-transitions\")")
                .contains("@PostMapping(\"/batch-import\")")
                .contains("@GetMapping(\"/preset-tags\")")
                .contains("@PutMapping(\"/{id}/tags\")")
                .contains("@GetMapping(\"/{id}/shipping-address\")")
                .contains("@PutMapping(\"/{id}/shipping-address\")")
                .contains("@PostMapping(\"/{id}/claims\")");

        assertThat(profileController)
                .contains("@PostMapping(\"/resolve-profile\")")
                .contains("@PostMapping(\"/{id}/sync-profile\")")
                .contains("TalentProfileSyncService")
                .contains("talentQueryService.assertCanOperate");

        assertThat(profileApplication)
                .contains("TalentTagPolicy.normalize")
                .contains("businessRuleConfigService.getPresetTalentTags")
                .contains("TalentAddressPolicy.normalize")
                .contains("talentClaimMapper.findActiveByTalentAndUser")
                .contains("persistTalentClaim")
                .contains("TalentEnrichOrchestrator");

        assertThat(talentService)
                .contains("batchImport")
                .contains("claim(UUID talentId, UUID userId, UUID deptId)")
                .contains("release(UUID talentId, UUID userId, UUID deptId")
                .contains("updateTags")
                .contains("updateShippingAddress");

        assertThat(talentQueryService)
                .contains("detail(")
                .contains("page(")
                .contains("SampleDomainFacade")
                .contains("DataScopeResolver")
                .contains("CurrentUserPermissionChecker");

        assertThat(matrix)
                .contains("| T-1 |")
                .contains("| T-2 |")
                .contains("| T-3 |");
        assertThat(domainStatus)
                .contains("达人域逐卡 evidence index")
                .contains("T-1/T-2/T-3/T-4");
    }

    @Test
    void talentClaimAndProtectionRulesShouldStayAtPolicyAndApplicationBoundary() throws IOException {
        String claimApplication = readProjectFile("backend/src/main/java/com/colonel/saas/domain/talent/application/TalentClaimApplicationService.java");
        String claimPolicy = readProjectFile("backend/src/main/java/com/colonel/saas/domain/talent/policy/TalentClaimPolicy.java");
        String claimApplicationTest = readProjectFile("backend/src/test/java/com/colonel/saas/domain/talent/application/TalentClaimApplicationServiceTest.java");
        String claimPolicyTest = readProjectFile("backend/src/test/java/com/colonel/saas/domain/talent/policy/TalentClaimPolicyTest.java");
        String configBoundaryTest = readProjectFile("backend/src/test/java/com/colonel/saas/architecture/DddConfig002SampleTalentConfigTest.java");
        String orderBoundaryTest = readProjectFile("backend/src/test/java/com/colonel/saas/architecture/DddOrderSyncedTalentClaimBoundaryTest.java");

        assertThat(claimApplication)
                .contains("TalentClaimPolicy.requireClaimUser")
                .contains("TalentClaimPolicy.assertNotDuplicateActiveClaim")
                .contains("TalentClaimPolicy.protectedUntil")
                .contains("TalentClaimPolicy.selectReleaseTarget")
                .contains("configDomainFacade.getTalentClaimProtectDays")
                .contains("orderReadFacade.findOrdersCreatedSince")
                .contains("extendActiveClaimProtectionByTalentUid");

        assertThat(claimPolicy)
                .contains("STATUS_ACTIVE")
                .contains("STATUS_EXPIRED")
                .contains("STATUS_RELEASED")
                .contains("protectedUntil")
                .contains("selectReleaseTarget")
                .contains("仅认领人或管理员可以释放达人");

        assertThat(claimApplicationTest)
                .contains("extendActiveClaimProtectionByTalentUid_shouldOnlyExtendOlderActiveClaims")
                .contains("extendActiveClaimProtectionByTalentUid_shouldSkipBlankInput");
        assertThat(claimPolicyTest)
                .contains("requireClaimUser_shouldRejectNull")
                .contains("assertNotDuplicateActiveClaim_shouldRejectExisting")
                .contains("protectedUntil_shouldAddDays")
                .contains("selectReleaseTarget_shouldPreferCurrentUser");
        assertThat(configBoundaryTest)
                .contains("getTalentClaimProtectDays")
                .contains("protectionDays");
        assertThat(orderBoundaryTest)
                .contains("TalentClaimApplicationService")
                .contains("extendActiveClaimProtectionByTalentUid");
    }

    @Test
    void talentAddressAndSampleConsumptionShouldStayConnected() throws IOException {
        String talentController = readProjectFile("backend/src/main/java/com/colonel/saas/controller/TalentController.java");
        String profileApplication = readProjectFile("backend/src/main/java/com/colonel/saas/domain/talent/application/TalentProfileApplicationService.java");
        String talentFacade = readProjectFile("backend/src/main/java/com/colonel/saas/domain/talent/facade/LegacyTalentDomainFacade.java");
        String productQuickSampleService = readProjectFile("backend/src/main/java/com/colonel/saas/service/ProductQuickSampleService.java");
        String quickSampleCommand = readProjectFile("backend/src/main/java/com/colonel/saas/domain/product/port/QuickSampleApplyCommand.java");
        String sampleApplicationPort = readProjectFile("backend/src/main/java/com/colonel/saas/domain/sample/application/SampleApplicationPortImpl.java");
        String quickSampleTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/QuickSampleApplyTest.java");

        assertThat(talentController)
                .contains("@GetMapping(\"/{id}/shipping-address\")")
                .contains("@PutMapping(\"/{id}/shipping-address\")")
                .contains("供寄样域 get_talent_address 调用")
                .contains("new ShippingAddressRequest")
                .contains("talentService.getShippingAddress")
                .contains("talentService.updateShippingAddress");

        assertThat(profileApplication)
                .contains("getShippingAddress(UUID id, UUID userId)")
                .contains("talentClaimMapper.findActiveByTalentAndUser")
                .contains("claim.getRecipientName()")
                .contains("claim.getRecipientPhone()")
                .contains("claim.getRecipientAddress()")
                .contains("地址仅存于 claim 层")
                .contains("claim.setRecipientName")
                .contains("claim.setRecipientPhone")
                .contains("claim.setRecipientAddress")
                .contains("persistTalentClaim(claim)");

        assertThat(talentFacade)
                .contains("writeBackClaimAddress")
                .contains("talentClaimMapper.findActiveByTalentAndUser")
                .contains("claim.setRecipientName")
                .contains("claim.setRecipientPhone")
                .contains("claim.setRecipientAddress")
                .contains("talentClaimMapper.updateById(claim)");

        assertThat(productQuickSampleService)
                .contains("new QuickSampleApplyCommand")
                .contains("request.getRecipientName()")
                .contains("request.getRecipientPhone()")
                .contains("request.getRecipientAddress()")
                .contains("productSampleApplicationPort.applyQuickSample(command)");

        assertThat(quickSampleCommand)
                .contains("String receiverName")
                .contains("String receiverPhone")
                .contains("String receiverAddress");

        assertThat(sampleApplicationPort)
                .contains("cmd.receiverName()")
                .contains("cmd.receiverPhone()")
                .contains("cmd.receiverAddress()")
                .contains("sample.setRecipientName")
                .contains("sample.setRecipientPhone")
                .contains("sample.setRecipientAddress")
                .contains("writeBackClaimAddress(cmd.channelUserId(), talent.getId(), sample)")
                .contains("talentDomainFacade.writeBackClaimAddress");

        assertThat(quickSampleTest)
                .contains("applyQuickSample_singleTalentSuccess")
                .contains("cmd.receiverAddress()")
                .contains("verify(productSampleApplicationPort).applyQuickSample(captor.capture())");
    }

    @Test
    void talentTagAndFollowAuditEvidenceShouldStayConnected() throws IOException {
        String talentController = readProjectFile("backend/src/main/java/com/colonel/saas/controller/TalentController.java");
        String profileApplication = readProjectFile("backend/src/main/java/com/colonel/saas/domain/talent/application/TalentProfileApplicationService.java");
        String talentEntity = readProjectFile("backend/src/main/java/com/colonel/saas/entity/Talent.java");
        String followRecord = readProjectFile("backend/src/main/java/com/colonel/saas/entity/TalentFollowRecord.java");
        String followService = readProjectFile("backend/src/main/java/com/colonel/saas/service/TalentFollowService.java");
        String profileApplicationTest = readProjectFile("backend/src/test/java/com/colonel/saas/domain/talent/application/TalentProfileApplicationServiceTest.java");
        String tagPolicyTest = readProjectFile("backend/src/test/java/com/colonel/saas/domain/talent/policy/TalentTagPolicyTest.java");
        String followServiceTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/TalentFollowServiceTest.java");

        assertThat(talentController)
                .contains("@GetMapping(\"/preset-tags\")")
                .contains("@PutMapping(\"/{id}/tags\")")
                .contains("talentService.listPresetTags")
                .contains("talentService.updateTags(id, tags, userId)");

        assertThat(profileApplication)
                .contains("TalentTagPolicy.normalize")
                .contains("businessRuleConfigService.getPresetTalentTags")
                .contains("talent.setTags(normalized)")
                .contains("talent.setTagUpdatedBy(operatorId)")
                .contains("talentMapper.updateById(talent)");

        assertThat(talentEntity)
                .contains("@TableField(\"tag_updated_by\")")
                .contains("private UUID tagUpdatedBy");

        assertThat(followRecord)
                .contains("@TableName(\"talent_follow_record\")")
                .contains("@TableField(\"talent_id\")")
                .contains("@TableField(\"follow_status\")")
                .contains("@TableField(\"next_follow_time\")")
                .contains("@TableField(\"operator_id\")")
                .contains("@TableField(\"operator_name\")");

        assertThat(followService)
                .contains("TalentFollowRecord record = new TalentFollowRecord()")
                .contains("record.setTalentId(talentId)")
                .contains("record.setFollowStatus(followStatus)")
                .contains("record.setContent(content)")
                .contains("record.setNextFollowTime(nextFollowTime)")
                .contains("record.setOperatorId(operatorId)")
                .contains("record.setOperatorName(operatorName)")
                .contains("talentFollowRecordMapper.insert(record)")
                .contains("orderByDesc(TalentFollowRecord::getCreateTime)");

        assertThat(profileApplicationTest)
                .contains("updateTagsNormalizesAndPersistsTags")
                .contains("assertThat(captor.getValue().getTags()).containsExactly")
                .contains("assertThat(captor.getValue().getTagUpdatedBy()).isEqualTo(operatorId)");
        assertThat(tagPolicyTest)
                .contains("TalentTagPolicy.normalize");
        assertThat(followServiceTest)
                .contains("createRecordShouldPopulateAndPersistFollowRecord")
                .contains("assertThat(result.getOperatorId()).isEqualTo(operatorId)")
                .contains("listByProductShouldReturnMapperResults");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(repoRoot().resolve(relativePath)).replace("\r\n", "\n");
    }

    private static Path repoRoot() {
        Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (userDir.getFileName() != null && "backend".equals(userDir.getFileName().toString())) {
            return userDir.getParent();
        }
        return userDir;
    }
}
