package com.colonel.saas.domain.talent.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ExclusiveTalent;
import com.colonel.saas.mapper.ExclusiveTalentMapper;
import org.springframework.stereotype.Service;

/**
 * {@link ExclusiveTalentReadFacade} 遗留实现：委派现有 {@link ExclusiveTalentMapper}。
 */
@Service
public class LegacyExclusiveTalentReadFacade implements ExclusiveTalentReadFacade {

    private final ExclusiveTalentMapper exclusiveTalentMapper;

    public LegacyExclusiveTalentReadFacade(ExclusiveTalentMapper exclusiveTalentMapper) {
        this.exclusiveTalentMapper = exclusiveTalentMapper;
    }

    @Override
    public IPage<ExclusiveTalent> selectPage(
            Page<ExclusiveTalent> page,
            LambdaQueryWrapper<ExclusiveTalent> wrapper) {
        return exclusiveTalentMapper.selectPage(page, wrapper);
    }
}
