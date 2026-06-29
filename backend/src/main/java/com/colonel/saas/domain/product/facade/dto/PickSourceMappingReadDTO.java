package com.colonel.saas.domain.product.facade.dto;

/**
 * pick_source 映射只读模型。
 *
 * <p>用于网关夹具和订单归因输入构造读取商品域转链映射事实，避免调用方直接依赖持久化实体或 Mapper。</p>
 */
public record PickSourceMappingReadDTO(
        String shortId,
        String productId,
        String activityId,
        String pickSource,
        String pickExtra,
        String talentId,
        String talentName) {
}
