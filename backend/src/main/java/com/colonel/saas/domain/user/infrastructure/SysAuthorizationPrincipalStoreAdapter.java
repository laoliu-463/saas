package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.domain.user.api.AuthorizationPrincipal;
import com.colonel.saas.domain.user.port.AuthorizationPrincipalStore;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class SysAuthorizationPrincipalStoreAdapter implements AuthorizationPrincipalStore {

    private final SysUserMapper sysUserMapper;

    public SysAuthorizationPrincipalStoreAdapter(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public Optional<AuthorizationPrincipal> loadLoginEligible(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null
                || (user.getDeleted() != null && user.getDeleted() != 0)
                || !SysUserStatus.canLogin(user.getStatus())
                || user.getAuthzVersion() == null
                || user.getAuthzVersion() < 1) {
            return Optional.empty();
        }
        return Optional.of(new AuthorizationPrincipal(
                user.getId(),
                user.getDeptId(),
                user.getUsername(),
                user.getAuthzVersion(),
                SysUserStatus.isPendingActivation(user.getStatus())));
    }
}
