package com.colonel.saas.domain.talent.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.talent.domain.ExclusiveTalentRepository;
import com.colonel.saas.entity.ExclusiveTalent;
import com.colonel.saas.mapper.ExclusiveTalentMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 独家达人 Repository 适配器（DDD-TALENT-004）。
 *
 * <p>委派给 {@link ExclusiveTalentMapper}，把 Entity 直接暴露给领域层。后续切到
 * 自定义表结构时，仅替换本类实现。</p>
 */
@Component
public class ExclusiveTalentRepositoryAdapter implements ExclusiveTalentRepository {

    private final ExclusiveTalentMapper mapper;

    public ExclusiveTalentRepositoryAdapter(ExclusiveTalentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ExclusiveTalent> findActiveByTalentUid(String talentUid, String effectiveMonth) {
        ExclusiveTalent record = mapper.selectOne(new LambdaQueryWrapper<ExclusiveTalent>()
                .eq(ExclusiveTalent::getTalentUid, talentUid)
                .eq(ExclusiveTalent::getEffectiveMonth, effectiveMonth)
                .eq(ExclusiveTalent::getStatus, 1)
                .eq(ExclusiveTalent::getDeleted, 0)
                .orderByDesc(ExclusiveTalent::getCreateTime)
                .last("limit 1"));
        return Optional.ofNullable(record);
    }

    @Override
    public Optional<ExclusiveTalent> findByTalentUidAndMonth(String talentUid, String effectiveMonth) {
        ExclusiveTalent record = mapper.selectOne(new LambdaQueryWrapper<ExclusiveTalent>()
                .eq(ExclusiveTalent::getTalentUid, talentUid)
                .eq(ExclusiveTalent::getEffectiveMonth, effectiveMonth)
                .eq(ExclusiveTalent::getDeleted, 0)
                .last("limit 1"));
        return Optional.ofNullable(record);
    }

    @Override
    public void save(ExclusiveTalent record) {
        mapper.insert(record);
    }

    @Override
    public void update(ExclusiveTalent record) {
        mapper.updateById(record);
    }

    @Override
    public List<ExclusiveTalent> listByEffectiveMonth(String effectiveMonth) {
        return mapper.selectList(new LambdaQueryWrapper<ExclusiveTalent>()
                .eq(ExclusiveTalent::getEffectiveMonth, effectiveMonth)
                .eq(ExclusiveTalent::getDeleted, 0));
    }
}