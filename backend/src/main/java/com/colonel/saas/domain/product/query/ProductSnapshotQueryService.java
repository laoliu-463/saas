package com.colonel.saas.domain.product.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ProductSnapshot;

import java.util.UUID;

/**
 * 商品快照读侧查询服务。
 */
public class ProductSnapshotQueryService {

    private final ProductSnapshotQueryRepository snapshotRepository;

    public ProductSnapshotQueryService(ProductSnapshotQueryRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    public IPage<ProductSnapshot> pageLatest(long page, long size, Integer status) {
        return snapshotRepository.pageLatest(Math.max(page, 1), Math.max(size, 1), status);
    }

    public ProductSnapshot findById(UUID id) {
        if (id == null) {
            return null;
        }
        return snapshotRepository.findById(id);
    }

    public ProductSnapshot requireById(UUID id) {
        ProductSnapshot snapshot = findById(id);
        if (snapshot == null) {
            throw BusinessException.notFound("商品不存在");
        }
        return snapshot;
    }

    public ProductSnapshot findActivityProduct(String activityId, String productId) {
        return snapshotRepository.findActivityProduct(activityId, productId);
    }

    public ProductSnapshot requireActivityProduct(String activityId, String productId) {
        ProductSnapshot snapshot = findActivityProduct(activityId, productId);
        if (snapshot == null) {
            throw BusinessException.notFound("未找到商品快照，请先同步活动商品");
        }
        return snapshot;
    }
}
