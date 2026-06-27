package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.UserGroupMembershipStore;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 通过现有 sys_user 持久化用户业务组成员变更。
 */
@Component
public class SysUserGroupMembershipStoreAdapter implements UserGroupMembershipStore {

    private final SysUserMapper sysUserMapper;

    public SysUserGroupMembershipStoreAdapter(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public Optional<GroupMember> findMember(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(toMember(sysUserMapper.selectById(userId)));
    }

    @Override
    public void updateDept(UUID userId, UUID deptId) {
        sysUserMapper.updateDeptById(userId, deptId);
    }

    private GroupMember toMember(SysUser user) {
        if (user == null) {
            return null;
        }
        return new GroupMember(user.getId(), user.getUsername(), user.getDeptId());
    }
}
