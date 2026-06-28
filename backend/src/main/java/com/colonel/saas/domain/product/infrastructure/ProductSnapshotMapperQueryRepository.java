package com.colonel.saas.domain.product.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.domain.product.query.ProductSnapshotQueryRepository;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ProductSnapshotMapper;

import java.util.UUID;

/**
 * MyBatis-backed 商品快照读侧适配器。
 */
public class ProductSnapshotMapperQueryRepository implements ProductSnapshotQueryRepository {

    private final ProductSnapshotMapper snapshotMapper;

    public ProductSnapshotMapperQueryRepository(ProductSnapshotMapper snapshotMapper) {
        this.snapshotMapper = snapshotMapper;
    }

    @Override
    public IPage<ProductSnapshot> pageLatest(long page, long size, Integer status) {
        Page<ProductSnapshot> query = new Page<>(page, size);
        LambdaQueryWrapper<ProductSnapshot> wrapper = new LambdaQueryWrapper<ProductSnapshot>()
                .orderByDesc(ProductSnapshot::getSyncTime)
                .orderByDesc(ProductSnapshot::getCreateTime);
        if (status != null) {
            wrapper.eq(ProductSnapshot::getStatus, status);
        }
        return snapshotMapper.selectPage(query, wrapper);
    }

    @Override
    public ProductSnapshot findById(UUID id) {
        return snapshotMapper.selectById(id);
    }

    @Override
    public ProductSnapshot findActivityProduct(String activityId, String productId) {
        return snapshotMapper.selectOne(new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getActivityId, activityId)
                .eq(ProductSnapshot::getProductId, productId)
                .last("limit 1"));
    }
}
