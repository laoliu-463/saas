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

/**
 * 商品展示规则审计日志服务。
 * <p>
 * 负责记录商品展示去重规则引擎（{@link ProductDisplayRuleService}）每次决策的审计日志，
 * 包括展示切换、候选列表、决策理由等信息，用于问题排查和合规审计。
 * </p>
 * <p>
 * 审计日志为只追加（append-only）模型，不支持修改或删除。
 * </p>
 *
 * @see ProductDisplayRuleService
 * @see DisplayRuleOperatorContext
 */
@Service
public class ProductDisplayAuditService {

    /** 审计日志数据访问 */
    private final ProductDisplayAuditLogMapper auditLogMapper;
    /** JSON 序列化工具，用于将候选 ID 列表和详情对象转为 JSON 字符串 */
    private final ObjectMapper objectMapper;

    /**
     * 构造注入依赖。
     *
     * @param auditLogMapper 审计日志 MyBatis Mapper
     * @param objectMapper   Jackson ObjectMapper 实例
     */
    public ProductDisplayAuditService(ProductDisplayAuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 写入一条展示规则审计日志。
     * <p>
     * 记录某次展示决策的完整上下文：旧/新展示关系、候选列表、选择理由、隐藏理由、
     * 规则版本号、操作者信息以及附加详情。所有序列化失败静默降级为空 JSON。
     * </p>
     *
     * @param productId       商品 ID（抖店 product_id）
     * @param oldRelationId   切换前展示中的 ProductOperationState 主键（UUID），可为 null
     * @param newRelationId   切换后展示中的 ProductOperationState 主键（UUID），可为 null
     * @param candidateIds    参与本次决策的所有候选关系 ID 列表
     * @param actionType      操作类型（如 DISPLAY_SWITCH）
     * @param selectedReason  被选中展示的理由（ADMIN_FORCE / ADVANTAGE_OVERRIDE / RULE_ENGINE）
     * @param hiddenReason    被隐藏的理由（如 REPLACED_BY_HIGHER_PRIORITY）
     * @param ruleVersion     展示规则引擎版本号
     * @param operator        操作者上下文（系统/定时任务/管理员）
     * @param detail          附加详情对象，将序列化为 JSON 存储
     */
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

    /**
     * 分页查询展示规则审计日志。
     * <p>
     * 按创建时间倒序返回，支持按商品 ID 精确过滤。
     * 页码和每页大小会自动修正为至少 1。
     * </p>
     *
     * @param productId 商品 ID 过滤条件（可为 null 或空白，表示不过滤）
     * @param page      页码（从 1 开始，自动修正为至少 1）
     * @param size      每页大小（自动修正为至少 1）
     * @return 分页结果，包含审计日志列表和分页元数据
     */
    public Page<ProductDisplayAuditLog> pageAuditLogs(String productId, long page, long size) {
        Page<ProductDisplayAuditLog> query = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<ProductDisplayAuditLog> wrapper = new LambdaQueryWrapper<ProductDisplayAuditLog>()
                .orderByDesc(ProductDisplayAuditLog::getCreatedAt);
        if (StringUtils.hasText(productId)) {
            wrapper.eq(ProductDisplayAuditLog::getProductId, productId.trim());
        }
        return auditLogMapper.selectPage(query, wrapper);
    }

    /**
     * 将对象序列化为 JSON 字符串；序列化失败时返回空数组 "[]"。
     *
     * @param value 待序列化对象
     * @return JSON 字符串；失败时返回 "[]"
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }
}
