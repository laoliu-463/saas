package com.colonel.saas.service;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductBizStatusService {

    private final ProductOperationStateMapper operationStateMapper;
    private final ProductOperationLogMapper operationLogMapper;

    public ProductBizStatusService(
            ProductOperationStateMapper operationStateMapper,
            ProductOperationLogMapper operationLogMapper) {
        this.operationStateMapper = operationStateMapper;
        this.operationLogMapper = operationLogMapper;
    }

    public ProductBizStatus readBizStatus(ProductOperationState state) {
        try {
            ProductBizStatus bizStatus = ProductBizStatus.fromCode(state == null ? null : state.getBizStatus());
            return bizStatus == null ? ProductBizStatus.PENDING_AUDIT : bizStatus;
        } catch (IllegalArgumentException ex) {
            return ProductBizStatus.PENDING_AUDIT;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductOperationState initStateIfAbsent(
            ProductOperationState existing,
            String activityId,
            String productId,
            UUID operatorId,
            UUID operatorDeptId,
            String remark) {
        if (existing != null) {
            return existing;
        }
        ProductOperationState state = new ProductOperationState();
        state.setActivityId(activityId);
        state.setProductId(productId);
        state.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());
        state.setAuditStatus(1);
        state.setLastOperationAt(LocalDateTime.now());
        state.setId(UUID.randomUUID());
        operationStateMapper.insert(state);
        writeLog(activityId, productId, "SYNC", null, ProductBizStatus.PENDING_AUDIT,
                operatorId, operatorDeptId, Map.of("bizStatus", ProductBizStatus.PENDING_AUDIT.name()), remark, true, null);
        return state;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductOperationState changeStatus(
            ProductOperationState state,
            ProductBizStatus targetStatus,
            String operationType,
            UUID operatorId,
            UUID operatorDeptId,
            Map<String, Object> payload,
            String remark,
            StatusMutation mutation) {
        ProductBizStatus beforeStatus = readBizStatus(state);
        ensureAllowed(beforeStatus, targetStatus, operationType);
        mutation.apply(state);
        state.setBizStatus(targetStatus.name());
        state.setLastOperationAt(LocalDateTime.now());
        if (state.getId() == null) {
            state.setId(UUID.randomUUID());
            operationStateMapper.insert(state);
        } else {
            operationStateMapper.updateById(state);
        }
        writeLog(state.getActivityId(), state.getProductId(), operationType, beforeStatus, targetStatus,
                operatorId, operatorDeptId, payload, remark, true, null);
        return state;
    }

    @Transactional(rollbackFor = Exception.class)
    public void logFailure(
            String activityId,
            String productId,
            ProductBizStatus beforeStatus,
            String operationType,
            UUID operatorId,
            UUID operatorDeptId,
            Map<String, Object> payload,
            String remark,
            String errorMessage) {
        writeLog(activityId, productId, operationType, beforeStatus, beforeStatus,
                operatorId, operatorDeptId, payload, remark, false, errorMessage);
    }

    private void ensureAllowed(ProductBizStatus beforeStatus, ProductBizStatus targetStatus, String operationType) {
        boolean allowed = switch (targetStatus) {
            case APPROVED -> beforeStatus == ProductBizStatus.PENDING_AUDIT;
            case REJECTED -> beforeStatus == ProductBizStatus.PENDING_AUDIT;
            case BOUND -> false;
            case ASSIGNED -> beforeStatus == ProductBizStatus.APPROVED || beforeStatus == ProductBizStatus.BOUND;
            case LINKED -> beforeStatus == ProductBizStatus.ASSIGNED;
            case FOLLOWING -> beforeStatus == ProductBizStatus.LINKED;
            case PENDING_AUDIT -> false;
        };
        if (!allowed) {
            throw new BusinessException("当前状态不允许执行" + operationType + "，当前状态：" + beforeStatus.name());
        }
    }

    private void writeLog(
            String activityId,
            String productId,
            String operationType,
            ProductBizStatus beforeStatus,
            ProductBizStatus afterStatus,
            UUID operatorId,
            UUID operatorDeptId,
            Map<String, Object> payload,
            String remark,
            boolean success,
            String errorMessage) {
        ProductOperationLog log = new ProductOperationLog();
        log.setActivityId(activityId);
        log.setProductId(productId);
        log.setOperationType(operationType);
        log.setBeforeStatus(beforeStatus == null ? null : beforeStatus.name());
        log.setAfterStatus(afterStatus == null ? null : afterStatus.name());
        log.setSuccess(success);
        log.setErrorMessage(errorMessage);
        log.setOperatorId(operatorId);
        log.setOperatorDeptId(operatorDeptId);
        log.setOperationPayload(String.valueOf(payload));
        log.setOperationRemark(remark);
        log.setId(UUID.randomUUID());
        operationLogMapper.insert(log);
    }

    @FunctionalInterface
    public interface StatusMutation {
        void apply(ProductOperationState state);
    }
}
