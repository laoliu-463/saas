package com.colonel.saas.service.display;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.service.ProductDisplayRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AdminProductDisplayService {

    private final ProductOperationStateMapper operationStateMapper;
    private final ProductDisplayRuleService productDisplayRuleService;
    private final ProductDomainEventPublisher productDomainEventPublisher;

    public AdminProductDisplayService(
            ProductOperationStateMapper operationStateMapper,
            ProductDisplayRuleService productDisplayRuleService,
            ProductDomainEventPublisher productDomainEventPublisher) {
        this.operationStateMapper = operationStateMapper;
        this.productDisplayRuleService = productDisplayRuleService;
        this.productDomainEventPublisher = productDomainEventPublisher;
    }

    @Transactional(rollbackFor = Exception.class)
    public void forceSwitch(UUID relationId, UUID adminId, String reason, LocalDateTime until) {
        ProductOperationState state = operationStateMapper.selectById(relationId);
        if (state == null) {
            throw BusinessException.notFound("商品关联不存在");
        }
        state.setForceDisplay(true);
        state.setForceDisplayBy(adminId);
        state.setForceDisplayReason(reason);
        state.setForceDisplayUntil(until);
        state.setDisplayPriority(Integer.MAX_VALUE);
        OptimisticLockSupport.requireUpdated(operationStateMapper.updateById(state));
        productDomainEventPublisher.publishForceDisplayChanged(
                relationId, state.getProductId(), true, adminId, reason, until);
        productDisplayRuleService.applyForProductId(state.getProductId(), DisplayRuleOperatorContext.admin(adminId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelForce(UUID relationId, UUID adminId) {
        ProductOperationState state = operationStateMapper.selectById(relationId);
        if (state == null) {
            throw BusinessException.notFound("商品关联不存在");
        }
        state.setForceDisplay(false);
        state.setForceDisplayBy(null);
        state.setForceDisplayReason(null);
        state.setForceDisplayUntil(null);
        state.setDisplayPriority(0);
        OptimisticLockSupport.requireUpdated(operationStateMapper.updateById(state));
        productDomainEventPublisher.publishForceDisplayChanged(
                relationId, state.getProductId(), false, adminId, null, null);
        productDisplayRuleService.applyForProductId(state.getProductId(), DisplayRuleOperatorContext.admin(adminId));
    }
}
