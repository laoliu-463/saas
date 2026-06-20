package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.UserDepartmentLookup;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 通过现有 SysUserMapper 查询用户部门归属的过渡适配器。
 */
@Component
public class SysUserDepartmentLookupAdapter implements UserDepartmentLookup {

    private final SysUserMapper sysUserMapper;

    public SysUserDepartmentLookupAdapter(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public Optional<UUID> findDepartmentId(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        SysUser user = sysUserMapper.selectById(userId);
        return Optional.ofNullable(user).map(SysUser::getDeptId);
    }
}
