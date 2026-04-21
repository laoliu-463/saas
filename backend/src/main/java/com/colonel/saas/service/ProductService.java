package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.douyin.api.PromotionApi;
import com.colonel.saas.entity.Product;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductService {

    private final List<Product> mockProducts = new ArrayList<>();
    private final Map<UUID, UUID> assigneeStore = new HashMap<>();
    private final Map<UUID, String> auditRemarkStore = new HashMap<>();
    private final PromotionApi promotionApi;

    public ProductService(PromotionApi promotionApi) {
        this.promotionApi = promotionApi;
        initMockData();
    }

    public IPage<Product> getPage(long page, long size, Integer status) {
        List<Product> filtered = mockProducts.stream()
                .filter(product -> status == null || status.equals(product.getStatus()))
                .sorted(Comparator.comparing(Product::getCreateTime).reversed())
                .map(this::hydrateTransientFields)
                .toList();

        long current = Math.max(page, 1);
        long pageSize = Math.max(size, 1);
        int fromIndex = (int) ((current - 1) * pageSize);
        int toIndex = Math.min(fromIndex + (int) pageSize, filtered.size());

        List<Product> records = fromIndex >= filtered.size()
                ? List.of()
                : filtered.subList(fromIndex, toIndex);

        Page<Product> result = new Page<>(current, pageSize);
        result.setTotal(filtered.size());
        result.setRecords(records);
        return result;
    }

    public Product getById(UUID id) {
        Product product = mockProducts.stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException("product not found"));
        return hydrateTransientFields(product);
    }

    public Product bindActivity(UUID id, UUID activityId) {
        Product product = getById(id);
        product.setActivityId(activityId);
        return product;
    }

    public Product assignProduct(UUID id, UUID assigneeId) {
        if (assigneeId == null) {
            throw new BusinessException("assigneeId cannot be empty");
        }
        Product product = getById(id);
        assigneeStore.put(id, assigneeId);
        product.setAssigneeId(assigneeId);
        return product;
    }

    public Product auditProduct(UUID id, boolean approved, String reason) {
        Product product = getById(id);
        if (!approved && !StringUtils.hasText(reason)) {
            throw new BusinessException("reason is required when reject product");
        }
        product.setCheckStatus(approved ? 2 : 3);
        if (approved) {
            product.setStatus(1);
            auditRemarkStore.remove(id);
            product.setAuditRemark(null);
        } else {
            product.setStatus(0);
            auditRemarkStore.put(id, reason);
            product.setAuditRemark(reason);
        }
        return product;
    }

    public PromotionApi.PromotionLinkResult generatePromotionLink(
            UUID id,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink) {
        Product product = getById(id);
        String finalExternalId = StringUtils.hasText(externalUniqueId) ? externalUniqueId : String.valueOf(userId);
        int finalPromotionScene = promotionScene == null ? 4 : promotionScene;
        String productCode = StringUtils.hasText(product.getProductId()) ? product.getProductId() : product.getId().toString();

        PromotionApi.PromotionLinkResult result = promotionApi.generateLink(
                finalExternalId,
                finalPromotionScene,
                List.of(productCode),
                needShortLink,
                new PromotionApi.PromotionContext(
                        userId,
                        deptId,
                        productCode,
                        product.getActivityId() == null ? null : product.getActivityId().toString(),
                        null
                )
        );
        product.setPromoteLink(result.promoteLink());
        product.setShortLink(result.shortLink());
        return result;
    }

    private Product hydrateTransientFields(Product product) {
        product.setAssigneeId(assigneeStore.get(product.getId()));
        product.setAuditRemark(auditRemarkStore.get(product.getId()));
        return product;
    }

    private void initMockData() {
        UUID actA = UUID.nameUUIDFromBytes("activity-A".getBytes());
        UUID actB = UUID.nameUUIDFromBytes("activity-B".getBytes());
        for (int i = 1; i <= 10; i++) {
            Product product = new Product();
            product.setId(UUID.nameUUIDFromBytes(("product-" + i).getBytes()));
            product.setProductId("dy_product_" + i);
            product.setName("test product-" + i);
            product.setPrice(1990L + i * 500L);
            product.setStatus(i % 3 == 0 ? 0 : 1);
            product.setCheckStatus(1);
            product.setCategory(i % 2 == 0 ? "beauty" : "clothes");
            product.setActivityId(i % 2 == 0 ? actA : actB);
            product.setCreateTime(LocalDateTime.now().minusDays(i));
            mockProducts.add(product);
        }
    }
}
