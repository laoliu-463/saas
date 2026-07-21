package com.colonel.saas.domain.sample.policy;

import com.colonel.saas.common.enums.SampleStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.vo.sample.SampleActionAvailabilityVO;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 合作台操作矩阵。权限与状态规则集中在寄样域，Controller 和前端只消费结果。
 */
public class SampleCooperationActionPolicy {

    public static final String APPROVE = "APPROVE";
    public static final String REJECT = "REJECT";
    public static final String EDIT = "EDIT";
    public static final String PROGRESS = "PROGRESS";
    public static final String COPY_LINK = "COPY_LINK";
    public static final String COPY_ORDER = "COPY_ORDER";
    public static final String COMPLAIN = "COMPLAIN";
    public static final String NOTE = "NOTE";

    private static final Set<SampleStatus> EDITABLE_STATUSES = Set.of(
            SampleStatus.PENDING_AUDIT,
            SampleStatus.PENDING_SHIP,
            SampleStatus.SHIPPING,
            SampleStatus.REJECTED);

    public Map<String, SampleActionAvailabilityVO> availability(
            SampleStatus status,
            UUID ownerUserId,
            UUID currentUserId,
            Object roleCodes) {
        // PR #fix-cooperation-action-availability: 前端 modal "通过合作单" 按钮
        // 永远显示导致用户在 PENDING_AUDIT → SHIPPING 之后重复点击
        // 触发后端 ensureTransition(SHIPPING, PENDING_AUDIT) 拒绝。
        // 这里按 status 严格返回 availability，前端据此隐藏不可用按钮。
        LinkedHashMap<String, SampleActionAvailabilityVO> actions = new LinkedHashMap<>();

        // APPROVE: 仅 PENDING_AUDIT 可通过（推到 PENDING_SHIP）
        actions.put(APPROVE, actionFor(status, SampleStatus.PENDING_AUDIT));

        // REJECT: 仅 PENDING_AUDIT 可驳回（推到 REJECTED）
        actions.put(REJECT, actionFor(status, SampleStatus.PENDING_AUDIT));

        // EDIT: 与 ensureCanEdit 同语义（已有 EDITABLE_STATUSES）
        actions.put(EDIT, inSet(status, EDITABLE_STATUSES) ? SampleActionAvailabilityVO.available() : SampleActionAvailabilityVO.unavailable("当前状态不允许编辑合作详情"));

        // PROGRESS: 业务方在 PENDING_SHIP 点 "已发货" 推到 SHIPPING
        actions.put(PROGRESS, actionFor(status, SampleStatus.PENDING_SHIP));

        // COPY_LINK / COPY_ORDER / NOTE: 任意状态可用（不是状态机操作）
        actions.put(COPY_LINK, SampleActionAvailabilityVO.available());
        actions.put(COPY_ORDER, SampleActionAvailabilityVO.available());
        actions.put(NOTE, SampleActionAvailabilityVO.available());

        // COMPLAIN: 暂不可用（功能未上线）
        actions.put(COMPLAIN, SampleActionAvailabilityVO.unavailable("投诉提交能力暂不可用"));

        return actions;
    }

    /**
     * 状态匹配检查：current 等于 expected 时返回 available，否则返回 unavailable。
     */
    private static SampleActionAvailabilityVO actionFor(SampleStatus current, SampleStatus expected) {
        if (current == expected) {
            return SampleActionAvailabilityVO.available();
        }
        String label = expected == null ? "未知" : expected.name();
        String currentLabel = current == null ? "未知" : current.name();
        return SampleActionAvailabilityVO.unavailable(
                String.format("当前状态为【%s】，该操作仅在【%s】状态可用", currentLabel, label));
    }

    /**
     * 集合包含检查
     */
    private static boolean inSet(SampleStatus status, Set<SampleStatus> set) {
        return status != null && set.contains(status);
    }

    public void ensureCanEdit(
            SampleStatus status,
            UUID ownerUserId,
            UUID currentUserId,
            Object roleCodes) {
        if (!EDITABLE_STATUSES.contains(status)) {
            throw BusinessException.stateInvalid("当前状态不允许编辑合作详情");
        }
    }
}
