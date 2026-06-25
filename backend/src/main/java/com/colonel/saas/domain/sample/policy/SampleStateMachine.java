package com.colonel.saas.domain.sample.policy;

import com.colonel.saas.common.enums.SampleStatus;
import com.colonel.saas.common.exception.BusinessException;

import java.util.Locale;
import java.util.Set;

/**
 * 寄样状态机 Policy（DDD-SAMPLE-006）。
 *
 * <p>集中维护动作归一化、前置状态校验与可删除状态判断。</p>
 */
public final class SampleStateMachine {

    private static final Set<SampleStatus> DELETABLE = Set.of(
            SampleStatus.PENDING_AUDIT,
            SampleStatus.REJECTED);

    private SampleStateMachine() {
    }

    public static String normalizeAction(String action) {
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "APPROVED" -> "PENDING_SHIP";
            case "SHIPPED" -> "SHIPPING";
            case "SIGNED", "PENDING_TASK" -> "PENDING_HOMEWORK";
            case "FINISHED" -> "COMPLETED";
            default -> normalized;
        };
    }

    public static void ensureTransition(SampleStatus current, SampleStatus expected) {
        if (current != expected) {
            throw BusinessException.stateInvalid("Current status does not allow this action: expected "
                    + expected.getApiStatus() + " but was " + current.getApiStatus());
        }
    }

    /**
     * PENDING_HOMEWORK 允许从 SHIPPING 直接推进（签收即待交作业），否则要求 DELIVERED。
     */
    public static void ensurePendingHomeworkTransition(SampleStatus current) {
        if (current == SampleStatus.SHIPPING || current == SampleStatus.DELIVERED) {
            return;
        }
        ensureTransition(current, SampleStatus.DELIVERED);
    }

    public static boolean isDeletable(SampleStatus status) {
        return status != null && DELETABLE.contains(status);
    }

    public static void ensureDeletable(SampleStatus status) {
        if (!isDeletable(status)) {
            throw BusinessException.stateInvalid("Only pending/rejected sample can be deleted");
        }
    }
}
