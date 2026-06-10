package com.colonel.saas.domain.product.facade;

import com.colonel.saas.domain.product.facade.dto.ActivityBriefDTO;
import com.colonel.saas.domain.product.facade.dto.ActivityProductForSampleDTO;
import com.colonel.saas.domain.product.facade.dto.PartnerBriefDTO;
import com.colonel.saas.domain.product.facade.dto.ProductBriefDTO;
import com.colonel.saas.domain.product.facade.dto.ProductDisplayInfoDTO;
import com.colonel.saas.domain.product.facade.dto.ProductOwnerDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 商品域只读门面（DDD-PRODUCT-001）。
 * <p>
 * 订单域、寄样域、业绩域和 BFF 后续应通过本接口读取商品、活动商品、合作方和展示状态上下文。
 * 第一版由 {@link LegacyProductDomainFacade} 委派既有 ProductService / Mapper，调用方暂不迁移，确保零业务行为变更。
 * </p>
 */
public interface ProductDomainFacade {

    /**
     * 按商品关系 ID（当前模型为 {@code product_snapshot.id} UUID）读取寄样所需的活动商品快照。
     */
    ActivityProductForSampleDTO getActivityProductForSample(UUID relationId);

    /**
     * 读取活动商品负责人；商品级负责人为空时回退活动默认招商负责人。
     */
    ProductOwnerDTO getRecruiterForActivityProduct(String activityId, String productId);

    /**
     * 读取商品摘要；同一 productId 存在多个活动快照时返回当前旧查询顺序下第一条。
     */
    ProductBriefDTO getProductBrief(String productId);

    /**
     * 读取活动摘要。
     */
    ActivityBriefDTO getActivityBrief(String activityId);

    /**
     * 读取团长合作方摘要；支持 UUID 主键或 colonelBuyinId。
     */
    PartnerBriefDTO getPartnerBrief(String partnerId);

    /**
     * 批量读取商品摘要，返回 productId 到摘要的映射。
     */
    Map<String, ProductBriefDTO> batchGetProductBrief(Collection<String> productIds);

    /**
     * 按关键字搜索团长合作方。
     */
    List<PartnerBriefDTO> listPartners(String keyword);

    /**
     * 判断商品关系是否满足现有商品库/快速寄样可见条件。
     */
    boolean checkProductVisibleForSample(UUID relationId);

    /**
     * 按商品关系 ID 读取负责人；商品级负责人为空时回退活动默认招商负责人。
     */
    ProductOwnerDTO getProductOwner(UUID relationId);

    /**
     * 按商品关系 ID 读取展示状态摘要。
     */
    ProductDisplayInfoDTO getProductDisplayInfo(UUID relationId);
}
