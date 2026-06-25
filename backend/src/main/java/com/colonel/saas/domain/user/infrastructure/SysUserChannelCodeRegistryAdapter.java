package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.UserChannelCodeRegistry;
import com.colonel.saas.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

/**
 * 通过现有 SysUserMapper 查询渠道编码占用情况的过渡适配器。
 */
@Component
public class SysUserChannelCodeRegistryAdapter implements UserChannelCodeRegistry {

    private final SysUserMapper sysUserMapper;

    public SysUserChannelCodeRegistryAdapter(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public boolean isOccupied(String channelCode) {
        return sysUserMapper.existsByChannelCodeIncludingDeleted(channelCode);
    }
}
