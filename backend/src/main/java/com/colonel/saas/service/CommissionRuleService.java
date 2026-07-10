package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.entity.CommissionRule;
import com.colonel.saas.mapper.CommissionRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 提成规则管理服务。
 *
 * <p>职责：管理提成规则的 CRUD 操作，并提供基于维度优先级的提成比例解析能力。
 *
 * <p>维度类型（dimensionType）支持四级优先级，从高到低为：
 * <ol>
 *   <li>product（商品级）—— 最高优先级，精确到具体商品</li>
 *   <li>activity（活动级）—— 次高优先级，精确到具体活动</li>
 *   <li>user（用户级）—— 针对特定招商员/渠道</li>
 *   <li>global（全局级）—— 默认兜底规则</li>
 * </ol>
 *
 * <p>提成类型（commissionType）分为两类：
 * <ul>
 *   <li>recruiter —— 招商提成</li>
 *   <li>channel —— 渠道提成</li>
 * </ul>
 *
 * <p>依赖服务/仓储：
 * <ul>
 *   <li>{@link CommissionRuleMapper} —— 提成规则数据访问</li>
 * </ul>
 */
@Service
public class CommissionRuleService {

    /** 维度类型：全局级 */
    public static final String DIMENSION_GLOBAL = "global";
    /** 维度类型：活动级 */
    public static final String DIMENSION_ACTIVITY = "activity";
    /** 维度类型：商品级 */
    public static final String DIMENSION_PRODUCT = "product";
    /** 维度类型：用户级 */
    public static final String DIMENSION_USER = "user";

    /** 提成类型：招商提成 */
    public static final String TYPE_RECRUITER = "recruiter";
    /** 提成类型：渠道提成 */
    public static final String TYPE_CHANNEL = "channel";

    /** 维度优先级链：product > activity > user > global，用于提成比例解析时的逐级回退 */
    private static final List<String> DIMENSION_PRIORITY = List.of(
            DIMENSION_PRODUCT,
            DIMENSION_ACTIVITY,
            DIMENSION_USER,
            DIMENSION_GLOBAL);

    private final CommissionRuleMapper commissionRuleMapper;

    public CommissionRuleService(CommissionRuleMapper commissionRuleMapper) {
        this.commissionRuleMapper = commissionRuleMapper;
    }

    /**
     * 提成比例解析上下文。
     * 携带当前订单的活动ID、商品ID、招商员用户ID，用于在优先级链中匹配最合适的提成规则。
     *
     * @param activityId      活动ID
     * @param productId       商品ID
     * @param recruiterUserId 招商员用户ID
     */
    public record CommissionResolutionContext(
            String activityId,
            String productId,
            UUID recruiterUserId) {
    }

    /**
     * 提成规则解析结果。
     *
     * @param ratio          命中的提成比例
     * @param ruleId         命中的规则 ID
     * @param ruleVersion    命中的规则版本
     * @param ruleUpdatedAt  命中的规则更新时间
     * @param dimensionType  命中的维度类型
     * @param dimensionId    命中的维度 ID
     * @param commissionType 命中的提成类型
     */
    public record CommissionRuleResolution(
            BigDecimal ratio,
            UUID ruleId,
            Integer ruleVersion,
            LocalDateTime ruleUpdatedAt,
            String dimensionType,
            String dimensionId,
            String commissionType) {
    }

    /**
     * 分页查询提成规则列表。
     *
     * <p>支持的可选筛选条件（彼此通过 AND 组合）：
     * <ul>
     *   <li>{@code dimensionType} —— 维度类型（global/activity/product/user）</li>
     *   <li>{@code commissionType} —— 提成类型（recruiter/channel）</li>
     *   <li>{@code status} —— 启用状态（1 启用 / 0 禁用）</li>
     *   <li>{@code effectiveStart} / {@code effectiveEnd} —— 生效时间区间
     *       （与规则有效期做"区间重叠"判定，区间端点可空表示不约束）</li>
     * </ul>
     *
     * @param dimensionType 维度类型筛选（可选，null 表示不筛选）
     * @param commissionType 提成类型筛选（可选，null 表示不筛选）
     * @param status        启用状态筛选（可选，null 表示不筛选）
     * @param effectiveStart 查询生效区间起点（可选，null 表示不限起点）
     * @param effectiveEnd   查询生效区间终点（可选，null 表示不限终点）
     * @param page          页码
     * @param size          每页大小
     * @return 分页结果
     */
    public IPage<CommissionRule> findPage(
            String dimensionType,
            String commissionType,
            Integer status,
            LocalDateTime effectiveStart,
            LocalDateTime effectiveEnd,
            int page,
            int size) {
        LambdaQueryWrapper<CommissionRule> wrapper = new LambdaQueryWrapper<CommissionRule>()
                .eq(CommissionRule::getDeleted, 0)
                .orderByDesc(CommissionRule::getUpdateTime)
                .orderByDesc(CommissionRule::getCreateTime);
        if (StringUtils.hasText(dimensionType)) {
            wrapper.eq(CommissionRule::getDimensionType, normalizeDimensionType(dimensionType));
        }
        if (StringUtils.hasText(commissionType)) {
            wrapper.eq(CommissionRule::getCommissionType, normalizeCommissionType(commissionType));
        }
        if (status != null) {
            // 仅接受 0/1，非法值当作"不筛选"以避免静默回错数据
            if (status == 0 || status == 1) {
                wrapper.eq(CommissionRule::getStatus, status);
            }
        }
        // 生效区间重叠判定：规则有效期与查询区间相交。
        // 查询区间端点为 null 时表示该方向不约束。
        if (effectiveStart != null && effectiveEnd != null
                && effectiveEnd.isBefore(effectiveStart)) {
            throw BusinessException.param("查询生效区间终点不能早于起点");
        }
        if (effectiveStart != null) {
            // 规则 effectiveEnd 为 null（长期） 或 >= 查询起点
            wrapper.and(w -> w.isNull(CommissionRule::getEffectiveEnd)
                    .or().ge(CommissionRule::getEffectiveEnd, effectiveStart));
        }
        if (effectiveEnd != null) {
            // 规则 effectiveStart 为 null（即时） 或 <= 查询终点
            wrapper.and(w -> w.isNull(CommissionRule::getEffectiveStart)
                    .or().le(CommissionRule::getEffectiveStart, effectiveEnd));
        }
        return commissionRuleMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 根据ID查询单条提成规则。
     *
     * @param id 规则ID
     * @return 规则实体
     * @throws BusinessException 规则不存在或已删除时抛出 NOT_FOUND 异常
     */
    public CommissionRule getById(UUID id) {
        CommissionRule rule = commissionRuleMapper.selectById(id);
        if (rule == null || rule.getDeleted() != null && rule.getDeleted() != 0) {
            throw BusinessException.notFound("提成规则不存在");
        }
        return rule;
    }

    /**
     * 创建新的提成规则。
     * 自动设置 UUID、默认状态（启用）、创建/更新时间。
     *
     * @param rule 规则实体，维度类型、提成类型、比例等字段必填
     * @return 创建后的规则实体（含生成的 ID）
     * @throws BusinessException 参数校验失败时抛出异常
     */
    @Transactional(rollbackFor = Exception.class)
    public CommissionRule create(CommissionRule rule) {
        validateRule(rule, true);
        LocalDateTime now = LocalDateTime.now();
        rule.setId(UUID.randomUUID());
        rule.setDeleted(0);
        rule.setStatus(rule.getStatus() == null ? 1 : rule.getStatus());
        rule.setVersion(1);
        rule.setCreateTime(now);
        rule.setUpdateTime(now);
        commissionRuleMapper.insert(rule);
        return rule;
    }

    /**
     * 更新已有的提成规则。
     * 仅更新允许修改的字段，保留原 ID 和创建时间。
     *
     * @param id    规则ID
     * @param rule  新的规则数据
     * @return 更新后的规则实体
     * @throws BusinessException 规则不存在或参数校验失败时抛出异常
     */
    @Transactional(rollbackFor = Exception.class)
    public CommissionRule update(UUID id, CommissionRule rule) {
        CommissionRule existing = getById(id);
        validateRule(rule, false);
        existing.setDimensionType(normalizeDimensionType(rule.getDimensionType()));
        existing.setDimensionId(normalizeDimensionId(existing.getDimensionType(), rule.getDimensionId()));
        existing.setCommissionType(normalizeCommissionType(rule.getCommissionType()));
        existing.setRatio(rule.getRatio());
        existing.setEffectiveStart(rule.getEffectiveStart());
        existing.setEffectiveEnd(rule.getEffectiveEnd());
        existing.setStatus(rule.getStatus() == null ? existing.getStatus() : rule.getStatus());
        existing.setUpdateTime(LocalDateTime.now());
        OptimisticLockSupport.requireUpdated(
                commissionRuleMapper.updateById(existing),
                "提成规则已被他人修改，请刷新后重试");
        return existing;
    }

    /**
     * 逻辑删除提成规则（设置 deleted=1）。
     *
     * @param id 规则ID
     * @throws BusinessException 规则不存在时抛出 NOT_FOUND 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id) {
        CommissionRule existing = getById(id);
        existing.setDeleted(1);
        existing.setUpdateTime(LocalDateTime.now());
        OptimisticLockSupport.requireUpdated(
                commissionRuleMapper.updateById(existing),
                "提成规则已被他人修改，请刷新后重试");
    }

    /**
     * 按优先级链解析提成比例。
     *
     * <p>解析策略：按 product > activity > user > global 的优先级依次查询生效中的提成规则，
     * 一旦匹配到即返回对应比例，不再继续向下查找。若所有维度均未命中，返回 null。
     *
     * @param commissionType 提成类型（recruiter / channel）
     * @param context        解析上下文，包含活动ID、商品ID、招商员ID
     * @param at             生效时间点，用于过滤有效期范围；null 时使用当前时间
     * @return 匹配到的提成比例（0~1），未匹配到时返回 null
     */
    public BigDecimal resolveRatio(String commissionType, CommissionResolutionContext context, LocalDateTime at) {
        CommissionRuleResolution resolution = resolveRule(commissionType, context, at);
        return resolution == null ? null : resolution.ratio();
    }

    /**
     * 按优先级链解析提成规则，并返回命中规则版本证据。
     *
     * @param commissionType 提成类型（recruiter / channel）
     * @param context        解析上下文，包含活动ID、商品ID、招商员ID
     * @param at             生效时间点，用于过滤有效期范围；null 时使用当前时间
     * @return 命中的规则快照，未匹配到时返回 null
     */
    public CommissionRuleResolution resolveRule(
            String commissionType,
            CommissionResolutionContext context,
            LocalDateTime at) {
        String normalizedType = normalizeCommissionType(commissionType);
        LocalDateTime effectiveAt = at == null ? LocalDateTime.now() : at;
        for (String dimensionType : DIMENSION_PRIORITY) {
            String dimensionId = resolveDimensionId(dimensionType, context);
            if (!DIMENSION_GLOBAL.equals(dimensionType) && !StringUtils.hasText(dimensionId)) {
                continue;
            }
            CommissionRule rule = findActiveRule(dimensionType, dimensionId, normalizedType, effectiveAt);
            if (rule != null && rule.getRatio() != null) {
                return new CommissionRuleResolution(
                        rule.getRatio(),
                        rule.getId(),
                        rule.getVersion(),
                        rule.getUpdateTime(),
                        rule.getDimensionType(),
                        rule.getDimensionId(),
                        rule.getCommissionType());
            }
        }
        return null;
    }

    /**
     * 在指定维度和提成类型下，查找当前生效的提成比例。
     * 查询条件包括：未删除、状态为启用、维度类型匹配、提成类型匹配、有效期覆盖指定时间点。
     * 同一维度下取最新更新时间的规则。
     */
    private CommissionRule findActiveRule(
            String dimensionType,
            String dimensionId,
            String commissionType,
            LocalDateTime at) {
        LambdaQueryWrapper<CommissionRule> wrapper = new LambdaQueryWrapper<CommissionRule>()
                .eq(CommissionRule::getDeleted, 0)
                .eq(CommissionRule::getStatus, 1)
                .eq(CommissionRule::getDimensionType, dimensionType)
                .eq(CommissionRule::getCommissionType, commissionType)
                .and(w -> w.isNull(CommissionRule::getEffectiveStart).or().le(CommissionRule::getEffectiveStart, at))
                .and(w -> w.isNull(CommissionRule::getEffectiveEnd).or().ge(CommissionRule::getEffectiveEnd, at))
                .orderByDesc(CommissionRule::getUpdateTime)
                .last("LIMIT 1");
        if (DIMENSION_GLOBAL.equals(dimensionType)) {
            wrapper.and(w -> w.isNull(CommissionRule::getDimensionId).or().eq(CommissionRule::getDimensionId, ""));
        } else {
            wrapper.eq(CommissionRule::getDimensionId, dimensionId);
        }
        return commissionRuleMapper.selectOne(wrapper);
    }

    /**
     * 根据维度类型从解析上下文中提取对应的维度ID。
     * activity -> activityId；product -> productId；user -> recruiterUserId.toString()；global -> null。
     */
    private String resolveDimensionId(String dimensionType, CommissionResolutionContext context) {
        if (context == null) {
            return null;
        }
        return switch (dimensionType) {
            case DIMENSION_ACTIVITY -> trimToNull(context.activityId());
            case DIMENSION_PRODUCT -> trimToNull(context.productId());
            case DIMENSION_USER -> context.recruiterUserId() == null ? null : context.recruiterUserId().toString();
            case DIMENSION_GLOBAL -> null;
            default -> null;
        };
    }

    /**
     * 校验提成规则的合法性。
     * 校验内容：规则非空、维度类型合法、提成类型合法、比例在 0~1 范围内、有效期逻辑正确。
     * 创建模式下强制清空 ID（由系统生成）。
     */
    private void validateRule(CommissionRule rule, boolean creating) {
        if (rule == null) {
            throw BusinessException.param("提成规则不能为空");
        }
        String dimensionType = normalizeDimensionType(rule.getDimensionType());
        String commissionType = normalizeCommissionType(rule.getCommissionType());
        rule.setDimensionType(dimensionType);
        rule.setCommissionType(commissionType);
        rule.setDimensionId(normalizeDimensionId(dimensionType, rule.getDimensionId()));
        if (rule.getRatio() == null) {
            throw BusinessException.param("提成比例不能为空");
        }
        if (rule.getRatio().compareTo(BigDecimal.ZERO) < 0 || rule.getRatio().compareTo(BigDecimal.ONE) > 0) {
            throw BusinessException.param("提成比例必须在 0~1 之间");
        }
        if (rule.getEffectiveStart() != null && rule.getEffectiveEnd() != null
                && rule.getEffectiveEnd().isBefore(rule.getEffectiveStart())) {
            throw BusinessException.param("生效结束时间不能早于开始时间");
        }
        if (creating && rule.getId() != null) {
            rule.setId(null);
        }
    }

    private String normalizeDimensionType(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw BusinessException.param("维度类型不能为空");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (!DIMENSION_PRIORITY.contains(normalized) && !DIMENSION_GLOBAL.equals(normalized)) {
            throw BusinessException.param("不支持的维度类型: " + raw);
        }
        return normalized;
    }

    private String normalizeCommissionType(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw BusinessException.param("提成类型不能为空");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (!TYPE_RECRUITER.equals(normalized) && !TYPE_CHANNEL.equals(normalized)) {
            throw BusinessException.param("提成类型必须为 recruiter 或 channel");
        }
        return normalized;
    }

    private String normalizeDimensionId(String dimensionType, String dimensionId) {
        if (DIMENSION_GLOBAL.equals(dimensionType)) {
            return null;
        }
        String normalized = trimToNull(dimensionId);
        if (!StringUtils.hasText(normalized)) {
            throw BusinessException.param("非 global 维度必须填写 dimensionId");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
