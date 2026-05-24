package com.colonel.saas.service.display;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import com.colonel.saas.entity.ProductDisplayAuditLog;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.mapper.ProductDisplayAuditLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.service.ProductDisplayRuleService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProductDisplayAuditService {

    private final ProductDisplayAuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public ProductDisplayAuditService(ProductDisplayAuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    public void writeAudit(
            String productId,
            UUID oldRelationId,
            UUID newRelationId,
            List<UUID> candidateIds,
            String actionType,
            String selectedReason,
            String hiddenReason,
            int ruleVersion,
            DisplayRuleOperatorContext operator,
            Object detail) {
        ProductDisplayAuditLog log = new ProductDisplayAuditLog();
        log.setId(UUID.randomUUID());
        log.setProductId(productId);
        log.setOldRelationId(oldRelationId);
        log.setNewRelationId(newRelationId);
        log.setCandidateRelationIds(toJson(candidateIds));
        log.setActionType(actionType);
        log.setSelectedReason(selectedReason);
        log.setHiddenReason(hiddenReason);
        log.setRuleVersion(ruleVersion);
        log.setOperatorType(operator.operatorType());
        log.setOperatorId(operator.operatorId() == null ? null : operator.operatorId().toString());
        log.setDetailJson(detail == null ? null : toJson(detail));
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    public Page<ProductDisplayAuditLog> pageAuditLogs(String productId, long page, long size) {
        Page<ProductDisplayAuditLog> query = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<ProductDisplayAuditLog> wrapper = new LambdaQueryWrapper<ProductDisplayAuditLog>()
                .orderByDesc(ProductDisplayAuditLog::getCreatedAt);
        if (StringUtils.hasText(productId)) {
            wrapper.eq(ProductDisplayAuditLog::getProductId, productId.trim());
        }
        return auditLogMapper.selectPage(query, wrapper);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }
}
