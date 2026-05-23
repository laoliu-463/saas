package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AttributionService {

    public static final String STATUS_ATTRIBUTED = "ATTRIBUTED";
    public static final String STATUS_UNATTRIBUTED = "UNATTRIBUTED";
    public static final String REASON_ATTRIBUTED = "ATTRIBUTED";
    public static final String REASON_NO_PICK_SOURCE = "NO_PICK_SOURCE";
    public static final String REASON_MAPPING_NOT_FOUND = "MAPPING_NOT_FOUND";
    public static final String REASON_COLONEL_MAPPING_NOT_FOUND = "COLONEL_MAPPING_NOT_FOUND";
    public static final String REASON_COLONEL_MAPPING_AMBIGUOUS = "COLONEL_MAPPING_AMBIGUOUS";
    public static final String REASON_PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
    public static final String REASON_ACTIVITY_NOT_FOUND = "ACTIVITY_NOT_FOUND";
    public static final String REASON_CHANNEL_NOT_FOUND = "CHANNEL_NOT_FOUND";
    public static final String REASON_SYNC_FAILED = "SYNC_FAILED";
    public static final String REASON_COLONEL_ORDER_INFO = "COLONEL_ORDER_INFO";
    public static final String REASON_TALENT_CLAIM_OWNER_CONFLICT = "TALENT_CLAIM_OWNER_CONFLICT";

    private final PickSourceMappingMapper pickSourceMappingMapper;
    private final ProductOperationStateMapper operationStateMapper;
    private final TalentMapper talentMapper;
    private final TalentClaimMapper talentClaimMapper;
    private final ExclusiveTalentService exclusiveTalentService;
    private final ExclusiveMerchantService exclusiveMerchantService;
    private final boolean exclusiveEnabled;

    @Autowired
    public AttributionService(
            PickSourceMappingMapper pickSourceMappingMapper,
            ProductOperationStateMapper operationStateMapper,
            TalentMapper talentMapper,
            TalentClaimMapper talentClaimMapper,
            ExclusiveTalentService exclusiveTalentService,
            ExclusiveMerchantService exclusiveMerchantService,
            @Value("${exclusive.enabled:false}") boolean exclusiveEnabled) {
        this.pickSourceMappingMapper = pickSourceMappingMapper;
        this.operationStateMapper = operationStateMapper;
        this.talentMapper = talentMapper;
        this.talentClaimMapper = talentClaimMapper;
        this.exclusiveTalentService = exclusiveTalentService;
        this.exclusiveMerchantService = exclusiveMerchantService;
        this.exclusiveEnabled = exclusiveEnabled;
    }

    public AttributionService(
            PickSourceMappingMapper pickSourceMappingMapper,
            ProductOperationStateMapper operationStateMapper,
            TalentMapper talentMapper,
            TalentClaimMapper talentClaimMapper,
            ExclusiveTalentService exclusiveTalentService,
            ExclusiveMerchantService exclusiveMerchantService) {
        this(
                pickSourceMappingMapper,
                operationStateMapper,
                talentMapper,
                talentClaimMapper,
                exclusiveTalentService,
                exclusiveMerchantService,
                false
        );
    }

    public AttributionResult resolveAttribution(ColonelsettlementOrder order, java.util.Map<String, Object> source) {
        String activityId = firstNonBlank(
                asString(source.get("colonel_activity_id")),
                asString(source.get("activity_id")),
                order.getActivityId()
        );
        String productId = order.getProductId();
        String pickSource = order.getPickSource();
        String pickExtra = asString(source.get("pick_extra"));
        String talentUid = talentUid(source);
        UUID talentId = resolveTalentId(talentUid);

        if (!StringUtils.hasText(productId)) {
            return AttributionResult.unattributed(
                    talentId,
                    talentUid,
                    activityId,
                    null,
                    REASON_PRODUCT_NOT_FOUND,
                    NativeMappingTrace.none()
            );
        }

        UUID colonelUserId = null;
        boolean activityStateMissing = false;
        if (StringUtils.hasText(activityId) && StringUtils.hasText(productId)) {
            ProductOperationState state = operationStateMapper.selectOne(new LambdaQueryWrapper<ProductOperationState>()
                    .eq(ProductOperationState::getActivityId, activityId)
                    .eq(ProductOperationState::getProductId, productId)
                    .last("limit 1"));
            if (state != null) {
                colonelUserId = state.getAssigneeId();
            } else {
                activityStateMissing = true;
            }
        }

        String merchantId = firstNonBlank(
                asString(source.get("merchant_id")),
                asString(source.get("shop_id")),
                order.getShopId() == null ? null : String.valueOf(order.getShopId())
        );

        ExclusiveOwner merchantExclusiveOwner = findExclusiveMerchantOwner(merchantId);
        if (merchantExclusiveOwner != null) {
            return AttributionResult.attributed(
                    merchantExclusiveOwner.userId(),
                    merchantExclusiveOwner.deptId(),
                    merchantExclusiveOwner.userId(),
                    talentId,
                    null,
                    activityId,
                    colonelUserId,
                    REASON_ATTRIBUTED,
                    NativeMappingTrace.none()
            );
        }

        ExclusiveOwner exclusiveOwner = findExclusiveTalentOwner(talentUid);
        if (exclusiveOwner != null) {
            return AttributionResult.attributed(
                    exclusiveOwner.userId(),
                    exclusiveOwner.deptId(),
                    exclusiveOwner.userId(),
                    talentId,
                    talentUid,
                    activityId,
                    colonelUserId,
                    REASON_ATTRIBUTED,
                    NativeMappingTrace.none()
            );
        }

        String colonelsBuyinId = firstNonBlank(
                asString(source.get("colonel_buyin_id")),
                asString(source.get("colonelBuyinId"))
        );
        String secondColonelsBuyinId = firstNonBlank(
                asString(source.get("second_colonel_buyin_id")),
                asString(source.get("secondColonelBuyinId"))
        );
        String secondActivityId = firstNonBlank(
                asString(source.get("second_colonel_activity_id")),
                asString(source.get("secondColonelActivityId"))
        );
        if (StringUtils.hasText(colonelsBuyinId) || StringUtils.hasText(secondColonelsBuyinId)) {
            NativeColonelMappingResolution colonelResolution = resolveNativeColonelAttribution(
                    colonelsBuyinId,
                    activityId,
                    secondColonelsBuyinId,
                    secondActivityId,
                    productId
            );
            PickSourceMapping colonelMapping = colonelResolution.mapping();
            if (colonelMapping != null && colonelMapping.getUserId() != null) {
                return attributedWithClaimGuard(
                        colonelMapping.getUserId(),
                        colonelMapping.getDeptId(),
                        colonelMapping.getUserId(),
                        talentId,
                        talentUid,
                        firstNonBlank(colonelResolution.activityId(), colonelMapping.getActivityId(), activityId),
                        colonelUserId,
                        REASON_COLONEL_ORDER_INFO,
                        colonelResolution.trace()
                );
            }
            String reason = colonelResolution.reason();
            if (!StringUtils.hasText(reason)) {
                reason = REASON_COLONEL_MAPPING_NOT_FOUND;
            }
            if (colonelMapping != null && colonelMapping.getUserId() == null) {
                reason = REASON_CHANNEL_NOT_FOUND;
            }
            return AttributionResult.unattributed(
                    talentId,
                    talentUid,
                    firstNonBlank(colonelResolution.activityId(), activityId),
                    colonelUserId,
                    reason,
                    colonelResolution.trace()
            );
        }

        if (!StringUtils.hasText(pickSource) && !StringUtils.hasText(pickExtra)) {
            return AttributionResult.unattributed(
                    talentId,
                    talentUid,
                    activityId,
                    colonelUserId,
                    REASON_NO_PICK_SOURCE,
                    NativeMappingTrace.none()
            );
        }
        PickSourceMapping mapping = findPickSourceMapping(pickSource, pickExtra);
        if (mapping == null) {
            return AttributionResult.unattributed(
                    talentId,
                    talentUid,
                    activityId,
                    colonelUserId,
                    activityStateMissing ? REASON_ACTIVITY_NOT_FOUND : REASON_MAPPING_NOT_FOUND,
                    NativeMappingTrace.none()
            );
        }
        if (mapping.getUserId() == null) {
            return AttributionResult.unattributed(
                    talentId,
                    talentUid,
                    activityId,
                    colonelUserId,
                    REASON_CHANNEL_NOT_FOUND,
                    NativeMappingTrace.none()
            );
        }

        return attributedWithClaimGuard(
                mapping.getUserId(),
                mapping.getDeptId(),
                mapping.getUserId(),
                talentId,
                talentUid,
                firstNonBlank(mapping.getActivityId(), activityId),
                colonelUserId,
                REASON_ATTRIBUTED,
                NativeMappingTrace.none()
        );
    }

    protected ExclusiveOwner findExclusiveMerchantOwner(String merchantId) {
        if (!exclusiveEnabled) {
            return null;
        }
        return exclusiveMerchantService.findActiveOwnerByMerchantId(merchantId);
    }

    protected ExclusiveOwner findExclusiveTalentOwner(String talentUid) {
        if (!exclusiveEnabled) {
            return null;
        }
        return exclusiveTalentService.findActiveOwnerByTalentUid(talentUid);
    }

    private AttributionResult attributedWithClaimGuard(
            UUID channelUserId,
            UUID deptId,
            UUID userId,
            UUID talentId,
            String talentUid,
            String activityId,
            UUID colonelUserId,
            String remark,
            NativeMappingTrace trace) {
        if (hasTalentClaimOwnerConflict(talentId, userId)) {
            return AttributionResult.unattributed(
                    talentId,
                    talentUid,
                    activityId,
                    colonelUserId,
                    REASON_TALENT_CLAIM_OWNER_CONFLICT,
                    trace
            );
        }
        return AttributionResult.attributed(
                channelUserId,
                deptId,
                userId,
                talentId,
                talentUid,
                activityId,
                colonelUserId,
                remark,
                trace
        );
    }

    private boolean hasTalentClaimOwnerConflict(UUID talentId, UUID userId) {
        if (talentId == null || userId == null) {
            return false;
        }
        List<TalentClaim> activeClaims = talentClaimMapper.findActiveByTalentId(talentId);
        if (activeClaims == null || activeClaims.isEmpty()) {
            return false;
        }
        List<UUID> activeClaimUserIds = activeClaims.stream()
                .map(TalentClaim::getUserId)
                .filter(Objects::nonNull)
                .toList();
        return !activeClaimUserIds.isEmpty() && activeClaimUserIds.stream().noneMatch(userId::equals);
    }

    protected NativeColonelMappingResolution resolveNativeColonelAttribution(
            String firstColonelsBuyinId,
            String firstActivityId,
            String secondColonelsBuyinId,
            String secondActivityId,
            String productId) {
        boolean hasSecondActivity = StringUtils.hasText(secondActivityId);
        NativeColonelMappingResolution firstResolution = resolveNativeColonelOrderMapping(
                firstColonelsBuyinId,
                firstActivityId,
                productId,
                !hasSecondActivity
        );
        if (firstResolution.mapping() != null || REASON_COLONEL_MAPPING_AMBIGUOUS.equals(firstResolution.reason())) {
            return firstResolution;
        }
        if (hasSecondActivity || StringUtils.hasText(secondColonelsBuyinId)) {
            NativeColonelMappingResolution secondResolution = resolveNativeColonelOrderMapping(
                    secondColonelsBuyinId,
                    secondActivityId,
                    productId,
                    false
            );
            if (secondResolution.mapping() != null || REASON_COLONEL_MAPPING_AMBIGUOUS.equals(secondResolution.reason())) {
                return secondResolution;
            }
            if (hasSecondActivity) {
                return secondResolution;
            }
        }
        return firstResolution;
    }

    protected NativeColonelMappingResolution resolveNativeColonelOrderMapping(
            String colonelsBuyinId,
            String activityId,
            String productId,
            boolean allowGenericFallback) {
        if (!StringUtils.hasText(colonelsBuyinId)) {
            return new NativeColonelMappingResolution(null, REASON_COLONEL_MAPPING_NOT_FOUND, activityId, NativeMappingTrace.none());
        }
        if (StringUtils.hasText(activityId) && StringUtils.hasText(productId)) {
            NativeColonelMappingResolution exactNative = singleMappingWithReason(
                    pickSourceMappingMapper.selectList(new LambdaQueryWrapper<PickSourceMapping>()
                            .eq(PickSourceMapping::getColonelBuyinId, colonelsBuyinId)
                            .eq(PickSourceMapping::getActivityId, activityId)
                            .eq(PickSourceMapping::getProductId, productId)
                            .eq(PickSourceMapping::getSourceType, PickSourceMappingService.SOURCE_TYPE_NATIVE)
                            .eq(PickSourceMapping::getStatus, 1)
                            .orderByDesc(PickSourceMapping::getUpdateTime)),
                    activityId,
                    false,
                    true
            );
            if (exactNative.mapping() != null || REASON_COLONEL_MAPPING_AMBIGUOUS.equals(exactNative.reason())) {
                return exactNative;
            }

            NativeColonelMappingResolution exactActivityProduct = singleMappingWithReason(
                    pickSourceMappingMapper.selectList(new LambdaQueryWrapper<PickSourceMapping>()
                            .eq(PickSourceMapping::getActivityId, activityId)
                            .eq(PickSourceMapping::getProductId, productId)
                            .eq(PickSourceMapping::getSourceType, PickSourceMappingService.SOURCE_TYPE_NATIVE)
                            .eq(PickSourceMapping::getStatus, 1)
                            .orderByDesc(PickSourceMapping::getUpdateTime)),
                    activityId,
                    true,
                    false
            );
            if (exactActivityProduct.mapping() != null || REASON_COLONEL_MAPPING_AMBIGUOUS.equals(exactActivityProduct.reason())) {
                PickSourceMapping mapping = exactActivityProduct.mapping();
                if (mapping != null && !colonelsBuyinId.equals(mapping.getColonelBuyinId())) {
                    return new NativeColonelMappingResolution(
                            mapping,
                            exactActivityProduct.reason(),
                            firstNonBlank(activityId, mapping.getActivityId()),
                            exactActivityProduct.trace().withColonelBuyinIdMismatch(true)
                    );
                }
                return exactActivityProduct;
            }
        }

        if (!allowGenericFallback) {
            return new NativeColonelMappingResolution(null, REASON_COLONEL_MAPPING_NOT_FOUND, activityId, NativeMappingTrace.none());
        }

        return singleMappingWithReason(
                pickSourceMappingMapper.selectList(new LambdaQueryWrapper<PickSourceMapping>()
                        .eq(PickSourceMapping::getColonelBuyinId, colonelsBuyinId)
                        .eq(PickSourceMapping::getSourceType, PickSourceMappingService.SOURCE_TYPE_NATIVE)
                        .eq(PickSourceMapping::getStatus, 1)
                        .orderByDesc(PickSourceMapping::getUpdateTime)),
                activityId,
                false,
                true
        );
    }

    protected PickSourceMapping findPickSourceMappingByShortId(String colonelsBuyinId) {
        return resolveNativeColonelOrderMapping(colonelsBuyinId, null, null, true).mapping();
    }

    private NativeColonelMappingResolution singleMappingWithReason(
            List<PickSourceMapping> mappings,
            String activityId,
            boolean fallbackActivityProduct,
            boolean nativeKeyMatched) {
        if (mappings == null || mappings.isEmpty()) {
            return new NativeColonelMappingResolution(null, REASON_COLONEL_MAPPING_NOT_FOUND, activityId, NativeMappingTrace.none());
        }
        if (mappings.size() == 1) {
            PickSourceMapping mapping = mappings.get(0);
            NativeMappingTrace trace = new NativeMappingTrace(
                    nativeKeyMatched || fallbackActivityProduct,
                    false,
                    false,
                    fallbackActivityProduct,
                    mapping == null ? null : mapping.getCreateTime()
            );
            return new NativeColonelMappingResolution(mapping, REASON_COLONEL_ORDER_INFO, firstNonBlank(activityId, mapping.getActivityId()), trace);
        }
        return new NativeColonelMappingResolution(
                null,
                REASON_COLONEL_MAPPING_AMBIGUOUS,
                activityId,
                new NativeMappingTrace(nativeKeyMatched || fallbackActivityProduct, false, true, fallbackActivityProduct, null)
        );
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
            PickSourceMapping byPickExtra = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                    .eq(PickSourceMapping::getPickExtra, pickExtra)
                    .eq(PickSourceMapping::getStatus, 1)
                    .last("limit 1"));
            if (byPickExtra != null) {
                return byPickExtra;
            }
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

    private String talentUid(java.util.Map<String, Object> source) {
        return firstNonBlank(
                asString(source.get("talent_uid")),
                asString(source.get("author_id")),
                asString(source.get("author_buyin_id")),
                asString(source.get("authorBuyinId"))
        );
    }

    private UUID resolveTalentId(String talentUid) {
        if (!StringUtils.hasText(talentUid)) {
            return null;
        }
        com.colonel.saas.entity.Talent talent = talentMapper.selectOne(new LambdaQueryWrapper<com.colonel.saas.entity.Talent>()
                .eq(com.colonel.saas.entity.Talent::getDouyinUid, talentUid)
                .eq(com.colonel.saas.entity.Talent::getDeleted, 0)
                .last("limit 1"));
        return talent == null ? null : talent.getId();
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

    public record NativeColonelMappingResolution(
            PickSourceMapping mapping,
            String reason,
            String activityId,
            NativeMappingTrace trace) {
    }

    public record NativeMappingTrace(
            boolean nativeKeyMatched,
            boolean colonelBuyinIdMismatch,
            boolean ambiguousMapping,
            boolean usedActivityProductFallback,
            LocalDateTime mappingCreatedAt) {
        public static NativeMappingTrace none() {
            return new NativeMappingTrace(false, false, false, false, null);
        }

        public NativeMappingTrace withColonelBuyinIdMismatch(boolean mismatch) {
            return new NativeMappingTrace(nativeKeyMatched, mismatch, ambiguousMapping, usedActivityProductFallback, mappingCreatedAt);
        }
    }

    public record AttributionResult(
            UUID channelUserId,
            UUID deptId,
            UUID userId,
            UUID talentId,
            String talentUid,
            String activityId,
            UUID colonelUserId,
            String attributionStatus,
            String attributionRemark,
            NativeMappingTrace nativeTrace) {

        public static AttributionResult attributed(
                UUID channelUserId,
                UUID deptId,
                UUID userId,
                UUID talentId,
                String talentUid,
                String activityId,
                UUID colonelUserId,
                String remark) {
            return new AttributionResult(channelUserId, deptId, userId, talentId, talentUid, activityId, colonelUserId, STATUS_ATTRIBUTED, remark, NativeMappingTrace.none());
        }

        public static AttributionResult attributed(
                UUID channelUserId,
                UUID deptId,
                UUID userId,
                UUID talentId,
                String talentUid,
                String activityId,
                UUID colonelUserId,
                String remark,
                NativeMappingTrace nativeTrace) {
            return new AttributionResult(channelUserId, deptId, userId, talentId, talentUid, activityId, colonelUserId, STATUS_ATTRIBUTED, remark, nativeTrace == null ? NativeMappingTrace.none() : nativeTrace);
        }

        public static AttributionResult unattributed(
                UUID talentId,
                String talentUid,
                String activityId,
                UUID colonelUserId,
                String remark) {
            return new AttributionResult(null, null, null, talentId, talentUid, activityId, colonelUserId, STATUS_UNATTRIBUTED, remark, NativeMappingTrace.none());
        }

        public static AttributionResult unattributed(
                UUID talentId,
                String talentUid,
                String activityId,
                UUID colonelUserId,
                String remark,
                NativeMappingTrace nativeTrace) {
            return new AttributionResult(null, null, null, talentId, talentUid, activityId, colonelUserId, STATUS_UNATTRIBUTED, remark, nativeTrace == null ? NativeMappingTrace.none() : nativeTrace);
        }
    }
}
