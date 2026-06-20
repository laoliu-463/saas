package com.colonel.saas.auth.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SysDeptServiceBoundaryTest {

    @Test
    void directoryQueriesShouldUseUserDomainApplicationService() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/auth/service/SysDeptService.java"));

        assertThat(source).contains("OrgUnitDirectoryApplicationService");
        assertThat(source).doesNotContain("sysDeptMapper.findAllActive()");
    }

    @Test
    void detailAndGroupQueriesShouldUseUserDomainApplicationService() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/auth/service/SysDeptService.java"));

        assertThat(source).contains("return orgUnitDirectoryApplicationService.getById(id);");
        assertThat(source).contains("return orgUnitDirectoryApplicationService.findGroupsByParent(parentId, deptType);");
        assertThat(source).doesNotContain("sysDeptMapper.findByParentId(parentId)");
    }

    @Test
    void statsQueryShouldUseUserDomainApplicationService() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/auth/service/SysDeptService.java"));

        assertThat(source).contains("return orgUnitDirectoryApplicationService.getStats(deptId);");
        assertThat(source).doesNotContain("sysDeptMapper.countMembersUnderDept(deptId)");
        assertThat(source).doesNotContain("sysDeptMapper.countChildGroupsByType(deptId");
    }

    @Test
    void membersQueryShouldUseUserDomainApplicationService() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/auth/service/SysDeptService.java"));

        assertThat(source).contains("return orgUnitDirectoryApplicationService.findMembers(deptId, request);");
        assertThat(source).doesNotContain("return sysUserService.findDeptMembers(deptId, request);");
        assertThat(source).doesNotContain("requireDept(deptId);");
    }

    @Test
    void writeOperationsShouldUseUserDomainApplicationService() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/auth/service/SysDeptService.java"));

        assertThat(source).contains("OrgUnitWriteApplicationService");
        assertThat(source).contains("return orgUnitWriteApplicationService.create(request, currentUserId);");
        assertThat(source).contains("return orgUnitWriteApplicationService.update(id, request, currentUserId, roleCodes);");
        assertThat(source).contains("orgUnitWriteApplicationService.delete(id, currentUserId, roleCodes);");
        assertThat(source).doesNotContain("sysDeptMapper.insert");
        assertThat(source).doesNotContain("sysDeptMapper.updateById");
        assertThat(source).doesNotContain("sysDeptMapper.softDeleteById");
        assertThat(source).doesNotContain("operationLogService.recordSystemAction");
        assertThat(source).doesNotContain("orgStructureService.assertCanDeleteDept");
    }
}
