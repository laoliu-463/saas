package com.colonel.saas.domain.performance.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.performance.domain.ExclusiveMerchantRepository;
import com.colonel.saas.entity.ExclusiveMerchant;
import com.colonel.saas.mapper.ExclusiveMerchantMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 独家商家仓库 MyBatis 实现（DDD-PERF-005）。
 */
@Repository
public class ExclusiveMerchantRepositoryAdapter implements ExclusiveMerchantRepository {

    private final ExclusiveMerchantMapper mapper;

    public ExclusiveMerchantRepositoryAdapter(ExclusiveMerchantMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ExclusiveMerchant> findByMerchantIdAndMonth(String merchantId, String month) {
        ExclusiveMerchant record = mapper.selectOne(new LambdaQueryWrapper<ExclusiveMerchant>()
                .eq(ExclusiveMerchant::getMerchantId, merchantId)
                .eq(ExclusiveMerchant::getEffectiveMonth, month)
                .eq(ExclusiveMerchant::getDeleted, 0)
                .last("limit 1"));
        return Optional.ofNullable(record);
    }

    @Override
    public Optional<ExclusiveMerchant> findActiveByMerchantIdAndMonth(String merchantId, String month) {
        ExclusiveMerchant record = mapper.selectOne(new LambdaQueryWrapper<ExclusiveMerchant>()
                .eq(ExclusiveMerchant::getMerchantId, merchantId)
                .eq(ExclusiveMerchant::getEffectiveMonth, month)
                .eq(ExclusiveMerchant::getStatus, 1)
                .eq(ExclusiveMerchant::getDeleted, 0)
                .orderByDesc(ExclusiveMerchant::getCreateTime)
                .last("limit 1"));
        return Optional.ofNullable(record);
    }

    @Override
    public List<ExclusiveMerchant> listByEffectiveMonth(String month) {
        return mapper.selectList(new LambdaQueryWrapper<ExclusiveMerchant>()
                .eq(ExclusiveMerchant::getEffectiveMonth, month)
                .eq(ExclusiveMerchant::getDeleted, 0));
    }

    @Override
    public void save(ExclusiveMerchant record) {
        mapper.insert(record);
    }

    @Override
    public void update(ExclusiveMerchant record) {
        mapper.updateById(record);
    }
}
