package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.OrgLeaderCandidateLookup;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 通过现有用户/角色 Mapper 查询组织负责人候选人的过渡适配器。
 */
@Component
public class SysOrgLeaderCandidateLookupAdapter implements OrgLeaderCandidateLookup {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public SysOrgLeaderCandidateLookupAdapter(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    @Override
    public Optional<LeaderCandidate> findActiveLeaderCandidate(UUID leaderUserId) {
        if (leaderUserId == null) {
            return Optional.empty();
        }
        SysUser leader = sysUserMapper.selectById(leaderUserId);
        if (leader == null || Objects.equals(leader.getDeleted(), 1)) {
            return Optional.empty();
        }
        List<SysUserRole> userRoles = sysUserRoleMapper.findByUserId(leaderUserId);
        Set<String> roleCodes = (userRoles == null ? List.<SysUserRole>of() : userRoles).stream()
                .map(SysUserRole::getRoleId)
                .map(sysRoleMapper::selectById)
                .filter(Objects::nonNull)
                .map(SysRole::getRoleCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return Optional.of(new LeaderCandidate(
                leader.getId(),
                leader.getRealName(),
                leader.getUsername(),
                roleCodes));
    }
}
