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
        LinkedHashMap<String, SampleActionAvailabilityVO> actions = new LinkedHashMap<>();
        actions.put(APPROVE, SampleActionAvailabilityVO.available());
        actions.put(REJECT, SampleActionAvailabilityVO.available());
        actions.put(EDIT, SampleActionAvailabilityVO.available());
        actions.put(PROGRESS, SampleActionAvailabilityVO.available());
        actions.put(COPY_LINK, SampleActionAvailabilityVO.available());
        actions.put(COPY_ORDER, SampleActionAvailabilityVO.available());
        actions.put(COMPLAIN, SampleActionAvailabilityVO.unavailable("投诉提交能力暂不可用"));
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
    }
}
