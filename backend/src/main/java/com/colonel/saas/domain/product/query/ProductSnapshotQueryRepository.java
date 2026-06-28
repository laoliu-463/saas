package com.colonel.saas.domain.product.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.entity.ProductSnapshot;

import java.util.UUID;

/**
 * 商品快照读侧持久化端口。
 */
public interface ProductSnapshotQueryRepository {

    IPage<ProductSnapshot> pageLatest(long page, long size, Integer status);

    ProductSnapshot findById(UUID id);

    ProductSnapshot findActivityProduct(String activityId, String productId);
}
