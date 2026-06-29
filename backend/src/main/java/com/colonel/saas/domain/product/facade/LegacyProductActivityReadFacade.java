package com.colonel.saas.domain.product.facade;

import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link ProductActivityReadFacade} 遗留实现：委派现有 {@link ColonelsettlementActivityMapper}。
 */
@Service
public class LegacyProductActivityReadFacade implements ProductActivityReadFacade {

    private final ColonelsettlementActivityMapper activityMapper;

    public LegacyProductActivityReadFacade(ColonelsettlementActivityMapper activityMapper) {
        this.activityMapper = activityMapper;
    }

    @Override
    public List<ColonelsettlementActivity> selectNamesByActivityIds(List<String> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) {
            return List.of();
        }
        return activityMapper.selectNamesByActivityIds(activityIds);
    }

    @Override
    public List<ColonelsettlementActivity> selectExportPage(
            long offset,
            long limit,
            String activityName,
            LocalDateTime now) {
        return activityMapper.selectExportPage(offset, limit, activityName, now);
    }
}
