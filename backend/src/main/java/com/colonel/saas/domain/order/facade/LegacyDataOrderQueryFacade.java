package com.colonel.saas.domain.order.facade;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * {@link DataOrderQueryFacade} 遗留实现：委派现有 {@link ColonelsettlementOrderMapper}，零行为变更。
 */
@Service
public class LegacyDataOrderQueryFacade implements DataOrderQueryFacade {

    private final ColonelsettlementOrderMapper orderMapper;

    public LegacyDataOrderQueryFacade(ColonelsettlementOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public IPage<ColonelsettlementOrder> findPageWithScope(
            Page<ColonelsettlementOrder> page,
            QueryWrapper<ColonelsettlementOrder> wrapper) {
        return orderMapper.findPageWithScope(page, wrapper);
    }

    @Override
    public List<Map<String, Object>> selectMaps(QueryWrapper<ColonelsettlementOrder> wrapper) {
        return orderMapper.selectMaps(wrapper);
    }
}
