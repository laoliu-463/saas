package com.colonel.saas.domain.performance.domain;

import com.colonel.saas.entity.ExclusiveMerchant;
import java.util.List;
import java.util.Optional;

/**
 * 独家商家数据仓库接口（DDD-PERF-005）。
 */
public interface ExclusiveMerchantRepository {

    Optional<ExclusiveMerchant> findByMerchantIdAndMonth(String merchantId, String month);

    Optional<ExclusiveMerchant> findActiveByMerchantIdAndMonth(String merchantId, String month);

    List<ExclusiveMerchant> listByEffectiveMonth(String month);

    void save(ExclusiveMerchant record);

    void update(ExclusiveMerchant record);
}
