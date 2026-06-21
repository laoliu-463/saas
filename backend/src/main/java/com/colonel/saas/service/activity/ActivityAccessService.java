package com.colonel.saas.service.activity;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
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
    private final CurrentUserPermissionPolicy currentUserPermissionPolicy;

    public ActivityAccessService(
            ColonelsettlementActivityMapper activityMapper,
            CurrentUserPermissionPolicy currentUserPermissionPolicy) {
        this.activityMapper = activityMapper;
        this.currentUserPermissionPolicy = currentUserPermissionPolicy;
    }

    /**
     * 校验当前用户是否有权读取指定活动（列表详情、活动商品等）。
     * <p>
     * 权限规则：
     * <ul>
     *   <li>ADMIN：直接放行</li>
     *   <li>BIZ_LEADER：活动分配给自己（recruiter_user_id）或分配给同部门（recruiter_dept_id = 用户主部门）</li>
     *   <li>BIZ_STAFF：仅活动分配给自己（recruiter_user_id）</li>
     * </ul>
     */
    public void assertActivityReadable(String activityId, UUID userId, UUID deptId, Collection<String> roleCodes) {
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
        if (activity == null) {
            throw BusinessException.forbidden("无权访问该活动");
        }
        // 个人分配优先
        if (userId.equals(activity.getRecruiterUserId())) {
            return;
        }
        // BIZ_LEADER + dataScope=2：dept-level 访问
        if (isBizLeader(roleCodes) && deptId != null && deptId.equals(activity.getRecruiterDeptId())) {
            return;
        }
        throw BusinessException.forbidden("无权访问该活动");
    }

    public boolean isAdmin(Collection<String> roleCodes) {
        return currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.ADMIN);
    }

    public boolean isRecruiterRole(Collection<String> roleCodes) {
        return currentUserPermissionPolicy.hasAnyRole(roleCodes, RECRUITER_ROLES.toArray(String[]::new));
    }

    public boolean isBizLeader(Collection<String> roleCodes) {
        return currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.BIZ_LEADER);
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

    public Collection<String> normalizeRoles(Object roleCodes) {
        return currentUserPermissionPolicy.normalizeRoleCodes(roleCodes);
    }

    public static Collection<String> normalizeRoleCodes(Object roleCodes) {
        return new CurrentUserPermissionPolicy().normalizeRoleCodes(roleCodes);
    }
}
