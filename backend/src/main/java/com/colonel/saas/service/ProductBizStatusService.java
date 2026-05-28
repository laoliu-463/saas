package com.colonel.saas.service;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 商品业务状态机服务。
 * <p>
 * 管理商品在业务流程中的状态流转，核心状态包括：
 * <ul>
 *   <li>{@code PENDING_AUDIT} — 待审核（初始状态）</li>
 *   <li>{@code APPROVED} — 审核通过</li>
 *   <li>{@code REJECTED} — 审核驳回</li>
 *   <li>{@code ASSIGNED} — 已分配招商</li>
 *   <li>{@code LINKED} — 已生成推广链接</li>
 *   <li>{@code FOLLOWING} — 跟进中</li>
 *   <li>{@code BOUND} — 已绑定（仅作为中间态，不允许直接转入）</li>
 * </ul>
 * </p>
 * <p>
 * 状态流转通过 {@link #ensureAllowed} 强制校验合法转换路径，非法转换将抛出异常。
 * 每次状态变更都会写入 {@link ProductOperationLog} 审计日志。
 * </p>
 *
 * @see ProductBizStatus
 * @see ProductOperationState
 * @see ProductOperationLog
 */
@Service
public class ProductBizStatusService {

    /** 商品运营状态 Mapper */
    private final ProductOperationStateMapper operationStateMapper;
    /** 商品操作日志 Mapper */
    private final ProductOperationLogMapper operationLogMapper;

    /**
     * 构造注入依赖。
     *
     * @param operationStateMapper 商品运营状态 Mapper
     * @param operationLogMapper   商品操作日志 Mapper
     */
    public ProductBizStatusService(
            ProductOperationStateMapper operationStateMapper,
            ProductOperationLogMapper operationLogMapper) {
        this.operationStateMapper = operationStateMapper;
        this.operationLogMapper = operationLogMapper;
    }

    /**
     * 读取商品运营状态的业务状态枚举值。
     * <p>
     * 当状态为 null、空白或无法识别时，安全降级为 {@code PENDING_AUDIT}。
     * </p>
     *
     * @param state 商品运营状态实体（可为 null）
     * @return 对应的业务状态枚举；无法识别时返回 PENDING_AUDIT
     */
    public ProductBizStatus readBizStatus(ProductOperationState state) {
        try {
            ProductBizStatus bizStatus = ProductBizStatus.fromCode(state == null ? null : state.getBizStatus());
            return bizStatus == null ? ProductBizStatus.PENDING_AUDIT : bizStatus;
        } catch (IllegalArgumentException ex) {
            return ProductBizStatus.PENDING_AUDIT;
        }
    }

    /**
     * 初始化商品运营状态（幂等：已存在时直接返回）。
     * <p>
     * 当商品首次同步到系统时调用，创建初始运营状态记录（PENDING_AUDIT），
     * 并写入一条 "SYNC" 类型的操作日志。
     * </p>
     *
     * @param existing      已存在的运营状态（非 null 时直接返回，不重复创建）
     * @param activityId    活动 ID
     * @param productId     商品 ID
     * @param operatorId    操作者 ID
     * @param operatorDeptId 操作者部门 ID
     * @param remark        操作备注
     * @return 商品运营状态（新建或已有）
     */
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

    /**
     * 变更商品业务状态。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>读取当前业务状态</li>
     *   <li>校验目标状态是否允许从当前状态转入（{@link #ensureAllowed}）</li>
     *   <li>执行外部传入的状态变更回调（{@link StatusMutation}），用于同步更新关联字段</li>
     *   <li>更新 bizStatus 和 lastOperationAt</li>
     *   <li>新增或更新运营状态记录（乐观锁）</li>
     *   <li>写入操作日志</li>
     * </ol>
     * </p>
     *
     * @param state         当前商品运营状态实体
     * @param targetStatus  目标业务状态
     * @param operationType 操作类型标识（如 APPROVE、ASSIGN、LINK 等）
     * @param operatorId    操作者 ID
     * @param operatorDeptId 操作者部门 ID
     * @param payload       操作附加数据（记录到日志中）
     * @param remark        操作备注
     * @param mutation      状态变更回调（在状态字段更新前执行，用于修改关联字段）
     * @return 更新后的商品运营状态
     * @throws BusinessException 当状态转换不合法时
     */
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
            OptimisticLockSupport.requireUpdated(operationStateMapper.updateById(state));
        }
        writeLog(state.getActivityId(), state.getProductId(), operationType, beforeStatus, targetStatus,
                operatorId, operatorDeptId, payload, remark, true, null);
        return state;
    }

    /**
     * 记录操作失败日志（不改变状态）。
     * <p>
     * 使用 {@code REQUIRES_NEW} 事务传播，确保即使外层事务回滚，失败日志也能持久化。
     * beforeStatus 和 afterStatus 相同（状态未变更）。
     * </p>
     *
     * @param activityId    活动 ID
     * @param productId     商品 ID
     * @param beforeStatus  操作前状态
     * @param operationType 操作类型
     * @param operatorId    操作者 ID
     * @param operatorDeptId 操作者部门 ID
     * @param payload       操作附加数据
     * @param remark        操作备注
     * @param errorMessage  错误信息
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
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

    /**
     * 记录状态变更日志（不改变状态，用于审核等只记录操作不跳转状态的场景）。
     * <p>
     * 与 {@link #changeStatus} 不同，此方法仅写日志不修改运营状态，
     * 适用于审核通过/驳回等需要记录操作但状态由调用方自行管理的场景。
     * </p>
     *
     * @param activityId    活动 ID
     * @param productId     商品 ID
     * @param operationType 操作类型
     * @param beforeStatus  操作前状态
     * @param afterStatus   操作后状态
     * @param operatorId    操作者 ID
     * @param operatorDeptId 操作者部门 ID
     * @param payload       操作附加数据
     * @param remark        操作备注
     * @param success       操作是否成功
     * @param errorMessage  错误信息（成功时为 null）
     */
    @Transactional(rollbackFor = Exception.class)
    public void logStatusChange(
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
        writeLog(activityId, productId, operationType, beforeStatus, afterStatus,
                operatorId, operatorDeptId, payload, remark, success, errorMessage);
    }

    /**
     * 校验状态转换是否合法。
     * <p>
     * 通过 switch 表达式定义每个目标状态允许的前置状态集合：
     * <ul>
     *   <li>APPROVED: 仅从 PENDING_AUDIT 允许</li>
     *   <li>REJECTED: 仅从 PENDING_AUDIT 允许</li>
     *   <li>BOUND: 不允许直接转入（仅作为中间态）</li>
     *   <li>ASSIGNED: 从 APPROVED / BOUND / ASSIGNED 允许（支持重复分配）</li>
     *   <li>LINKED: 从 APPROVED / ASSIGNED 允许</li>
     *   <li>FOLLOWING: 仅从 LINKED 允许</li>
     *   <li>PENDING_AUDIT: 不允许回退</li>
     * </ul>
     * </p>
     *
     * @param beforeStatus  当前状态
     * @param targetStatus  目标状态
     * @param operationType 操作类型（用于错误消息）
     * @throws BusinessException 当状态转换不合法时
     */
    private void ensureAllowed(ProductBizStatus beforeStatus, ProductBizStatus targetStatus, String operationType) {
        /* 使用 switch 表达式定义状态转换规则 */
        boolean allowed = switch (targetStatus) {
            case APPROVED -> beforeStatus == ProductBizStatus.PENDING_AUDIT;
            case REJECTED -> beforeStatus == ProductBizStatus.PENDING_AUDIT;
            case BOUND -> false;
            case ASSIGNED -> beforeStatus == ProductBizStatus.APPROVED
                    || beforeStatus == ProductBizStatus.BOUND
                    || beforeStatus == ProductBizStatus.ASSIGNED;
            case LINKED -> beforeStatus == ProductBizStatus.APPROVED
                    || beforeStatus == ProductBizStatus.ASSIGNED;
            case FOLLOWING -> beforeStatus == ProductBizStatus.LINKED;
            case PENDING_AUDIT -> false;
        };
        if (!allowed) {
            throw BusinessException.stateInvalid("当前状态不允许执行" + operationType
                    + "，当前状态：" + beforeStatus.name()
                    + "，目标状态：" + targetStatus.name());
        }
    }

    /**
     * 内部方法：写入商品操作日志记录。
     * <p>
     * 构建 {@link ProductOperationLog} 实体并持久化，记录操作的完整上下文：
     * 活动/商品标识、操作类型、状态前后变化、操作者信息、附加数据（payload）
     * 以及操作备注。UUID 主键在此方法内自动生成。
     * </p>
     * <p>
     * 注意：payload 通过 {@code String.valueOf()} 转换为字符串存储，
     * 如果 payload 为 null 则存储 "null" 字符串。
     * </p>
     *
     * @param activityId    活动 ID
     * @param productId     商品 ID
     * @param operationType 操作类型标识
     * @param beforeStatus  操作前状态（null 时 before_status 列为 null）
     * @param afterStatus   操作后状态（null 时 after_status 列为 null）
     * @param operatorId    操作者 ID
     * @param operatorDeptId 操作者部门 ID
     * @param payload       操作附加数据（Map 序列化为字符串存储）
     * @param remark        操作备注
     * @param success       操作是否成功
     * @param errorMessage  错误信息（成功时为 null）
     */
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
        /* 状态枚举转为名称字符串存储；null 状态保持 null */
        log.setBeforeStatus(beforeStatus == null ? null : beforeStatus.name());
        log.setAfterStatus(afterStatus == null ? null : afterStatus.name());
        log.setSuccess(success);
        log.setErrorMessage(errorMessage);
        log.setOperatorId(operatorId);
        log.setOperatorDeptId(operatorDeptId);
        /* payload 使用 String.valueOf() 转换，null 时存储 "null" 字符串 */
        log.setOperationPayload(String.valueOf(payload));
        log.setOperationRemark(remark);
        log.setId(UUID.randomUUID());
        operationLogMapper.insert(log);
    }

    /**
     * 状态变更回调接口（函数式接口）。
     * <p>
     * 用于 {@link #changeStatus} 方法中，在状态字段更新前执行外部传入的自定义逻辑。
     * 典型用途：同步更新关联字段（如分配人、分配时间、审核意见等）。
     * </p>
     *
     * @see #changeStatus
     */
    @FunctionalInterface
    public interface StatusMutation {
        /**
         * 执行状态变更前的自定义修改。
         *
         * @param state 待修改的商品运营状态实体（调用方可直接修改其字段）
         */
        void apply(ProductOperationState state);
    }
}
