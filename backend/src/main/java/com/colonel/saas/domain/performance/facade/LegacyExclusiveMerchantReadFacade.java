package com.colonel.saas.domain.performance.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ExclusiveMerchant;
import com.colonel.saas.mapper.ExclusiveMerchantMapper;
import org.springframework.stereotype.Service;

/**
 * {@link ExclusiveMerchantReadFacade} 遗留实现：委派现有 {@link ExclusiveMerchantMapper}。
 */
@Service
public class LegacyExclusiveMerchantReadFacade implements ExclusiveMerchantReadFacade {

    private final ExclusiveMerchantMapper exclusiveMerchantMapper;

    public LegacyExclusiveMerchantReadFacade(ExclusiveMerchantMapper exclusiveMerchantMapper) {
        this.exclusiveMerchantMapper = exclusiveMerchantMapper;
    }

    @Override
    public IPage<ExclusiveMerchant> selectPage(
            Page<ExclusiveMerchant> page,
            LambdaQueryWrapper<ExclusiveMerchant> wrapper) {
        return exclusiveMerchantMapper.selectPage(page, wrapper);
    }
}
