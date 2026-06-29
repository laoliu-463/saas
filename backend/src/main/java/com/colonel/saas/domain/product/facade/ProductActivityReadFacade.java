package com.colonel.saas.domain.product.facade;

import com.colonel.saas.entity.ColonelsettlementActivity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品域活动事实只读门面。
 */
public interface ProductActivityReadFacade {

    List<ColonelsettlementActivity> selectNamesByActivityIds(List<String> activityIds);

    List<ColonelsettlementActivity> selectExportPage(
            long offset,
            long limit,
            String activityName,
            LocalDateTime now);
}
