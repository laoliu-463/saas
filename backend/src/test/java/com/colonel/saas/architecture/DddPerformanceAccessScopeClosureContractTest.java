package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddPerformanceAccessScopeClosureContractTest {

    @Test
    void performanceController_shouldBuildAccessContextForReadSummaryAndExportEntrypoints() throws Exception {
        String source = Files.readString(
                sourcePath("src/main/java/com/colonel/saas/controller/PerformanceController.java"));

        assertThat(countOccurrences(source, "PerformanceAccessContext.of(userId, deptId, dataScope, roleCodes)"))
                .as("getByOrderId, batchGet, list, summary and export must pass request scope into performance domain")
                .isGreaterThanOrEqualTo(5);
        assertThat(source)
                .contains("PerformanceAccessScope.canExport(context, currentUserPermissionChecker)")
                .contains("throw BusinessException.forbidden(\"无权导出业绩明细\")")
                .contains("@RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})")
                .contains("@RequireRoles({RoleCodes.ADMIN})");
    }

    @Test
    void performanceQueryService_shouldApplyAccessScopeToReadListBatchAndExportQueries() throws Exception {
        String source = Files.readString(
                sourcePath("src/main/java/com/colonel/saas/service/PerformanceQueryService.java"));

        assertThat(source)
                .contains("PerformanceAccessScope.canAccessRecord(record, context, currentUserPermissionChecker)")
                .contains("PerformanceAccessScope.assertFilterAllowed(")
                .contains("PerformanceAccessScope.appendScopeCondition(where, args, context, prAlias, currentUserPermissionChecker)")
                .contains("PerformanceAccessScope.appendScopeConditionWithResolver(")
                .contains("listDetailsForExport(PerformanceListQuery query, PerformanceAccessContext context)")
                .doesNotContain("new CurrentUserPermissionPolicy()")
                .doesNotContain("new DataScopePolicy()");
    }

    @Test
    void exportApplicationService_shouldKeepExportRowsBehindScopedQueryService() throws Exception {
        String source = Files.readString(
                sourcePath("src/main/java/com/colonel/saas/domain/performance/application/PerformanceExportApplicationService.java"));

        assertThat(source)
                .contains("PerformanceAccessContext context")
                .contains("performanceQueryService.listDetailsForExport(query, context)")
                .doesNotContain("PerformanceRecordMapper")
                .doesNotContain("JdbcTemplate")
                .doesNotContain("performance_records");
    }

    @Test
    void accessScopeBehaviorTests_shouldPinPositiveAndNegativePermissionCases() throws Exception {
        String controllerTest = Files.readString(
                sourcePath("src/test/java/com/colonel/saas/controller/PerformanceControllerTest.java"));
        String scopeTest = Files.readString(
                sourcePath("src/test/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScopeTest.java"));

        assertThat(controllerTest)
                .contains("list_shouldPassRequestAttributesIntoPerformanceAccessContext")
                .contains("export_shouldRejectStaffRoleBeforeServiceCall")
                .contains("export_shouldPassAccessContextToExportServiceAndWriteBytes");
        assertThat(scopeTest)
                .contains("canExport_shouldAllowLeadersAndAdmin")
                .contains("canRecalculateMonth_shouldAllowOnlyAdmin")
                .contains("assertFilterAllowed_shouldRejectMissingContextAndCrossStaffFilters")
                .contains("appendScopeCondition_shouldFailClosedWhenUserOrDeptMissing");
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static Path sourcePath(String relativePath) {
        Path path = Path.of(relativePath);
        if (Files.exists(path)) {
            return path;
        }
        return Path.of("backend").resolve(relativePath);
    }
}
