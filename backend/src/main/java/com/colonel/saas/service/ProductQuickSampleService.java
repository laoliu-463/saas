package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.sample.api.ApplySampleFromProductCommand;
import com.colonel.saas.domain.sample.api.ApplySampleFromProductResult;
import com.colonel.saas.domain.sample.api.SampleApplicationPort;
import com.colonel.saas.dto.product.QuickSampleApplyRequest;
import com.colonel.saas.dto.product.QuickSampleApplyResponse;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.gateway.douyin.DouyinQuickSampleGateway;
import com.colonel.saas.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 商品快速寄样服务（DDD-PRODUCT-005 重构版）。
 * <p>
 * 商品域只负责：角色校验 → 商品上下文解析 → 构建寄样命令 → 委托寄样域端口 → 映射响应。
 * 寄样创建（达人解析、私海校验、去重、资质评估、外部网关调用、寄样单落库、状态机、领域事件）
 * 全部由 {@link SampleApplicationPort} 在寄样域内完成。
 * </p>
 *
 * @see SampleApplicationPort
 * @see DouyinQuickSampleGateway
 */
@Slf4j
@Service
public class ProductQuickSampleService {

    /** 申请来源：快速商品库入口 */
    public static final String APPLY_SOURCE_QUICK_PRODUCT_LIBRARY = "quick_product_library";
    /** 申请来源：抖店外部快速寄样 */
    public static final String APPLY_SOURCE_DOUYIN_QUICK = "DOUYIN_QUICK_SAMPLE";
    /** 申请来源：系统内本地降级 */
    public static final String APPLY_SOURCE_LOCAL_FALLBACK = "LOCAL_FALLBACK";
    /** 降级类型标识：本地降级 */
    public static final String FALLBACK_TYPE_LOCAL = "LOCAL_FALLBACK";
    /** 网关状态：当前 SDK 不支持 */
    public static final String GATEWAY_STATUS_UNSUPPORTED = "UNSUPPORTED_BY_SDK";
    /** 降级提示消息 */
    public static final String FALLBACK_MESSAGE = "抖店外部寄样暂未接通，已创建系统内寄样申请";

    /** 商品服务，用于查询商品信息 */
    private final ProductService productService;
    /** 商品主表 Mapper，用于在寄样落库前确保 sample_request.product_id 有有效外键 */
    private final ProductMapper productMapper;
    /** 商品快照 Mapper，用于获取商品快照详情 */
    private final ProductSnapshotMapper productSnapshotMapper;
    /** 商品运营状态 Mapper，用于校验展示状态 */
    private final ProductOperationStateMapper productOperationStateMapper;
    /** 抖店快速寄样网关，用于检查网关状态 */
    private final DouyinQuickSampleGateway douyinQuickSampleGateway;
    /** 寄样域应用端口 — 实际寄样创建由此完成（DDD-PRODUCT-005） */
    private final SampleApplicationPort sampleApplicationPort;
    /** 是否启用抖店外部快速寄样 */
    private final boolean douyinQuickSampleEnabled;

    /**
     * 构造注入。
     *
     * @param productService               商品服务
     * @param productMapper                商品主表 Mapper
     * @param productSnapshotMapper        商品快照 Mapper
     * @param productOperationStateMapper  商品运营状态 Mapper
     * @param douyinQuickSampleGateway     抖店快速寄样网关
     * @param sampleApplicationPort        寄样域应用端口
     * @param douyinQuickSampleEnabled     是否启用外部快速寄样
     */
    public ProductQuickSampleService(
            ProductService productService,
            ProductMapper productMapper,
            ProductSnapshotMapper productSnapshotMapper,
            ProductOperationStateMapper productOperationStateMapper,
            DouyinQuickSampleGateway douyinQuickSampleGateway,
            SampleApplicationPort sampleApplicationPort,
            @Value("${app.douyin.quick-sample.enabled:false}") boolean douyinQuickSampleEnabled) {
        this.productService = productService;
        this.productMapper = productMapper;
        this.productSnapshotMapper = productSnapshotMapper;
        this.productOperationStateMapper = productOperationStateMapper;
        this.douyinQuickSampleGateway = douyinQuickSampleGateway;
        this.sampleApplicationPort = sampleApplicationPort;
        this.douyinQuickSampleEnabled = douyinQuickSampleEnabled;
    }

    /**
     * 批量发起快速寄样申请。
     * <p>
     * 商品域只负责角色校验、商品上下文解析和命令构建，
     * 实际的寄样创建委托给 {@link SampleApplicationPort}。
     * </p>
     *
     * @param relationId 商品 relationId（Product 主键）
     * @param request    寄样申请请求（含达人列表、收件信息、备注等）
     * @param userId     操作用户 ID
     * @param deptId     操作用户部门 ID
     * @param roleCodes  操作用户角色集合（Collection 或逗号分隔字符串）
     * @return 申请结果汇总（含每个达人的明细）
     * @throws BusinessException 商品不存在或非展示状态时
     * @throws ForbiddenException 非渠道角色时
     */
    @Transactional(rollbackFor = Exception.class)
    public QuickSampleApplyResponse applyQuickSample(
            UUID relationId,
            QuickSampleApplyRequest request,
            UUID userId,
            UUID deptId,
            Object roleCodes) {
        /* 第一步：校验调用方必须是渠道角色或管理员 */
        ensureChannelRole(roleCodes);
        /* 第二步：解析商品库快照，并确保 product 主表存在 */
        QuickSampleProductContext productContext = resolveQuickSampleProductContext(relationId);

        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        UUID effectiveChannelUserId = request.getChannelUserId() != null ? request.getChannelUserId() : userId;

        /* 检查外部网关状态 */
        boolean externalEnabled = douyinQuickSampleEnabled;
        boolean externalSupported = douyinQuickSampleGateway.isSupported();
        DouyinQuickSampleGateway.SupportStatus supportStatus = douyinQuickSampleGateway.supportStatus();

        /* 构建命令，委托给寄样域 */
        ApplySampleFromProductCommand command = new ApplySampleFromProductCommand(
                relationId,
                productContext.snapshot().getProductId(),
                deptId,
                request.getTalentIds(),
                request.getSpecification(),
                quantity,
                request.getRemark(),
                request.getRecipientName(),
                request.getRecipientPhone(),
                request.getRecipientAddress(),
                APPLY_SOURCE_QUICK_PRODUCT_LIBRARY,
                userId,
                effectiveChannelUserId,
                roleCodes,
                productContext.snapshot().getTitle(),
                productContext.snapshot().getPrice(),
                productContext.snapshot().getActivityId(),
                productContext.state() == null ? null : productContext.state().getAssigneeId(),
                externalEnabled,
                externalSupported,
                request.getSkuId()
        );

        /* 委托寄样域执行 */
        ApplySampleFromProductResult portResult = sampleApplicationPort.applyFromProduct(command);

        /* 映射端口结果到前端响应 DTO */
        QuickSampleApplyResponse response = new QuickSampleApplyResponse();
        response.setExternalEnabled(externalEnabled);
        response.setExternalSupported(externalSupported);
        response.setGatewayStatus(supportStatus == null ? null : supportStatus.name());
        response.setFallbackType(!externalSupported ? FALLBACK_TYPE_LOCAL : null);
        response.setMessage(!externalSupported ? FALLBACK_MESSAGE : null);
        response.setSuccessCount(portResult.getSuccessCount());
        response.setFailureCount(portResult.getFailureCount());
        response.setSuccess(portResult.isSuccess());

        for (ApplySampleFromProductResult.TalentResult tr : portResult.getItems()) {
            QuickSampleApplyResponse.QuickSampleApplyItemResult item =
                    new QuickSampleApplyResponse.QuickSampleApplyItemResult();
            item.setTalentId(tr.getTalentId());
            item.setSuccess(tr.isSuccess());
            item.setSampleRequestId(tr.getSampleRequestId());
            item.setExternalApplied(tr.isExternalApplied());
            item.setExternalApplyId(tr.getExternalApplyId());
            item.setFallback(tr.isFallback());
            item.setGatewayStatus(tr.getGatewayStatus());
            item.setFallbackType(tr.getFallbackType());
            item.setMessage(tr.getMessage());
            response.getItems().add(item);
        }

        return response;
    }

    // --- Product-domain private helpers ---

    private QuickSampleProductContext resolveQuickSampleProductContext(UUID relationId) {
        Product legacyProduct = productService.getById(relationId);
        if (legacyProduct == null) {
            throw BusinessException.notFound("商品不存在或已不在商品库，请刷新商品后重试");
        }
        ProductSnapshot snapshot = productSnapshotMapper.selectById(legacyProduct.getId());
        if (snapshot == null || !StringUtils.hasText(snapshot.getProductId())) {
            throw BusinessException.notFound("商品快照不存在或商品 ID 缺失，请刷新商品后重试");
        }
        ProductOperationState state = resolveDisplayingState(snapshot);
        ensurePersistedProduct(snapshot);
        return new QuickSampleProductContext(legacyProduct, snapshot, state);
    }

    private void ensurePersistedProduct(ProductSnapshot snapshot) {
        Product existing = findPersistedProduct(snapshot.getProductId());
        if (existing != null) {
            return;
        }
        Product product = materializeProductFromSnapshot(snapshot);
        try {
            productMapper.insert(product);
        } catch (DuplicateKeyException ex) {
            Product raced = findPersistedProduct(snapshot.getProductId());
            if (raced != null) {
                return;
            }
            throw ex;
        }
    }

    private Product findPersistedProduct(String productId) {
        if (!StringUtils.hasText(productId)) {
            return null;
        }
        return productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getProductId, productId)
                .last("LIMIT 1"));
    }

    private Product materializeProductFromSnapshot(ProductSnapshot snapshot) {
        Product product = new Product();
        product.setId(snapshot.getId());
        product.setProductId(snapshot.getProductId());
        product.setName(StringUtils.hasText(snapshot.getTitle()) ? snapshot.getTitle() : snapshot.getProductId());
        product.setPrice(snapshot.getPrice());
        product.setCover(snapshot.getCover());
        product.setDetailUrl(snapshot.getDetailUrl());
        product.setStatus(snapshot.getStatus() == null ? 1 : snapshot.getStatus());
        product.setCheckStatus(2);
        return product;
    }

    private ProductOperationState resolveDisplayingState(ProductSnapshot snapshot) {
        ProductOperationState state = productOperationStateMapper.selectOne(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getActivityId, snapshot.getActivityId())
                .eq(ProductOperationState::getProductId, snapshot.getProductId())
                .last("LIMIT 1"));
        if (state == null || !ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus())) {
            throw BusinessException.stateInvalid("仅展示中的商品可发起快速寄样");
        }
        if (!Boolean.TRUE.equals(state.getSelectedToLibrary())) {
            throw BusinessException.stateInvalid("该商品尚未加入商品库，请先审核并加入商品库后再进行寄样操作");
        }
        return state;
    }

    private void ensureChannelRole(Object roleCodes) {
        if (!hasAnyRole(roleCodes, RoleCodes.CHANNEL_STAFF, RoleCodes.CHANNEL_LEADER)
                && !hasAnyRole(roleCodes, RoleCodes.ADMIN)) {
            throw new ForbiddenException("仅渠道角色可使用快速寄样");
        }
    }

    private record QuickSampleProductContext(
            Product product,
            ProductSnapshot snapshot,
            ProductOperationState state) {
    }

    private boolean hasAnyRole(Object roleCodes, String... expectedRoles) {
        if (roleCodes == null || expectedRoles == null || expectedRoles.length == 0) {
            return false;
        }
        java.util.Set<String> expected = java.util.Arrays.stream(expectedRoles)
                .map(role -> role == null ? "" : role.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        if (roleCodes instanceof Collection<?> collection) {
            return collection.stream()
                    .map(item -> item == null ? "" : item.toString().trim().toLowerCase(Locale.ROOT))
                    .anyMatch(expected::contains);
        }
        String raw = roleCodes.toString();
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        for (String role : raw.replace("[", "").replace("]", "").split(",")) {
            if (expected.contains(role.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
