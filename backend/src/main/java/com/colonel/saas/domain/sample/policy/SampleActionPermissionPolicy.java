package com.colonel.saas.domain.sample.policy;

import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;

import java.util.Objects;

/**
 * 寄样动作权限策略。
 *
 * <p>寄样域负责定义哪些角色可执行寄样申请、审核、物流、导出等业务动作；
 * 用户域权限策略负责解释当前用户的角色编码集合。</p>
 */
public class SampleActionPermissionPolicy {

    private final CurrentUserPermissionPolicy currentUserPermissionPolicy;

    public SampleActionPermissionPolicy(CurrentUserPermissionPolicy currentUserPermissionPolicy) {
        this.currentUserPermissionPolicy = Objects.requireNonNull(currentUserPermissionPolicy, "currentUserPermissionPolicy");
    }

    public void ensureCanApply(Object roleCodes) {
        if (!currentUserPermissionPolicy.hasAnyRole(roleCodes,
                RoleCodes.ADMIN,
                RoleCodes.CHANNEL_LEADER,
                RoleCodes.CHANNEL_STAFF)) {
            throw new ForbiddenException("仅渠道角色可以发起寄样申请");
        }
    }

    public void ensureCanDelete(Object roleCodes) {
        if (!currentUserPermissionPolicy.hasAnyRole(roleCodes,
                RoleCodes.ADMIN,
                RoleCodes.CHANNEL_LEADER,
                RoleCodes.CHANNEL_STAFF)) {
            throw new ForbiddenException("仅渠道角色可以删除寄样申请");
        }
    }

    public void ensureCanPerformAction(String action, Object roleCodes) {
        switch (action) {
            case "PENDING_SHIP", "REJECTED" -> ensureReviewAction(roleCodes);
            case "SHIPPING", "DELIVERED", "PENDING_HOMEWORK" -> ensureLogisticsAction(roleCodes);
            case "COMPLETED", "CLOSED" -> throw new ForbiddenException("完成与关闭状态仅允许系统自动推进");
            default -> {
            }
        }
    }

    public void ensureCanSyncLogistics(Object roleCodes) {
        if (!currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.OPS_STAFF)) {
            throw new ForbiddenException("仅运营或管理员可触发物流同步");
        }
    }

    public void ensureCanImportLogistics(Object roleCodes) {
        if (!canImportLogistics(roleCodes)) {
            throw new ForbiddenException("仅运营或管理员可导入物流单号");
        }
    }

    public boolean canImportLogistics(Object roleCodes) {
        return currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.OPS_STAFF);
    }

    public void ensureCanOverwriteLogisticsImport(Object roleCodes) {
        if (!canOverwriteLogisticsImport(roleCodes)) {
            throw new ForbiddenException("仅管理员可覆盖已有物流单号");
        }
    }

    public boolean canOverwriteLogisticsImport(Object roleCodes) {
        return currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.ADMIN);
    }

    public void ensureCanExport(Object roleCodes) {
        if (!currentUserPermissionPolicy.hasAnyRole(roleCodes,
                RoleCodes.ADMIN,
                RoleCodes.BIZ_LEADER,
                RoleCodes.BIZ_STAFF,
                RoleCodes.OPS_STAFF)) {
            throw new ForbiddenException("仅管理员、招商或运营可导出寄样数据");
        }
    }

    public boolean isSevenDayLimitExempt(Object roleCodes) {
        return currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.CHANNEL_LEADER);
    }

    public boolean isOpsStaffOnly(Object roleCodes) {
        return currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.OPS_STAFF)
                && !currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.ADMIN);
    }

    public boolean isPlainBizStaff(Object roleCodes) {
        return currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.BIZ_STAFF)
                && !currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.BIZ_LEADER);
    }

    public boolean hasGlobalSampleAccess(Object roleCodes) {
        return currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.ADMIN);
    }

    public boolean canAccessAssignedSampleProduct(Object roleCodes) {
        return currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.BIZ_STAFF);
    }

    public boolean requiresChannelTalentClaim(Object roleCodes) {
        return currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.CHANNEL_STAFF, RoleCodes.CHANNEL_LEADER)
                && !currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.ADMIN);
    }

    private void ensureReviewAction(Object roleCodes) {
        if (!currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.BIZ_STAFF)) {
            throw new ForbiddenException("仅招商角色可以审核寄样");
        }
    }

    private void ensureLogisticsAction(Object roleCodes) {
        if (!currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.OPS_STAFF)) {
            throw new ForbiddenException("仅运营角色可以推进物流状态");
        }
    }
}
