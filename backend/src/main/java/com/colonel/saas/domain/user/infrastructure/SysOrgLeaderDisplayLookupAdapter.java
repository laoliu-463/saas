package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.OrgLeaderDisplayLookup;
import com.colonel.saas.domain.user.port.OrgLeaderDisplayLookup.LeaderDisplay;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysUserMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

/**
 * 通过现有 SysUserMapper 查询组织负责人展示名的过渡适配器。
 */
@Component
public class SysOrgLeaderDisplayLookupAdapter implements OrgLeaderDisplayLookup {

    private final SysUserMapper sysUserMapper;

    public SysOrgLeaderDisplayLookupAdapter(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public Optional<LeaderDisplay> findDisplay(UUID leaderUserId) {
        if (leaderUserId == null) {
            return Optional.empty();
        }
        SysUser user = sysUserMapper.selectById(leaderUserId);
        if (user == null || user.getDeleted() != null && user.getDeleted() != 0) {
            return Optional.empty();
        }
        return Optional.of(new LeaderDisplay(StringUtils.hasText(user.getRealName())
                ? user.getRealName()
                : user.getUsername()));
    }
}
