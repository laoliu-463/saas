package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class AttributionService {

    private final PickSourceMappingMapper pickSourceMappingMapper;
    private final ProductOperationStateMapper operationStateMapper;
    private final ExclusiveTalentService exclusiveTalentService;
    private final ExclusiveMerchantService exclusiveMerchantService;

    public AttributionService(
            PickSourceMappingMapper pickSourceMappingMapper,
            ProductOperationStateMapper operationStateMapper,
            ExclusiveTalentService exclusiveTalentService,
            ExclusiveMerchantService exclusiveMerchantService) {
        this.pickSourceMappingMapper = pickSourceMappingMapper;
        this.operationStateMapper = operationStateMapper;
        this.exclusiveTalentService = exclusiveTalentService;
        this.exclusiveMerchantService = exclusiveMerchantService;
    }

    public AttributionResult resolveAttribution(ColonelsettlementOrder order, java.util.Map<String, Object> source) {
        String activityId = firstNonBlank(
                asString(source.get("colonel_activity_id")),
                asString(source.get("activity_id")),
                order.getActivityId()
        );
        String productId = order.getProductId();
        
        // 招商归因：查找该商品的负责人
        UUID colonelUserId = null;
        if (StringUtils.hasText(activityId) && StringUtils.hasText(productId)) {
            ProductOperationState state = operationStateMapper.selectOne(new LambdaQueryWrapper<ProductOperationState>()
                    .eq(ProductOperationState::getActivityId, activityId)
                    .eq(ProductOperationState::getProductId, productId)
                    .last("limit 1"));
            if (state != null) {
                colonelUserId = state.getAssigneeId();
            }
        }

        String merchantId = firstNonBlank(
                asString(source.get("merchant_id")),
                asString(source.get("shop_id")),
                order.getShopId() == null ? null : String.valueOf(order.getShopId())
        );
        
        // 商家独家归因
        ExclusiveOwner merchantExclusiveOwner = findExclusiveMerchantOwner(merchantId);
        if (merchantExclusiveOwner != null) {
            return AttributionResult.attributed(
                    merchantExclusiveOwner.userId(),
                    merchantExclusiveOwner.deptId(),
                    merchantExclusiveOwner.userId(),
                    null,
                    activityId,
                    colonelUserId,
                    "商家独家归因"
            );
        }

        String talentUid = firstNonBlank(asString(source.get("talent_uid")), asString(source.get("author_id")));
        
        // 达人独家归因
        ExclusiveOwner exclusiveOwner = findExclusiveTalentOwner(talentUid);
        if (exclusiveOwner != null) {
            return AttributionResult.attributed(
                    exclusiveOwner.userId(),
                    exclusiveOwner.deptId(),
                    exclusiveOwner.userId(),
                    talentUid,
                    activityId,
                    colonelUserId,
                    "达人独家归因"
            );
        }

        // 渠道归因 (PickSource)
        PickSourceMapping mapping = findPickSourceMapping(order.getPickSource(), asString(source.get("pick_extra")));
        if (mapping == null || mapping.getUserId() == null) {
            return AttributionResult.unattributed(
                    talentUid,
                    activityId,
                    colonelUserId,
                    "pick_source 未匹配到有效归因映射"
            );
        }
        
        return AttributionResult.attributed(
                mapping.getUserId(),
                mapping.getDeptId(),
                mapping.getUserId(),
                talentUid,
                firstNonBlank(mapping.getActivityId(), activityId),
                colonelUserId,
                "pick_source 归因成功"
        );
    }

    protected ExclusiveOwner findExclusiveMerchantOwner(String merchantId) {
        return exclusiveMerchantService.findActiveOwnerByMerchantId(merchantId);
    }

    protected ExclusiveOwner findExclusiveTalentOwner(String talentUid) {
        return exclusiveTalentService.findActiveOwnerByTalentUid(talentUid);
    }

    protected PickSourceMapping findPickSourceMapping(String pickSource, String pickExtra) {
        if (StringUtils.hasText(pickSource)) {
            PickSourceMapping byPickSource = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                    .eq(PickSourceMapping::getPickSource, pickSource)
                    .eq(PickSourceMapping::getStatus, 1)
                    .last("limit 1"));
            if (byPickSource != null) {
                return byPickSource;
            }
        }
        if (StringUtils.hasText(pickExtra)) {
            String normalized = pickExtra.length() > 20 ? pickExtra.substring(pickExtra.length() - 20) : pickExtra;
            PickSourceMapping byShortId = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                    .eq(PickSourceMapping::getShortId, normalized)
                    .eq(PickSourceMapping::getStatus, 1)
                    .last("limit 1"));
            if (byShortId != null) {
                return byShortId;
            }
        }
        return null;
    }

    private String asString(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    public record ExclusiveOwner(UUID userId, UUID deptId) {
    }

    public record AttributionResult(
            UUID channelUserId,
            UUID deptId,
            UUID userId,
            String talentUid,
            String activityId,
            UUID colonelUserId,
            String attributionStatus,
            String attributionRemark) {

        public static AttributionResult attributed(
                UUID channelUserId,
                UUID deptId,
                UUID userId,
                String talentUid,
                String activityId,
                UUID colonelUserId,
                String remark) {
            return new AttributionResult(channelUserId, deptId, userId, talentUid, activityId, colonelUserId, "ATTRIBUTED", remark);
        }

        public static AttributionResult unattributed(
                String talentUid,
                String activityId,
                UUID colonelUserId,
                String remark) {
            return new AttributionResult(null, null, null, talentUid, activityId, colonelUserId, "UNATTRIBUTED", remark);
        }
    }
}
