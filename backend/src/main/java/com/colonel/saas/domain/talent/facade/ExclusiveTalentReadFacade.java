package com.colonel.saas.domain.talent.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ExclusiveTalent;

/**
 * 达人域独家达人只读门面。
 */
public interface ExclusiveTalentReadFacade {

    IPage<ExclusiveTalent> selectPage(
            Page<ExclusiveTalent> page,
            LambdaQueryWrapper<ExclusiveTalent> wrapper);
}
