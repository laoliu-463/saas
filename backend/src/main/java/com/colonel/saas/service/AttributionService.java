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

/**
 * 订单归属（Attribution）核心服务。
 *
 * <p>职责：根据订单中的 pick_source、colonel_order_info、exclusive 等信息，
 * 解析订单应该归属到哪个渠道用户（channelUserId）和部门（deptId）。
 *
 * <p>归属优先级（从高到低）：
 * <ol>
 *   <li>独家商家归属 —— 若商家已被某用户独占，直接归属</li>
 *   <li>独家达人归属 —— 若达人已被某用户独占，直接归属</li>
 *   <li>原生团长映射 —— 通过 colonel_order_info / colonel_buyin_id 查找 pick_source_mapping</li>
 *   <li>pick_source / pick_extra 映射 —— 通过转链映射查找</li>
 * </ol>
 *
 * <p>依赖服务/仓储：
 * <ul>
 *   <li>{@link PickSourceMappingMapper} —— 转链映射数据访问</li>
 *   <li>{@link ProductOperationStateMapper} —— 商品操作状态，用于查找活动负责人</li>
 *   <li>{@link TalentMapper} / {@link TalentClaimMapper} —— 达人信息及认领冲突校验</li>
 *   <li>{@link ExclusiveTalentService} —— 独家达人归属查询</li>
 *   <li>{@link ExclusiveMerchantService} —— 独家商家归属查询</li>
 * </ul>
 */
@Service
public class AttributionService {

    /** 归属状态：已归属 */
    public static final String STATUS_ATTRIBUTED = "ATTRIBUTED";
    /** 归属状态：未归属 */
    public static final String STATUS_UNATTRIBUTED = "UNATTRIBUTED";
    /** 归属原因：正常归属成功 */
    public static final String REASON_ATTRIBUTED = "ATTRIBUTED";
    /** 归属原因：订单缺少 pick_source 和 pick_extra */
    public static final String REASON_NO_PICK_SOURCE = "NO_PICK_SOURCE";
    /** 归属原因：通过 pick_source 未找到匹配映射 */
    public static final String REASON_MAPPING_NOT_FOUND = "MAPPING_NOT_FOUND";
    /** 归属原因：通过团长 buyin_id 未找到匹配映射 */
    public static final String REASON_COLONEL_MAPPING_NOT_FOUND = "COLONEL_MAPPING_NOT_FOUND";
    /** 归属原因：团长映射存在多条，归属结果不明确 */
    public static final String REASON_COLONEL_MAPPING_AMBIGUOUS = "COLONEL_MAPPING_AMBIGUOUS";
    /** 归属原因：订单中商品ID不存在 */
    public static final String REASON_PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
    /** 归属原因：活动操作状态记录缺失 */
    public static final String REASON_ACTIVITY_NOT_FOUND = "ACTIVITY_NOT_FOUND";
    /** 归属原因：映射记录存在但渠道用户ID为空 */
    public static final String REASON_CHANNEL_NOT_FOUND = "CHANNEL_NOT_FOUND";
    /** 归属原因：同步过程失败 */
    public static final String REASON_SYNC_FAILED = "SYNC_FAILED";
    /** 归属原因：通过原生团长订单信息（colonel_order_info）归属 */
    public static final String REASON_COLONEL_ORDER_INFO = "COLONEL_ORDER_INFO";
    /** 归属原因：达人已被其他人认领，存在归属冲突 */
    public static final String REASON_TALENT_CLAIM_OWNER_CONFLICT = "TALENT_CLAIM_OWNER_CONFLICT";

    private final PickSourceMappingMapper pickSourceMappingMapper;
    private final ProductOperationStateMapper operationStateMapper;
    private final TalentMapper talentMapper;
    private final TalentClaimMapper talentClaimMapper;
    private final ExclusiveTalentService exclusiveTalentService;
    private final ExclusiveMerchantService exclusiveMerchantService;
    private final boolean exclusiveEnabled;
    private final com.colonel.saas.domain.order.application.OrderAttributionRouter orderAttributionRouter;

    @Autowired
    public AttributionService(
            PickSourceMappingMapper pickSourceMappingMapper,
            ProductOperationStateMapper operationStateMapper,
            TalentMapper talentMapper,
            TalentClaimMapper talentClaimMapper,
            ExclusiveTalentService exclusiveTalentService,
            ExclusiveMerchantService exclusiveMerchantService,
            @Value("${exclusive.enabled:false}") boolean exclusiveEnabled,
            com.colonel.saas.domain.order.application.OrderAttributionRouter orderAttributionRouter) {
        this.pickSourceMappingMapper = pickSourceMappingMapper;
        this.operationStateMapper = operationStateMapper;
        this.talentMapper = talentMapper;
        this.talentClaimMapper = talentClaimMapper;
        this.exclusiveTalentService = exclusiveTalentService;
        this.exclusiveMerchantService = exclusiveMerchantService;
        this.exclusiveEnabled = exclusiveEnabled;
        this.orderAttributionRouter = orderAttributionRouter;
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
                false,
                null
        );
    }

    /**
     * 解析订单归属。
     *
     * <p>按优先级依次尝试：独家商家 -> 独家达人 -> 原生团长映射 -> pick_source/pick_extra 映射。</p>
     */
    public AttributionResult resolveAttribution(ColonelsettlementOrder order, java.util.Map<String, Object> source) {
        if (orderAttributionRouter != null) {
            return orderAttributionRouter.resolveAttribution(order, source, this::resolveLegacyAttribution);
        }
        return resolveLegacyAttribution(order, source);
    }

    public AttributionResult resolveLegacyAttribution(ColonelsettlementOrder order, java.util.Map<String, Object> source) {
        /* 从 source 和 order 中提取关键字段，按优先级取首个非空值 */
        String activityId = firstNonBlank(
                asString(source.get("colonel_activity_id")),
                asString(source.get("activity_id")),
                order.getActivityId()
        );
        String productId = order.getProductId();
        String pickSource = order.getPickSource();
        String pickExtra = asString(source.get("pick_extra"));
        /* 从多个可能的字段中提取达人UID */
        String talentUid = talentUid(source);
        /* 将达人UID解析为内部达人ID */
        UUID talentId = resolveTalentId(talentUid);

        /* 边界条件：商品ID为空则无法归属 */
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

        /* 查询活动-商品操作状态，获取该商品在该活动下的负责人（assigneeId） */
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

        /* 第一优先级：独家商家归属 */
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

        /* 第二优先级：独家达人归属 */
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

        /* 第三优先级：原生团长映射（通过 colonel_order_info 字段） */
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

        /* 第四优先级：pick_source / pick_extra 映射 */
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

    /**
     * 查找独家商家的拥有者。
     *
     * @param merchantId 商家ID
     * @return 独家归属信息（userId + deptId），若未启用独家或未找到则返回 null
     */
    protected ExclusiveOwner findExclusiveMerchantOwner(String merchantId) {
        if (!exclusiveEnabled) {
            return null;
        }
        return exclusiveMerchantService.findActiveOwnerByMerchantId(merchantId);
    }

    /**
     * 查找独家达人的拥有者。
     *
     * @param talentUid 达人抖音UID
     * @return 独家归属信息（userId + deptId），若未启用独家或未找到则返回 null
     */
    protected ExclusiveOwner findExclusiveTalentOwner(String talentUid) {
        if (!exclusiveEnabled) {
            return null;
        }
        return exclusiveTalentService.findActiveOwnerByTalentUid(talentUid);
    }

    /**
     * 带达人认领冲突保护的归属结果构建。
     * 若达人已被其他人认领（active claim），则返回未归属状态。
     *
     * @param channelUserId 渠道用户ID
     * @param deptId        部门ID
     * @param userId        归属用户ID
     * @param talentId      达人内部ID
     * @param talentUid     达人抖音UID
     * @param activityId    活动ID
     * @param colonelUserId 活动负责人ID
     * @param remark        归属原因说明
     * @param trace         原生映射追踪信息
     * @return 归属结果，若存在认领冲突则为未归属
     */
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

    /**
     * 检查达人是否存在认领冲突。
     * 当达人有其他用户的活跃认领记录（active claim），且该用户与当前归属用户不同时，判定为冲突。
     *
     * @param talentId 达人内部ID
     * @param userId   当前归属用户ID
     * @return true 表示存在冲突，归属应被阻止
     */
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

    /**
     * 解析原生团长归属映射（支持双团映射）。
     * 先尝试第一组团长信息；若失败再尝试第二组（second_colonel_*）。
     *
     * @param firstColonelsBuyinId  第一团长 buyin_id
     * @param firstActivityId       第一活动ID
     * @param secondColonelsBuyinId 第二团长 buyin_id
     * @param secondActivityId      第二活动ID
     * @param productId             商品ID
     * @return 原生映射解析结果，包含映射记录、原因、活动ID和追踪信息
     */
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

    /**
     * 解析单组团长订单映射。
     * 查找策略（按顺序）：
     * 1. 精确匹配：colonel_buyin_id + activity_id + product_id（source_type=NATIVE）
     * 2. 活动商品匹配：activity_id + product_id（source_type=NATIVE），若 colonel_buyin_id 不一致则标记
     * 3. 通用回退：仅 colonel_buyin_id（source_type=NATIVE），仅在 allowGenericFallback=true 时生效
     *
     * @param colonelsBuyinId      团长 buyin_id
     * @param activityId           活动ID
     * @param productId            商品ID
     * @param allowGenericFallback 是否允许仅按 buyin_id 做通用回退查找
     * @return 映射解析结果
     */
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

    /**
     * 通过 pick_source 或 pick_extra 查找转链映射。
     * 先尝试精确匹配 pickSource，再尝试 pickExtra 完整值，最后尝试 pickExtra 截取后20位匹配 shortId。
     *
     * @param pickSource 转链标识
     * @param pickExtra  转链附加信息
     * @return 匹配的映射记录，未找到返回 null
     */
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

    /** 独家归属拥有者信息 */
    public record ExclusiveOwner(UUID userId, UUID deptId) {
    }

    /** 原生团长映射解析结果 */
    public record NativeColonelMappingResolution(
            PickSourceMapping mapping,
            String reason,
            String activityId,
            NativeMappingTrace trace) {
    }

    /** 原生映射追踪信息，用于排查归属决策过程 */
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

    /**
     * 归属结果记录。
     * 包含归属状态（ATTRIBUTED / UNATTRIBUTED）、渠道用户信息、达人信息及归属原因。
     */
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
