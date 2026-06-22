package com.colonel.saas.domain.product.policy;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.constant.ProductDisplayStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * 商品库展示去重纯策略（DDD-PRODUCT-002）。
 * <p>同一 {@code productId} 下最多一条 {@link ProductDisplayStatus#DISPLAYING}，其余合格记录隐藏。</p>
 */
public class ProductDisplayPolicy {

    public static final int DEFAULT_PROTECTION_MONTHS = 3;
    private static final int PROMOTING_DOUYIN_STATUS = 1;

    public static final String HIDDEN_REASON_REPLACED = "REPLACED_BY_HIGHER_PRIORITY";
    public static final String HIDDEN_REASON_REPLACED_BY_ADVANTAGE = "REPLACED_BY_ADVANTAGE";
    public static final String HIDDEN_REASON_NOT_ELIGIBLE = "NOT_ELIGIBLE";
    public static final String HIDDEN_REASON_LOCAL_REJECTED = "LOCAL_REJECTED";
    public static final String HIDDEN_REASON_UPSTREAM_NOT_PROMOTING = "UPSTREAM_NOT_PROMOTING";
    public static final String HIDDEN_REASON_LOCAL_PAUSED = "LOCAL_PAUSED";
    public static final String HIDDEN_REASON_ACTIVITY_EXPIRED = "ACTIVITY_EXPIRED";
    public static final String DISPLAY_REASON_FORCE = "ADMIN_FORCE";
    public static final String DISPLAY_REASON_ADVANTAGE = "ADVANTAGE_OVERRIDE";
    public static final String DISPLAY_REASON_RULE = "RULE_ENGINE";

    public static final String DECISION_NONE = "NONE";
    public static final String DECISION_DISPLAY = "DISPLAY";
    public static final String DECISION_HIDE_ALL = "HIDE_ALL";
    private static final String ACTIVITY_PRODUCT_QUERY_STATUS_HINT =
            "商品状态仅支持 0=待审核、1=推广中、2=申请未通过、3=合作已终止、6=合作已到期";

    private static final Comparator<ProductDisplayRelationInput> PRIORITY_COMPARATOR = Comparator
            .comparing(ProductDisplayRelationInput::hasTrafficSupport)
            .thenComparing(ProductDisplayRelationInput::commissionRate, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(ProductDisplayRelationInput::serviceFeeRate, Comparator.nullsFirst(Comparator.reverseOrder()))
            .thenComparing(ProductDisplayRelationInput::shelfTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(input -> input.relationId().toString());

    /**
     * 对同一 productId 的全部关联记录做展示去重决策。
     */
    public ProductDisplayPolicyResult decide(List<ProductDisplayRelationInput> relations, LocalDateTime now) {
        if (relations == null || relations.isEmpty()) {
            return emptyResult(null);
        }
        UUID previousDisplayRelationId = relations.stream()
                .filter(relation -> ProductDisplayStatus.DISPLAYING.name().equals(relation.displayStatus()))
                .map(ProductDisplayRelationInput::relationId)
                .findFirst()
                .orElse(null);

        List<ProductDisplayRelationInput> eligible = relations.stream()
                .filter(relation -> isEligibleForDisplay(relation, now))
                .toList();

        ProductDisplayRelationInput currentDisplaying = relations.stream()
                .filter(relation -> ProductDisplayStatus.DISPLAYING.name().equals(relation.displayStatus()))
                .findFirst()
                .orElse(null);

        LocalDateTime productFirstDisplayedAt = resolveProductFirstDisplayedAt(relations, currentDisplaying);
        Integer protectionMonths = resolveProtectionMonths(currentDisplaying);

        ProductDisplayRelationInput winner = selectWinner(
                eligible,
                currentDisplaying,
                productFirstDisplayedAt,
                protectionMonths,
                now);
        String selectedReason = resolveSelectedReason(winner, currentDisplaying, now);

        Map<UUID, String> hideReasons = new LinkedHashMap<>();
        Map<UUID, String> displayReasons = new LinkedHashMap<>();
        List<ProductDisplayPolicyResult.RelationDisplayOutcome> outcomes = new ArrayList<>();

        for (ProductDisplayRelationInput relation : relations) {
            String nextStatus;
            String hiddenReason = null;
            String displayReason = null;

            if (!relation.selectedToLibrary()) {
                nextStatus = ProductDisplayStatus.PENDING.name();
            } else if (winner != null && relation.relationId().equals(winner.relationId())) {
                nextStatus = ProductDisplayStatus.DISPLAYING.name();
                displayReason = selectedReason;
                displayReasons.put(relation.relationId(), selectedReason);
            } else if (isEligibleForDisplay(relation, now)) {
                nextStatus = ProductDisplayStatus.HIDDEN.name();
                hiddenReason = resolveReplacedReason(relation, winner, currentDisplaying, eligible, now);
                hideReasons.put(relation.relationId(), hiddenReason);
            } else if (relation.selectedToLibrary()) {
                nextStatus = ProductDisplayStatus.HIDDEN.name();
                hiddenReason = resolveIneligibleReason(relation, now);
                hideReasons.put(relation.relationId(), hiddenReason);
            } else {
                nextStatus = ProductDisplayStatus.PENDING.name();
            }
            outcomes.add(new ProductDisplayPolicyResult.RelationDisplayOutcome(
                    relation.relationId(), nextStatus, hiddenReason, displayReason));
        }

        UUID selectedRelationId = winner == null ? null : winner.relationId();
        List<UUID> hiddenRelationIds = outcomes.stream()
                .filter(outcome -> ProductDisplayStatus.HIDDEN.name().equals(outcome.nextDisplayStatus()))
                .map(ProductDisplayPolicyResult.RelationDisplayOutcome::relationId)
                .toList();
        List<UUID> eventCandidates = eligible.stream().map(ProductDisplayRelationInput::relationId).toList();
        boolean whetherNeedEvent = !Objects.equals(previousDisplayRelationId, selectedRelationId);
        String displayDecision = selectedRelationId == null
                ? (eligible.isEmpty() ? DECISION_HIDE_ALL : DECISION_NONE)
                : DECISION_DISPLAY;

        return new ProductDisplayPolicyResult(
                selectedRelationId,
                hiddenRelationIds,
                displayDecision,
                hideReasons,
                displayReasons,
                whetherNeedEvent,
                eventCandidates,
                previousDisplayRelationId,
                outcomes);
    }

    /**
     * 商品库列表二次排序（投流 &gt; 佣金 &gt; 晚上架），不含置顶逻辑。
     */
    public int compareLibraryPresentation(LibraryPresentationKey left, LibraryPresentationKey right) {
        if (left.hasTrafficSupport() != right.hasTrafficSupport()) {
            return left.hasTrafficSupport() ? -1 : 1;
        }
        int commissionCompare = compareDecimalDesc(left.commissionRate(), right.commissionRate());
        if (commissionCompare != 0) {
            return commissionCompare;
        }
        return compareDateTimeDesc(left.listedAt(), right.listedAt());
    }

    public record LibraryPresentationKey(
            boolean hasTrafficSupport,
            BigDecimal commissionRate,
            LocalDateTime listedAt
    ) {
    }

    public record DisplayPresentation(
            ProductDisplayStatus displayStatus,
            String displayMark,
            String displayMarkLabel,
            String hiddenReason,
            LocalDateTime firstDisplayedAt,
            LocalDateTime lastDisplayedAt,
            boolean libraryVisible
    ) {
    }

    public record ActivityProductStatusPresentation(
            String officialStatus,
            String reviewStatus,
            String publishStatus,
            boolean manualDisabled,
            boolean selectedToLibrary,
            ProductDisplayStatus displayStatus,
            String displayMark,
            String displayMarkLabel,
            String hiddenReason
    ) {
    }

    public DisplayPresentation resolveDisplayPresentation(
            boolean hasState,
            boolean selectedToLibrary,
            String displayStatusCode,
            String hiddenReason,
            LocalDateTime firstDisplayedAt,
            LocalDateTime lastDisplayedAt) {
        ProductDisplayStatus displayStatus;
        if (!hasState) {
            displayStatus = ProductDisplayStatus.PENDING;
        } else if (ProductDisplayStatus.HIDDEN == ProductDisplayStatus.fromCode(displayStatusCode)) {
            displayStatus = ProductDisplayStatus.HIDDEN;
        } else if (!selectedToLibrary) {
            displayStatus = ProductDisplayStatus.PENDING;
        } else {
            displayStatus = ProductDisplayStatus.fromCode(displayStatusCode);
        }
        return new DisplayPresentation(
                displayStatus,
                legacyDisplayMark(displayStatus),
                displayStatus.getLabel(),
                hasState ? hiddenReason : null,
                hasState ? firstDisplayedAt : null,
                hasState ? lastDisplayedAt : null,
                displayStatus == ProductDisplayStatus.DISPLAYING);
    }

    public ActivityProductStatusPresentation resolveActivityProductStatusPresentation(
            Integer upstreamStatus,
            String statusText,
            Integer auditStatus,
            String bizStatusCode,
            boolean manualDisabled,
            boolean selectedToLibrary,
            boolean hasPromoteLink,
            boolean hasShortLink,
            String displayStatusCode,
            String hiddenReason) {
        String officialStatus = resolveOfficialStatus(upstreamStatus, statusText);
        String reviewStatus = resolveReviewStatus(officialStatus, auditStatus, bizStatusCode);
        String publishStatus = resolvePublishStatus(
                manualDisabled, selectedToLibrary, hasPromoteLink, hasShortLink);
        ProductDisplayStatus displayStatus = ProductDisplayStatus.fromCode(displayStatusCode);
        return new ActivityProductStatusPresentation(
                officialStatus,
                reviewStatus,
                publishStatus,
                manualDisabled,
                selectedToLibrary,
                displayStatus,
                legacyDisplayMark(displayStatus),
                displayStatus.getLabel(),
                hiddenReason);
    }

    public int normalizeActivityProductStatus(int status) {
        return status;
    }

    public Integer normalizeActivityProductStatus(Integer status) {
        return status == null ? null : normalizeActivityProductStatus(status.intValue());
    }

    public Integer normalizeActivityProductFilterStatus(Integer status) {
        return normalizeActivityProductStatus(status);
    }

    public boolean isSupportedActivityProductQueryStatus(Integer status) {
        return status == null
                || status == 0
                || status == 1
                || status == 2
                || status == 3
                || status == 6;
    }

    public String activityProductQueryStatusHint() {
        return ACTIVITY_PRODUCT_QUERY_STATUS_HINT;
    }

    public String normalizeActivityProductSortBy(String sortBy) {
        if (!StringUtils.hasText(sortBy)) {
            return "default";
        }
        String normalized = sortBy.trim().toLowerCase(Locale.ROOT);
        return "latest".equals(normalized) ? "latest" : "default";
    }

    public String normalizeSelectedLibrarySortBy(String sortBy) {
        if (!StringUtils.hasText(sortBy)) {
            return "default";
        }
        String normalized = sortBy.trim();
        if ("default".equals(normalized) || "pinned".equals(normalized)) {
            return "default";
        }
        if ("latest".equals(normalized)) {
            return "latest";
        }
        return normalized;
    }

    public boolean hasPromotionLink(String... links) {
        if (links == null || links.length == 0) {
            return false;
        }
        for (String link : links) {
            if (StringUtils.hasText(link)) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesSelectedLibraryPromotionLinkFilter(
            String promotionLink,
            String promoteLink,
            String shortLink,
            String bizStatus) {
        boolean linked = hasPromotionLink(promoteLink, shortLink);
        boolean failed = !linked && containsAny(bizStatus, "LINKED", "FOLLOWING");
        return switch (promotionLink) {
            case "LINKED" -> linked;
            case "PENDING" -> !linked && !failed;
            case "FAILED" -> failed;
            default -> true;
        };
    }

    public boolean matchesSelectedLibraryPublishedFilter(String published, String promoteLink, String shortLink) {
        boolean linked = hasPromotionLink(promoteLink, shortLink);
        return "1".equals(published) ? linked : !linked;
    }

    public boolean matchesSelectedLibraryListedFilter(String listed, Integer upstreamStatus) {
        boolean upstreamListed = Integer.valueOf(PROMOTING_DOUYIN_STATUS).equals(upstreamStatus);
        return "1".equals(listed) ? upstreamListed : !upstreamListed;
    }

    public boolean matchesSelectedLibraryAllianceStatusFilter(
            String allianceStatus,
            Integer upstreamStatus,
            String upstreamStatusText) {
        if ("pending_audit".equals(allianceStatus) && Objects.equals(upstreamStatus, 0)) {
            return true;
        }
        if ("promoting".equals(allianceStatus) && Objects.equals(upstreamStatus, 1)) {
            return true;
        }
        if ("rejected".equals(allianceStatus) && Objects.equals(upstreamStatus, 2)) {
            return true;
        }
        if ("terminated".equals(allianceStatus) && Objects.equals(upstreamStatus, 3)) {
            return true;
        }
        if ("expired".equals(allianceStatus) && Objects.equals(upstreamStatus, 6)) {
            return true;
        }
        return switch (allianceStatus) {
            case "pending_audit" -> containsAny(upstreamStatusText, "待审核", "审核中");
            case "promoting" -> containsAny(upstreamStatusText, "推广中", "推广");
            case "rejected" -> containsAny(upstreamStatusText, "未通过", "拒绝", "申请未通过");
            case "terminated" -> containsAny(upstreamStatusText, "终止", "已终止");
            case "expired" -> containsAny(upstreamStatusText, "过期", "已过期", "到期", "已到期");
            default -> true;
        };
    }

    public boolean matchesSelectedLibraryCoreVisibility(
            Integer upstreamStatus,
            boolean hasOperationState,
            Integer auditStatus,
            String bizStatus,
            Boolean manualDisabled) {
        return Integer.valueOf(PROMOTING_DOUYIN_STATUS).equals(upstreamStatus)
                && hasOperationState
                && !isLocalRejectedProductState(auditStatus, bizStatus)
                && !Boolean.TRUE.equals(manualDisabled);
    }

    public boolean isLocalRejectedProductState(Integer auditStatus, String bizStatus) {
        if (Integer.valueOf(3).equals(auditStatus)) {
            return true;
        }
        try {
            return ProductBizStatus.REJECTED == ProductBizStatus.fromCode(bizStatus);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public String normalizeActivityProductStatusText(Integer status, String statusText) {
        return statusText;
    }

    public String legacyDisplayMark(ProductDisplayStatus displayStatus) {
        return switch (displayStatus) {
            case DISPLAYING -> "SHOWING";
            case HIDDEN -> "HIDDEN";
            case PENDING -> "PENDING";
        };
    }

    public boolean isEligibleForDisplay(ProductDisplayRelationInput relation, LocalDateTime now) {
        if (relation == null) {
            return false;
        }
        if (relation.localPaused()) {
            return false;
        }
        if (!relation.selectedToLibrary()) {
            return false;
        }
        if (!Integer.valueOf(PROMOTING_DOUYIN_STATUS).equals(relation.douyinStatus())) {
            return false;
        }
        return !isActivityExpired(relation, now);
    }

    public boolean isInProtectionPeriod(LocalDateTime firstDisplayedAt, Integer monthsOfProtection, LocalDateTime now) {
        if (firstDisplayedAt == null) {
            return false;
        }
        LocalDateTime protectionEnd = firstDisplayedAt.plusMonths(resolveProtectionMonths(monthsOfProtection));
        return now.isBefore(protectionEnd);
    }

    public boolean hasAdvantageOver(ProductDisplayRelationInput challenger, ProductDisplayRelationInput incumbent) {
        if (challenger == null || incumbent == null || challenger.relationId().equals(incumbent.relationId())) {
            return false;
        }
        if (safeDecimal(challenger.commissionRate()).compareTo(safeDecimal(incumbent.commissionRate())) > 0) {
            return true;
        }
        if (safeDecimal(challenger.serviceFeeRate()).compareTo(safeDecimal(incumbent.serviceFeeRate())) < 0) {
            return true;
        }
        return challenger.hasTrafficSupport() && !incumbent.hasTrafficSupport();
    }

    public int compareByPriority(ProductDisplayRelationInput left, ProductDisplayRelationInput right) {
        return PRIORITY_COMPARATOR.compare(left, right);
    }

    ProductDisplayRelationInput selectWinner(
            List<ProductDisplayRelationInput> eligible,
            ProductDisplayRelationInput currentDisplaying,
            LocalDateTime productFirstDisplayedAt,
            Integer protectionMonths,
            LocalDateTime now) {
        if (eligible.isEmpty()) {
            return null;
        }
        ProductDisplayRelationInput forced = eligible.stream()
                .filter(candidate -> isForceDisplayActive(candidate, now))
                .max(this::compareByPriority)
                .orElse(null);
        if (forced != null) {
            return forced;
        }
        if (currentDisplaying == null) {
            return eligible.stream().max(this::compareByPriority).orElse(null);
        }
        boolean currentEligible = eligible.stream()
                .anyMatch(candidate -> candidate.relationId().equals(currentDisplaying.relationId()));
        if (!currentEligible) {
            return eligible.stream().max(this::compareByPriority).orElse(null);
        }
        if (!isInProtectionPeriod(productFirstDisplayedAt, protectionMonths, now)) {
            return eligible.stream().max(this::compareByPriority).orElse(null);
        }
        ProductDisplayRelationInput advantageWinner = eligible.stream()
                .filter(candidate -> !candidate.relationId().equals(currentDisplaying.relationId()))
                .filter(candidate -> hasAdvantageOver(candidate, currentDisplaying))
                .max(this::compareByPriority)
                .orElse(null);
        if (advantageWinner != null) {
            return advantageWinner;
        }
        return currentDisplaying;
    }

    private String resolveSelectedReason(
            ProductDisplayRelationInput winner,
            ProductDisplayRelationInput currentDisplaying,
            LocalDateTime now) {
        if (winner == null) {
            return null;
        }
        if (isForceDisplayActive(winner, now)) {
            return DISPLAY_REASON_FORCE;
        }
        if (currentDisplaying != null
                && !winner.relationId().equals(currentDisplaying.relationId())
                && hasAdvantageOver(winner, currentDisplaying)) {
            return DISPLAY_REASON_ADVANTAGE;
        }
        return DISPLAY_REASON_RULE;
    }

    private String resolveReplacedReason(
            ProductDisplayRelationInput relation,
            ProductDisplayRelationInput winner,
            ProductDisplayRelationInput currentDisplaying,
            List<ProductDisplayRelationInput> eligible,
            LocalDateTime now) {
        if (winner == null || currentDisplaying == null) {
            return HIDDEN_REASON_REPLACED;
        }
        if (currentDisplaying.relationId().equals(relation.relationId())
                && winner.relationId().equals(currentDisplaying.relationId())) {
            return HIDDEN_REASON_REPLACED;
        }
        if (currentDisplaying.relationId().equals(relation.relationId())) {
            ProductDisplayRelationInput replacement = eligible.stream()
                    .filter(candidate -> candidate.relationId().equals(winner.relationId()))
                    .findFirst()
                    .orElse(null);
            if (replacement != null && hasAdvantageOver(replacement, currentDisplaying)) {
                return HIDDEN_REASON_REPLACED_BY_ADVANTAGE;
            }
            return HIDDEN_REASON_REPLACED;
        }
        return HIDDEN_REASON_REPLACED;
    }

    private String resolveIneligibleReason(ProductDisplayRelationInput relation, LocalDateTime now) {
        if (!Integer.valueOf(PROMOTING_DOUYIN_STATUS).equals(relation.douyinStatus())) {
            return HIDDEN_REASON_UPSTREAM_NOT_PROMOTING;
        }
        if (relation.localPaused()) {
            return HIDDEN_REASON_LOCAL_PAUSED;
        }
        if (isActivityExpired(relation, now)) {
            return HIDDEN_REASON_ACTIVITY_EXPIRED;
        }
        if (relation.localRejected()) {
            return HIDDEN_REASON_LOCAL_REJECTED;
        }
        return HIDDEN_REASON_NOT_ELIGIBLE;
    }

    private boolean isForceDisplayActive(ProductDisplayRelationInput relation, LocalDateTime now) {
        if (!relation.forceDisplay()) {
            return false;
        }
        return relation.forceDisplayUntil() == null || !now.isAfter(relation.forceDisplayUntil());
    }

    private boolean isActivityExpired(ProductDisplayRelationInput relation, LocalDateTime now) {
        LocalDateTime endTime = relation.activityEndTime();
        return endTime != null && endTime.isBefore(now);
    }

    private LocalDateTime resolveProductFirstDisplayedAt(
            List<ProductDisplayRelationInput> relations,
            ProductDisplayRelationInput currentDisplaying) {
        if (currentDisplaying != null && currentDisplaying.firstDisplayedAt() != null) {
            return currentDisplaying.firstDisplayedAt();
        }
        return relations.stream()
                .map(ProductDisplayRelationInput::firstDisplayedAt)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    private int resolveProtectionMonths(Integer monthsOfProtection) {
        if (monthsOfProtection == null || monthsOfProtection <= 0) {
            return DEFAULT_PROTECTION_MONTHS;
        }
        return monthsOfProtection;
    }

    private Integer resolveProtectionMonths(ProductDisplayRelationInput currentDisplaying) {
        if (currentDisplaying == null || currentDisplaying.protectionMonths() == null) {
            return DEFAULT_PROTECTION_MONTHS;
        }
        return resolveProtectionMonths(currentDisplaying.protectionMonths());
    }

    private ProductDisplayPolicyResult emptyResult(UUID previousDisplayRelationId) {
        return new ProductDisplayPolicyResult(
                null,
                List.of(),
                DECISION_NONE,
                Map.of(),
                Map.of(),
                false,
                List.of(),
                previousDisplayRelationId,
                List.of());
    }

    private static BigDecimal safeDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int compareDecimalDesc(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return right.compareTo(left);
    }

    private static int compareDateTimeDesc(LocalDateTime left, LocalDateTime right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return right.compareTo(left);
    }

    private String resolveOfficialStatus(Integer upstreamStatus, String statusText) {
        if (upstreamStatus != null) {
            switch (upstreamStatus) {
                case 0:
                    return "PENDING_REVIEW";
                case 1:
                    return "PROMOTING";
                case 2:
                    return "REJECTED";
                case 3:
                    return "TERMINATED";
                case 6:
                    return "EXPIRED";
                default:
                    break;
            }
        }
        String text = statusText == null ? "" : statusText.trim();
        if (text.contains("待审核") || text.contains("审核中")) {
            return "PENDING_REVIEW";
        }
        if (text.contains("未通过") || text.contains("拒绝")) {
            return "REJECTED";
        }
        if (text.contains("终止")) {
            return "TERMINATED";
        }
        if (text.contains("到期") || text.contains("过期")) {
            return "EXPIRED";
        }
        if (text.contains("推广")) {
            return "PROMOTING";
        }
        return "PENDING_REVIEW";
    }

    private String resolveReviewStatus(String officialStatus, Integer auditStatus, String bizStatusCode) {
        if ("PROMOTING".equals(officialStatus)) {
            return "APPROVED";
        }
        if (Integer.valueOf(1).equals(auditStatus)) {
            return "PENDING";
        }
        if (Integer.valueOf(2).equals(auditStatus)) {
            return "APPROVED";
        }
        if (Integer.valueOf(3).equals(auditStatus)) {
            return "REJECTED";
        }
        if ("PENDING_REVIEW".equals(officialStatus)) {
            return "PENDING";
        }
        if ("REJECTED".equals(officialStatus)) {
            return "REJECTED";
        }
        ProductBizStatus status = readBizStatus(bizStatusCode);
        return status == ProductBizStatus.REJECTED ? "REJECTED" : "APPROVED";
    }

    private String resolvePublishStatus(
            boolean manualDisabled,
            boolean selectedToLibrary,
            boolean hasPromoteLink,
            boolean hasShortLink) {
        if (manualDisabled) {
            return "PAUSED";
        }
        if (selectedToLibrary || hasPromoteLink || hasShortLink) {
            return "PUBLISHED";
        }
        return "UNPUBLISHED";
    }

    private ProductBizStatus readBizStatus(String code) {
        try {
            return ProductBizStatus.fromCode(code);
        } catch (IllegalArgumentException ex) {
            return ProductBizStatus.PENDING_AUDIT;
        }
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
