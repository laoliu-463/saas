package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.enums.TalentFollowStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.Merchant;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.TalentFollowRecord;
import com.colonel.saas.entity.PromotionLink;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.MerchantMapper;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.PromotionLinkMapper;
import com.colonel.saas.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final DouyinPromotionGateway douyinPromotionGateway;
    private final DouyinProductGateway douyinProductGateway;
    private final ProductSnapshotMapper snapshotMapper;
    private final ProductOperationStateMapper operationStateMapper;
    private final ProductOperationLogMapper operationLogMapper;
    private final PromotionLinkMapper promotionLinkMapper;
    private final ColonelsettlementOrderMapper orderMapper;
    private final MerchantMapper merchantMapper;
    private final SysUserMapper sysUserMapper;
    private final PickSourceMappingService pickSourceMappingService;
    private final ProductBizStatusService productBizStatusService;
    private final TalentFollowService talentFollowService;

    public ProductService(
            DouyinPromotionGateway douyinPromotionGateway,
            DouyinProductGateway douyinProductGateway,
            ProductSnapshotMapper snapshotMapper,
            ProductOperationStateMapper operationStateMapper,
            ProductOperationLogMapper operationLogMapper,
            PromotionLinkMapper promotionLinkMapper,
            ColonelsettlementOrderMapper orderMapper,
            MerchantMapper merchantMapper,
            SysUserMapper sysUserMapper,
            PickSourceMappingService pickSourceMappingService,
            ProductBizStatusService productBizStatusService,
            TalentFollowService talentFollowService) {
        this.douyinPromotionGateway = douyinPromotionGateway;
        this.douyinProductGateway = douyinProductGateway;
        this.snapshotMapper = snapshotMapper;
        this.operationStateMapper = operationStateMapper;
        this.operationLogMapper = operationLogMapper;
        this.promotionLinkMapper = promotionLinkMapper;
        this.orderMapper = orderMapper;
        this.merchantMapper = merchantMapper;
        this.sysUserMapper = sysUserMapper;
        this.pickSourceMappingService = pickSourceMappingService;
        this.productBizStatusService = productBizStatusService;
        this.talentFollowService = talentFollowService;
    }

    public IPage<Product> getPage(long page, long size, Integer status) {
        Page<ProductSnapshot> query = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<ProductSnapshot> wrapper = new LambdaQueryWrapper<ProductSnapshot>()
                .orderByDesc(ProductSnapshot::getSyncTime)
                .orderByDesc(ProductSnapshot::getCreateTime);
        if (status != null) {
            wrapper.eq(ProductSnapshot::getStatus, status);
        }
        IPage<ProductSnapshot> snapshotPage = snapshotMapper.selectPage(query, wrapper);

        Page<Product> result = new Page<>(snapshotPage.getCurrent(), snapshotPage.getSize());
        result.setTotal(snapshotPage.getTotal());
        result.setRecords(snapshotPage.getRecords().stream().map(this::toLegacyProduct).toList());
        return result;
    }

    public Product getById(UUID id) {
        ProductSnapshot snapshot = getSnapshotById(id);
        return toLegacyProduct(snapshot);
    }

    @Transactional(rollbackFor = Exception.class)
    public Product bindActivity(UUID id, UUID activityId) {
        ProductSnapshot snapshot = getSnapshotById(id);
        bindActivity(snapshot.getActivityId(), snapshot.getProductId(), activityId == null ? null : activityId.toString(), null, null);
        return getById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Product assignProduct(UUID id, UUID assigneeId) {
        ProductSnapshot snapshot = getSnapshotById(id);
        assignProduct(snapshot.getActivityId(), snapshot.getProductId(), assigneeId, null, null);
        return getById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Product auditProduct(UUID id, boolean approved, String reason) {
        ProductSnapshot snapshot = getSnapshotById(id);
        auditProduct(snapshot.getActivityId(), snapshot.getProductId(), approved, reason, null, null);
        return getById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public DouyinPromotionGateway.PromotionLinkResult generatePromotionLink(
            UUID id,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink) {
        return generatePromotionLink(id, userId, deptId, externalUniqueId, promotionScene, needShortLink, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public DouyinPromotionGateway.PromotionLinkResult generatePromotionLink(
            UUID id,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink,
            String scene,
            String talentId) {
        ProductSnapshot snapshot = getSnapshotById(id);
        return generatePromotionLink(
                snapshot.getActivityId(),
                snapshot.getProductId(),
                userId,
                deptId,
                externalUniqueId,
                promotionScene,
                needShortLink,
                scene,
                talentId
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void upsertSnapshots(String activityId, List<DouyinProductGateway.ActivityProductItem> items) {
        if (!StringUtils.hasText(activityId) || items == null || items.isEmpty()) {
            return;
        }
        for (DouyinProductGateway.ActivityProductItem item : items) {
            String productId = String.valueOf(item.productId());
            if (!StringUtils.hasText(productId)) {
                continue;
            }
            UUID snapshotId = buildSnapshotId(activityId, productId);
            ProductSnapshot snapshot = snapshotMapper.selectById(snapshotId);
            if (snapshot == null) {
                snapshot = new ProductSnapshot();
                snapshot.setId(snapshotId);
                snapshot.setActivityId(activityId);
                snapshot.setProductId(productId);
            }
            fillSnapshot(snapshot, item);
            if (snapshot.getCreateTime() == null) {
                snapshotMapper.insert(snapshot);
            } else {
                snapshotMapper.updateById(snapshot);
            }
            ProductOperationState existingState = getOperationState(activityId, productId);
            productBizStatusService.initStateIfAbsent(existingState, activityId, productId, null, null, "活动商品同步");
        }
    }

    public Map<String, Object> buildActivityProductListView(
            DouyinProductGateway.ActivityProductListResult result) {
        Map<String, Object> data = new LinkedHashMap<>(result.toMap());
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

        data.put("items", items.stream().map(item -> {
            Map<String, Object> view = new LinkedHashMap<>(item.toMap());
            ProductOperationState state = stateMap.get(String.valueOf(item.productId()));
            if (state != null) {
                ProductBizStatus bizStatus = productBizStatusService.readBizStatus(state);
                view.put("bizStatus", bizStatus.name());
                view.put("bizStatusLabel", bizStatus.getLabel());
                view.put("boundActivityId", state.getBoundActivityId());
                view.put("assigneeId", state.getAssigneeId());
                view.put("auditStatus", state.getAuditStatus());
                view.put("auditRemark", state.getAuditRemark());
                view.put("shortLink", state.getShortLink());
                view.put("promoteLink", state.getPromoteLink());
            } else {
                ProductBizStatus bizStatus = ProductBizStatus.PENDING_AUDIT;
                view.put("bizStatus", bizStatus.name());
                view.put("bizStatusLabel", bizStatus.getLabel());
            }
            return view;
        }).toList());
        return data;
    }

    public boolean hasActivitySnapshots(String activityId) {
        Long count = snapshotMapper.selectCount(new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getActivityId, activityId));
        return count != null && count > 0;
    }

    public Map<String, Object> buildActivityProductListViewFromDb(
            String activityId,
            Integer count,
            String cursor,
            String productInfo,
            String bizStatus) {
        int pageSize = Math.min(Math.max(count == null ? 20 : count, 1), 20);
        int offset = parseCursor(cursor);

        LambdaQueryWrapper<ProductSnapshot> countWrapper = new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getActivityId, activityId)
                .and(StringUtils.hasText(productInfo), w -> w.like(ProductSnapshot::getTitle, productInfo.trim())
                        .or()
                        .like(ProductSnapshot::getProductId, productInfo.trim()));
        Long total = snapshotMapper.selectCount(countWrapper);

        List<ProductSnapshot> snapshots = snapshotMapper.selectList(new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getActivityId, activityId)
                .and(StringUtils.hasText(productInfo), w -> w.like(ProductSnapshot::getTitle, productInfo.trim())
                        .or()
                        .like(ProductSnapshot::getProductId, productInfo.trim()))
                .orderByDesc(ProductSnapshot::getSyncTime)
                .orderByDesc(ProductSnapshot::getCreateTime)
                .last("limit " + pageSize + " offset " + offset));

        if (snapshots.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("mock", true);
            empty.put("activityId", activityId);
            empty.put("institutionId", 0);
            empty.put("total", total == null ? 0 : total);
            empty.put("nextCursor", "");
            empty.put("hasMore", false);
            empty.put("items", List.of());
            return empty;
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
        Map<String, OrderSummary> orderSummaryMap = buildOrderSummaryMap(activityId, productIds);
        Map<String, PromotionSummary> promotionSummaryMap = buildPromotionSummaryMap(activityId, productIds);
        Map<Long, Merchant> merchantMap = buildMerchantMap(snapshots.stream()
                .map(ProductSnapshot::getShopId)
                .collect(Collectors.toCollection(HashSet::new)));

        List<Map<String, Object>> items = snapshots.stream()
                .map(snapshot -> toActivityProductView(
                        snapshot,
                        stateMap.get(snapshot.getProductId()),
                        orderSummaryMap.get(snapshot.getProductId()),
                        promotionSummaryMap.get(snapshot.getProductId()),
                        merchantMap.get(snapshot.getShopId())))
                .filter(item -> !StringUtils.hasText(bizStatus) || bizStatus.equals(item.get("bizStatus")))
                .toList();

        int nextOffset = offset + snapshots.size();
        boolean hasMore = total != null && nextOffset < total;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mock", true);
        result.put("activityId", activityId);
        result.put("institutionId", 0);
        result.put("total", total == null ? items.size() : total);
        result.put("nextCursor", hasMore ? String.valueOf(nextOffset) : "");
        result.put("hasMore", hasMore);
        result.put("items", items);
        return result;
    }

    public Map<String, Object> getActivityProductDetail(String activityId, String productId) {
        ProductSnapshot snapshot = getSnapshot(activityId, productId);
        ProductOperationState state = getOperationState(activityId, productId);
        OrderSummary orderSummary = buildOrderSummaryMap(activityId, Set.of(productId)).get(productId);
        PromotionSummary promotionSummary = buildPromotionSummaryMap(activityId, Set.of(productId)).get(productId);
        Merchant merchant = buildMerchantMap(Set.of(snapshot.getShopId())).get(snapshot.getShopId());

        Map<String, Object> detail = toActivityProductView(snapshot, state, orderSummary, promotionSummary, merchant);
        if (state != null) {
            detail.put("promotionScene", state.getPromotionScene());
            detail.put("externalUniqueId", state.getExternalUniqueId());
            detail.put("lastOperationAt", state.getLastOperationAt());
        }
        detail.put("promotionLinks", promotionSummary == null ? List.of() : promotionSummary.linkRecords());
        detail.put("promotionMaterialPack", buildPromotionMaterialPack(snapshot, state, merchant));
        detail.put("followRecords", talentFollowService.listByProduct(activityId, productId));
        return detail;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bindActivity(
            String activityId,
            String productId,
            String boundActivityId,
            UUID operatorId,
            UUID operatorDeptId) {
        ensureSnapshotExists(activityId, productId);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("boundActivityId", boundActivityId);
        productBizStatusService.changeStatus(
                state,
                ProductBizStatus.BOUND,
                "BIND_ACTIVITY",
                operatorId,
                operatorDeptId,
                payload,
                "绑定活动成功",
                current -> current.setBoundActivityId(boundActivityId)
        );
        return getActivityProductDetail(activityId, productId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> assignProduct(
            String activityId,
            String productId,
            UUID assigneeId,
            UUID operatorId,
            UUID operatorDeptId) {
        if (assigneeId == null) {
            throw new BusinessException("assigneeId 不能为空");
        }
        ensureSnapshotExists(activityId, productId);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assigneeId", assigneeId);
        productBizStatusService.changeStatus(
                state,
                ProductBizStatus.ASSIGNED,
                "ASSIGN",
                operatorId,
                operatorDeptId,
                payload,
                "分配招商成功",
                current -> current.setAssigneeId(assigneeId)
        );
        return getActivityProductDetail(activityId, productId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> auditProduct(
            String activityId,
            String productId,
            boolean approved,
            String reason,
            UUID operatorId,
            UUID operatorDeptId) {
        if (!approved && !StringUtils.hasText(reason)) {
            throw new BusinessException("审核拒绝时必须填写原因");
        }
        ensureSnapshotExists(activityId, productId);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approved", approved);
        payload.put("reason", reason);
        productBizStatusService.changeStatus(
                state,
                approved ? ProductBizStatus.APPROVED : ProductBizStatus.REJECTED,
                "AUDIT",
                operatorId,
                operatorDeptId,
                payload,
                approved ? "审核通过" : "审核拒绝",
                current -> {
                    current.setAuditStatus(approved ? 2 : 3);
                    current.setAuditRemark(approved ? null : reason);
                }
        );
        return getActivityProductDetail(activityId, productId);
    }

    @Transactional(rollbackFor = Exception.class)
    public DouyinPromotionGateway.PromotionLinkResult generatePromotionLink(
            String activityId,
            String productId,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink) {
        return generatePromotionLink(activityId, productId, userId, deptId, externalUniqueId, promotionScene, needShortLink, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public DouyinPromotionGateway.PromotionLinkResult generatePromotionLink(
            String activityId,
            String productId,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink,
            String scene,
            String talentId) {
        ProductSnapshot snapshot = ensureSnapshotExists(activityId, productId);
        String finalExternalId = StringUtils.hasText(externalUniqueId) ? externalUniqueId : String.valueOf(userId);
        int finalPromotionScene = promotionScene == null ? 4 : promotionScene;
        String finalScene = normalizePromotionScene(scene);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        ProductBizStatus beforeStatus = productBizStatusService.readBizStatus(state);

        try {
            DouyinPromotionGateway.PromotionLinkResult result = douyinPromotionGateway.generateLink(
                    new DouyinPromotionGateway.PromotionLinkCommand(
                            finalExternalId,
                            finalPromotionScene,
                            List.of(snapshot.getProductId()),
                            needShortLink,
                            new DouyinPromotionGateway.PromotionContext(
                                    userId,
                                    deptId,
                                    snapshot.getProductId(),
                                    snapshot.getActivityId(),
                                    snapshot.getDetailUrl(),
                                    finalScene,
                                    talentId
                            )
                    )
            );

            // 1. 保存 PromotionLink
            SysUser user = sysUserMapper.selectById(userId);
            PromotionLink link = new PromotionLink();
            link.setId(UUID.randomUUID());
            link.setProductId(snapshot.getProductId());
            link.setActivityId(snapshot.getActivityId());
            link.setTalentId(talentId);
            link.setChannelUserId(userId);
            link.setChannelUserName(user != null ? user.getRealName() : "unknown");
            link.setOriginalProductUrl(snapshot.getDetailUrl());
            link.setPromotionUrl(result.promoteLink());
            link.setShortUrl(result.shortLink());
            link.setPickSource(result.pickSource());
            link.setPickExtra(result.pickExtra());
            link.setLinkStatus("ACTIVE");
            link.setOperatorId(userId);
            link.setOperatorName(user != null ? user.getRealName() : "system");
            link.setCreatedAt(LocalDateTime.now());
            link.setUpdatedAt(LocalDateTime.now());
            promotionLinkMapper.insert(link);

            // 2. 保存 PickSourceMapping (用于订单归因反查)
            pickSourceMappingService.saveOrUpdate(
                    userId,
                    user != null ? user.getRealName() : "unknown",
                    deptId,
                    talentId,
                    null,
                    result.pickExtra(), // shortId
                    null,
                    result.pickSource(),
                    snapshot.getProductId(),
                    snapshot.getActivityId(),
                    snapshot.getDetailUrl(),
                    result.promoteLink(),
                    link.getId(),
                    finalScene
            );

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("promotionScene", finalPromotionScene);
            payload.put("needShortLink", needShortLink);
            payload.put("externalUniqueId", finalExternalId);
            payload.put("scene", finalScene);
            payload.put("talentId", talentId);
            payload.put("shortLink", result.shortLink());
            payload.put("promoteLink", result.promoteLink());
            payload.put("pickSource", result.pickSource());
            productBizStatusService.changeStatus(
                    state,
                    ProductBizStatus.LINKED,
                    "PROMOTION_LINK",
                    userId,
                    deptId,
                    payload,
                    "转链成功",
                    current -> {
                        current.setPromoteLink(result.promoteLink());
                        current.setShortLink(result.shortLink());
                        current.setPromotionScene(finalPromotionScene);
                        current.setExternalUniqueId(finalExternalId);
                    }
            );
            return result;
        } catch (RuntimeException ex) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("promotionScene", finalPromotionScene);
            payload.put("needShortLink", needShortLink);
            payload.put("externalUniqueId", finalExternalId);
            payload.put("scene", finalScene);
            payload.put("talentId", talentId);
            productBizStatusService.logFailure(
                    activityId,
                    productId,
                    beforeStatus,
                    "PROMOTION_LINK",
                    userId,
                    deptId,
                    payload,
                    "转链失败",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    private String normalizePromotionScene(String scene) {
        if (!StringUtils.hasText(scene)) {
            return "PRODUCT_LIBRARY";
        }
        String normalized = scene.trim().toUpperCase();
        return switch (normalized) {
            case "PRODUCT_LIBRARY", "PRODUCT_DETAIL", "TALENT_SHARE", "SAMPLE_DESK" -> normalized;
            default -> "PRODUCT_LIBRARY";
        };
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> startTalentFollow(
            UUID id,
            UUID talentId,
            String talentName,
            String followStatus,
            String content,
            java.time.LocalDateTime nextFollowTime,
            UUID operatorId,
            String operatorName) {
        ProductSnapshot snapshot = getSnapshotById(id);
        return startTalentFollow(
                snapshot.getActivityId(),
                snapshot.getProductId(),
                talentId,
                talentName,
                followStatus,
                content,
                nextFollowTime,
                operatorId,
                operatorName
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> startTalentFollow(
            String activityId,
            String productId,
            UUID talentId,
            String talentName,
            String followStatus,
            String content,
            java.time.LocalDateTime nextFollowTime,
            UUID operatorId,
            String operatorName) {
        ensureSnapshotExists(activityId, productId);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        ProductBizStatus beforeStatus = productBizStatusService.readBizStatus(state);
        TalentFollowStatus normalizedStatus = normalizeFollowStatus(followStatus);

        TalentFollowRecord record = talentFollowService.createRecord(
                activityId,
                productId,
                talentId,
                talentName,
                normalizedStatus.name(),
                content,
                nextFollowTime,
                operatorId,
                operatorName
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("talentId", talentId);
        payload.put("talentName", talentName);
        payload.put("followStatus", normalizedStatus.name());
        payload.put("recordId", record.getId());
        if (beforeStatus == ProductBizStatus.FOLLOWING) {
            productBizStatusService.logFailure(
                    activityId,
                    productId,
                    beforeStatus,
                    "TALENT_FOLLOW_APPEND",
                    operatorId,
                    null,
                    payload,
                    "追加达人跟进记录",
                    null
            );
        } else {
            productBizStatusService.changeStatus(
                    state,
                    ProductBizStatus.FOLLOWING,
                    "TALENT_FOLLOW",
                    operatorId,
                    null,
                    payload,
                    "达人跟进创建成功",
                    current -> {
                    }
            );
        }
        return getActivityProductDetail(activityId, productId);
    }

    public IPage<ProductOperationLog> getOperationLogs(String activityId, String productId, long page, long size) {
        Page<ProductOperationLog> query = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<ProductOperationLog> wrapper = new LambdaQueryWrapper<ProductOperationLog>()
                .eq(ProductOperationLog::getActivityId, activityId)
                .eq(ProductOperationLog::getProductId, productId)
                .orderByDesc(ProductOperationLog::getCreateTime);
        return operationLogMapper.selectPage(query, wrapper);
    }

    private ProductSnapshot getSnapshotById(UUID id) {
        ProductSnapshot snapshot = snapshotMapper.selectById(id);
        if (snapshot == null) {
            throw new BusinessException("商品不存在");
        }
        return snapshot;
    }

    private ProductSnapshot ensureSnapshotExists(String activityId, String productId) {
        ProductSnapshot snapshot = getSnapshot(activityId, productId);
        if (snapshot == null) {
            throw new BusinessException("未找到商品快照，请先同步活动商品");
        }
        return snapshot;
    }

    private ProductSnapshot getSnapshot(String activityId, String productId) {
        return snapshotMapper.selectOne(new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getActivityId, activityId)
                .eq(ProductSnapshot::getProductId, productId)
                .last("limit 1"));
    }

    private ProductOperationState getOperationState(String activityId, String productId) {
        return operationStateMapper.selectOne(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getActivityId, activityId)
                .eq(ProductOperationState::getProductId, productId)
                .last("limit 1"));
    }

    private ProductOperationState getOrInitOperationState(String activityId, String productId) {
        ProductOperationState state = getOperationState(activityId, productId);
        if (state != null) {
            if (!StringUtils.hasText(state.getBizStatus())) {
                state.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());
            }
            return state;
        }
        state = new ProductOperationState();
        state.setActivityId(activityId);
        state.setProductId(productId);
        state.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());
        state.setAuditStatus(1);
        return state;
    }

    private void fillSnapshot(ProductSnapshot snapshot, DouyinProductGateway.ActivityProductItem item) {
        snapshot.setTitle(item.title());
        snapshot.setCover(item.cover());
        snapshot.setPrice(item.price());
        snapshot.setPriceText(item.priceText());
        snapshot.setShopId(item.shopId());
        snapshot.setShopName(item.shopName());
        snapshot.setStatus(item.status());
        snapshot.setStatusText(item.statusText());
        snapshot.setCategoryName(item.categoryName());
        snapshot.setProductStock(item.productStock());
        snapshot.setSales(item.sales());
        snapshot.setDetailUrl(item.detailUrl());
        snapshot.setPromotionStartTime(item.promotionStartTime());
        snapshot.setPromotionEndTime(item.promotionEndTime());
        snapshot.setActivityCosRatio(item.activityCosRatio());
        snapshot.setActivityCosRatioText(item.activityCosRatioText());
        snapshot.setCosType(item.cosType());
        snapshot.setCosTypeText(item.cosTypeText());
        snapshot.setAdServiceRatio(item.adServiceRatio());
        snapshot.setActivityAdCosRatio(item.activityAdCosRatio());
        snapshot.setHasDouinGoodsTag(item.hasDouinGoodsTag());
        snapshot.setRawPayload(String.valueOf(item.toMap()));
        snapshot.setSyncTime(java.time.LocalDateTime.now());
    }

    private Product toLegacyProduct(ProductSnapshot snapshot) {
        Product product = new Product();
        product.setId(snapshot.getId());
        product.setProductId(snapshot.getProductId());
        product.setName(snapshot.getTitle());
        product.setPrice(snapshot.getPrice());
        product.setStatus(snapshot.getStatus());
        product.setCategory(snapshot.getCategoryName());
        product.setActivityId(toUuid(snapshot.getActivityId()));
        product.setCreateTime(snapshot.getCreateTime());
        product.setUpdateTime(snapshot.getUpdateTime());

        ProductOperationState state = getOperationState(snapshot.getActivityId(), snapshot.getProductId());
        if (state != null) {
            product.setCheckStatus(state.getAuditStatus());
            product.setAuditRemark(state.getAuditRemark());
            product.setAssigneeId(state.getAssigneeId());
            product.setPromoteLink(state.getPromoteLink());
            product.setShortLink(state.getShortLink());
            ProductBizStatus bizStatus = productBizStatusService.readBizStatus(state);
            product.setBizStatus(bizStatus.name());
            product.setBizStatusLabel(bizStatus.getLabel());
        }
        return product;
    }

    private UUID buildSnapshotId(String activityId, String productId) {
        return UUID.nameUUIDFromBytes((activityId + ":" + productId).getBytes(StandardCharsets.UTF_8));
    }

    private UUID toUuid(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (Exception ignore) {
            return null;
        }
    }

    private Map<String, Object> toActivityProductView(
            ProductSnapshot snapshot,
            ProductOperationState state,
            OrderSummary orderSummary,
            PromotionSummary promotionSummary,
            Merchant merchant) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", snapshot.getId());
        view.put("activityId", snapshot.getActivityId());
        view.put("productId", snapshot.getProductId());
        view.put("title", snapshot.getTitle());
        view.put("cover", snapshot.getCover());
        view.put("price", snapshot.getPrice());
        view.put("priceText", snapshot.getPriceText());
        view.put("shopId", snapshot.getShopId());
        view.put("shopName", snapshot.getShopName());
        view.put("status", snapshot.getStatus());
        view.put("statusText", snapshot.getStatusText());
        view.put("categoryName", snapshot.getCategoryName());
        view.put("productStock", snapshot.getProductStock());
        view.put("sales", snapshot.getSales());
        view.put("detailUrl", snapshot.getDetailUrl());
        view.put("promotionStartTime", snapshot.getPromotionStartTime());
        view.put("promotionEndTime", snapshot.getPromotionEndTime());
        view.put("activityCosRatio", snapshot.getActivityCosRatio());
        view.put("activityCosRatioText", snapshot.getActivityCosRatioText());
        view.put("cosType", snapshot.getCosType());
        view.put("cosTypeText", snapshot.getCosTypeText());
        view.put("adServiceRatio", snapshot.getAdServiceRatio());
        view.put("activityAdCosRatio", snapshot.getActivityAdCosRatio());
        view.put("hasDouinGoodsTag", snapshot.getHasDouinGoodsTag());
        view.put("syncTime", snapshot.getSyncTime());

        ProductBizStatus currentStatus = state == null
                ? ProductBizStatus.PENDING_AUDIT
                : productBizStatusService.readBizStatus(state);
        view.put("bizStatus", currentStatus.name());
        view.put("bizStatusLabel", currentStatus.getLabel());

        if (state != null) {
            view.put("boundActivityId", state.getBoundActivityId());
            view.put("assigneeId", state.getAssigneeId());
            view.put("assigneeName", resolveUserDisplayName(state.getAssigneeId()));
            view.put("auditStatus", state.getAuditStatus());
            view.put("auditRemark", state.getAuditRemark());
            view.put("shortLink", state.getShortLink());
            view.put("promoteLink", state.getPromoteLink());
        }

        BigDecimal commissionRate = resolveCommissionRate(snapshot);
        BigDecimal serviceFeeRate = resolveServiceFeeRate(snapshot);
        BigDecimal estimatedCommission = estimateFee(snapshot.getPrice(), commissionRate);
        BigDecimal estimatedServiceFee = estimateFee(snapshot.getPrice(), serviceFeeRate);
        LocalDateTime promotionEndTime = parseDateTime(snapshot.getPromotionEndTime());
        long remainingDays = calculateRemainingDays(promotionEndTime);
        boolean activityExpired = promotionEndTime != null && promotionEndTime.isBefore(LocalDateTime.now());
        boolean promotionAvailable = !activityExpired && StringUtils.hasText(snapshot.getDetailUrl());
        boolean hasMaterial = StringUtils.hasText(snapshot.getTitle()) && StringUtils.hasText(snapshot.getDetailUrl());
        boolean hasSampleRule = !activityExpired && StringUtils.hasText(snapshot.getStatusText());
        long platformSales = snapshot.getSales() == null ? 0L : snapshot.getSales();
        long orderCount = orderSummary == null ? 0L : orderSummary.orderCount();
        long attributedCount = orderSummary == null ? 0L : orderSummary.attributedCount();
        long unattributedCount = orderSummary == null ? 0L : orderSummary.unattributedCount();
        BigDecimal gmv = orderSummary == null ? yuan(0L) : yuan(orderSummary.gmvCent());
        BigDecimal serviceFee = orderSummary == null ? yuan(0L) : yuan(orderSummary.serviceFeeCent());

        view.put("commissionRate", commissionRate);
        view.put("serviceFeeRate", serviceFeeRate);
        view.put("estimatedCommission", estimatedCommission.toPlainString());
        view.put("estimatedCommissionAmount", estimatedCommission.toPlainString());
        view.put("estimatedServiceFee", estimatedServiceFee.toPlainString());
        view.put("estimatedServiceFeeAmount", estimatedServiceFee.toPlainString());
        view.put("activityExpired", activityExpired);
        view.put("activityRemainingDays", Math.max(remainingDays, 0));
        view.put("timeLeft", formatTimeLeft(promotionEndTime));
        view.put("promotionAvailable", promotionAvailable);
        view.put("hasMaterial", hasMaterial);
        view.put("hasSampleRule", hasSampleRule);
        view.put("sales30d", platformSales);
        view.put("promotionLinkCount", promotionSummary == null ? 0 : promotionSummary.linkCount());
        view.put("orderCount", orderCount);
        view.put("attributedCount", attributedCount);
        view.put("attributedOrderCount", attributedCount);
        view.put("unattributedCount", unattributedCount);
        view.put("unattributedOrderCount", unattributedCount);
        view.put("gmv", gmv.toPlainString());
        view.put("gmv30d", gmv.toPlainString());
        view.put("serviceFee", serviceFee.toPlainString());
        view.put("lastOrderTime", orderSummary == null ? null : orderSummary.lastOrderTime());
        view.put("attributionRate", formatRate(orderCount == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(attributedCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP)));

        if (merchant != null) {
            view.put("merchantId", merchant.getMerchantId());
            view.put("merchantName", merchant.getMerchantName());
            view.put("merchantShopName", merchant.getShopName());
            view.put("merchantStatus", merchant.getStatus());
        } else {
            view.put("merchantId", snapshot.getShopId() == null ? null : String.valueOf(snapshot.getShopId()));
            view.put("merchantName", snapshot.getShopName());
            view.put("merchantShopName", snapshot.getShopName());
            view.put("merchantStatus", null);
        }

        PromotionView promotionView = buildPromotionView(currentStatus, state, promotionSummary);
        view.put("promotionLinkStatus", promotionView.status());
        view.put("promotionLinkStatusLabel", promotionView.statusLabel());
        view.put("promotionLink", promotionView.link());
        view.put("promotionLinkGeneratedAt", promotionView.generatedAt());
        view.put("promotionLinkExpireAt", promotionView.expireAt());
        view.put("promotionLinkFailReason", promotionView.failReason());
        Map<String, Object> promotion = new LinkedHashMap<>();
        promotion.put("status", promotionView.status());
        promotion.put("statusLabel", promotionView.statusLabel());
        promotion.put("link", promotionView.link());
        promotion.put("generatedAt", promotionView.generatedAt());
        promotion.put("expireAt", promotionView.expireAt());
        promotion.put("failReason", promotionView.failReason());
        promotion.put("copyEnabled", StringUtils.hasText(promotionView.link()));
        view.put("promotion", promotion);

        view.put("systemTags", buildSystemTags(snapshot, state, commissionRate, serviceFeeRate, platformSales, promotionSummary, activityExpired, remainingDays));
        view.put("alertTags", buildAlertTags(snapshot, state, commissionRate, serviceFeeRate, activityExpired));
        return view;
    }

    private String resolveUserDisplayName(UUID userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        String realName = StringUtils.trimWhitespace(user.getRealName());
        String username = StringUtils.trimWhitespace(user.getUsername());
        if (StringUtils.hasText(realName) && StringUtils.hasText(username)) {
            return realName + " (" + username + ")";
        }
        if (StringUtils.hasText(realName)) {
            return realName;
        }
        if (StringUtils.hasText(username)) {
            return username;
        }
        return null;
    }

    private int parseCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(cursor.trim()), 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private TalentFollowStatus normalizeFollowStatus(String rawStatus) {
        TalentFollowStatus status = TalentFollowStatus.fromCode(rawStatus);
        return status == null ? TalentFollowStatus.NOT_CONTACTED : status;
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
        return mutableMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().freeze(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
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
        return mutableMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().freeze(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
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

    private Map<String, Object> buildPromotionMaterialPack(ProductSnapshot snapshot, ProductOperationState state, Merchant merchant) {
        Map<String, Object> pack = new LinkedHashMap<>();
        List<String> sellingPoints = new ArrayList<>();
        sellingPoints.add(snapshot.getTitle() + " 已进入活动商品库，可直接用于渠道选品。");
        if (StringUtils.hasText(snapshot.getActivityCosRatioText())) {
            sellingPoints.add("当前活动佣金率 " + snapshot.getActivityCosRatioText() + "，可直接作为达人沟通收益点。");
        }
        if (StringUtils.hasText(snapshot.getAdServiceRatio())) {
            sellingPoints.add("服务费率 " + normalizePercentText(snapshot.getAdServiceRatio()) + "，方便预估单品收益。");
        }
        if (Boolean.TRUE.equals(snapshot.getHasDouinGoodsTag())) {
            sellingPoints.add("商品带有抖音商品标识，适合放入优先推广池。");
        }

        String merchantName = merchant != null && StringUtils.hasText(merchant.getMerchantName())
                ? merchant.getMerchantName()
                : snapshot.getShopName();
        String commissionText = StringUtils.hasText(snapshot.getActivityCosRatioText())
                ? snapshot.getActivityCosRatioText()
                : formatRate(resolveCommissionRate(snapshot));
        String serviceFeeText = formatRate(resolveServiceFeeRate(snapshot));

        pack.put("sellingPoints", sellingPoints);
        pack.put("outreachScript", "你好，我这边是 " + safeText(merchantName, "品牌方")
                + " 的商品合作负责人。当前这款【" + safeText(snapshot.getTitle(), "活动商品")
                + "】已经进入团长活动商品库，佣金 " + commissionText
                + "，服务费率 " + serviceFeeText + "，如果你有短视频或直播档期，可以直接沟通合作。");
        pack.put("shortVideoScript", "短视频脚本建议：开场点出【" + safeText(snapshot.getTitle(), "活动商品")
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
        return pack;
    }

    private PromotionView buildPromotionView(
            ProductBizStatus currentStatus,
            ProductOperationState state,
            PromotionSummary promotionSummary) {
        String link = state == null ? null : state.getPromoteLink();
        String generatedAt = promotionSummary == null || promotionSummary.lastLinkTime() == null
                ? null
                : promotionSummary.lastLinkTime().toString();
        String expireAt = null;
        if (StringUtils.hasText(link)) {
            return new PromotionView("READY", "已生成", link, generatedAt, expireAt, null);
        }
        if (currentStatus == ProductBizStatus.LINKED || currentStatus == ProductBizStatus.FOLLOWING) {
            return new PromotionView("FAILED", "生成失败", null, generatedAt, expireAt, "推广链接缺失，请后台重试");
        }
        if (currentStatus == ProductBizStatus.ASSIGNED) {
            return new PromotionView("PENDING", "生成中", null, generatedAt, expireAt, null);
        }
        return new PromotionView("PENDING", "未生成", null, generatedAt, expireAt, null);
    }

    private List<String> buildSystemTags(
            ProductSnapshot snapshot,
            ProductOperationState state,
            BigDecimal commissionRate,
            BigDecimal serviceFeeRate,
            long platformSales,
            PromotionSummary promotionSummary,
            boolean activityExpired,
            long remainingDays) {
        List<String> tags = new ArrayList<>();
        if (commissionRate.compareTo(BigDecimal.valueOf(20)) >= 0) {
            tags.add("高佣");
        }
        if (serviceFeeRate.compareTo(BigDecimal.TEN) >= 0) {
            tags.add("高服务费");
        }
        if (platformSales >= 1_000) {
            tags.add("高销量");
        }
        if (Boolean.TRUE.equals(snapshot.getHasDouinGoodsTag())) {
            tags.add("抖音商品标");
        }
        if (!activityExpired && remainingDays >= 0 && remainingDays <= 3) {
            tags.add("活动临期");
        }
        if (state != null && StringUtils.hasText(state.getPromoteLink())) {
            tags.add("已转链");
        }
        if (promotionSummary != null && promotionSummary.linkCount() > 0) {
            tags.add("已有推广记录");
        }
        return tags;
    }

    private List<String> buildAlertTags(
            ProductSnapshot snapshot,
            ProductOperationState state,
            BigDecimal commissionRate,
            BigDecimal serviceFeeRate,
            boolean activityExpired) {
        List<String> tags = new ArrayList<>();
        if (!StringUtils.hasText(snapshot.getDetailUrl())) {
            tags.add("无商品链接");
        }
        if (activityExpired) {
            tags.add("活动过期");
        }
        Integer stock = parseInteger(snapshot.getProductStock());
        if (stock != null && stock <= 10) {
            tags.add("库存不足");
        }
        if (commissionRate.compareTo(BigDecimal.ZERO) <= 0) {
            tags.add("佣金异常");
        }
        if (serviceFeeRate.compareTo(BigDecimal.ZERO) <= 0) {
            tags.add("服务费异常");
        }
        if (state == null || state.getAssigneeId() == null) {
            tags.add("未分配负责人");
        }
        return tags;
    }

    private BigDecimal resolveCommissionRate(ProductSnapshot snapshot) {
        BigDecimal fromText = parsePercentValue(snapshot.getActivityCosRatioText());
        if (fromText.compareTo(BigDecimal.ZERO) > 0) {
            return fromText;
        }
        return normalizeRatioNumber(snapshot.getActivityCosRatio());
    }

    private BigDecimal resolveServiceFeeRate(ProductSnapshot snapshot) {
        BigDecimal rate = parsePercentValue(snapshot.getAdServiceRatio());
        if (rate.compareTo(BigDecimal.ZERO) > 0) {
            return rate;
        }
        return normalizeRatioNumber(snapshot.getActivityAdCosRatio());
    }

    private BigDecimal normalizeRatioNumber(Long raw) {
        if (raw == null || raw <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = BigDecimal.valueOf(raw);
        if (raw >= 1000) {
            return value.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parsePercentValue(String raw) {
        if (!StringUtils.hasText(raw)) {
            return BigDecimal.ZERO;
        }
        String normalized = raw.trim()
                .replace("%", "")
                .replace("％", "")
                .replace(",", "")
                .replace(" ", "");
        try {
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal estimateFee(Long priceCent, BigDecimal ratePercent) {
        if (priceCent == null || priceCent <= 0 || ratePercent == null || ratePercent.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(priceCent)
                .multiply(ratePercent)
                .divide(BigDecimal.valueOf(10000), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal yuan(Long cent) {
        long value = cent == null ? 0L : cent;
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private Integer parseInteger(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (!StringUtils.hasText(digits)) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.matches("^\\d{13}$")) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(value)), ZoneId.systemDefault());
        }
        if (value.matches("^\\d{10}$")) {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(value)), ZoneId.systemDefault());
        }
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                if (formatter == formatters.get(formatters.size() - 1)) {
                    return LocalDate.parse(value, formatter).atStartOfDay();
                }
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignore) {
                // try next
            }
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay();
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }

    private long calculateRemainingDays(LocalDateTime endTime) {
        if (endTime == null) {
            return -1;
        }
        long days = java.time.Duration.between(LocalDateTime.now(), endTime).toDays();
        if (days < 0) {
            return 0;
        }
        return days;
    }

    private String formatTimeLeft(LocalDateTime endTime) {
        if (endTime == null) {
            return "长期";
        }
        LocalDateTime now = LocalDateTime.now();
        if (endTime.isBefore(now)) {
            return "已结束";
        }
        java.time.Duration duration = java.time.Duration.between(now, endTime);
        long days = duration.toDays();
        long hours = duration.minusDays(days).toHours();
        if (days > 0) {
            return days + "天 " + hours + "小时";
        }
        long minutes = duration.minusHours(duration.toHours()).toMinutes();
        if (hours > 0) {
            return hours + "小时 " + minutes + "分钟";
        }
        return Math.max(minutes, 1) + "分钟";
    }

    private String formatRate(BigDecimal rate) {
        BigDecimal value = rate == null ? BigDecimal.ZERO : rate.setScale(2, RoundingMode.HALF_UP);
        return value.stripTrailingZeros().toPlainString() + "%";
    }

    private String normalizePercentText(String raw) {
        BigDecimal value = parsePercentValue(raw);
        return formatRate(value);
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
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

    private record OrderSummary(
            long orderCount,
            long attributedCount,
            long unattributedCount,
            long gmvCent,
            long serviceFeeCent,
            LocalDateTime lastOrderTime) {
    }

    private record PromotionSummary(
            int linkCount,
            LocalDateTime lastLinkTime,
            List<Map<String, Object>> linkRecords) {
    }

    private record PromotionView(
            String status,
            String statusLabel,
            String link,
            String generatedAt,
            String expireAt,
            String failReason) {
    }
}
