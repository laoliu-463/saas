package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddPerformanceCommissionRuleVersionContractTest {

    @Test
    void commissionRuleSourceShouldHaveVersionedSchemaAndEntity() throws IOException {
        String entity = readProjectFile("src/main/java/com/colonel/saas/entity/CommissionRule.java");
        String migration = readProjectFile("src/main/resources/db/alter-v2-config-20260523.sql").toLowerCase();
        String migrateAll = readProjectFile("src/main/resources/db/migrate-all.sql").toLowerCase();
        String testSupport = readProjectFile("src/test/java/com/colonel/saas/testsupport/BaseIntegrationTest.java")
                .toLowerCase();

        assertThat(entity)
                .contains(
                        "import com.colonel.saas.common.base.VersionedEntity;",
                        "public class CommissionRule extends VersionedEntity");
        assertThat(migration)
                .contains(
                        "create table if not exists commissions",
                        "version int not null default 1",
                        "add column if not exists version int not null default 1");
        assertThat(migrateAll)
                .contains(
                        "create table if not exists commissions",
                        "version int not null default 1",
                        "add column if not exists version int not null default 1");
        assertThat(testSupport)
                .contains(
                        "create table if not exists commissions",
                        "version int not null default 1",
                        "alter table commissions add column if not exists version int not null default 1");
    }

    @Test
    void commissionRuleServiceShouldExposeMatchedRuleVersionEvidence() throws IOException {
        String service = readProjectFile("src/main/java/com/colonel/saas/service/CommissionRuleService.java");
        String test = readProjectFile("src/test/java/com/colonel/saas/service/CommissionRuleServiceTest.java");

        assertThat(service)
                .contains(
                        "record CommissionRuleResolution",
                        "Integer ruleVersion",
                        "public CommissionRuleResolution resolveRule",
                        "rule.setVersion(1)",
                        "rule.getVersion()",
                        "rule.getUpdateTime()");
        assertThat(test)
                .contains(
                        "resolveRule_shouldExposeMatchedRuleVersionEvidence",
                        "create_shouldPersistValidatedRule",
                        "update_shouldKeepLoadedVersionForOptimisticLockEvidence",
                        "assertThat(resolution.ruleVersion()).isEqualTo(7)");
    }

    @Test
    void commissionSummaryShouldCarryRuleSourceSnapshotWithoutChangingPerformanceRecordSchema() throws IOException {
        String commissionService = readProjectFile("src/main/java/com/colonel/saas/service/CommissionService.java");
        String performanceRecord = readProjectFile("src/main/java/com/colonel/saas/entity/PerformanceRecord.java");
        String mapper = readProjectFile("src/main/resources/mapper/PerformanceRecordMapper.xml");
        String test = readProjectFile("src/test/java/com/colonel/saas/service/CommissionServiceTest.java");

        assertThat(commissionService)
                .contains(
                        "commissionRuleService.resolveRule",
                        "bizRatioSource",
                        "bizRatioSourceKey",
                        "bizRatioRuleId",
                        "bizRatioSourceVersion",
                        "channelRatioSource",
                        "channelRatioSourceKey",
                        "channelRatioRuleId",
                        "channelRatioSourceVersion");
        assertThat(test)
                .contains(
                        "calculate_shouldUseCommissionRuleRatiosBeforeLegacyActivityConfig",
                        "assertThat(summary.bizRatioSource()).isEqualTo(\"commission_rule\")",
                        "assertThat(summary.bizRatioSourceVersion()).isEqualTo(\"6\")",
                        "assertThat(summary.channelRatioSourceVersion()).isEqualTo(\"8\")");
        assertThat(performanceRecord)
                .doesNotContain("CommissionRuleVersion", "commissionRuleVersion", "ruleSourceVersion");
        assertThat(mapper)
                .doesNotContain("commission_rule_version", "rule_source_version");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
