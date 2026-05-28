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

/**
 * 管理员商品强制展示服务。
 *
 * <p>职责：提供管理员对商品展示状态的手动干预能力，包括强制展示（forceSwitch）
 * 和取消强制展示（cancelForce）。操作完成后自动触发展示规则引擎重新评估，
 * 并发布领域事件供下游监听。
 *
 * <p>业务规则：
 * <ul>
 *   <li>强制展示时，展示优先级设为 {@code Integer.MAX_VALUE}（最高优先级）</li>
 *   <li>强制展示可设置有效期（until），到期后由定时任务自动取消</li>
 *   <li>所有操作使用乐观锁（{@link OptimisticLockSupport}）保证并发安全</li>
 *   <li>操作完成后发布 {@code ForceDisplayChanged} 领域事件</li>
 * </ul>
 *
 * <p>依赖服务/仓储：
 * <ul>
 *   <li>{@link ProductOperationStateMapper} —— 商品操作状态数据访问</li>
 *   <li>{@link ProductDisplayRuleService} —— 展示规则引擎（操作后触发重新评估）</li>
 *   <li>{@link ProductDomainEventPublisher} —— 商品领域事件发布器</li>
 * </ul>
 */
@Service
public class AdminProductDisplayService {

    /** 商品操作状态数据访问（存储强制展示标志、优先级等） */
    private final ProductOperationStateMapper operationStateMapper;
    /** 展示规则引擎，强制展示/取消后触发重新评估 */
    private final ProductDisplayRuleService productDisplayRuleService;
    /** 商品领域事件发布器，用于发布强制展示变更事件 */
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
