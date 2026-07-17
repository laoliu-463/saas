package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddTalentPermissionOverreachNegativeEvidenceTest {

    @Test
    void talentOperatePermissionNegativeEvidenceShouldStayConnected() throws IOException {
        String queryService = readProjectFile("backend/src/main/java/com/colonel/saas/service/TalentQueryService.java");
        String queryServiceTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/TalentQueryServiceTest.java");
        String profileControllerTest = readProjectFile("backend/src/test/java/com/colonel/saas/controller/TalentProfileControllerTest.java");
        String claimPolicyTest = readProjectFile("backend/src/test/java/com/colonel/saas/domain/talent/policy/TalentClaimPolicyTest.java");

        assertThat(queryService)
                .contains("assertCanOperate")
                .contains("currentUserPermissionChecker.hasAnyRole(roleCodes, RoleCodes.ADMIN)")
                .contains("currentUserPermissionChecker.hasAnyRole(roleCodes, RoleCodes.CHANNEL_LEADER)")
                .contains("currentUserPermissionChecker.hasAnyRole(roleCodes, RoleCodes.CHANNEL_STAFF)")
                .contains("talentClaimMapper.findActiveByTalentId(talent.getId())")
                .contains("throw new ForbiddenException(\"无权操作该达人\")");

        assertThat(queryServiceTest)
                .contains("assertCanOperate_shouldRejectChannelStaffWithoutOwnActiveClaim")
                .contains("assertCanOperate_shouldAllowChannelLeaderForOwnDeptClaim")
                .contains("assertCanOperate_shouldNormalizeRoleCodesViaUserPolicy");

        assertThat(profileControllerTest)
                .contains("syncProfile_shouldRejectWhenTalentIsOutsideClaimScope")
                .contains(".andExpect(status().isForbidden())")
                .contains("verify(talentProfileSyncService, never()).syncExistingProfile");

        assertThat(claimPolicyTest)
                .contains("selectReleaseTarget_shouldRejectNonOwnerNonAdmin")
                .contains("ForbiddenException.class");
    }

    @Test
    void talentDataScopeNegativeEvidenceShouldStayConnected() throws IOException {
        String queryService = readProjectFile("backend/src/main/java/com/colonel/saas/service/TalentQueryService.java");
        String queryServiceTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/TalentQueryServiceTest.java");
        String boundaryTest = readProjectFile("backend/src/test/java/com/colonel/saas/architecture/DddUserFacadeTalentQueryBoundaryTest.java");

        assertThat(queryService)
                .contains("assertCanAccessLegacy")
                .contains("assertCanAccessWithPolicy")
                .contains("dataScopeResolver.resolve(currentUserId, currentDeptId, dataScope)")
                .contains("!resolvedScope.contextSatisfied()")
                .contains("resolvedScope.filtersUser()")
                .contains("resolvedScope.filtersDept()")
                .contains("throw new ForbiddenException(\"无权查看该达人详情\")");

        assertThat(queryServiceTest)
                .contains("dataScopeAccess_shouldKeepLegacyDefaultAndDelegateEnabledPathToUserPolicy")
                .contains("assertCanAccessLegacy")
                .contains("assertCanAccessWithPolicy")
                .contains("dataScopeResolver.resolve")
                .contains("currentUserPermissionChecker.hasAnyRole");

        assertThat(boundaryTest)
                .contains("talentPermissionConsumers_shouldUseUserCheckerAndDataScopeResolver")
                .contains("doesNotContain(\"import com.colonel.saas.domain.user.policy.DataScopePolicy;\")")
                .contains("doesNotContain(\"import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;\")")
                .contains("contains(\"DataScopeResolver\")")
                .contains("contains(\"CurrentUserPermissionChecker\")");
    }

    @Test
    void talentListDetailScopeAndGenderEvidenceShouldStayConnected() throws IOException {
        String queryService = readProjectFile("backend/src/main/java/com/colonel/saas/service/TalentQueryService.java");
        String queryServiceTest = readProjectFile("backend/src/test/java/com/colonel/saas/service/TalentQueryServiceTest.java");
        String matrix = readProjectFile("docs/ddd-completion-evidence-matrix.md");

        assertThat(queryService)
                .contains("resolveBaseScope")
                .contains("\"TEAM_PUBLIC\".equalsIgnoreCase(firstNonBlank(query.getView(), \"\"))")
                .contains("return DataScope.ALL")
                .contains("personalPublicView")
                .contains("itemOrZero(talent.getActiveClaimCount()) == 0")
                .contains("rejectUnsupportedFilters")
                .contains("gender 筛选当前不支持");

        assertThat(queryServiceTest)
                .contains("page_teamPublicPersonalShouldExcludeTalentsClaimedByOthers")
                .contains("page_teamPrivateShouldReturnDeptClaimedTalents")
                .contains("detail_shouldRejectPersonalScopeWhenViewerHasNoActiveClaim")
                .contains("detailAccess_dataScopePolicyEnabledPathShouldPreserveClaimScopeSemantics")
                .contains("page_shouldRejectUnsupportedGenderFilter");

        assertThat(matrix)
                .contains("| T-8 |")
                .contains("| T-12 |")
                .contains("| T-13 |");
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
