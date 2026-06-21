package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.UserBasicLookup;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 通过现有 SysUserMapper 查询用户基础读模型的过渡适配器。
 */
@Component
public class SysUserBasicLookupAdapter implements UserBasicLookup {

    private final SysUserMapper sysUserMapper;

    public SysUserBasicLookupAdapter(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public Optional<BasicUser> findById(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(toBasicUser(sysUserMapper.selectById(userId)));
    }

    @Override
    public List<BasicUser> findByIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<UUID> distinct = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(distinct);
        if (users == null) {
            return List.of();
        }
        return users.stream()
                .filter(Objects::nonNull)
                .map(this::toBasicUser)
                .toList();
    }

    private BasicUser toBasicUser(SysUser user) {
        if (user == null) {
            return null;
        }
        return new BasicUser(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getDeptId(),
                user.getChannelCode()
        );
    }
}
