package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddTalentExceptionBranchEvidenceTest {

    @Test
    void talentValidationAndAccessExceptionBranchesShouldStayConnected() throws IOException {
        String talentService = readProjectFile("backend/src/main/java/com/colonel/saas/service/TalentService.java");
        String talentQueryService = readProjectFile("backend/src/main/java/com/colonel/saas/service/TalentQueryService.java");
        String talentInputParser = readProjectFile("backend/src/main/java/com/colonel/saas/service/talent/TalentInputParser.java");

        String talentServiceTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/TalentServiceTest.java");
        String talentQueryServiceTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/TalentQueryServiceTest.java");
        String talentInputParserTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/talent/TalentInputParserTest.java");

        assertThat(talentService)
                .contains("throw BusinessException.notFound(\"达人不存在\")")
                .contains("BusinessException.param(\"标签必须从预设库选择: \" + normalized)")
                .contains("throw BusinessException.notFound(\"目标负责人不存在\")")
                .contains("throw new ForbiddenException(\"无权操作该达人\")")
                .contains("OptimisticLockSupport.requireUpdated");

        assertThat(talentQueryService)
                .contains("gender 筛选当前不支持")
                .contains("throw new ForbiddenException(\"无权查看该达人详情\")")
                .contains("catch (Exception ex)")
                .contains("return 0L")
                .contains("return null");

        assertThat(talentInputParser)
                .contains("BusinessException.param(\"达人抖音号或链接不能为空\")");

        assertThat(talentServiceTest)
                .contains("updateTags_shouldRejectTagsOutsidePresetLibrary")
                .contains("getById_shouldThrowWhenTalentIsSoftDeleted")
                .contains("create_shouldRejectMissingIdentityAndDuplicateUid")
                .contains("claim_shouldRejectMissingUserLockConflictAndSelfDuplicate")
                .contains("release_shouldRejectMissingUserNoActiveClaimAndAllowAdminRelease")
                .contains("release_shouldRejectDeptLeaderReleasingOtherUsersClaim")
                .contains("overrideTalentAssignment_shouldThrowWhenNewUserIdNull")
                .contains("overrideTalentAssignment_shouldThrowWhenUserNotFound");

        assertThat(talentQueryServiceTest)
                .contains("page_shouldRejectUnsupportedGenderFilter")
                .contains("detail_shouldRejectPersonalScopeWhenViewerHasNoActiveClaim")
                .contains("page_shouldNotFailWhenCategoryFilterAndTalentHasNoCategoryFields")
                .contains("page_shouldNotFailWhenRegionFilterAndTalentHasNoLocation");

        assertThat(talentInputParserTest)
                .contains("parseShouldRejectBlankInput")
                .contains("BusinessException.class")
                .contains("不能为空");
    }

    @Test
    void talentEnrichProviderFailureBranchesShouldStayConnected() throws IOException {
        String profileApplication = readProjectFile(
                "backend/src/main/java/com/colonel/saas/domain/talent/application/TalentProfileApplicationService.java");
        String douyinApiProvider = readProjectFile(
                "backend/src/main/java/com/colonel/saas/service/talent/profile/provider/DouyinApiTalentProfileProvider.java");
        String testProvider = readProjectFile(
                "backend/src/main/java/com/colonel/saas/service/talent/provider/TestTalentProvider.java");

        String talentServiceTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/TalentServiceTest.java");
        String douyinApiProviderTest = readProjectFile(
                "backend/src/test/java/com/colonel/saas/service/talent/profile/provider/DouyinApiTalentProfileProviderTest.java");
        String testProviderTest = readProjectFile(
                "backend/src/test/java/com/colonel/saas/service/talent/provider/TestTalentProviderTest.java");

        assertThat(profileApplication)
                .contains("catch (RuntimeException ex)")
                .contains("setEnrichStatus(ENRICH_TASK_STATUS_FAILED)")
                .contains("setEnrichStatus(\"FAILED\")")
                .contains("markEnrichTask(task, ENRICH_TASK_STATUS_FAILED, ex.getMessage())")
                .contains("markEnrichTask(task, \"FAILED\", ex.getMessage())");

        assertThat(douyinApiProvider)
                .contains("NOT_CONFIGURED")
                .contains("UNSUPPORTED")
                .contains("API_FAILED")
                .contains(".success(false)")
                .contains(".syncStatus(TalentProfileResult.STATUS_FAILED)");

        assertThat(testProvider)
                .contains("test_fail")
                .contains("throw new IllegalStateException(\"test provider simulated failure\")");

        assertThat(talentServiceTest)
                .contains("create_shouldKeepTalentForManualFillWhenEnrichProviderFails")
                .contains("refresh_shouldRejectWhenPublicPageCrawlDisabled")
                .contains("refresh_shouldRecordFailureWithoutThrowingWhenProviderFails")
                .contains("getById_shouldThrowWhenMissing");

        assertThat(douyinApiProviderTest)
                .contains("fetch_shouldReturnNotConfiguredWhenTokenMissing")
                .contains("fetch_shouldReturnUnsupportedWhenTokenPresentButNoProfileApi")
                .contains("supports_shouldBeFalseInMockCollectMode");

        assertThat(testProviderTest)
                .contains("enrich_canReturnEmptyOrPartialOrFailForScenarioMarkers")
                .contains("simulated failure");
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
