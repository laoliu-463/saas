package com.colonel.saas.domain.product.policy;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/**
 * 商品库历史状态 repair 决策。
 *
 * <p>只负责根据快照与本地运营状态生成/应用修复决策；查询、dry-run、写库和
 * display rule 重算仍由应用服务编排。</p>
 */
public class ProductLibraryRepairPolicy {

    public static final String HIDDEN_REASON_NOT_ELIGIBLE = "NOT_ELIGIBLE";
    public static final String HIDDEN_REASON_LOCAL_REJECTED = "LOCAL_REJECTED";
    public static final String HIDDEN_REASON_UPSTREAM_NOT_PROMOTING = "UPSTREAM_NOT_PROMOTING";
    public static final String HIDDEN_REASON_LOCAL_PAUSED = "LOCAL_PAUSED";
    public static final String HIDDEN_REASON_ACTIVITY_EXPIRED = "ACTIVITY_EXPIRED";
    public static final String REPAIR_REASON_UPSTREAM_PROMOTING_AUTO_LIBRARY = "UPSTREAM_PROMOTING_AUTO_LIBRARY";
    public static final String REPAIR_REASON_UPSTREAM_NOT_PROMOTING = "UPSTREAM_NOT_PROMOTING";
    public static final String REPAIR_REASON_LOCAL_PAUSED = "LOCAL_PAUSED";
    public static final String REPAIR_REASON_EXPIRED = "EXPIRED";

    private static final int PROMOTING_STATUS = 1;
    private static final List<DateTimeFormatter> TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
    );

    public Decision decide(ProductSnapshot snapshot, ProductOperationState state, LocalDateTime now) {
        boolean oldSelected = Boolean.TRUE.equals(state.getSelectedToLibrary());
        String oldDisplayStatus = ProductDisplayStatus.fromCode(state.getDisplayStatus()).name();
        String oldHiddenReason = state.getHiddenReason();
        Integer oldAuditStatus = state.getAuditStatus();
        String oldBizStatus = state.getBizStatus();

        boolean newSelected = oldSelected;
        String newDisplayStatus = oldDisplayStatus;
        String newHiddenReason = oldHiddenReason;
        Integer newAuditStatus = oldAuditStatus;
        String newBizStatus = oldBizStatus;
        String reason = null;
        boolean willDisplay = false;

        if (shouldAutoEnterLibrary(snapshot, state, now)) {
            newSelected = true;
            newDisplayStatus = ProductDisplayStatus.DISPLAYING.name().equals(oldDisplayStatus)
                    ? ProductDisplayStatus.DISPLAYING.name()
                    : ProductDisplayStatus.PENDING.name();
            newHiddenReason = null;
            newAuditStatus = 2;
            ProductBizStatus currentBizStatus = safeBizStatus(newBizStatus);
            if (currentBizStatus == null
                    || currentBizStatus == ProductBizStatus.PENDING_AUDIT
                    || currentBizStatus == ProductBizStatus.REJECTED) {
                newBizStatus = ProductBizStatus.APPROVED.name();
            }
            reason = REPAIR_REASON_UPSTREAM_PROMOTING_AUTO_LIBRARY;
            willDisplay = true;
        } else if (isLocalPaused(state)
                && isLocallyDisplayableSnapshotStatus(snapshot)
                && !isPromotionExpired(snapshot, now)) {
            newSelected = true;
            newDisplayStatus = ProductDisplayStatus.HIDDEN.name();
            newHiddenReason = HIDDEN_REASON_LOCAL_PAUSED;
            newAuditStatus = 2;
            ProductBizStatus currentBizStatus = safeBizStatus(newBizStatus);
            if (currentBizStatus == null
                    || currentBizStatus == ProductBizStatus.PENDING_AUDIT
                    || currentBizStatus == ProductBizStatus.REJECTED) {
                newBizStatus = ProductBizStatus.APPROVED.name();
            }
            reason = REPAIR_REASON_LOCAL_PAUSED;
        } else {
            newDisplayStatus = ProductDisplayStatus.HIDDEN.name();
            newHiddenReason = resolveHiddenReason(snapshot, state, now);
            reason = HIDDEN_REASON_ACTIVITY_EXPIRED.equals(newHiddenReason)
                    ? REPAIR_REASON_EXPIRED
                    : REPAIR_REASON_UPSTREAM_NOT_PROMOTING;
        }

        boolean changed = oldSelected != newSelected
                || !Objects.equals(oldDisplayStatus, newDisplayStatus)
                || !Objects.equals(oldHiddenReason, newHiddenReason)
                || !Objects.equals(oldAuditStatus, newAuditStatus)
                || !Objects.equals(oldBizStatus, newBizStatus);
        return new Decision(
                oldSelected,
                newSelected,
                oldDisplayStatus,
                newDisplayStatus,
                oldHiddenReason,
                newHiddenReason,
                oldAuditStatus,
                newAuditStatus,
                oldBizStatus,
                newBizStatus,
                reason,
                changed,
                willDisplay);
    }

    public void apply(
            ProductOperationState state,
            Decision decision,
            LocalDateTime now,
            int displayRuleVersion,
            String autoLibraryRepairRemark) {
        state.setSelectedToLibrary(decision.newSelectedToLibrary());
        if (decision.newSelectedToLibrary() && state.getSelectedAt() == null) {
            state.setSelectedAt(now);
        }
        state.setDisplayStatus(decision.newDisplayStatus());
        state.setHiddenReason(decision.newHiddenReason());
        state.setAuditStatus(decision.newAuditStatus());
        state.setBizStatus(decision.newBizStatus());
        state.setDisplayRuleVersion(displayRuleVersion);
        if (REPAIR_REASON_UPSTREAM_PROMOTING_AUTO_LIBRARY.equals(decision.reason())
                && (state.getAuditRemark() == null || state.getAuditRemark().isBlank())) {
            state.setAuditRemark(autoLibraryRepairRemark);
        }
        state.setLastOperationAt(now);
    }

    public boolean shouldAutoEnterLibrary(ProductSnapshot snapshot, ProductOperationState state, LocalDateTime now) {
        if (snapshot == null) {
            return false;
        }
        if (!isLocallyDisplayableSnapshotStatus(snapshot)) {
            return false;
        }
        if (isLocalPaused(state)) {
            return false;
        }
        return !isPromotionExpired(snapshot, now);
    }

    private String resolveHiddenReason(ProductSnapshot snapshot, ProductOperationState state, LocalDateTime now) {
        if (snapshot != null && !isLocallyDisplayableSnapshotStatus(snapshot)) {
            return HIDDEN_REASON_UPSTREAM_NOT_PROMOTING;
        }
        if (isLocalPaused(state)) {
            return HIDDEN_REASON_LOCAL_PAUSED;
        }
        if (snapshot != null && isPromotionExpired(snapshot, now)) {
            return HIDDEN_REASON_ACTIVITY_EXPIRED;
        }
        if (isLocalRejected(state)) {
            return HIDDEN_REASON_LOCAL_REJECTED;
        }
        return HIDDEN_REASON_NOT_ELIGIBLE;
    }

    private boolean isLocallyDisplayableSnapshotStatus(ProductSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        return Integer.valueOf(PROMOTING_STATUS).equals(snapshot.getStatus());
    }

    private boolean isLocalRejected(ProductOperationState state) {
        if (state == null) {
            return false;
        }
        if (Integer.valueOf(3).equals(state.getAuditStatus())) {
            return true;
        }
        return safeBizStatus(state.getBizStatus()) == ProductBizStatus.REJECTED;
    }

    private boolean isLocalPaused(ProductOperationState state) {
        return state != null && Boolean.TRUE.equals(state.getManualDisabled());
    }

    private boolean isPromotionExpired(ProductSnapshot snapshot, LocalDateTime now) {
        LocalDateTime endTime = parseDateTime(snapshot.getPromotionEndTime());
        return endTime != null && endTime.isBefore(now);
    }

    private ProductBizStatus safeBizStatus(String raw) {
        try {
            ProductBizStatus status = ProductBizStatus.fromCode(raw);
            return status == null ? ProductBizStatus.PENDING_AUDIT : status;
        } catch (IllegalArgumentException ex) {
            return ProductBizStatus.PENDING_AUDIT;
        }
    }

    private LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        try {
            return LocalDateTime.ofEpochSecond(Long.parseLong(value), 0, java.time.ZoneOffset.UTC);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record Decision(
            boolean oldSelectedToLibrary,
            boolean newSelectedToLibrary,
            String oldDisplayStatus,
            String newDisplayStatus,
            String oldHiddenReason,
            String newHiddenReason,
            Integer oldAuditStatus,
            Integer newAuditStatus,
            String oldBizStatus,
            String newBizStatus,
            String reason,
            boolean changed,
            boolean willDisplay) {
    }
}
