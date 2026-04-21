package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class AttributionService {

    private final PickSourceMappingMapper pickSourceMappingMapper;
    private final ExclusiveTalentService exclusiveTalentService;
    private final ExclusiveMerchantService exclusiveMerchantService;

    public AttributionService(
            PickSourceMappingMapper pickSourceMappingMapper,
            ExclusiveTalentService exclusiveTalentService,
            ExclusiveMerchantService exclusiveMerchantService) {
        this.pickSourceMappingMapper = pickSourceMappingMapper;
        this.exclusiveTalentService = exclusiveTalentService;
        this.exclusiveMerchantService = exclusiveMerchantService;
    }

    public AttributionResult resolveAttribution(ColonelsettlementOrder order, java.util.Map<String, Object> source) {
        String merchantId = firstNonBlank(
                asString(source.get("merchant_id")),
                asString(source.get("shop_id")),
                order.getShopId() == null ? null : String.valueOf(order.getShopId())
        );
        ExclusiveOwner merchantExclusiveOwner = findExclusiveMerchantOwner(merchantId);
        if (merchantExclusiveOwner != null) {
            return new AttributionResult(
                    merchantExclusiveOwner.userId(),
                    merchantExclusiveOwner.deptId(),
                    merchantExclusiveOwner.userId(),
                    null);
        }

        String talentUid = firstNonBlank(asString(source.get("talent_uid")), asString(source.get("author_id")));
        ExclusiveOwner exclusiveOwner = findExclusiveTalentOwner(talentUid);
        if (exclusiveOwner != null) {
            return new AttributionResult(exclusiveOwner.userId(), exclusiveOwner.deptId(), exclusiveOwner.userId(), talentUid);
        }

        PickSourceMapping mapping = findPickSourceMapping(order.getPickSource(), asString(source.get("pick_extra")));
        if (mapping == null || mapping.getUserId() == null) {
            throw new BusinessException("归因失败：pick_source 未找到对应渠道");
        }
        return new AttributionResult(mapping.getUserId(), mapping.getDeptId(), mapping.getUserId(), talentUid);
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

    public record AttributionResult(UUID channelUserId, UUID deptId, UUID userId, String talentUid) {
    }
}
