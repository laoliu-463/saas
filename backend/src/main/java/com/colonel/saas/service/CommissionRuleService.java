package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.BusinessException;
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

@Service
public class CommissionRuleService {

    public static final String DIMENSION_GLOBAL = "global";
    public static final String DIMENSION_ACTIVITY = "activity";
    public static final String DIMENSION_PRODUCT = "product";
    public static final String DIMENSION_USER = "user";

    public static final String TYPE_RECRUITER = "recruiter";
    public static final String TYPE_CHANNEL = "channel";

    private static final List<String> DIMENSION_PRIORITY = List.of(
            DIMENSION_PRODUCT,
            DIMENSION_ACTIVITY,
            DIMENSION_USER,
            DIMENSION_GLOBAL);

    private final CommissionRuleMapper commissionRuleMapper;

    public CommissionRuleService(CommissionRuleMapper commissionRuleMapper) {
        this.commissionRuleMapper = commissionRuleMapper;
    }

    public record CommissionResolutionContext(
            String activityId,
            String productId,
            UUID recruiterUserId) {
    }

    public IPage<CommissionRule> findPage(String dimensionType, String commissionType, int page, int size) {
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
        return commissionRuleMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public CommissionRule getById(UUID id) {
        CommissionRule rule = commissionRuleMapper.selectById(id);
        if (rule == null || rule.getDeleted() != null && rule.getDeleted() != 0) {
            throw BusinessException.notFound("提成规则不存在");
        }
        return rule;
    }

    @Transactional(rollbackFor = Exception.class)
    public CommissionRule create(CommissionRule rule) {
        validateRule(rule, true);
        LocalDateTime now = LocalDateTime.now();
        rule.setId(UUID.randomUUID());
        rule.setDeleted(0);
        rule.setStatus(rule.getStatus() == null ? 1 : rule.getStatus());
        rule.setCreateTime(now);
        rule.setUpdateTime(now);
        commissionRuleMapper.insert(rule);
        return rule;
    }

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
        commissionRuleMapper.updateById(existing);
        return existing;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id) {
        CommissionRule existing = getById(id);
        existing.setDeleted(1);
        existing.setUpdateTime(LocalDateTime.now());
        commissionRuleMapper.updateById(existing);
    }

    public BigDecimal resolveRatio(String commissionType, CommissionResolutionContext context, LocalDateTime at) {
        String normalizedType = normalizeCommissionType(commissionType);
        LocalDateTime effectiveAt = at == null ? LocalDateTime.now() : at;
        for (String dimensionType : DIMENSION_PRIORITY) {
            String dimensionId = resolveDimensionId(dimensionType, context);
            if (!DIMENSION_GLOBAL.equals(dimensionType) && !StringUtils.hasText(dimensionId)) {
                continue;
            }
            BigDecimal ratio = findActiveRatio(dimensionType, dimensionId, normalizedType, effectiveAt);
            if (ratio != null) {
                return ratio;
            }
        }
        return null;
    }

    private BigDecimal findActiveRatio(
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
        CommissionRule rule = commissionRuleMapper.selectOne(wrapper);
        return rule == null || rule.getRatio() == null ? null : rule.getRatio();
    }

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
