package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.enums.TalentFollowStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.Merchant;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.TalentFollowRecord;
import com.colonel.saas.entity.PromotionLink;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.MerchantMapper;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.PromotionLinkMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long SELECTED_LIBRARY_BATCH_SIZE = 200L;
    private static final Pattern BUYIN_ID_PATTERN = Pattern.compile("(?:origin_colonel_buyin_id|originColonelBuyinId|colonel_buyin_id|colonelBuyinId)\\s*[=:]\\s*['\\\"]?([0-9]{10,})");

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
    private final ColonelsettlementActivityMapper colonelActivityMapper;
    private final TalentFollowService talentFollowService;
    private final DouyinActivityGateway douyinActivityGateway;

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
            ColonelsettlementActivityMapper colonelActivityMapper,
            TalentFollowService talentFollowService,
            DouyinActivityGateway douyinActivityGateway) {
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
        this.colonelActivityMapper = colonelActivityMapper;
        this.talentFollowService = talentFollowService;
        this.douyinActivityGateway = douyinActivityGateway;
    }

    public IPage<Product> getPage(long page, long size, Integer status) {
        return getPage(page, size, status, null);
    }

    public IPage<Product> getPage(long page, long size, Integer status, UUID assigneeId) {
        if (assigneeId != null) {
            return getAssignedPickPage(page, size, assigneeId);
        }
        Page<ProductSnapshot> query = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<ProductSnapshot> wrapper = new LambdaQueryWrapper<ProductSnapshot>()
                .orderByDesc(ProductSnapshot::getSyncTime)
                .orderByDesc(ProductSnapshot::getCreateTime);
        if (status != null) {
            wrapper.eq(ProductSnapshot::getStatus, status);
        }
        IPage<ProductSnapshot> snapshotPage = snapshotMapper.selectPage(query, wrapper);
        List<ProductSnapshot> snapshots = snapshotPage.getRecords();
        Map<String, ProductOperationState> stateMap = loadOperationStatesForSnapshots(snapshots);
        Map<UUID, String> assigneeNameMap = loadUserDisplayNames(stateMap.values().stream()
                .map(ProductOperationState::getAssigneeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        Page<Product> result = new Page<>(snapshotPage.getCurrent(), snapshotPage.getSize());
        result.setTotal(snapshotPage.getTotal());
        result.setRecords(snapshots.stream()
                .map(snapshot -> toLegacyProduct(
                        snapshot,
                        stateMap.get(stateBatchKey(snapshot.getActivityId(), snapshot.getProductId())),
                        assigneeNameMap))
                .toList());
        return result;
    }

    private IPage<Product> getAssignedPickPage(long page, long size, UUID assigneeId) {
        Page<ProductOperationState> query = new Page<>(Math.max(page, 1), Math.max(size, 1));
        IPage<ProductOperationState> statePage = operationStateMapper.selectPage(
                query,
                new LambdaQueryWrapper<ProductOperationState>()
                        .eq(ProductOperationState::getAssigneeId, assigneeId)
                        .orderByDesc(ProductOperationState::getUpdateTime)
                        .orderByDesc(ProductOperationState::getCreateTime)
        );
        List<ProductOperationState> states = statePage.getRecords();
        Map<String, ProductSnapshot> snapshotMap = loadSnapshotsForStateBatch(states);
        Map<UUID, String> assigneeNameMap = loadUserDisplayNames(Set.of(assigneeId));

        Page<Product> result = new Page<>(statePage.getCurrent(), statePage.getSize());
        result.setTotal(statePage.getTotal());
        result.setRecords(states.stream()
                .map(state -> {
                    ProductSnapshot snapshot = snapshotMap.get(stateBatchKey(state.getActivityId(), state.getProductId()));
                    return snapshot == null ? null : toLegacyProduct(snapshot, state, assigneeNameMap);
                })
                .filter(java.util.Objects::nonNull)
                .toList());
        return result;
    }

    public IPage<Product> getSelectedLibraryPage(long page, long size, String keyword, Integer status) {
        long currentPage = Math.max(page, 1);
        long pageSize = Math.max(size, 1);
        long fromIndex = (currentPage - 1) * pageSize;
        List<Product> pageRecords = new ArrayList<>();
        long matchedTotal = 0L;

        long statePageNo = 1L;
        boolean hasMore = true;
        while (hasMore) {
            Page<ProductOperationState> requestPage = new Page<>(statePageNo, SELECTED_LIBRARY_BATCH_SIZE);
            Page<ProductOperationState> statePage = (Page<ProductOperationState>) operationStateMapper.selectPage(
                    requestPage,
                    new LambdaQueryWrapper<ProductOperationState>()
                            .eq(ProductOperationState::getSelectedToLibrary, true)
                            .orderByDesc(ProductOperationState::getSelectedAt)
                            .orderByDesc(ProductOperationState::getUpdateTime)
            );
            List<ProductOperationState> stateBatch = statePage.getRecords();
            if (stateBatch == null || stateBatch.isEmpty()) {
                break;
            }
            Map<String, ProductSnapshot> snapshotMap = loadSnapshotsForStateBatch(stateBatch);
            Map<UUID, String> assigneeNameMap = loadUserDisplayNames(stateBatch.stream()
                    .map(ProductOperationState::getAssigneeId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            for (ProductOperationState state : stateBatch) {
                ProductSnapshot snapshot = snapshotMap.get(stateBatchKey(state.getActivityId(), state.getProductId()));
                if (snapshot == null) {
                    continue;
                }
                Product product = toLegacyProduct(snapshot, state, assigneeNameMap);
                if (!matchesSelectedLibraryFilters(product, keyword, status)) {
                    continue;
                }
                if (matchedTotal >= fromIndex && pageRecords.size() < pageSize) {
                    pageRecords.add(product);
                }
                matchedTotal++;
            }
            hasMore = statePage.getTotal() > statePageNo * SELECTED_LIBRARY_BATCH_SIZE;
            statePageNo++;
        }

        Page<Product> result = new Page<>(currentPage, pageSize, matchedTotal);
        result.setRecords(pageRecords);
        return result;
    }

    public PageResult<Map<String, Object>> getPromotionLinkHistory(String productId, long page, long size) {
        long currentPage = Math.max(page, 1);
        long pageSize = Math.max(size, 1);
        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setPage(currentPage);
        result.setSize(pageSize);
        if (!StringUtils.hasText(productId)) {
            result.setTotal(0);
            result.setRecords(List.of());
            return result;
        }
        List<PromotionLink> links = promotionLinkMapper.selectList(new LambdaQueryWrapper<PromotionLink>()
                .eq(PromotionLink::getProductId, productId)
                .orderByDesc(PromotionLink::getCreatedAt));
        if (links == null || links.isEmpty()) {
            result.setTotal(0);
            result.setRecords(List.of());
            return result;
        }
        long fromIndexLong = (currentPage - 1) * pageSize;
        if (fromIndexLong >= links.size()) {
            result.setTotal(links.size());
            result.setRecords(List.of());
            return result;
        }
        int fromIndex = Math.toIntExact(fromIndexLong);
        int toIndex = (int) Math.min(fromIndexLong + pageSize, links.size());
        result.setTotal(links.size());
        result.setRecords(links.subList(fromIndex, toIndex).stream()
                .map(this::toPromotionLinkHistoryItem)
                .toList());
        return result;
    }

    private Map<String, ProductSnapshot> loadSnapshotsForStateBatch(List<ProductOperationState> stateBatch) {
        if (stateBatch == null || stateBatch.isEmpty()) {
            return Map.of();
        }
        List<UUID> snapshotIds = stateBatch.stream()
                .filter(state -> StringUtils.hasText(state.getActivityId()) && StringUtils.hasText(state.getProductId()))
                .map(state -> buildSnapshotId(state.getActivityId(), state.getProductId()))
                .toList();
        if (snapshotIds.isEmpty()) {
            return Map.of();
        }
        return snapshotMapper.selectBatchIds(snapshotIds).stream()
                .filter(snapshot -> StringUtils.hasText(snapshot.getActivityId()) && StringUtils.hasText(snapshot.getProductId()))
                .collect(Collectors.toMap(
                        snapshot -> stateBatchKey(snapshot.getActivityId(), snapshot.getProductId()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, ProductOperationState> loadOperationStatesForSnapshots(List<ProductSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return Map.of();
        }
        Set<String> activityIds = snapshots.stream()
                .map(ProductSnapshot::getActivityId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> productIds = snapshots.stream()
                .map(ProductSnapshot::getProductId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (activityIds.isEmpty() || productIds.isEmpty()) {
            return Map.of();
        }
        return operationStateMapper.selectList(new LambdaQueryWrapper<ProductOperationState>()
                        .in(ProductOperationState::getActivityId, activityIds)
                        .in(ProductOperationState::getProductId, productIds))
                .stream()
                .filter(state -> StringUtils.hasText(state.getActivityId()) && StringUtils.hasText(state.getProductId()))
                .collect(Collectors.toMap(
                        state -> stateBatchKey(state.getActivityId(), state.getProductId()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<UUID, String> loadUserDisplayNames(Set<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(userIds).stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(
                        SysUser::getId,
                        this::formatUserDisplayName,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private String stateBatchKey(String activityId, String productId) {
        return activityId + "::" + productId;
    }

    private boolean matchesSelectedLibraryFilters(Product product, String keyword, Integer status) {
        if (product == null) {
            return false;
        }
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            boolean matchedByName = StringUtils.hasText(product.getName()) && product.getName().contains(trimmed);
            boolean matchedByProductId = StringUtils.hasText(product.getProductId()) && product.getProductId().contains(trimmed);
            if (!matchedByName && !matchedByProductId) {
                return false;
            }
        }
        return status == null || java.util.Objects.equals(product.getStatus(), status);
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
        auditProduct(snapshot.getActivityId(), snapshot.getProductId(), approved, reason, null, null, null);
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
            snapshotMapper.upsert(snapshot);
            ProductOperationState existingState = getOperationState(activityId, productId);
            productBizStatusService.initStateIfAbsent(existingState, activityId, productId, null, null, "活动商品同步");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int refreshActivitySnapshots(DouyinProductGateway.ActivityProductQueryRequest request) {
        if (request == null || !StringUtils.hasText(request.activityId())) {
            return 0;
        }
        int pageSize = Math.min(Math.max(request.count() == null ? 20 : request.count(), 1), 20);
        String cursor = request.cursor();
        Set<String> seenProductKeys = new LinkedHashSet<>();
        int maxPages = 100;

        for (int pageNo = 0; pageNo < maxPages; pageNo++) {
            DouyinProductGateway.ActivityProductListResult result = douyinProductGateway.queryActivityProducts(
                    new DouyinProductGateway.ActivityProductQueryRequest(
                            request.appId(),
                            request.activityId(),
                            request.searchType(),
                            request.sortType(),
                            pageSize,
                            request.cooperationInfo(),
                            request.cooperationType(),
                            request.productInfo(),
                            request.status(),
                            1L,
                            cursor,
                            null
                    )
            );
            List<DouyinProductGateway.ActivityProductItem> items = result.items();
            if (items == null || items.isEmpty()) {
                break;
            }
            upsertSnapshots(request.activityId(), items);

            int newItems = 0;
            for (DouyinProductGateway.ActivityProductItem item : items) {
                String productId = String.valueOf(item.productId());
                if (StringUtils.hasText(productId) && seenProductKeys.add(request.activityId() + "::" + productId)) {
                    newItems++;
                }
            }

            String nextCursor = StringUtils.hasText(result.nextCursor()) ? result.nextCursor().trim() : "";
            if (!StringUtils.hasText(nextCursor)
                    || nextCursor.equals(cursor)
                    || newItems == 0
                    || items.size() < pageSize) {
                break;
            }
            cursor = nextCursor;
        }
        return seenProductKeys.size();
    }

    public Map<String, Object> buildActivityProductListView(
            DouyinProductGateway.ActivityProductListResult result) {
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
            } else {
                ProductBizStatus bizStatus = ProductBizStatus.PENDING_AUDIT;
                view.put("bizStatus", bizStatus.name());
                view.put("bizStatusLabel", bizStatus.getLabel());
            }
            applyDecisionSummary(view, decisionSummary);
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
        BizStatusFilter bizStatusFilter = resolveBizStatusFilter(activityId, bizStatus);
        if (bizStatusFilter.isEmptyFilter()) {
            return emptyActivityProductListView(activityId);
        }

        LambdaQueryWrapper<ProductSnapshot> countWrapper = new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getActivityId, activityId)
                .and(StringUtils.hasText(productInfo), w -> w.like(ProductSnapshot::getTitle, productInfo.trim())
                        .or()
                        .like(ProductSnapshot::getProductId, productInfo.trim()));
        applyBizStatusFilter(countWrapper, bizStatusFilter);
        Long total = snapshotMapper.selectCount(countWrapper);

        Page<ProductSnapshot> snapshotPage = new Page<>(offset / pageSize + 1, pageSize);
        LambdaQueryWrapper<ProductSnapshot> queryWrapper = new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getActivityId, activityId)
                .and(StringUtils.hasText(productInfo), w -> w.like(ProductSnapshot::getTitle, productInfo.trim())
                        .or()
                        .like(ProductSnapshot::getProductId, productInfo.trim()))
                .orderByDesc(ProductSnapshot::getSyncTime)
                .orderByDesc(ProductSnapshot::getCreateTime);
        applyBizStatusFilter(queryWrapper, bizStatusFilter);
        List<ProductSnapshot> snapshots = snapshotMapper.selectPage(snapshotPage, queryWrapper).getRecords();

        if (snapshots.isEmpty()) {
            return emptyActivityProductListView(activityId, total == null ? 0 : total);
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

        List<Map<String, Object>> items = snapshots.stream()
                .map(snapshot -> toActivityProductView(
                        snapshot,
                        stateMap.get(snapshot.getProductId()),
                        decisionSummaryMap.get(snapshot.getProductId()),
                        orderSummaryMap.get(snapshot.getProductId()),
                        promotionSummaryMap.get(snapshot.getProductId()),
                        merchantMap.get(snapshot.getShopId()),
                        assigneeNameMap))
                .toList();

        int nextOffset = offset + snapshots.size();
        boolean hasMore = total != null && nextOffset < total;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activityId", activityId);
        result.put("institutionId", 0);
        result.put("total", total == null ? items.size() : total);
        result.put("nextCursor", hasMore ? String.valueOf(nextOffset) : "");
        result.put("hasMore", hasMore);
        result.put("items", items);
        return result;
    }

    private Map<String, Object> emptyActivityProductListView(String activityId) {
        return emptyActivityProductListView(activityId, 0L);
    }

    private Map<String, Object> emptyActivityProductListView(String activityId, long total) {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("activityId", activityId);
        empty.put("institutionId", 0);
        empty.put("total", total);
        empty.put("nextCursor", "");
        empty.put("hasMore", false);
        empty.put("items", List.of());
        return empty;
    }

    public Map<String, Object> getActivityProductDetail(String activityId, String productId) {
        ProductSnapshot snapshot = getSnapshot(activityId, productId);
        ProductOperationState state = getOperationState(activityId, productId);
        DecisionSummary decisionSummary = findDecisionSummary(activityId, productId);
        OrderSummary orderSummary = findOrderSummary(activityId, productId);
        PromotionSummary promotionSummary = findPromotionSummary(activityId, productId);
        Merchant merchant = findMerchant(snapshot.getShopId());

        Map<String, Object> detail = toActivityProductView(snapshot, state, decisionSummary, orderSummary, promotionSummary, merchant);
        Map<String, Object> auditSupplement = parseAuditPayload(state == null ? null : state.getAuditPayload());
        if (state != null) {
            detail.put("promotionScene", state.getPromotionScene());
            detail.put("externalUniqueId", state.getExternalUniqueId());
            detail.put("lastOperationAt", state.getLastOperationAt());
        }
        detail.put("auditSupplement", auditSupplement);
        detail.put("promotionLinks", promotionSummary == null ? List.of() : promotionSummary.linkRecords());
        detail.put("promotionMaterialPack", buildPromotionMaterialPack(snapshot, state, merchant, auditSupplement));
        detail.put("followRecords", talentFollowService.listByProduct(activityId, productId));
        return detail;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> putIntoLibrary(
            String activityId,
            String productId,
            UUID operatorId,
            UUID operatorDeptId) {
        ProductSnapshot snapshot = ensureSnapshotExists(activityId, productId);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        if (Boolean.TRUE.equals(state.getSelectedToLibrary())) {
            Map<String, Object> existingDetail = getActivityProductDetail(activityId, productId);
            existingDetail.put("selectedToLibrary", true);
            existingDetail.put("libraryVisible", true);
            return existingDetail;
        }
        requireApprovedAuditForLibraryEntry(state);
        state.setSelectedToLibrary(true);
        state.setSelectedAt(LocalDateTime.now());
        state.setSelectedBy(operatorId);
        state.setLastOperationAt(LocalDateTime.now());
        ProductBizStatus currentStatus = productBizStatusService.readBizStatus(state);
        if (currentStatus == ProductBizStatus.PENDING_AUDIT) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventLabel", "加入商品库");
            payload.put("productTitle", safeText(snapshot.getTitle(), "活动商品"));
            productBizStatusService.changeStatus(
                    state,
                    ProductBizStatus.APPROVED,
                    "LIBRARY_ENTRY",
                    operatorId,
                    operatorDeptId,
                    payload,
                    "已加入商品库，对全员可见",
                    current -> {
                        current.setSelectedToLibrary(true);
                        current.setSelectedAt(LocalDateTime.now());
                        current.setSelectedBy(operatorId);
                    }
            );
            Map<String, Object> detail = getActivityProductDetail(activityId, productId);
            detail.put("selectedToLibrary", true);
            detail.put("libraryVisible", true);
            return detail;
        } else if (state.getId() == null) {
            operationStateMapper.insert(state);
        } else {
            operationStateMapper.updateById(state);
        }

        ProductOperationLog log = new ProductOperationLog();
        log.setActivityId(activityId);
        log.setProductId(productId);
        log.setBeforeStatus(state.getBizStatus());
        log.setAfterStatus(state.getBizStatus());
        log.setSuccess(true);
        log.setOperationType("LIBRARY_ENTRY");
        log.setOperatorId(operatorId);
        log.setOperatorDeptId(operatorDeptId);
        log.setOperationRemark("已加入商品库，对全员可见");
        log.setOperationPayload("{eventLabel=加入商品库, productTitle=" + safeText(snapshot.getTitle(), "活动商品") + "}");
        operationLogMapper.insert(log);

        Map<String, Object> detail = getActivityProductDetail(activityId, productId);
        detail.put("selectedToLibrary", true);
        detail.put("libraryVisible", true);
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
        ProductBizStatus currentStatus = productBizStatusService.readBizStatus(state);
        state.setBoundActivityId(StringUtils.hasText(boundActivityId) ? boundActivityId.trim() : null);
        state.setLastOperationAt(LocalDateTime.now());
        if (state.getId() == null) {
            state.setId(UUID.randomUUID());
            operationStateMapper.insert(state);
        } else {
            operationStateMapper.updateById(state);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("boundActivityId", state.getBoundActivityId());
        payload.put("eventLabel", "商品活动绑定已更新");

        ProductOperationLog log = new ProductOperationLog();
        log.setId(UUID.randomUUID());
        log.setActivityId(activityId);
        log.setProductId(productId);
        log.setOperationType("BIND_ACTIVITY");
        log.setBeforeStatus(currentStatus.name());
        log.setAfterStatus(currentStatus.name());
        log.setSuccess(true);
        log.setOperatorId(operatorId);
        log.setOperatorDeptId(operatorDeptId);
        log.setOperationPayload(String.valueOf(payload));
        log.setOperationRemark("绑定活动成功");
        operationLogMapper.insert(log);
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
        ensurePostAuditLibraryFlag(state, operatorId);
        requireSelectedToLibrary(state, "分配招商");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assigneeId", assigneeId);
        payload.put("assigneeName", resolveUserDisplayName(assigneeId));
        payload.put("operatorId", operatorId);
        payload.put("operatorName", resolveUserDisplayName(operatorId));
        payload.put("eventLabel", "商品已分配给招商负责人");
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
    public Map<String, Object> assignAuditOwner(
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
        ProductBizStatus currentStatus = productBizStatusService.readBizStatus(state);
        if (currentStatus != ProductBizStatus.PENDING_AUDIT) {
            throw new BusinessException("仅待审核商品可分配审核人");
        }
        state.setAssigneeId(assigneeId);
        state.setLastOperationAt(LocalDateTime.now());
        if (state.getId() == null) {
            state.setId(UUID.randomUUID());
            operationStateMapper.insert(state);
        } else {
            operationStateMapper.updateById(state);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assigneeId", assigneeId);
        payload.put("assigneeName", resolveUserDisplayName(assigneeId));
        payload.put("operatorId", operatorId);
        payload.put("operatorName", resolveUserDisplayName(operatorId));
        payload.put("eventLabel", "商品已分配给审核负责人");
        productBizStatusService.logStatusChange(
                activityId,
                productId,
                "ASSIGN_AUDIT",
                currentStatus,
                currentStatus,
                operatorId,
                operatorDeptId,
                payload,
                "分配审核人成功",
                true,
                null
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
        return auditProduct(activityId, productId, approved, reason, null, operatorId, operatorDeptId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> auditProduct(
            String activityId,
            String productId,
            boolean approved,
            String reason,
            Map<String, Object> supplement,
            UUID operatorId,
            UUID operatorDeptId) {
        if (!approved && !StringUtils.hasText(reason)) {
            throw new BusinessException("审核拒绝时必须填写原因");
        }
        ensureSnapshotExists(activityId, productId);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        ProductBizStatus beforeStatus = productBizStatusService.readBizStatus(state);
        if (beforeStatus == null) {
            beforeStatus = ProductBizStatus.PENDING_AUDIT;
        }
        String auditPayload = null;
        if (approved) {
            validateAuditSupplement(supplement);
            auditPayload = writeAuditPayload(supplement);
        }
        final String approvedAuditPayload = auditPayload;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approved", approved);
        payload.put("reason", reason);
        state.setLastOperationAt(LocalDateTime.now());
        state.setAuditStatus(approved ? 2 : 3);
        state.setAuditRemark(approved ? null : reason);
        state.setAuditPayload(approvedAuditPayload);
        if (approved) {
            state.setSelectedToLibrary(true);
            state.setSelectedAt(LocalDateTime.now());
            state.setSelectedBy(operatorId);
            payload.put("eventLabel", "审核通过并加入商品库");
            payload.put("selectedToLibrary", true);
            payload.put("libraryVisible", true);
            payload.put("supplement", normalizeAuditSupplement(supplement));
            productBizStatusService.changeStatus(
                    state,
                    ProductBizStatus.APPROVED,
                    "AUDIT",
                    operatorId,
                    operatorDeptId,
                    payload,
                    "审核通过，已加入商品库",
                    current -> {
                        current.setAuditStatus(2);
                        current.setAuditRemark(null);
                        current.setAuditPayload(approvedAuditPayload);
                        current.setSelectedToLibrary(true);
                        current.setSelectedAt(LocalDateTime.now());
                        current.setSelectedBy(operatorId);
                        current.setLastOperationAt(LocalDateTime.now());
                    }
            );
            Map<String, Object> detail = getActivityProductDetail(activityId, productId);
            detail.put("selectedToLibrary", true);
            detail.put("libraryVisible", true);
            return detail;
        }

        state.setSelectedToLibrary(false);
        payload.put("eventLabel", "审核拒绝");
        productBizStatusService.changeStatus(
                state,
                ProductBizStatus.REJECTED,
                "AUDIT",
                operatorId,
                operatorDeptId,
                payload,
                "审核拒绝",
                current -> {
                    current.setAuditStatus(3);
                    current.setAuditRemark(reason);
                    current.setAuditPayload(null);
                    current.setLastOperationAt(LocalDateTime.now());
                }
        );
        return getActivityProductDetail(activityId, productId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> recordProductDecision(
            String activityId,
            String productId,
            String decisionLevel,
            String reason,
            UUID operatorId,
            UUID operatorDeptId) {
        String normalizedLevel = normalizeDecisionLevel(decisionLevel);
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("推进判断原因不能为空");
        }
        ensureSnapshotExists(activityId, productId);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        requireSelectedToLibrary(state, "保存推进判断");
        ProductBizStatus currentStatus = productBizStatusService.readBizStatus(state);
        state.setLastOperationAt(LocalDateTime.now());
        if (state.getId() == null) {
            operationStateMapper.insert(state);
        } else {
            operationStateMapper.updateById(state);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("decisionLevel", normalizedLevel);
        payload.put("decisionLabel", decisionLabel(normalizedLevel));
        payload.put("operatorId", operatorId);
        payload.put("operatorName", resolveUserDisplayName(operatorId));
        payload.put("eventLabel", "商品推进判断已更新");

        ProductOperationLog log = new ProductOperationLog();
        log.setActivityId(activityId);
        log.setProductId(productId);
        log.setOperationType("DECISION");
        log.setBeforeStatus(currentStatus.name());
        log.setAfterStatus(currentStatus.name());
        log.setSuccess(true);
        log.setOperatorId(operatorId);
        log.setOperatorDeptId(operatorDeptId);
        log.setOperationPayload(String.valueOf(payload));
        log.setOperationRemark(reason.trim());
        operationLogMapper.insert(log);

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
        NativeColonelBuyinResolution nativeColonelBuyin = resolveColonelBuyinIdForNativeMapping(snapshot.getActivityId(), snapshot.getProductId());
        String finalExternalId = StringUtils.hasText(externalUniqueId) ? externalUniqueId : String.valueOf(userId);
        int finalPromotionScene = promotionScene == null ? 4 : promotionScene;
        String finalScene = normalizePromotionScene(scene);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        requireSelectedToLibrary(state, "生成推广链接");
        ProductBizStatus beforeStatus = productBizStatusService.readBizStatus(state);
        if (beforeStatus == null) {
            beforeStatus = ProductBizStatus.fromCode(state.getBizStatus());
        }
        boolean relinkExistingProduct = beforeStatus == ProductBizStatus.LINKED;
        if (beforeStatus != ProductBizStatus.APPROVED
                && beforeStatus != ProductBizStatus.ASSIGNED
                && !relinkExistingProduct) {
            throw new BusinessException("当前状态不允许执行PROMOTION_LINK，当前状态：" + beforeStatus.name());
        }
        SysUser user = sysUserMapper.selectById(userId);
        String desiredPickExtra = buildPickExtra(userId);

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
                                    talentId,
                                    desiredPickExtra
                            )
                    )
            );

            // 1. 保存 PromotionLink
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
            if (nativeColonelBuyin.resolved()) {
                log.info("Native mapping resolved for activityId={}, productId={}, colonelBuyinId={}, source={}",
                        snapshot.getActivityId(),
                        snapshot.getProductId(),
                        nativeColonelBuyin.colonelBuyinId(),
                        nativeColonelBuyin.source());
                pickSourceMappingService.saveOrUpdate(
                        userId,
                        user != null ? user.getRealName() : "unknown",
                        deptId,
                        talentId,
                        null,
                        result.shortId(),
                        null,
                        result.pickSource(),
                        snapshot.getProductId(),
                        snapshot.getActivityId(),
                        snapshot.getDetailUrl(),
                        result.promoteLink(),
                        link.getId(),
                        finalScene,
                        result.pickExtra(),
                        nativeColonelBuyin.colonelBuyinId(),
                        PickSourceMappingService.SOURCE_TYPE_NATIVE
                );
            } else {
                log.warn("Skip native mapping creation because colonel_buyin_id is unresolved, activityId={}, productId={}, source={}",
                        snapshot.getActivityId(),
                        snapshot.getProductId(),
                        nativeColonelBuyin.source());
                pickSourceMappingService.saveOrUpdate(
                        userId,
                        user != null ? user.getRealName() : "unknown",
                        deptId,
                        talentId,
                        null,
                        result.shortId(),
                        null,
                        result.pickSource(),
                        snapshot.getProductId(),
                        snapshot.getActivityId(),
                        snapshot.getDetailUrl(),
                        result.promoteLink(),
                        link.getId(),
                        finalScene,
                        result.pickExtra()
                );
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("promotionScene", finalPromotionScene);
            payload.put("needShortLink", needShortLink);
            payload.put("externalUniqueId", finalExternalId);
            payload.put("scene", finalScene);
            payload.put("talentId", talentId);
            payload.put("shortLink", result.shortLink());
            payload.put("promoteLink", result.promoteLink());
            payload.put("pickSource", result.pickSource());
            if (relinkExistingProduct) {
                state.setPromoteLink(result.promoteLink());
                state.setShortLink(result.shortLink());
                state.setPromotionScene(finalPromotionScene);
                state.setExternalUniqueId(finalExternalId);
                operationStateMapper.updateById(state);
                productBizStatusService.logStatusChange(
                        snapshot.getActivityId(),
                        snapshot.getProductId(),
                        "PROMOTION_LINK",
                        ProductBizStatus.LINKED,
                        ProductBizStatus.LINKED,
                        userId,
                        deptId,
                        payload,
                        "已转链商品重新生成推广链接",
                        true,
                        null
                );
                return result;
            }
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

    private String buildPickExtra(UUID userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null && StringUtils.hasText(user.getChannelCode())) {
            String candidate = "channel_" + user.getChannelCode().trim().toLowerCase(Locale.ROOT);
            return candidate.length() <= 20 ? candidate : candidate.substring(0, 20);
        }
        String fallback = "channel_" + userId.toString().replace("-", "");
        return fallback.substring(0, Math.min(fallback.length(), 20));
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

    /**
     * 从 colonel_activity 表按 activity_id 查询 colonel_buyin_id。
     * 返回的 Long 会自动转 String（19 位数字字符串），供 saveOrUpdate 写入 pick_source_mapping。
     */
    private NativeColonelBuyinResolution resolveColonelBuyinIdForNativeMapping(String activityId, String productId) {
        String colonelBuyinId = resolveColonelBuyinIdFromSnapshot(activityId, productId);
        if (StringUtils.hasText(colonelBuyinId)) {
            return new NativeColonelBuyinResolution(colonelBuyinId, NativeColonelBuyinSource.PRODUCT_SNAPSHOT);
        }
        colonelBuyinId = resolveColonelBuyinIdFromOperationState(activityId, productId);
        if (StringUtils.hasText(colonelBuyinId)) {
            return new NativeColonelBuyinResolution(colonelBuyinId, NativeColonelBuyinSource.PRODUCT_OPERATION_STATE);
        }
        colonelBuyinId = resolveColonelBuyinIdFromActivity(activityId);
        if (StringUtils.hasText(colonelBuyinId)) {
            return new NativeColonelBuyinResolution(colonelBuyinId, NativeColonelBuyinSource.COLONEL_ACTIVITY);
        }
        colonelBuyinId = hydrateColonelActivityMeta(activityId);
        if (StringUtils.hasText(colonelBuyinId)) {
            return new NativeColonelBuyinResolution(colonelBuyinId, NativeColonelBuyinSource.ACTIVITY_API_DETAIL);
        }
        return NativeColonelBuyinResolution.unresolved();
    }

    private String resolveColonelBuyinIdFromActivity(String activityId) {
        if (!StringUtils.hasText(activityId)) {
            return null;
        }
        ColonelsettlementActivity activity = colonelActivityMapper.selectByActivityId(activityId);
        if (activity != null && activity.getColonelBuyinId() != null) {
            return String.valueOf(activity.getColonelBuyinId());
        }
        // extra_data 回源：hydrateColonelActivityMeta 已将真实 colonel_buyin_id 存入 extraData JSONB
        Map<String, Object> extraData = colonelActivityMapper.selectExtraDataByActivityId(activityId);
        if (extraData != null && !extraData.isEmpty()) {
            String fromExtra = firstNonBlank(
                    readString(extraData, "colonel_buyin_id", "colonelBuyinId"),
                    readString(readNestedMap(extraData, "extra_data", "extraData"), "colonel_buyin_id", "colonelBuyinId")
            );
            if (StringUtils.hasText(fromExtra)) {
                return fromExtra;
            }
        }
        return null;
    }

    private String hydrateColonelActivityMeta(String activityId) {
        if (!StringUtils.hasText(activityId)) {
            return null;
        }
        try {
            Map<String, Object> response = douyinActivityGateway.activityDetail(null, activityId);
            Map<String, Object> data = readPrimaryDataNode(response);
            Long colonelBuyinId = readLong(data, "colonel_buyin_id", "colonelBuyinId");
            if (colonelBuyinId == null || colonelBuyinId <= 0L) {
                return null;
            }
            colonelActivityMapper.upsertRealActivityMeta(
                    UUID.nameUUIDFromBytes(("real-activity-" + activityId).getBytes(StandardCharsets.UTF_8)),
                    activityId,
                    readString(data, "activity_name", "activityName", "name"),
                    readLong(data, "shop_id", "shopId"),
                    readString(data, "shop_name", "shopName"),
                    colonelBuyinId,
                    readRateDecimal(data, "commission_rate", "commissionRate"),
                    readRateDecimal(data, "service_rate", "serviceRate"),
                    parseDateTimeValue(readString(data, "activity_start_time", "activityStartTime", "start_time", "startTime")),
                    parseDateTimeValue(readString(data, "activity_end_time", "activityEndTime", "end_time", "endTime")),
                    readString(data, "status_text", "statusText", "status"),
                    LocalDateTime.now(),
                    data
            );
            return String.valueOf(colonelBuyinId);
        } catch (Exception ex) {
            // 真实活动详情补水仅作为 native 映射生成前的兜底，不阻断转链主流程。
            log.warn("Hydrate colonel activity meta failed, activityId={}", activityId, ex);
            return null;
        }
    }

    private String resolveColonelBuyinIdFromSnapshot(String activityId, String productId) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(productId)) {
            return null;
        }
        ProductSnapshot snapshot = snapshotMapper.selectOne(new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getActivityId, activityId)
                .eq(ProductSnapshot::getProductId, productId)
                .eq(ProductSnapshot::getDeleted, 0)
                .last("limit 1"));
        if (snapshot == null) {
            return null;
        }
        String fromPayload = resolveColonelBuyinIdFromPayload(snapshot.getRawPayload());
        if (StringUtils.hasText(fromPayload)) {
            return fromPayload;
        }
        return extractColonelBuyinIdFromText(snapshot.getRawPayload());
    }

    private String resolveColonelBuyinIdFromOperationState(String activityId, String productId) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(productId)) {
            return null;
        }
        ProductOperationState state = operationStateMapper.selectOne(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getActivityId, activityId)
                .eq(ProductOperationState::getProductId, productId)
                .eq(ProductOperationState::getDeleted, 0)
                .last("limit 1"));
        if (state == null) {
            return null;
        }
        String fromPayload = resolveColonelBuyinIdFromPayload(state.getAuditPayload());
        if (StringUtils.hasText(fromPayload)) {
            return fromPayload;
        }
        return extractColonelBuyinIdFromText(state.getAuditPayload());
    }

    private Map<String, Object> readPrimaryDataNode(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> data = readNestedMap(response, "data");
        if (!data.isEmpty()) {
            Map<String, Object> detail = readNestedMap(data, "data", "detail");
            if (!detail.isEmpty()) {
                return detail;
            }
            return data;
        }
        return response;
    }

    private Map<String, Object> parseJsonObject(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String resolveColonelBuyinIdFromPayload(String raw) {
        Map<String, Object> payload = parseJsonObject(raw);
        if (payload.isEmpty()) {
            return null;
        }
        String fromRoot = readString(payload,
                "origin_colonel_buyin_id",
                "originColonelBuyinId",
                "colonel_buyin_id",
                "colonelBuyinId");
        if (StringUtils.hasText(fromRoot)) {
            return fromRoot;
        }
        Map<String, Object> extraData = readNestedMap(payload, "extra_data", "extraData");
        return readString(extraData,
                "origin_colonel_buyin_id",
                "originColonelBuyinId",
                "colonel_buyin_id",
                "colonelBuyinId");
    }

    private String extractColonelBuyinIdFromText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        Matcher matcher = BUYIN_ID_PATTERN.matcher(raw);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private Map<String, Object> readNestedMap(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return Map.of();
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Map<?, ?> raw) {
                Map<String, Object> converted = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : raw.entrySet()) {
                    if (entry.getKey() != null) {
                        converted.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                return converted;
            }
        }
        return Map.of();
    }

    private String readString(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private Long readLong(Map<String, Object> source, String... keys) {
        String text = readString(source, keys);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal readDecimal(Map<String, Object> source, String... keys) {
        String text = readString(source, keys);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal readRateDecimal(Map<String, Object> source, String... keys) {
        BigDecimal value = readDecimal(source, keys);
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value;
        BigDecimal abs = value.abs();
        if (abs.compareTo(new BigDecimal("100")) > 0) {
            normalized = value.divide(new BigDecimal("10000"), 8, RoundingMode.HALF_UP);
        } else if (abs.compareTo(BigDecimal.ONE) > 0) {
            normalized = value.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
        }
        if (normalized.abs().compareTo(new BigDecimal("9.9999")) > 0) {
            return null;
        }
        return normalized.setScale(4, RoundingMode.HALF_UP);
    }

    private LocalDateTime parseDateTimeValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            return LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
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

    private record NativeColonelBuyinResolution(String colonelBuyinId, NativeColonelBuyinSource source) {
        private static NativeColonelBuyinResolution unresolved() {
            return new NativeColonelBuyinResolution(null, NativeColonelBuyinSource.UNRESOLVED);
        }

        private boolean resolved() {
            return StringUtils.hasText(colonelBuyinId);
        }
    }

    private enum NativeColonelBuyinSource {
        PRODUCT_SNAPSHOT,
        PRODUCT_OPERATION_STATE,
        COLONEL_ACTIVITY,
        ACTIVITY_API_DETAIL,
        UNRESOLVED
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
        requireSelectedToLibrary(state, "创建达人跟进");
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
        snapshot.setRawPayload(writeSnapshotPayload(item));
        snapshot.setSyncTime(java.time.LocalDateTime.now());
    }

    private String writeSnapshotPayload(DouyinProductGateway.ActivityProductItem item) {
        try {
            return OBJECT_MAPPER.writeValueAsString(item.toMap());
        } catch (Exception ex) {
            log.warn("Serialize product snapshot payload failed, activityProductItem={}", item.productId(), ex);
            return String.valueOf(item.toMap());
        }
    }

    private Product toLegacyProduct(ProductSnapshot snapshot) {
        return toLegacyProduct(snapshot, null);
    }

    private Product toLegacyProduct(ProductSnapshot snapshot, ProductOperationState providedState) {
        return toLegacyProduct(snapshot, providedState, null);
    }

    private Product toLegacyProduct(
            ProductSnapshot snapshot,
            ProductOperationState providedState,
            Map<UUID, String> userDisplayNames) {
        Product product = new Product();
        product.setId(snapshot.getId());
        product.setProductId(snapshot.getProductId());
        product.setName(snapshot.getTitle());
        product.setPrice(snapshot.getPrice());
        product.setStatus(snapshot.getStatus());
        product.setCategory(snapshot.getCategoryName());
        product.setActivityId(toUuid(snapshot.getActivityId()));
        product.setSourceActivityId(snapshot.getActivityId());
        product.setCover(snapshot.getCover());
        product.setShopName(snapshot.getShopName());
        product.setPriceText(snapshot.getPriceText());
        product.setActivityCosRatioText(snapshot.getActivityCosRatioText());
        product.setEstimatedServiceFee(estimateFee(snapshot.getPrice(), resolveServiceFeeRate(snapshot)).toPlainString());
        product.setCreateTime(snapshot.getCreateTime());
        product.setUpdateTime(snapshot.getUpdateTime());

        ProductOperationState state = providedState != null
                ? providedState
                : getOperationState(snapshot.getActivityId(), snapshot.getProductId());
        if (state != null) {
            product.setCheckStatus(state.getAuditStatus());
            product.setAuditRemark(state.getAuditRemark());
            product.setAssigneeId(state.getAssigneeId());
            product.setPromoteLink(state.getPromoteLink());
            product.setShortLink(state.getShortLink());
            product.setSelectedToLibrary(Boolean.TRUE.equals(state.getSelectedToLibrary()));
            product.setAssigneeName(resolveUserDisplayName(state.getAssigneeId(), userDisplayNames));
            product.setSelectedAt(state.getSelectedAt());
            Map<String, Object> auditSupplement = parseAuditPayload(state.getAuditPayload());
            product.setAuditSupplement(auditSupplement);
            ProductBizStatus bizStatus = productBizStatusService.readBizStatus(state);
            if (bizStatus == null) {
                bizStatus = ProductBizStatus.PENDING_AUDIT;
            }
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
            DecisionSummary decisionSummary,
            OrderSummary orderSummary,
            PromotionSummary promotionSummary,
            Merchant merchant) {
        return toActivityProductView(snapshot, state, decisionSummary, orderSummary, promotionSummary, merchant, null);
    }

    private Map<String, Object> toActivityProductView(
            ProductSnapshot snapshot,
            ProductOperationState state,
            DecisionSummary decisionSummary,
            OrderSummary orderSummary,
            PromotionSummary promotionSummary,
            Merchant merchant,
            Map<UUID, String> assigneeNameMap) {
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

        ProductBizStatus currentStatus = ProductBizStatus.PENDING_AUDIT;
        if (state != null) {
            ProductBizStatus resolvedStatus = productBizStatusService.readBizStatus(state);
            if (resolvedStatus != null) {
                currentStatus = resolvedStatus;
            }
        }
        view.put("bizStatus", currentStatus.name());
        view.put("bizStatusLabel", currentStatus.getLabel());

        if (state != null) {
            view.put("boundActivityId", state.getBoundActivityId());
            view.put("assigneeId", state.getAssigneeId());
            view.put("assigneeName", resolveUserDisplayName(state.getAssigneeId(), assigneeNameMap));
            view.put("auditStatus", state.getAuditStatus());
            view.put("auditRemark", state.getAuditRemark());
            view.put("shortLink", state.getShortLink());
            view.put("promoteLink", state.getPromoteLink());
            view.put("selectedToLibrary", Boolean.TRUE.equals(state.getSelectedToLibrary()));
            view.put("libraryVisible", Boolean.TRUE.equals(state.getSelectedToLibrary()));
            view.put("selectedAt", state.getSelectedAt());
        }
        applyDecisionSummary(view, decisionSummary);

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

    private void applyDecisionSummary(Map<String, Object> view, DecisionSummary decisionSummary) {
        if (decisionSummary == null) {
            view.put("latestDecisionLevel", null);
            view.put("latestDecisionLabel", null);
            view.put("latestDecisionReason", null);
            view.put("latestDecisionAt", null);
            return;
        }
        view.put("latestDecisionLevel", decisionSummary.level());
        view.put("latestDecisionLabel", decisionSummary.label());
        view.put("latestDecisionReason", decisionSummary.reason());
        view.put("latestDecisionAt", decisionSummary.time());
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
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return formatUserDisplayName(user);
    }

    private String formatUserDisplayName(SysUser user) {
        if (user == null) {
            return null;
        }
        String realName = normalizeDisplayText(user.getRealName());
        String username = normalizeDisplayText(user.getUsername());
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

    private BizStatusFilter resolveBizStatusFilter(String activityId, String bizStatus) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(bizStatus)) {
            return BizStatusFilter.none();
        }
        String normalizedStatus = bizStatus.trim().toUpperCase(Locale.ROOT);
        List<ProductOperationState> states = operationStateMapper.selectList(
                new LambdaQueryWrapper<ProductOperationState>()
                        .eq(ProductOperationState::getActivityId, activityId)
        );
        if (states == null || states.isEmpty()) {
            return ProductBizStatus.PENDING_AUDIT.name().equals(normalizedStatus)
                    ? BizStatusFilter.none()
                    : BizStatusFilter.empty();
        }
        Set<String> knownProductIds = states.stream()
                .map(ProductOperationState::getProductId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> matchedProductIds = states.stream()
                .filter(state -> normalizedStatus.equalsIgnoreCase(state.getBizStatus()))
                .map(ProductOperationState::getProductId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ProductBizStatus.PENDING_AUDIT.name().equals(normalizedStatus)) {
            return BizStatusFilter.pendingAudit(knownProductIds, matchedProductIds);
        }
        return matchedProductIds.isEmpty()
                ? BizStatusFilter.empty()
                : BizStatusFilter.includeOnly(matchedProductIds);
    }

    private void applyBizStatusFilter(LambdaQueryWrapper<ProductSnapshot> wrapper, BizStatusFilter filter) {
        if (wrapper == null || filter == null || filter.mode() == BizStatusFilterMode.NONE) {
            return;
        }
        if (filter.mode() == BizStatusFilterMode.EMPTY) {
            wrapper.apply("1 = 0");
            return;
        }
        if (filter.mode() == BizStatusFilterMode.INCLUDE_ONLY) {
            wrapper.in(ProductSnapshot::getProductId, filter.includeProductIds());
            return;
        }
        if (filter.mode() == BizStatusFilterMode.PENDING_AUDIT) {
            Set<String> includeIds = filter.includeProductIds();
            Set<String> excludeIds = filter.excludeProductIds();
            if (!includeIds.isEmpty() && !excludeIds.isEmpty()) {
                wrapper.and(w -> w.in(ProductSnapshot::getProductId, includeIds)
                        .or()
                        .notIn(ProductSnapshot::getProductId, excludeIds));
            } else if (!includeIds.isEmpty()) {
                wrapper.in(ProductSnapshot::getProductId, includeIds);
            } else if (!excludeIds.isEmpty()) {
                wrapper.notIn(ProductSnapshot::getProductId, excludeIds);
            }
        }
    }

    private TalentFollowStatus normalizeFollowStatus(String rawStatus) {
        TalentFollowStatus status = TalentFollowStatus.fromCode(rawStatus);
        return status == null ? TalentFollowStatus.NOT_CONTACTED : status;
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
                                    payload.getOrDefault("decisionLabel", decisionLabel(level)),
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
                payload.getOrDefault("decisionLabel", decisionLabel(level)),
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

    private String normalizeDecisionLevel(String decisionLevel) {
        if (!StringUtils.hasText(decisionLevel)) {
            throw new BusinessException("推进判断不能为空");
        }
        String normalized = decisionLevel.trim().toUpperCase();
        if (!Set.of("MAIN", "SECONDARY", "PAUSE", "DROP").contains(normalized)) {
            throw new BusinessException("未知推进判断：" + decisionLevel);
        }
        return normalized;
    }

    private String decisionLabel(String decisionLevel) {
        return switch (decisionLevel) {
            case "MAIN" -> "主推";
            case "SECONDARY" -> "次推";
            case "PAUSE" -> "暂缓";
            case "DROP" -> "放弃";
            default -> decisionLevel;
        };
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
        return summary.freeze();
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
        return summary.freeze();
    }

    private Map<String, Object> toPromotionLinkHistoryItem(PromotionLink link) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", link.getId());
        item.put("activityId", link.getActivityId());
        item.put("productId", link.getProductId());
        item.put("talentId", link.getTalentId());
        item.put("talentName", link.getTalentName());
        item.put("channelUserId", link.getChannelUserId());
        item.put("channelUserName", link.getChannelUserName());
        item.put("pickSource", link.getPickSource());
        item.put("pickExtra", link.getPickExtra());
        item.put("promotionUrl", link.getPromotionUrl());
        item.put("promoteLink", link.getPromotionUrl());
        item.put("shortUrl", link.getShortUrl());
        item.put("shortLink", link.getShortUrl());
        item.put("doukouling", link.getDoukouling());
        item.put("linkStatus", link.getLinkStatus());
        item.put("expireTime", link.getExpireTime());
        item.put("createdAt", link.getCreatedAt());
        item.put("operatorId", link.getOperatorId());
        item.put("operatorName", link.getOperatorName());
        return item;
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

    private Merchant findMerchant(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return null;
        }
        return merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getShopId, shopId)
                .last("limit 1"));
    }

    private Map<String, Object> buildPromotionMaterialPack(
            ProductSnapshot snapshot,
            ProductOperationState state,
            Merchant merchant,
            Map<String, Object> auditSupplement) {
        Map<String, Object> pack = new LinkedHashMap<>();
        List<String> sellingPoints = readStringList(auditSupplement, "sellingPoints");
        if (sellingPoints.isEmpty()) {
            sellingPoints = new ArrayList<>();
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
        }

        String merchantName = merchant != null && StringUtils.hasText(merchant.getMerchantName())
                ? merchant.getMerchantName()
                : snapshot.getShopName();
        String commissionText = StringUtils.hasText(snapshot.getActivityCosRatioText())
                ? snapshot.getActivityCosRatioText()
                : formatRate(resolveCommissionRate(snapshot));
        String serviceFeeText = formatRate(resolveServiceFeeRate(snapshot));
        String promotionScript = readString(auditSupplement, "promotionScript");

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
        pack.put("supportsAds", readBoolean(auditSupplement, "supportsAds"));
        pack.put("materialFiles", readStringList(auditSupplement, "materialFiles"));
        return pack;
    }

    private void requireApprovedAuditForLibraryEntry(ProductOperationState state) {
        Integer auditStatus = state == null ? null : state.getAuditStatus();
        if (Integer.valueOf(2).equals(auditStatus)) {
            return;
        }
        if (Integer.valueOf(3).equals(auditStatus)) {
            throw new BusinessException("审核拒绝的商品不能加入商品库");
        }
        throw new BusinessException("请先完成审核通过，再继续后续业务操作");
    }

    private void requireSelectedToLibrary(ProductOperationState state, String actionLabel) {
        if (!Boolean.TRUE.equals(state.getSelectedToLibrary())) {
            throw new BusinessException("请先将商品加入商品库后再" + actionLabel);
        }
    }

    private void ensurePostAuditLibraryFlag(ProductOperationState state, UUID operatorId) {
        if (Boolean.TRUE.equals(state.getSelectedToLibrary())) {
            return;
        }
        ProductBizStatus currentStatus = productBizStatusService.readBizStatus(state);
        if (currentStatus != ProductBizStatus.APPROVED
                && currentStatus != ProductBizStatus.BOUND
                && currentStatus != ProductBizStatus.ASSIGNED
                && currentStatus != ProductBizStatus.LINKED
                && currentStatus != ProductBizStatus.FOLLOWING) {
            return;
        }
        state.setSelectedToLibrary(true);
        state.setSelectedAt(LocalDateTime.now());
        state.setSelectedBy(operatorId);
        operationStateMapper.updateById(state);
    }

    private void validateAuditSupplement(Map<String, Object> supplement) {
        Map<String, Object> normalized = normalizeAuditSupplement(supplement);
        List<String> missing = new ArrayList<>();
        requireText(normalized, "exclusivePriceRemark", "专属价说明", missing);
        requireText(normalized, "shippingInfo", "发货信息", missing);
        requireList(normalized, "sellingPoints", "商品卖点", missing);
        requireText(normalized, "promotionScript", "推广话术", missing);
        if (!normalized.containsKey("supportsAds")) {
            missing.add("是否支持投流");
        }
        requireText(normalized, "rewardRemark", "奖励说明", missing);
        requireText(normalized, "participationRequirements", "参与要求", missing);
        requireText(normalized, "campaignTimeRemark", "活动时间", missing);
        requireList(normalized, "materialFiles", "手卡素材", missing);
        if (!missing.isEmpty()) {
            throw new BusinessException("审核通过前请补充：" + String.join("、", missing));
        }
    }

    private void requireText(Map<String, Object> payload, String key, String label, List<String> missing) {
        if (!StringUtils.hasText(readString(payload, key))) {
            missing.add(label);
        }
    }

    private void requireList(Map<String, Object> payload, String key, String label, List<String> missing) {
        if (readStringList(payload, key).isEmpty()) {
            missing.add(label);
        }
    }

    private String writeAuditPayload(Map<String, Object> supplement) {
        Map<String, Object> normalized = normalizeAuditSupplement(supplement);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(normalized);
        } catch (Exception ex) {
            throw new BusinessException("审核补充信息保存失败");
        }
    }

    private Map<String, Object> parseAuditPayload(String rawPayload) {
        if (!StringUtils.hasText(rawPayload)) {
            return Map.of();
        }
        try {
            return normalizeAuditSupplement(OBJECT_MAPPER.readValue(rawPayload, new TypeReference<Map<String, Object>>() {}));
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Map<String, Object> normalizeAuditSupplement(Map<String, Object> supplement) {
        if (supplement == null || supplement.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        putNormalizedText(normalized, "exclusivePriceRemark", supplement.get("exclusivePriceRemark"));
        putNormalizedText(normalized, "shippingInfo", supplement.get("shippingInfo"));
        putNormalizedText(normalized, "promotionScript", supplement.get("promotionScript"));
        putNormalizedText(normalized, "rewardRemark", supplement.get("rewardRemark"));
        putNormalizedText(normalized, "participationRequirements", supplement.get("participationRequirements"));
        putNormalizedText(normalized, "campaignTimeRemark", supplement.get("campaignTimeRemark"));
        putNormalizedText(normalized, "sampleThresholdRemark", supplement.get("sampleThresholdRemark"));
        List<String> sellingPoints = normalizeStringList(supplement.get("sellingPoints"));
        if (!sellingPoints.isEmpty()) {
            normalized.put("sellingPoints", sellingPoints);
        }
        List<String> materialFiles = normalizeStringList(supplement.get("materialFiles"));
        if (!materialFiles.isEmpty()) {
            normalized.put("materialFiles", materialFiles);
        }
        if (supplement.containsKey("supportsAds") && supplement.get("supportsAds") != null) {
            normalized.put("supportsAds", Boolean.parseBoolean(String.valueOf(supplement.get("supportsAds"))));
        }
        putNormalizedNumber(normalized, "sampleThresholdSales", supplement.get("sampleThresholdSales"));
        putNormalizedNumber(normalized, "sampleThresholdLevel", supplement.get("sampleThresholdLevel"));
        return normalized;
    }

    private void putNormalizedText(Map<String, Object> payload, String key, Object rawValue) {
        String value = rawValue == null ? null : String.valueOf(rawValue).trim();
        if (StringUtils.hasText(value)) {
            payload.put(key, value);
        }
    }

    private void putNormalizedNumber(Map<String, Object> payload, String key, Object rawValue) {
        if (rawValue == null) {
            return;
        }
        if (rawValue instanceof Number number) {
            payload.put(key, number.longValue());
            return;
        }
        String value = String.valueOf(rawValue).trim();
        if (!StringUtils.hasText(value)) {
            return;
        }
        try {
            payload.put(key, Long.parseLong(value));
        } catch (NumberFormatException ex) {
            payload.put(key, value);
        }
    }

    private String readString(Map<String, Object> payload, String key) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private Boolean readBoolean(Map<String, Object> payload, String key) {
        if (payload == null || payload.isEmpty() || !payload.containsKey(key) || payload.get(key) == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(payload.get(key)));
    }

    private List<String> readStringList(Map<String, Object> payload, String key) {
        if (payload == null || payload.isEmpty()) {
            return List.of();
        }
        return normalizeStringList(payload.get(key));
    }

    private List<String> normalizeStringList(Object rawValue) {
        if (rawValue == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        if (rawValue instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    normalized.add(String.valueOf(item).trim());
                }
            }
            return normalized;
        }
        String text = String.valueOf(rawValue).trim();
        if (StringUtils.hasText(text)) {
            normalized.add(text);
        }
        return normalized;
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

    private record BizStatusFilter(
            BizStatusFilterMode mode,
            Set<String> includeProductIds,
            Set<String> excludeProductIds
    ) {
        static BizStatusFilter none() {
            return new BizStatusFilter(BizStatusFilterMode.NONE, Set.of(), Set.of());
        }

        static BizStatusFilter empty() {
            return new BizStatusFilter(BizStatusFilterMode.EMPTY, Set.of(), Set.of());
        }

        static BizStatusFilter includeOnly(Set<String> includeProductIds) {
            return new BizStatusFilter(BizStatusFilterMode.INCLUDE_ONLY, Set.copyOf(includeProductIds), Set.of());
        }

        static BizStatusFilter pendingAudit(Set<String> knownProductIds, Set<String> pendingAuditProductIds) {
            return new BizStatusFilter(
                    BizStatusFilterMode.PENDING_AUDIT,
                    Set.copyOf(pendingAuditProductIds),
                    Set.copyOf(knownProductIds)
            );
        }

        boolean isEmptyFilter() {
            return mode == BizStatusFilterMode.EMPTY;
        }
    }

    private enum BizStatusFilterMode {
        NONE,
        EMPTY,
        INCLUDE_ONLY,
        PENDING_AUDIT
    }

    private record DecisionSummary(
            String level,
            String label,
            String reason,
            String time) {
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
