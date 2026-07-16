package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.UserRoleRecipientLookup;
import com.colonel.saas.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 通过用户、角色及其关系表查找业务通知接收人。
 */
@Component
public class SysUserRoleRecipientLookupAdapter implements UserRoleRecipientLookup {

    private final SysUserMapper sysUserMapper;

    public SysUserRoleRecipientLookupAdapter(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public List<UUID> findActiveUserIdsByRoleCodes(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of();
        }
        List<String> distinctRoleCodes = roleCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(roleCode -> !roleCode.isEmpty())
                .distinct()
                .toList();
        if (distinctRoleCodes.isEmpty()) {
            return List.of();
        }
        List<UUID> userIds = sysUserMapper.findActiveIdsByRoleCodes(distinctRoleCodes);
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
