package com.colonel.saas.domain.product.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.product.facade.dto.ProductReadDTO;
import com.colonel.saas.domain.product.facade.dto.ProductSnapshotReadDTO;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@link ProductDomainFacade} 遗留实现：委派现有 Mapper，零行为变更（DDD-PRODUCT-001）。
 */
@Service
public class LegacyProductDomainFacade implements ProductDomainFacade {

    private final ProductMapper productMapper;
    private final ProductSnapshotMapper productSnapshotMapper;
    private final ProductOperationStateMapper productOperationStateMapper;
    private final ColonelsettlementActivityMapper colonelsettlementActivityMapper;

    public LegacyProductDomainFacade(
            ProductMapper productMapper,
            ProductSnapshotMapper productSnapshotMapper,
            ProductOperationStateMapper productOperationStateMapper,
            ColonelsettlementActivityMapper colonelsettlementActivityMapper) {
        this.productMapper = productMapper;
        this.productSnapshotMapper = productSnapshotMapper;
        this.productOperationStateMapper = productOperationStateMapper;
        this.colonelsettlementActivityMapper = colonelsettlementActivityMapper;
    }

    @Override
    public ProductReadDTO findProductById(UUID productId) {
        if (productId == null) {
            return null;
        }
        return toProductRead(productMapper.selectById(productId));
    }

    @Override
    public ProductReadDTO findProductByExternalId(String externalProductId) {
        if (!StringUtils.hasText(externalProductId)) {
            return null;
        }
        Product product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getProductId, externalProductId.trim())
                .last("LIMIT 1"));
        return toProductRead(product);
    }

    @Override
    public ProductSnapshotReadDTO findSnapshotById(UUID snapshotId) {
        if (snapshotId == null) {
            return null;
        }
        return toSnapshotRead(productSnapshotMapper.selectById(snapshotId));
    }

    @Override
    public boolean existsById(UUID productId) {
        if (productId == null) {
            return false;
        }
        return productMapper.selectById(productId) != null;
    }

    @Override
    public Map<UUID, String> loadProductNamesByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        List<Product> products = productMapper.selectBatchIds(distinct);
        if (products == null || products.isEmpty()) {
            return Map.of();
        }
        return products.stream()
                .filter(product -> product.getId() != null && StringUtils.hasText(product.getName()))
                .collect(Collectors.toMap(
                        Product::getId,
                        Product::getName,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private static ProductReadDTO toProductRead(Product product) {
        if (product == null) {
            return null;
        }
        return new ProductReadDTO(
                product.getId(),
                product.getProductId(),
                product.getOuterProductId(),
                product.getName(),
                product.getCover(),
                product.getPrice());
    }

    @Override
    public UUID findProductAssigneeId(String activityId, String externalProductId) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(externalProductId)) {
            return null;
        }
        ProductOperationState state = productOperationStateMapper.selectOne(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getActivityId, activityId.trim())
                .eq(ProductOperationState::getProductId, externalProductId.trim())
                .last("LIMIT 1"));
        return state == null ? null : state.getAssigneeId();
    }

    @Override
    public UUID findActivityDefaultRecruiterId(String activityId) {
        if (!StringUtils.hasText(activityId)) {
            return null;
        }
        ColonelsettlementActivity activity = colonelsettlementActivityMapper.selectByActivityId(activityId.trim());
        return activity == null ? null : activity.getRecruiterUserId();
    }

    private static ProductSnapshotReadDTO toSnapshotRead(ProductSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new ProductSnapshotReadDTO(
                snapshot.getId(),
                snapshot.getActivityId(),
                snapshot.getProductId(),
                snapshot.getTitle(),
                snapshot.getCover(),
                snapshot.getShopId(),
                snapshot.getShopName(),
                snapshot.getPrice(),
                snapshot.getStatus(),
                snapshot.getDetailUrl());
    }
}
