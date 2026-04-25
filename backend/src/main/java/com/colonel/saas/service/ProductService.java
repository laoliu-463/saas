package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.enums.TalentFollowStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.TalentFollowRecord;
import com.colonel.saas.entity.PromotionLink;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.PromotionLinkMapper;
import com.colonel.saas.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
        ProductSnapshot snapshot = getSnapshotById(id);
        return generatePromotionLink(
                snapshot.getActivityId(),
                snapshot.getProductId(),
                userId,
                deptId,
                externalUniqueId,
                promotionScene,
                needShortLink
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

        List<Map<String, Object>> items = snapshots.stream()
                .map(snapshot -> toActivityProductView(snapshot, stateMap.get(snapshot.getProductId())))
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

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", snapshot.getId());
        detail.put("activityId", snapshot.getActivityId());
        detail.put("productId", snapshot.getProductId());
        detail.put("title", snapshot.getTitle());
        detail.put("cover", snapshot.getCover());
        detail.put("price", snapshot.getPrice());
        detail.put("priceText", snapshot.getPriceText());
        detail.put("shopId", snapshot.getShopId());
        detail.put("shopName", snapshot.getShopName());
        detail.put("status", snapshot.getStatus());
        detail.put("statusText", snapshot.getStatusText());
        detail.put("categoryName", snapshot.getCategoryName());
        detail.put("productStock", snapshot.getProductStock());
        detail.put("sales", snapshot.getSales());
        detail.put("detailUrl", snapshot.getDetailUrl());
        detail.put("promotionStartTime", snapshot.getPromotionStartTime());
        detail.put("promotionEndTime", snapshot.getPromotionEndTime());
        detail.put("activityCosRatio", snapshot.getActivityCosRatio());
        detail.put("activityCosRatioText", snapshot.getActivityCosRatioText());
        detail.put("cosType", snapshot.getCosType());
        detail.put("cosTypeText", snapshot.getCosTypeText());
        detail.put("adServiceRatio", snapshot.getAdServiceRatio());
        detail.put("activityAdCosRatio", snapshot.getActivityAdCosRatio());
        detail.put("hasDouinGoodsTag", snapshot.getHasDouinGoodsTag());
        detail.put("syncTime", snapshot.getSyncTime());

        if (state != null) {
            ProductBizStatus bizStatus = productBizStatusService.readBizStatus(state);
            detail.put("boundActivityId", state.getBoundActivityId());
            detail.put("bizStatus", bizStatus.name());
            detail.put("bizStatusLabel", bizStatus.getLabel());
            detail.put("assigneeId", state.getAssigneeId());
            detail.put("auditStatus", state.getAuditStatus());
            detail.put("auditRemark", state.getAuditRemark());
            detail.put("promoteLink", state.getPromoteLink());
            detail.put("shortLink", state.getShortLink());
            detail.put("promotionScene", state.getPromotionScene());
            detail.put("externalUniqueId", state.getExternalUniqueId());
            detail.put("lastOperationAt", state.getLastOperationAt());
        }
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
        ProductSnapshot snapshot = ensureSnapshotExists(activityId, productId);
        String finalExternalId = StringUtils.hasText(externalUniqueId) ? externalUniqueId : String.valueOf(userId);
        int finalPromotionScene = promotionScene == null ? 4 : promotionScene;
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
                                    snapshot.getDetailUrl()
                            )
                    )
            );

            // 1. 保存 PromotionLink
            SysUser user = sysUserMapper.selectById(userId);
            PromotionLink link = new PromotionLink();
            link.setId(UUID.randomUUID());
            link.setProductId(snapshot.getProductId());
            link.setActivityId(snapshot.getActivityId());
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
                    null, // talentId currently null for general links
                    null,
                    result.pickExtra(), // shortId
                    null,
                    result.pickSource(),
                    snapshot.getProductId(),
                    snapshot.getActivityId(),
                    snapshot.getDetailUrl(),
                    result.promoteLink(),
                    link.getId()
            );

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("promotionScene", finalPromotionScene);
            payload.put("needShortLink", needShortLink);
            payload.put("externalUniqueId", finalExternalId);
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

    private Map<String, Object> toActivityProductView(ProductSnapshot snapshot, ProductOperationState state) {
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
            view.put("auditStatus", state.getAuditStatus());
            view.put("auditRemark", state.getAuditRemark());
            view.put("shortLink", state.getShortLink());
            view.put("promoteLink", state.getPromoteLink());
        }
        return view;
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
}
