package com.colonel.saas.domain.product.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.common.exception.ValidateException;
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
import java.util.Set;
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
    public ProductReadDTO findOrMaterializeSampleProduct(UUID productId) {
        if (productId == null) {
            throw new ValidateException("Selected product does not exist");
        }
        Product product = productMapper.selectById(productId);
        if (product != null) {
            return toProductRead(product);
        }
        ProductSnapshot snapshot = productSnapshotMapper.selectById(productId);
        if (snapshot != null && StringUtils.hasText(snapshot.getProductId())) {
            product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                    .eq(Product::getProductId, snapshot.getProductId())
                    .last("LIMIT 1"));
            if (product == null) {
                product = materializeProductFromSnapshot(snapshot);
                productMapper.insert(product);
            }
        }
        if (product == null) {
            throw new ValidateException("Selected product does not exist");
        }
        return toProductRead(product);
    }

    @Override
    public boolean isSelectedToLibraryForSample(UUID productId) {
        if (productId == null) {
            return false;
        }
        ProductSnapshot snapshot = productSnapshotMapper.selectById(productId);
        if (snapshot == null) {
            return true;
        }
        ProductOperationState state = productOperationStateMapper.selectOne(
                new LambdaQueryWrapper<ProductOperationState>()
                        .eq(ProductOperationState::getActivityId, snapshot.getActivityId())
                        .eq(ProductOperationState::getProductId, snapshot.getProductId())
                        .last("LIMIT 1"));
        return state != null && Boolean.TRUE.equals(state.getSelectedToLibrary());
    }

    @Override
    public boolean isSampleProductAssignedToUser(UUID productId, UUID userId) {
        if (productId == null || userId == null) {
            return false;
        }
        String sourceProductId = resolveSampleSourceProductId(productId);
        if (!StringUtils.hasText(sourceProductId)) {
            return false;
        }
        return productOperationStateMapper.selectCount(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getProductId, sourceProductId)
                .eq(ProductOperationState::getAssigneeId, userId)) > 0;
    }

    @Override
    public Set<UUID> findProductIdsByKeyword(String keyword, long limit) {
        if (!StringUtils.hasText(keyword) || limit <= 0) {
            return Set.of();
        }
        QueryWrapper<Product> wrapper = new QueryWrapper<Product>()
                .select("id")
                .and(query -> query.like("name", keyword).or().like("product_id", keyword))
                .last("LIMIT " + limit);
        return productMapper.selectList(wrapper).stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Override
    public Set<UUID> findProductSnapshotIdsByShopKeyword(String keyword, long limit) {
        if (!StringUtils.hasText(keyword) || limit <= 0) {
            return Set.of();
        }
        QueryWrapper<ProductSnapshot> wrapper = new QueryWrapper<ProductSnapshot>()
                .select("id")
                .and(query -> {
                    query.like("shop_name", keyword);
                    Long shopId = parseLongOrNull(keyword);
                    if (shopId != null) {
                        query.or().eq("shop_id", shopId);
                    }
                })
                .last("LIMIT " + limit);
        return productSnapshotMapper.selectList(wrapper).stream()
                .map(ProductSnapshot::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Override
    public Map<UUID, ProductReadDTO> loadProductsByIds(Collection<UUID> ids) {
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
        Map<UUID, ProductReadDTO> map = new LinkedHashMap<>();
        for (Product product : products) {
            if (product != null && product.getId() != null) {
                map.put(product.getId(), toProductRead(product));
            }
        }
        return map;
    }

    @Override
    public UUID findProductSnapshotAssigneeId(UUID productId) {
        if (productId == null) {
            return null;
        }
        ProductSnapshot snapshot = productSnapshotMapper.selectById(productId);
        if (snapshot == null) {
            return null;
        }
        ProductOperationState state = productOperationStateMapper.selectOne(
                new LambdaQueryWrapper<ProductOperationState>()
                        .eq(ProductOperationState::getActivityId, snapshot.getActivityId())
                        .eq(ProductOperationState::getProductId, snapshot.getProductId())
                        .last("LIMIT 1"));
        return state == null ? null : state.getAssigneeId();
    }

    @Override
    public String resolveSampleSourceProductId(UUID productId) {
        if (productId == null) {
            return null;
        }
        Product product = productMapper.selectById(productId);
        if (product != null && StringUtils.hasText(product.getProductId())) {
            return product.getProductId();
        }
        ProductSnapshot snapshot = productSnapshotMapper.selectById(productId);
        if (snapshot != null && StringUtils.hasText(snapshot.getProductId())) {
            return snapshot.getProductId();
        }
        return null;
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

    private static Product materializeProductFromSnapshot(ProductSnapshot snapshot) {
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

    private static Long parseLongOrNull(String value) {
        try {
            return StringUtils.hasText(value) ? Long.parseLong(value.trim()) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
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
                product.getPrice(),
                product.getActivityId(),
                product.getDetailUrl(),
                product.getStatus(),
                product.getCheckStatus());
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
                snapshot.getPriceText(),
                snapshot.getStatus(),
                snapshot.getDetailUrl());
    }
}
