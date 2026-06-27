package com.colonel.saas.domain.product.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ProductSnapshotMapper;

import java.util.UUID;

/**
 * 商品快照读侧查询服务。
 */
public class ProductSnapshotQueryService {

    private final ProductSnapshotMapper snapshotMapper;

    public ProductSnapshotQueryService(ProductSnapshotMapper snapshotMapper) {
        this.snapshotMapper = snapshotMapper;
    }

    public IPage<ProductSnapshot> pageLatest(long page, long size, Integer status) {
        Page<ProductSnapshot> query = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<ProductSnapshot> wrapper = new LambdaQueryWrapper<ProductSnapshot>()
                .orderByDesc(ProductSnapshot::getSyncTime)
                .orderByDesc(ProductSnapshot::getCreateTime);
        if (status != null) {
            wrapper.eq(ProductSnapshot::getStatus, status);
        }
        return snapshotMapper.selectPage(query, wrapper);
    }

    public ProductSnapshot findById(UUID id) {
        if (id == null) {
            return null;
        }
        return snapshotMapper.selectById(id);
    }

    public ProductSnapshot requireById(UUID id) {
        ProductSnapshot snapshot = findById(id);
        if (snapshot == null) {
            throw BusinessException.notFound("商品不存在");
        }
        return snapshot;
    }

    public ProductSnapshot findActivityProduct(String activityId, String productId) {
        return snapshotMapper.selectOne(new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getActivityId, activityId)
                .eq(ProductSnapshot::getProductId, productId)
                .last("limit 1"));
    }

    public ProductSnapshot requireActivityProduct(String activityId, String productId) {
        ProductSnapshot snapshot = findActivityProduct(activityId, productId);
        if (snapshot == null) {
            throw BusinessException.notFound("未找到商品快照，请先同步活动商品");
        }
        return snapshot;
    }
}
