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
            // PR #fix-cooperation-action-availability: 中文化 + 业务可读
            // 例如：「合作单当前状态为【SHIPPING】（发货中），该操作仅在【PENDING_AUDIT】（待审核）状态可用」
            String currentZh = statusToChinese(current);
            String expectedZh = statusToChinese(expected);
            String message = String.format(
                    "合作单当前状态为【%s】（%s），该操作仅在【%s】（%s）状态可用",
                    current.getApiStatus(), currentZh,
                    expected.getApiStatus(), expectedZh);
            throw BusinessException.stateInvalid(message);
        }
    }

    private static String statusToChinese(SampleStatus status) {
        if (status == null) return "未知";
        switch (status) {
            case PENDING_AUDIT: return "待审核";
            case PENDING_SHIP:  return "待发货";
            case SHIPPING:      return "发货中";
            case DELIVERED:     return "已签收";
            case PENDING_HOMEWORK: return "待交作业";
            case COMPLETED:     return "已完成";
            case REJECTED:      return "已驳回";
            case CLOSED:        return "已关闭";
            default:            return status.name();
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
