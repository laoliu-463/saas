package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.douyin.api.PromotionApi;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductService {

    private final PromotionApi promotionApi;
    private final ProductSnapshotMapper snapshotMapper;
    private final ProductOperationStateMapper operationStateMapper;
    private final ProductOperationLogMapper operationLogMapper;
    @Value("${douyin.mock.enabled:false}")
    private boolean douyinMockEnabled;

    public ProductService(
            PromotionApi promotionApi,
            ProductSnapshotMapper snapshotMapper,
            ProductOperationStateMapper operationStateMapper,
            ProductOperationLogMapper operationLogMapper) {
        this.promotionApi = promotionApi;
        this.snapshotMapper = snapshotMapper;
        this.operationStateMapper = operationStateMapper;
        this.operationLogMapper = operationLogMapper;
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
    public PromotionApi.PromotionLinkResult generatePromotionLink(
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
    public void upsertSnapshots(String activityId, List<Map<String, Object>> items) {
        if (!StringUtils.hasText(activityId) || items == null || items.isEmpty()) {
            return;
        }
        for (Map<String, Object> item : items) {
            String productId = asText(item.get("productId"));
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
        }
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
            detail.put("boundActivityId", state.getBoundActivityId());
            detail.put("assigneeId", state.getAssigneeId());
            detail.put("auditStatus", state.getAuditStatus());
            detail.put("auditRemark", state.getAuditRemark());
            detail.put("promoteLink", state.getPromoteLink());
            detail.put("shortLink", state.getShortLink());
            detail.put("promotionScene", state.getPromotionScene());
            detail.put("externalUniqueId", state.getExternalUniqueId());
            detail.put("lastOperationAt", state.getLastOperationAt());
        }
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
        state.setBoundActivityId(boundActivityId);
        state.setLastOperationAt(LocalDateTime.now());
        saveOperationState(state);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("boundActivityId", boundActivityId);
        saveOperationLog(activityId, productId, "BIND_ACTIVITY", operatorId, operatorDeptId,
                payload, "绑定活动成功");
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
        state.setAssigneeId(assigneeId);
        state.setLastOperationAt(LocalDateTime.now());
        saveOperationState(state);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assigneeId", assigneeId);
        saveOperationLog(activityId, productId, "ASSIGN", operatorId, operatorDeptId,
                payload, "分配招商成功");
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
            throw new BusinessException("驳回时必须填写原因");
        }
        ensureSnapshotExists(activityId, productId);
        ProductOperationState state = getOrInitOperationState(activityId, productId);
        state.setAuditStatus(approved ? 2 : 3);
        state.setAuditRemark(approved ? null : reason);
        state.setLastOperationAt(LocalDateTime.now());
        saveOperationState(state);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approved", approved);
        payload.put("reason", reason);
        saveOperationLog(activityId, productId, "AUDIT", operatorId, operatorDeptId,
                payload, approved ? "审核通过" : "审核驳回");
        return getActivityProductDetail(activityId, productId);
    }

    @Transactional(rollbackFor = Exception.class)
    public PromotionApi.PromotionLinkResult generatePromotionLink(
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

        PromotionApi.PromotionLinkResult result = promotionApi.generateLink(
                finalExternalId,
                finalPromotionScene,
                List.of(snapshot.getProductId()),
                needShortLink,
                new PromotionApi.PromotionContext(
                        userId,
                        deptId,
                        snapshot.getProductId(),
                        snapshot.getActivityId(),
                        snapshot.getDetailUrl()
                )
        );
        result = fillMockPromotionLinkIfNeeded(result, snapshot.getActivityId(), snapshot.getProductId());

        ProductOperationState state = getOrInitOperationState(activityId, productId);
        state.setPromoteLink(result.promoteLink());
        state.setShortLink(result.shortLink());
        state.setPromotionScene(finalPromotionScene);
        state.setExternalUniqueId(finalExternalId);
        state.setLastOperationAt(LocalDateTime.now());
        saveOperationState(state);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("promotionScene", finalPromotionScene);
        payload.put("needShortLink", needShortLink);
        payload.put("externalUniqueId", finalExternalId);
        payload.put("shortLink", result.shortLink());
        payload.put("promoteLink", result.promoteLink());
        saveOperationLog(activityId, productId, "PROMOTION_LINK", userId, deptId, payload, "转链成功");
        return result;
    }

    private PromotionApi.PromotionLinkResult fillMockPromotionLinkIfNeeded(
            PromotionApi.PromotionLinkResult result,
            String activityId,
            String productId) {
        if (!douyinMockEnabled) {
            return result;
        }
        if (result == null) {
            String suffix = String.valueOf(Math.abs((activityId + "-" + productId).hashCode()));
            return new PromotionApi.PromotionLinkResult(
                    "MOCK" + suffix.substring(0, Math.min(6, suffix.length())),
                    "https://mock.short.link/" + productId,
                    "https://mock.promote.link/activity/" + activityId + "/product/" + productId,
                    UUID.randomUUID().toString()
            );
        }
        if (StringUtils.hasText(result.shortLink()) || StringUtils.hasText(result.promoteLink())) {
            return result;
        }
        String suffix = String.valueOf(Math.abs((activityId + "-" + productId).hashCode()));
        return new PromotionApi.PromotionLinkResult(
                result.shortId(),
                "https://mock.short.link/" + productId,
                "https://mock.promote.link/activity/" + activityId + "/product/" + productId,
                StringUtils.hasText(result.uuidSeed()) ? result.uuidSeed() : UUID.randomUUID().toString()
        );
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
            throw new BusinessException("未找到商品快照，请先调用活动商品列表接口");
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
            return state;
        }
        state = new ProductOperationState();
        state.setActivityId(activityId);
        state.setProductId(productId);
        state.setAuditStatus(0);
        return state;
    }

    private void saveOperationState(ProductOperationState state) {
        if (state.getId() == null) {
            operationStateMapper.insert(state);
            return;
        }
        operationStateMapper.updateById(state);
    }

    private void saveOperationLog(
            String activityId,
            String productId,
            String operationType,
            UUID operatorId,
            UUID operatorDeptId,
            Map<String, Object> payload,
            String remark) {
        ProductOperationLog log = new ProductOperationLog();
        log.setActivityId(activityId);
        log.setProductId(productId);
        log.setOperationType(operationType);
        log.setOperatorId(operatorId);
        log.setOperatorDeptId(operatorDeptId);
        log.setOperationPayload(String.valueOf(payload));
        log.setOperationRemark(remark);
        operationLogMapper.insert(log);
    }

    private void fillSnapshot(ProductSnapshot snapshot, Map<String, Object> item) {
        snapshot.setTitle(asText(item.get("title")));
        snapshot.setCover(asText(item.get("cover")));
        snapshot.setPrice(asLong(item.get("price")));
        snapshot.setPriceText(asText(item.get("priceText")));
        snapshot.setShopId(asLong(item.get("shopId")));
        snapshot.setShopName(asText(item.get("shopName")));
        snapshot.setStatus(asInteger(item.get("status")));
        snapshot.setStatusText(asText(item.get("statusText")));
        snapshot.setCategoryName(asText(item.get("categoryName")));
        snapshot.setProductStock(asText(item.get("productStock")));
        snapshot.setSales(asLong(item.get("sales")));
        snapshot.setDetailUrl(asText(item.get("detailUrl")));
        snapshot.setPromotionStartTime(asText(item.get("promotionStartTime")));
        snapshot.setPromotionEndTime(asText(item.get("promotionEndTime")));
        snapshot.setActivityCosRatio(asLong(item.get("activityCosRatio")));
        snapshot.setActivityCosRatioText(asText(item.get("activityCosRatioText")));
        snapshot.setCosType(asInteger(item.get("cosType")));
        snapshot.setCosTypeText(asText(item.get("cosTypeText")));
        snapshot.setAdServiceRatio(asText(item.get("adServiceRatio")));
        snapshot.setActivityAdCosRatio(asLong(item.get("activityAdCosRatio")));
        snapshot.setHasDouinGoodsTag(asBoolean(item.get("hasDouinGoodsTag")));
        snapshot.setRawPayload(String.valueOf(item));
        snapshot.setSyncTime(LocalDateTime.now());
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

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignore) {
            return null;
        }
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignore) {
            return null;
        }
    }

    private Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        return "true".equals(text) || "1".equals(text) || "yes".equals(text);
    }
}
