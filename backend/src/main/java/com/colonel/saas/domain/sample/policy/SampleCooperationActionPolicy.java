package com.colonel.saas.domain.sample.policy;

import com.colonel.saas.common.enums.SampleStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.vo.sample.SampleActionAvailabilityVO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 合作台操作矩阵。权限与状态规则集中在寄样域，Controller 和前端只消费结果。
 */
@Component
public class SampleCooperationActionPolicy {

    public static final String APPROVE = "APPROVE";
    public static final String REJECT = "REJECT";
    public static final String EDIT = "EDIT";
    public static final String PROGRESS = "PROGRESS";
    public static final String COPY_LINK = "COPY_LINK";
    public static final String COPY_ORDER = "COPY_ORDER";
    public static final String NOTE = "NOTE";

    private static final Set<SampleStatus> EDITABLE_STATUSES = Set.of(
            SampleStatus.PENDING_AUDIT,
            SampleStatus.PENDING_SHIP,
            SampleStatus.SHIPPING,
            SampleStatus.REJECTED);

    private final CurrentUserPermissionChecker permissionChecker;

    public SampleCooperationActionPolicy(CurrentUserPermissionChecker permissionChecker) {
        this.permissionChecker = Objects.requireNonNull(permissionChecker, "permissionChecker");
    }

    public Map<String, SampleActionAvailabilityVO> availability(
            SampleStatus status,
            UUID ownerUserId,
            UUID currentUserId,
            Object roleCodes) {
        LinkedHashMap<String, SampleActionAvailabilityVO> actions = new LinkedHashMap<>();
        boolean pendingAudit = status == SampleStatus.PENDING_AUDIT;
        boolean reviewer = permissionChecker.hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.BIZ_STAFF);
        actions.put(APPROVE, availableWhen(
                pendingAudit && reviewer,
                pendingAudit ? "仅管理员或招商专员可审核" : "仅待审核合作单可通过"));
        actions.put(REJECT, availableWhen(
                pendingAudit && reviewer,
                pendingAudit ? "仅管理员或招商专员可审核" : "仅待审核合作单可拒绝"));

        boolean editableStatus = EDITABLE_STATUSES.contains(status);
        boolean editor = Objects.equals(ownerUserId, currentUserId)
                || permissionChecker.hasAnyRole(roleCodes, RoleCodes.ADMIN);
        actions.put(EDIT, availableWhen(
                editableStatus && editor,
                editableStatus ? "仅申请人或管理员可编辑" : "当前状态不允许编辑"));

        actions.put(PROGRESS, SampleActionAvailabilityVO.available());
        actions.put(COPY_LINK, SampleActionAvailabilityVO.unavailable("商品链接复制能力暂不可用"));
        actions.put(COPY_ORDER, SampleActionAvailabilityVO.unavailable("订单复制能力暂不可用"));
        actions.put(NOTE, SampleActionAvailabilityVO.available());
        return actions;
    }

    public void ensureCanEdit(
            SampleStatus status,
            UUID ownerUserId,
            UUID currentUserId,
            Object roleCodes) {
        if (!EDITABLE_STATUSES.contains(status)) {
            throw BusinessException.stateInvalid("当前状态不允许编辑合作详情");
        }
        if (!Objects.equals(ownerUserId, currentUserId)
                && !permissionChecker.hasAnyRole(roleCodes, RoleCodes.ADMIN)) {
            throw new ForbiddenException("仅申请人或管理员可编辑合作详情");
        }
    }

    private SampleActionAvailabilityVO availableWhen(boolean available, String reason) {
        return available
                ? SampleActionAvailabilityVO.available()
                : SampleActionAvailabilityVO.unavailable(reason);
    }
}
