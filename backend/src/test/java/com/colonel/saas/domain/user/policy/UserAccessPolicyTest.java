package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.user.port.UserDepartmentLookup;
import com.colonel.saas.domain.user.policy.UserAccessPolicy.AccessibleUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccessPolicyTest {

    private Map<UUID, UUID> userDeptIds;

    private UserAccessPolicy policy;

    @BeforeEach
    void setUp() {
        userDeptIds = new HashMap<>();
        UserDepartmentLookup userDepartmentLookup = userId -> Optional.ofNullable(userDeptIds.get(userId));
        policy = new UserAccessPolicy(userDepartmentLookup);
    }

    @Test
    void assertCanAccess_nullDataScope_shouldReject() {
        AccessibleUser target = user(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> policy.assertCanAccess(target, UUID.randomUUID(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无法确认数据权限，拒绝访问");
    }

    @Test
    void assertCanAccess_personalScope_shouldAllowSelfOnly() {
        UUID userId = UUID.randomUUID();
        AccessibleUser target = user(userId, UUID.randomUUID());

        policy.assertCanAccess(target, userId, DataScope.PERSONAL);

        assertThatThrownBy(() -> policy.assertCanAccess(target, UUID.randomUUID(), DataScope.PERSONAL))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权访问该用户");
    }

    @Test
    void assertCanAccess_deptScope_shouldAllowSameDeptOnly() {
        UUID currentUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        AccessibleUser target = user(UUID.randomUUID(), deptId);
        userDeptIds.put(currentUserId, deptId);

        policy.assertCanAccess(target, currentUserId, DataScope.DEPT);

        AccessibleUser otherDeptTarget = user(UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> policy.assertCanAccess(otherDeptTarget, currentUserId, DataScope.DEPT))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权访问该部门外用户");
    }

    @Test
    void assertCanAccess_allScope_shouldAllowAnyTarget() {
        policy.assertCanAccess(user(UUID.randomUUID(), null), null, DataScope.ALL);
    }

    @Test
    void policy_shouldDependOnUserDomainPortNotPersistenceMapper() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/user/policy/UserAccessPolicy.java"));

        assertThat(source).contains("UserDepartmentLookup");
        assertThat(source).doesNotContain("com.colonel.saas.mapper.SysUserMapper");
        assertThat(source).doesNotContain("com.colonel.saas.entity.SysUser");
    }

    private static AccessibleUser user(UUID userId, UUID deptId) {
        return new AccessibleUser(userId, deptId);
    }
}
