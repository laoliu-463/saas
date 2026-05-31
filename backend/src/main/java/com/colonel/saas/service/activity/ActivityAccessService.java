package com.colonel.saas.service.activity;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 活动读权限校验：非 admin 招商角色仅可访问已分配给自己的活动。
 */
@Service
public class ActivityAccessService {

    private static final List<String> RECRUITER_ROLES = List.of(
            RoleCodes.BIZ_LEADER,
            RoleCodes.BIZ_STAFF
    );

    private final ColonelsettlementActivityMapper activityMapper;

    public ActivityAccessService(ColonelsettlementActivityMapper activityMapper) {
        this.activityMapper = activityMapper;
    }

    /**
     * 校验当前用户是否有权读取指定活动（列表详情、活动商品等）。
     */
    public void assertActivityReadable(String activityId, UUID userId, Collection<String> roleCodes) {
        if (isAdmin(roleCodes)) {
            return;
        }
        if (!isRecruiterRole(roleCodes)) {
            throw BusinessException.forbidden("无权访问该活动");
        }
        if (userId == null) {
            throw BusinessException.forbidden("无权访问该活动");
        }
        if (!StringUtils.hasText(activityId)) {
            throw BusinessException.param("activityId 不能为空");
        }
        ColonelsettlementActivity activity = activityMapper.selectByActivityId(activityId.trim());
        if (activity == null || activity.getRecruiterUserId() == null || !userId.equals(activity.getRecruiterUserId())) {
            throw BusinessException.forbidden("无权访问该活动");
        }
    }

    public boolean isAdmin(Collection<String> roleCodes) {
        return roleCodes != null && roleCodes.contains(RoleCodes.ADMIN);
    }

    public boolean isRecruiterRole(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        return roleCodes.stream().anyMatch(RECRUITER_ROLES::contains);
    }

    /**
     * 解析活动列表分配筛选：非 admin 强制 mine。
     */
    public String resolveEffectiveAssignmentFilter(String assignmentFilter, Collection<String> roleCodes) {
        if (isAdmin(roleCodes)) {
            return normalizeAssignmentFilter(assignmentFilter);
        }
        return "mine";
    }

    /**
     * 是否应走本地库分配分页（非 all 筛选，或非 admin 招商角色）。
     */
    public boolean shouldUseLocalAssignmentList(String effectiveFilter, Collection<String> roleCodes) {
        if (!"all".equals(effectiveFilter)) {
            return true;
        }
        return isRecruiterRole(roleCodes) && !isAdmin(roleCodes);
    }

    public static String normalizeAssignmentFilter(String assignmentFilter) {
        if (!StringUtils.hasText(assignmentFilter)) {
            return "all";
        }
        String normalized = assignmentFilter.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "assigned", "unassigned", "mine" -> normalized;
            default -> "all";
        };
    }

    public static Collection<String> normalizeRoleCodes(Object roleCodes) {
        if (roleCodes == null) {
            return List.of();
        }
        if (roleCodes instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        return List.of(String.valueOf(roleCodes).split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
