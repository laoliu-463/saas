package com.colonel.saas.domain.product.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.product.query.ActivityProductReadModelRepository;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.Merchant;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.MerchantMapper;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;

import java.util.List;
import java.util.Set;

/**
 * MyBatis-backed 活动商品读模型适配器。
 */
public class ActivityProductReadModelMapperRepository implements ActivityProductReadModelRepository {

    private final ProductOperationStateMapper operationStateMapper;
    private final ProductOperationLogMapper operationLogMapper;
    private final MerchantMapper merchantMapper;
    private final ColonelsettlementActivityMapper colonelActivityMapper;

    public ActivityProductReadModelMapperRepository(
            ProductOperationStateMapper operationStateMapper,
            ProductOperationLogMapper operationLogMapper,
            MerchantMapper merchantMapper,
            ColonelsettlementActivityMapper colonelActivityMapper) {
        this.operationStateMapper = operationStateMapper;
        this.operationLogMapper = operationLogMapper;
        this.merchantMapper = merchantMapper;
        this.colonelActivityMapper = colonelActivityMapper;
    }

    @Override
    public List<ProductOperationState> findStates(String activityId, Set<String> productIds) {
        return operationStateMapper.selectList(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getActivityId, activityId)
                .in(ProductOperationState::getProductId, productIds));
    }

    @Override
    public List<ProductOperationLog> findDecisionLogs(String activityId, Set<String> productIds) {
        return operationLogMapper.selectList(new LambdaQueryWrapper<ProductOperationLog>()
                .eq(ProductOperationLog::getActivityId, activityId)
                .eq(ProductOperationLog::getOperationType, "DECISION")
                .in(ProductOperationLog::getProductId, productIds)
                .orderByDesc(ProductOperationLog::getCreateTime));
    }

    @Override
    public ProductOperationLog findLatestDecisionLog(String activityId, String productId) {
        return operationLogMapper.selectOne(new LambdaQueryWrapper<ProductOperationLog>()
                .eq(ProductOperationLog::getActivityId, activityId)
                .eq(ProductOperationLog::getProductId, productId)
                .eq(ProductOperationLog::getOperationType, "DECISION")
                .orderByDesc(ProductOperationLog::getCreateTime)
                .last("limit 1"));
    }

    @Override
    public List<Merchant> findMerchants(Set<Long> shopIds) {
        return merchantMapper.selectList(new LambdaQueryWrapper<Merchant>()
                .in(Merchant::getShopId, shopIds));
    }

    @Override
    public Merchant findMerchant(Long shopId) {
        return merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getShopId, shopId)
                .last("limit 1"));
    }

    @Override
    public String findActivityName(String activityId) {
        ColonelsettlementActivity activity = colonelActivityMapper.selectByActivityId(activityId);
        return activity == null ? null : activity.getName();
    }
}
