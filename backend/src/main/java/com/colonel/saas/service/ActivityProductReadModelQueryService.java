package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.domain.product.application.ActivityProductViewAssembler;
import com.colonel.saas.domain.product.application.ActivityProductViewAssembler.DecisionSummary;
import com.colonel.saas.domain.product.application.ActivityProductViewAssembler.OrderSummary;
import com.colonel.saas.domain.product.application.ActivityProductViewAssembler.PromotionSummary;
import com.colonel.saas.domain.product.policy.ProductAuditSupplementPayload;
import com.colonel.saas.domain.product.policy.ProductDisplayPolicy;
import com.colonel.saas.domain.product.policy.ProductOperationDecisionPolicy;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.Merchant;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.PromotionLink;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.MerchantMapper;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.PromotionLinkMapper;
import com.colonel.saas.service.ProductBizStatusService;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 活动商品读侧查询与视图聚合服务。
 */
public class ActivityProductReadModelQueryService {

    private final ProductOperationStateMapper operationStateMapper;
    private final ProductOperationLogMapper operationLogMapper;
    private final PromotionLinkMapper promotionLinkMapper;
    private final ColonelsettlementOrderMapper orderMapper;
    private final MerchantMapper merchantMapper;
    private final ColonelsettlementActivityMapper colonelActivityMapper;
    private final UserDomainFacade userDomainFacade;
    private final ProductBizStatusService productBizStatusService;
    private final ProductOperationDecisionPolicy productOperationDecisionPolicy;
    private final ActivityProductViewAssembler activityProductViewAssembler;

    public ActivityProductReadModelQueryService(
            ProductOperationStateMapper operationStateMapper,
            ProductOperationLogMapper operationLogMapper,
            PromotionLinkMapper promotionLinkMapper,
            ColonelsettlementOrderMapper orderMapper,
            MerchantMapper merchantMapper,
            ColonelsettlementActivityMapper colonelActivityMapper,
            UserDomainFacade userDomainFacade,
            ProductBizStatusService productBizStatusService,
            ProductDisplayPolicy productDisplayPolicy,
            ProductOperationDecisionPolicy productOperationDecisionPolicy) {
        this.operationStateMapper = operationStateMapper;
        this.operationLogMapper = operationLogMapper;
        this.promotionLinkMapper = promotionLinkMapper;
        this.orderMapper = orderMapper;
        this.merchantMapper = merchantMapper;
        this.colonelActivityMapper = colonelActivityMapper;
        this.userDomainFacade = userDomainFacade;
        this.productBizStatusService = productBizStatusService;
        this.productOperationDecisionPolicy = productOperationDecisionPolicy;
        this.activityProductViewAssembler = new ActivityProductViewAssembler(productBizStatusService, productDisplayPolicy);
    }

    public Map<String, Object> buildRemoteListView(DouyinProductGateway.ActivityProductListResult result) {
        Map<String, Object> data = new LinkedHashMap<>(result.toMap());
        data.remove("test");
        List<DouyinProductGateway.ActivityProductItem> items = result.items();
        if (items == null || items.isEmpty()) {
            data.put("items", List.of());
            return data;
        }

        Set<String> productIds = items.stream()
                .map(item -> String.valueOf(item.productId()))
                .collect(Collectors.toCollection(HashSet::new));

        Map<String, ProductOperationState> stateMap = operationStateMapper.selectList(
                        new LambdaQueryWrapper<ProductOperationState>()
                                .eq(ProductOperationState::getActivityId, String.valueOf(result.activityId()))
                                .in(ProductOperationState::getProductId, productIds))
                .stream()
                .collect(Collectors.toMap(ProductOperationState::getProductId, Function.identity(), (left, right) -> left));
        Map<UUID, String> assigneeNameMap = loadUserDisplayNames(stateMap.values().stream()
                .map(ProductOperationState::getAssigneeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<String, DecisionSummary> decisionSummaryMap = buildDecisionSummaryMap(String.valueOf(result.activityId()), productIds);

        data.put("items", items.stream().map(item -> {
            Map<String, Object> view = new LinkedHashMap<>(item.toMap());
            view.put("relationId", buildSnapshotId(String.valueOf(result.activityId()), String.valueOf(item.productId())));
            ProductOperationState state = stateMap.get(String.valueOf(item.productId()));
            DecisionSummary decisionSummary = decisionSummaryMap.get(String.valueOf(item.productId()));
            if (state != null) {
                ProductBizStatus bizStatus = productBizStatusService.readBizStatus(state);
                view.put("bizStatus", bizStatus.name());
                view.put("bizStatusLabel", bizStatus.getLabel());
                view.put("boundActivityId", state.getBoundActivityId());
                view.put("assigneeId", state.getAssigneeId());
                view.put("assigneeName", resolveUserDisplayName(state.getAssigneeId(), assigneeNameMap));
                view.put("auditStatus", state.getAuditStatus());
                view.put("auditRemark", state.getAuditRemark());
                view.put("shortLink", state.getShortLink());
                view.put("promoteLink", state.getPromoteLink());
                activityProductViewAssembler.applyActivityProductStatusFields(view, item.status(), state);
            } else {
                ProductBizStatus bizStatus = ProductBizStatus.PENDING_AUDIT;
                view.put("bizStatus", bizStatus.name());
                view.put("bizStatusLabel", bizStatus.getLabel());
                activityProductViewAssembler.applyActivityProductStatusFields(view, item.status(), null);
            }
            activityProductViewAssembler.applyDecisionSummary(view, decisionSummary);
            return view;
        }).toList());
        return data;
    }

    public List<Map<String, Object>> buildSnapshotItems(String activityId, List<ProductSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        Set<String> productIds = snapshots.stream()
                .map(ProductSnapshot::getProductId)
                .collect(Collectors.toCollection(HashSet::new));
        Map<String, ProductOperationState> stateMap = operationStateMapper.selectList(
                        new LambdaQueryWrapper<ProductOperationState>()
                                .eq(ProductOperationState::getActivityId, activityId)
                                .in(ProductOperationState::getProductId, productIds))
                .stream()
                .collect(Collectors.toMap(ProductOperationState::getProductId, Function.identity(), (left, right) -> left));
        Map<String, DecisionSummary> decisionSummaryMap = buildDecisionSummaryMap(activityId, productIds);
        Map<String, OrderSummary> orderSummaryMap = buildOrderSummaryMap(activityId, productIds);
        Map<String, PromotionSummary> promotionSummaryMap = buildPromotionSummaryMap(activityId, productIds);
        Map<UUID, String> assigneeNameMap = loadUserDisplayNames(stateMap.values().stream()
                .map(ProductOperationState::getAssigneeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, Merchant> merchantMap = buildMerchantMap(snapshots.stream()
                .map(ProductSnapshot::getShopId)
                .collect(Collectors.toCollection(HashSet::new)));
        String activityName = findActivityName(activityId);
        return snapshots.stream()
                .map(snapshot -> toActivityProductView(
                        snapshot,
                        stateMap.get(snapshot.getProductId()),
                        decisionSummaryMap.get(snapshot.getProductId()),
                        orderSummaryMap.get(snapshot.getProductId()),
                        promotionSummaryMap.get(snapshot.getProductId()),
                        lookupMerchant(merchantMap, snapshot.getShopId()),
                        assigneeNameMap,
                        activityName))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public Map<String, Object> buildDetailBase(String activityId, String productId, ProductSnapshot snapshot, ProductOperationState state) {
        DecisionSummary decisionSummary = findDecisionSummary(activityId, productId);
        OrderSummary orderSummary = findOrderSummary(activityId, productId);
        PromotionSummary promotionSummary = findPromotionSummary(activityId, productId);
        Merchant merchant = findMerchant(snapshot.getShopId());
        String activityName = findActivityName(activityId);

        Map<String, Object> detail = toActivityProductView(
                snapshot, state, decisionSummary, orderSummary, promotionSummary, merchant, null, activityName);
        Map<String, Object> auditSupplement = ProductAuditSupplementPayload.parse(state == null ? null : state.getAuditPayload());
        if (state != null) {
            detail.put("promotionScene", state.getPromotionScene());
            detail.put("externalUniqueId", state.getExternalUniqueId());
            detail.put("lastOperationAt", state.getLastOperationAt());
        }
        detail.put("auditSupplement", auditSupplement);
        detail.put("promotionLinks", promotionSummary == null ? List.of() : promotionSummary.linkRecords());
        detail.put("promotionMaterialPack", buildPromotionMaterialPack(snapshot, state, merchant, auditSupplement));
        return detail;
    }

    public DecisionSummary findLatestDecisionSummary(String activityId, String productId) {
        return findDecisionSummary(activityId, productId);
    }

    public Map<String, Object> buildPromotionMaterialPack(
            ProductSnapshot snapshot,
            ProductOperationState state,
            Merchant merchant,
            Map<String, Object> auditSupplement) {
        Map<String, Object> pack = new LinkedHashMap<>();
        List<String> sellingPoints = ProductAuditSupplementPayload.readStringList(auditSupplement, "sellingPoints");
        if (sellingPoints.isEmpty()) {
            sellingPoints = new ArrayList<>();
            sellingPoints.add(snapshot.getTitle() + " 已进入活动商品库，可直接用于渠道选品。");
            if (StringUtils.hasText(snapshot.getActivityCosRatioText())) {
                sellingPoints.add("当前活动佣金率 " + snapshot.getActivityCosRatioText() + "，可直接作为达人沟通收益点。");
            }
            if (StringUtils.hasText(snapshot.getAdServiceRatio())) {
                sellingPoints.add("服务费率 " + activityProductViewAssembler.normalizePercentText(snapshot.getAdServiceRatio()) + "，方便预估单品收益。");
            }
            if (Boolean.TRUE.equals(snapshot.getHasDouinGoodsTag())) {
                sellingPoints.add("商品带有抖音商品标识，适合放入优先推广池。");
            }
        }

        String merchantName = merchant != null && StringUtils.hasText(merchant.getMerchantName())
                ? merchant.getMerchantName()
                : snapshot.getShopName();
        String commissionText = StringUtils.hasText(snapshot.getActivityCosRatioText())
                ? snapshot.getActivityCosRatioText()
                : activityProductViewAssembler.formatRate(activityProductViewAssembler.resolveCommissionRate(snapshot));
        String serviceFeeText = activityProductViewAssembler.formatRate(activityProductViewAssembler.resolveServiceFeeRate(snapshot));
        String promotionScript = ProductAuditSupplementPayload.readString(auditSupplement, "promotionScript");

        pack.put("sellingPoints", sellingPoints);
        pack.put("outreachScript", StringUtils.hasText(promotionScript)
                ? promotionScript
                : "你好，我这边是 " + safeText(merchantName, "品牌方")
                + " 的商品合作负责人。当前这款【" + safeText(snapshot.getTitle(), "活动商品")
                + "】已经进入团长活动商品库，佣金 " + commissionText
                + "，服务费率 " + serviceFeeText + "，如果你有短视频或直播档期，可以直接沟通合作。");
        pack.put("shortVideoScript", StringUtils.hasText(promotionScript)
                ? promotionScript
                : "短视频脚本建议：开场点出【" + safeText(snapshot.getTitle(), "活动商品")
                + "】核心使用场景，中段突出价格 " + safeText(snapshot.getPriceText(), "以页面为准")
                + " 和佣金优势，结尾引导点击专属推广链接下单。");
        pack.put("liveScript", "直播讲品建议：先讲痛点，再讲【" + safeText(snapshot.getTitle(), "活动商品")
                + "】卖点，补充价格 " + safeText(snapshot.getPriceText(), "以页面为准")
                + "、佣金 " + commissionText + "，并提醒使用专属链接下单。");
        pack.put("hasMaterial", StringUtils.hasText(snapshot.getTitle()) && StringUtils.hasText(snapshot.getDetailUrl()));
        pack.put("cover", snapshot.getCover());
        pack.put("detailUrl", snapshot.getDetailUrl());
        pack.put("promoteLink", state == null ? null : state.getPromoteLink());
        pack.put("shortLink", state == null ? null : state.getShortLink());
        pack.put("supportsAds", ProductAuditSupplementPayload.readBoolean(auditSupplement, "supportsAds"));
        String adsRule = ProductAuditSupplementPayload.readString(auditSupplement, "adsRule");
        if (StringUtils.hasText(adsRule)) {
            pack.put("adsRule", adsRule);
        }
        pack.put("materialFiles", ProductAuditSupplementPayload.readStringList(auditSupplement, "materialFiles"));
        return pack;
    }

    private Map<String, DecisionSummary> buildDecisionSummaryMap(String activityId, Set<String> productIds) {
        if (!StringUtils.hasText(activityId) || productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return operationLogMapper.selectList(new LambdaQueryWrapper<ProductOperationLog>()
                        .eq(ProductOperationLog::getActivityId, activityId)
                        .eq(ProductOperationLog::getOperationType, "DECISION")
                        .in(ProductOperationLog::getProductId, productIds)
                        .orderByDesc(ProductOperationLog::getCreateTime))
                .stream()
                .collect(Collectors.toMap(
                        ProductOperationLog::getProductId,
                        log -> {
                            Map<String, String> payload = parseOperationPayloadText(log.getOperationPayload());
                            String level = payload.get("decisionLevel");
                            return new DecisionSummary(
                                    level,
                                    payload.getOrDefault("decisionLabel", productOperationDecisionPolicy.decisionLabel(level)),
                                    normalizeFreeText(log.getOperationRemark()),
                                    log.getCreateTime() == null ? null : log.getCreateTime().toString()
                            );
                        },
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private DecisionSummary findDecisionSummary(String activityId, String productId) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(productId)) {
            return null;
        }
        ProductOperationLog log = operationLogMapper.selectOne(new LambdaQueryWrapper<ProductOperationLog>()
                .eq(ProductOperationLog::getActivityId, activityId)
                .eq(ProductOperationLog::getProductId, productId)
                .eq(ProductOperationLog::getOperationType, "DECISION")
                .orderByDesc(ProductOperationLog::getCreateTime)
                .last("limit 1"));
        if (log == null) {
            return null;
        }
        Map<String, String> payload = parseOperationPayloadText(log.getOperationPayload());
        String level = payload.get("decisionLevel");
        return new DecisionSummary(
                level,
                payload.getOrDefault("decisionLabel", productOperationDecisionPolicy.decisionLabel(level)),
                normalizeFreeText(log.getOperationRemark()),
                log.getCreateTime() == null ? null : log.getCreateTime().toString()
        );
    }

    private Map<String, String> parseOperationPayloadText(String raw) {
        String text = normalizeFreeText(raw);
        if (!StringUtils.hasText(text)) {
            return Map.of();
        }
        String trimmed = text.startsWith("{") && text.endsWith("}") ? text.substring(1, text.length() - 1) : text;
        Map<String, String> payload = new LinkedHashMap<>();
        for (String pair : trimmed.split(", ")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = pair.substring(0, index).trim();
            String value = pair.substring(index + 1).trim();
            if (StringUtils.hasText(key)) {
                payload.put(key, value);
            }
        }
        return payload;
    }

    private Map<String, OrderSummary> buildOrderSummaryMap(String activityId, Set<String> productIds) {
        if (!StringUtils.hasText(activityId) || productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<ColonelsettlementOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getActivityId, activityId)
                .in(ColonelsettlementOrder::getProductId, productIds)
                .orderByDesc(ColonelsettlementOrder::getCreateTime));
        if (orders == null || orders.isEmpty()) {
            return Map.of();
        }

        Map<String, MutableOrderSummary> mutableMap = new LinkedHashMap<>();
        for (ColonelsettlementOrder order : orders) {
            if (!StringUtils.hasText(order.getProductId())) {
                continue;
            }
            MutableOrderSummary summary = mutableMap.computeIfAbsent(order.getProductId(), key -> new MutableOrderSummary());
            addOrder(summary, order);
        }
        return mutableMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().freeze(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private OrderSummary findOrderSummary(String activityId, String productId) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(productId)) {
            return null;
        }
        List<ColonelsettlementOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getActivityId, activityId)
                .eq(ColonelsettlementOrder::getProductId, productId)
                .orderByDesc(ColonelsettlementOrder::getCreateTime));
        if (orders == null || orders.isEmpty()) {
            return null;
        }
        MutableOrderSummary summary = new MutableOrderSummary();
        for (ColonelsettlementOrder order : orders) {
            addOrder(summary, order);
        }
        return summary.freeze();
    }

    private void addOrder(MutableOrderSummary summary, ColonelsettlementOrder order) {
        summary.orderCount++;
        if ("ATTRIBUTED".equalsIgnoreCase(order.getAttributionStatus())) {
            summary.attributedCount++;
        } else {
            summary.unattributedCount++;
        }
        summary.gmvCent += safeLong(order.getOrderAmount());
        summary.serviceFeeCent += safeLong(order.getSettleColonelCommission());
        LocalDateTime candidateTime = order.getSettleTime() != null ? order.getSettleTime() : order.getCreateTime();
        if (candidateTime != null && (summary.lastOrderTime == null || candidateTime.isAfter(summary.lastOrderTime))) {
            summary.lastOrderTime = candidateTime;
        }
    }

    private Map<String, PromotionSummary> buildPromotionSummaryMap(String activityId, Set<String> productIds) {
        if (!StringUtils.hasText(activityId) || productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<PromotionLink> links = promotionLinkMapper.selectList(new LambdaQueryWrapper<PromotionLink>()
                .eq(PromotionLink::getActivityId, activityId)
                .in(PromotionLink::getProductId, productIds)
                .orderByDesc(PromotionLink::getCreatedAt));
        if (links == null || links.isEmpty()) {
            return Map.of();
        }
        Map<String, MutablePromotionSummary> mutableMap = new LinkedHashMap<>();
        for (PromotionLink link : links) {
            if (!StringUtils.hasText(link.getProductId())) {
                continue;
            }
            MutablePromotionSummary summary = mutableMap.computeIfAbsent(link.getProductId(), key -> new MutablePromotionSummary());
            addPromotionLink(summary, link);
        }
        return mutableMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().freeze(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private PromotionSummary findPromotionSummary(String activityId, String productId) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(productId)) {
            return null;
        }
        List<PromotionLink> links = promotionLinkMapper.selectList(new LambdaQueryWrapper<PromotionLink>()
                .eq(PromotionLink::getActivityId, activityId)
                .eq(PromotionLink::getProductId, productId)
                .orderByDesc(PromotionLink::getCreatedAt));
        if (links == null || links.isEmpty()) {
            return null;
        }
        MutablePromotionSummary summary = new MutablePromotionSummary();
        for (PromotionLink link : links) {
            addPromotionLink(summary, link);
        }
        return summary.freeze();
    }

    private void addPromotionLink(MutablePromotionSummary summary, PromotionLink link) {
        summary.linkCount++;
        if (link.getCreatedAt() != null && (summary.lastLinkTime == null || link.getCreatedAt().isAfter(summary.lastLinkTime))) {
            summary.lastLinkTime = link.getCreatedAt();
        }
        if (summary.linkRecords.size() < 10) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", link.getId());
            item.put("channelUserId", link.getChannelUserId());
            item.put("channelUserName", link.getChannelUserName());
            item.put("pickSource", link.getPickSource());
            item.put("promotionUrl", link.getPromotionUrl());
            item.put("promoteLink", link.getPromotionUrl());
            item.put("shortUrl", link.getShortUrl());
            item.put("shortLink", link.getShortUrl());
            item.put("linkStatus", link.getLinkStatus());
            item.put("createdAt", link.getCreatedAt());
            item.put("expireTime", link.getExpireTime());
            summary.linkRecords.add(item);
        }
    }

    private Map<Long, Merchant> buildMerchantMap(Set<Long> shopIds) {
        if (shopIds == null || shopIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> validShopIds = shopIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(HashSet::new));
        if (validShopIds.isEmpty()) {
            return Map.of();
        }
        return merchantMapper.selectList(new LambdaQueryWrapper<Merchant>()
                        .in(Merchant::getShopId, validShopIds))
                .stream()
                .filter(merchant -> merchant.getShopId() != null)
                .collect(Collectors.toMap(
                        Merchant::getShopId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Merchant lookupMerchant(Map<Long, Merchant> merchantMap, Long shopId) {
        if (shopId == null || merchantMap == null || merchantMap.isEmpty()) {
            return null;
        }
        return merchantMap.get(shopId);
    }

    private Merchant findMerchant(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return null;
        }
        return merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getShopId, shopId)
                .last("limit 1"));
    }

    private String findActivityName(String activityId) {
        ColonelsettlementActivity activity = colonelActivityMapper.selectByActivityId(activityId);
        return activity == null ? null : activity.getName();
    }

    private Map<UUID, String> loadUserDisplayNames(Set<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> displayLabels = userDomainFacade.loadUserDisplayLabelsByIds(userIds);
        return displayLabels == null ? Map.of() : displayLabels;
    }

    private Map<String, Object> toActivityProductView(
            ProductSnapshot snapshot,
            ProductOperationState state,
            DecisionSummary decisionSummary,
            OrderSummary orderSummary,
            PromotionSummary promotionSummary,
            Merchant merchant,
            Map<UUID, String> assigneeNameMap,
            String activityName) {
        return activityProductViewAssembler.toActivityProductView(
                snapshot, state, decisionSummary, orderSummary, promotionSummary, merchant,
                this::resolveUserDisplayName, assigneeNameMap, activityName);
    }

    private String resolveUserDisplayName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return resolveUserDisplayName(userId, null);
    }

    private String resolveUserDisplayName(UUID userId, Map<UUID, String> userDisplayNames) {
        if (userId == null) {
            return null;
        }
        if (userDisplayNames != null) {
            return userDisplayNames.get(userId);
        }
        Map<UUID, String> displayLabels = userDomainFacade.loadUserDisplayLabelsByIds(List.of(userId));
        if (displayLabels == null || displayLabels.isEmpty()) {
            return null;
        }
        return normalizeDisplayText(displayLabels.get(userId));
    }

    private UUID buildSnapshotId(String activityId, String productId) {
        return UUID.nameUUIDFromBytes((activityId + ":" + productId).getBytes(StandardCharsets.UTF_8));
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private String normalizeFreeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        return ("null".equalsIgnoreCase(text) || "undefined".equalsIgnoreCase(text)) ? "" : text;
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String normalizeDisplayText(String value) {
        return value == null ? null : value.trim();
    }

    private static final class MutableOrderSummary {
        private long orderCount;
        private long attributedCount;
        private long unattributedCount;
        private long gmvCent;
        private long serviceFeeCent;
        private LocalDateTime lastOrderTime;

        private OrderSummary freeze() {
            return new OrderSummary(orderCount, attributedCount, unattributedCount, gmvCent, serviceFeeCent, lastOrderTime);
        }
    }

    private static final class MutablePromotionSummary {
        private int linkCount;
        private LocalDateTime lastLinkTime;
        private final List<Map<String, Object>> linkRecords = new ArrayList<>();

        private PromotionSummary freeze() {
            linkRecords.sort(Comparator.comparing(
                    record -> (LocalDateTime) record.getOrDefault("createdAt", LocalDateTime.MIN),
                    Comparator.reverseOrder()
            ));
            return new PromotionSummary(linkCount, lastLinkTime, List.copyOf(linkRecords));
        }
    }
}
