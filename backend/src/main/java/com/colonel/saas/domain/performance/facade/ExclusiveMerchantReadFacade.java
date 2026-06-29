package com.colonel.saas.domain.performance.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ExclusiveMerchant;

/**
 * 业绩域独家商家只读门面。
 */
public interface ExclusiveMerchantReadFacade {

    IPage<ExclusiveMerchant> selectPage(
            Page<ExclusiveMerchant> page,
            LambdaQueryWrapper<ExclusiveMerchant> wrapper);
}
