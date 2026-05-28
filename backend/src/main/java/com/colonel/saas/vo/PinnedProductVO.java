package com.colonel.saas.vo;

import java.time.LocalDateTime;

/**
 * 置顶商品展示视图对象。
 * <p>
 * 用于商品置顶管理页面的展示。置顶商品是指被运营人员手动标记为优先展示的商品，
 * 在商品列表和推荐位中获得更高的曝光权重。置顶具有有效期，到期后自动解除。
 * </p>
 *
 * @param activityId 关联的活动 ID
 * @param productId 商品 ID
 * @param productName 商品名称
 * @param cover 商品封面图 URL
 * @param pinnedAt 置顶开始时间
 * @param pinnedUntil 置顶截止时间，到期后自动解除置顶
 */
public record PinnedProductVO(
        /** 关联的活动 ID */
        String activityId,
        /** 商品 ID */
        String productId,
        /** 商品名称 */
        String productName,
        /** 商品封面图 URL */
        String cover,
        /** 置顶开始时间 */
        LocalDateTime pinnedAt,
        /** 置顶截止时间 */
        LocalDateTime pinnedUntil) {
}
