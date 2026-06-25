package com.colonel.saas.domain.order.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 订单域 pick_source 映射读取适配器（DDD-ORDER-004）。
 */
@Component
public class OrderPickSourceMappingAdapter {

    private final PickSourceMappingMapper pickSourceMappingMapper;

    public OrderPickSourceMappingAdapter(PickSourceMappingMapper pickSourceMappingMapper) {
        this.pickSourceMappingMapper = pickSourceMappingMapper;
    }

    public PickSourceMapping findByPickSourceOrExtra(String pickSource, String pickExtra) {
        if (StringUtils.hasText(pickSource)) {
            PickSourceMapping byPickSource = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                    .eq(PickSourceMapping::getPickSource, pickSource.trim())
                    .eq(PickSourceMapping::getStatus, 1)
                    .last("limit 1"));
            if (byPickSource != null) {
                return byPickSource;
            }
        }
        if (!StringUtils.hasText(pickExtra)) {
            return null;
        }
        PickSourceMapping byPickExtra = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getPickExtra, pickExtra.trim())
                .eq(PickSourceMapping::getStatus, 1)
                .last("limit 1"));
        if (byPickExtra != null) {
            return byPickExtra;
        }
        String normalized = pickExtra.length() > 20 ? pickExtra.substring(pickExtra.length() - 20) : pickExtra;
        return pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getShortId, normalized)
                .eq(PickSourceMapping::getStatus, 1)
                .last("limit 1"));
    }
}
