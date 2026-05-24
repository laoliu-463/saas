package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.service.display.DisplayRuleOperatorContext;
import com.colonel.saas.service.display.ProductDisplayAuditService;
import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 商品库展示去重规则：同 product_id 最多一条 {@link ProductDisplayStatus#DISPLAYING}。
 * 选择优先级：投流优先 → 佣金率高 → 晚上架；保护期内禁止普通切换，优势条件可覆盖。
 */
@Slf4j
@Service
public class ProductDisplayRuleService {

    public static final int DISPLAY_RULE_VERSION = 3;
    public static final int DEFAULT_PROTECTION_MONTHS = 3;

    public static final String HIDDEN_REASON_REPLACED = "REPLACED_BY_HIGHER_PRIORITY";
    public static final String HIDDEN_REASON_REPLACED_BY_ADVANTAGE = "REPLACED_BY_ADVANTAGE";
    public static final String HIDDEN_REASON_NOT_ELIGIBLE = "NOT_ELIGIBLE";
    public static final String HIDDEN_REASON_ACTIVITY_EXPIRED = "ACTIVITY_EXPIRED";
    public static final String HIDDEN_REASON_ADMIN_FORCE = "ADMIN_FORCE_REPLACED";
    public static final String DISPLAY_REASON_FORCE = "ADMIN_FORCE";
    public static final String DISPLAY_REASON_ADVANTAGE = "ADVANTAGE_OVERRIDE";
    public static final String DISPLAY_REASON_RULE = "RULE_ENGINE";

    private static final int PROMOTING_STATUS = 1;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<DateTimeFormatter> TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
    );

    private final ProductOperationStateMapper operationStateMapper;
    private final ProductSnapshotMapper snapshotMapper;
    private final ProductBizStatusService productBizStatusService;
    private final ColonelsettlementActivityMapper colonelActivityMapper;
    private final ProductDomainEventPublisher productDomainEventPublisher;
    private final ProductDisplayAuditService productDisplayAuditService;

    public ProductDisplayRuleService(
            ProductOperationStateMapper operationStateMapper,
            ProductSnapshotMapper snapshotMapper,
            ProductBizStatusService productBizStatusService,
            ColonelsettlementActivityMapper colonelActivityMapper,
            ProductDomainEventPublisher productDomainEventPublisher,
            ProductDisplayAuditService productDisplayAuditService) {
        this.operationStateMapper = operationStateMapper;
        this.snapshotMapper = snapshotMapper;
        this.productBizStatusService = productBizStatusService;
        this.colonelActivityMapper = colonelActivityMapper;
        this.productDomainEventPublisher = productDomainEventPublisher;
        this.productDisplayAuditService = productDisplayAuditService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void applyForProductId(String productId) {
        applyForProductId(productId, DisplayRuleOperatorContext.system());
    }

    @Transactional(rollbackFor = Exception.class)
    public void applyForProductId(String productId, DisplayRuleOperatorContext operator) {
        if (!StringUtils.hasText(productId)) {
            return;
        }
        List<ProductOperationState> states = operationStateMapper.selectList(
                new LambdaQueryWrapper<ProductOperationState>()
                        .eq(ProductOperationState::getProductId, productId.trim())
                        .orderByAsc(ProductOperationState::getCreateTime));
        if (states.isEmpty()) {
            return;
        }
        applyForStates(productId.trim(), states, operator);
    }

    @Transactional(rollbackFor = Exception.class)
    public void applyForActivityId(String activityId) {
        applyForActivityId(activityId, DisplayRuleOperatorContext.system());
    }

    @Transactional(rollbackFor = Exception.class)
    public void applyForActivityId(String activityId, DisplayRuleOperatorContext operator) {
        if (!StringUtils.hasText(activityId)) {
            return;
        }
        List<ProductOperationState> states = operationStateMapper.selectList(
                new LambdaQueryWrapper<ProductOperationState>()
                        .eq(ProductOperationState::getActivityId, activityId.trim())
                        .eq(ProductOperationState::getSelectedToLibrary, true)
                        .select(ProductOperationState::getProductId));
        Set<String> productIds = states.stream()
                .map(ProductOperationState::getProductId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String productId : productIds) {
            applyForProductId(productId, operator);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int reconcileAll() {
        return reconcileAll(DisplayRuleOperatorContext.job());
    }

    @Transactional(rollbackFor = Exception.class)
    public int reconcileAll(DisplayRuleOperatorContext operator) {
        List<ProductOperationState> libraryStates = operationStateMapper.selectList(
                new LambdaQueryWrapper<ProductOperationState>()
                        .eq(ProductOperationState::getSelectedToLibrary, true)
                        .select(ProductOperationState::getProductId));
        Set<String> productIds = libraryStates.stream()
                .map(ProductOperationState::getProductId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int processed = 0;
        for (String productId : productIds) {
            applyForProductId(productId, operator);
            processed++;
        }
        log.info("Product display rule reconcile completed, productIds={}", processed);
        return processed;
    }

    boolean isInProtectionPeriod(LocalDateTime firstDisplayedAt, Integer monthsOfProtection, LocalDateTime now) {
        if (firstDisplayedAt == null) {
            return false;
        }
        int months = resolveProtectionMonths(monthsOfProtection);
        LocalDateTime protectionEnd = firstDisplayedAt.plusMonths(months);
        return now.isBefore(protectionEnd);
    }

    BigDecimal resolveServiceFeeRatio(ProductSnapshot snapshot) {
        if (snapshot == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = parsePercentValue(snapshot.getAdServiceRatio());
        if (rate.compareTo(BigDecimal.ZERO) > 0) {
            return rate;
        }
        return normalizeRatioNumber(snapshot.getActivityAdCosRatio());
    }

    boolean hasAdvantageOver(DisplayCandidate challenger, DisplayCandidate incumbent) {
        if (challenger == null || incumbent == null || challenger.state().getId().equals(incumbent.state().getId())) {
            return false;
        }
        if (challenger.commissionRatio().compareTo(incumbent.commissionRatio()) > 0) {
            return true;
        }
        if (challenger.serviceFeeRatio().compareTo(incumbent.serviceFeeRatio()) < 0) {
            return true;
        }
        return challenger.supportsAds() && !incumbent.supportsAds();
    }

    private void applyForStates(String productId, List<ProductOperationState> states, DisplayRuleOperatorContext operator) {
        Map<String, ProductSnapshot> snapshotMap = loadSnapshots(states);
        hydrateProtectionMonths(snapshotMap);
        List<DisplayCandidate> eligible = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        UUID oldDisplayRelationId = states.stream()
                .filter(state -> ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus()))
                .map(ProductOperationState::getId)
                .findFirst()
                .orElse(null);

        for (ProductOperationState state : states) {
            ProductSnapshot snapshot = snapshotMap.get(snapshotKey(state.getActivityId(), state.getProductId()));
            if (isEligibleForDisplay(state, snapshot, now)) {
                eligible.add(toCandidate(state, snapshot));
            }
        }

        DisplayCandidate currentDisplaying = findCurrentDisplaying(states, snapshotMap, now);
        LocalDateTime productFirstDisplayedAt = resolveProductFirstDisplayedAt(states, currentDisplaying);
        Integer protectionMonths = resolveProtectionMonthsForCandidate(currentDisplaying, snapshotMap);

        DisplayCandidate winner = selectWinner(eligible, currentDisplaying, productFirstDisplayedAt, protectionMonths, now);
        String selectedReason = resolveSelectedReason(winner, currentDisplaying, now);

        for (ProductOperationState state : states) {
            ProductSnapshot snapshot = snapshotMap.get(snapshotKey(state.getActivityId(), state.getProductId()));
            ProductDisplayStatus nextStatus;
            String hiddenReason = null;
            String displayReason = null;

            if (!Boolean.TRUE.equals(state.getSelectedToLibrary())) {
                nextStatus = ProductDisplayStatus.PENDING;
            } else if (winner != null && state.getId().equals(winner.state().getId())) {
                nextStatus = ProductDisplayStatus.DISPLAYING;
                displayReason = selectedReason;
            } else if (isEligibleForDisplay(state, snapshot, now)) {
                nextStatus = ProductDisplayStatus.HIDDEN;
                hiddenReason = resolveReplacedReason(state, winner, currentDisplaying, eligible, snapshotMap, now);
            } else if (Boolean.TRUE.equals(state.getSelectedToLibrary())) {
                nextStatus = ProductDisplayStatus.HIDDEN;
                hiddenReason = resolveIneligibleReason(state, snapshot, now);
            } else {
                nextStatus = ProductDisplayStatus.PENDING;
            }
            persistDisplayDecision(state, nextStatus, hiddenReason, displayReason, productFirstDisplayedAt, now);
        }

        UUID newDisplayRelationId = states.stream()
                .filter(state -> ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus()))
                .map(ProductOperationState::getId)
                .findFirst()
                .orElse(null);
        if (!Objects.equals(oldDisplayRelationId, newDisplayRelationId)) {
            List<UUID> candidateIds = eligible.stream().map(c -> c.state().getId()).toList();
            productDisplayAuditService.writeAudit(
                    productId,
                    oldDisplayRelationId,
                    newDisplayRelationId,
                    candidateIds,
                    "DISPLAY_SWITCH",
                    selectedReason,
                    null,
                    DISPLAY_RULE_VERSION,
                    operator,
                    Map.of("candidateCount", candidateIds.size()));
            productDomainEventPublisher.publishDisplayRuleApplied(
                    productId,
                    oldDisplayRelationId,
                    newDisplayRelationId,
                    DISPLAY_RULE_VERSION,
                    operator.operatorType(),
                    operator.operatorId(),
                    Map.of("selectedReason", selectedReason));
        }
    }

    private String resolveSelectedReason(DisplayCandidate winner, DisplayCandidate currentDisplaying, LocalDateTime now) {
        if (winner == null) {
            return null;
        }
        if (isForceDisplayActive(winner.state(), now)) {
            return DISPLAY_REASON_FORCE;
        }
        if (currentDisplaying != null
                && !winner.state().getId().equals(currentDisplaying.state().getId())
                && hasAdvantageOver(winner, currentDisplaying)) {
            return DISPLAY_REASON_ADVANTAGE;
        }
        return DISPLAY_REASON_RULE;
    }

    private boolean isForceDisplayActive(ProductOperationState state, LocalDateTime now) {
        if (state == null || !Boolean.TRUE.equals(state.getForceDisplay())) {
            return false;
        }
        return state.getForceDisplayUntil() == null || !now.isAfter(state.getForceDisplayUntil());
    }

    private DisplayCandidate selectWinner(
            List<DisplayCandidate> eligible,
            DisplayCandidate currentDisplaying,
            LocalDateTime productFirstDisplayedAt,
            Integer protectionMonths,
            LocalDateTime now) {
        if (eligible.isEmpty()) {
            return null;
        }
        DisplayCandidate forced = eligible.stream()
                .filter(candidate -> isForceDisplayActive(candidate.state(), now))
                .max(this::compareCandidates)
                .orElse(null);
        if (forced != null) {
            return forced;
        }
        if (currentDisplaying == null) {
            return eligible.stream().max(this::compareCandidates).orElse(null);
        }

        boolean currentEligible = eligible.stream()
                .anyMatch(candidate -> candidate.state().getId().equals(currentDisplaying.state().getId()));

        if (!currentEligible) {
            return eligible.stream().max(this::compareCandidates).orElse(null);
        }

        boolean inProtection = isInProtectionPeriod(productFirstDisplayedAt, protectionMonths, now);
        if (!inProtection) {
            return eligible.stream().max(this::compareCandidates).orElse(null);
        }

        DisplayCandidate advantageWinner = eligible.stream()
                .filter(candidate -> !candidate.state().getId().equals(currentDisplaying.state().getId()))
                .filter(candidate -> hasAdvantageOver(candidate, currentDisplaying))
                .max(this::compareCandidates)
                .orElse(null);
        if (advantageWinner != null) {
            return advantageWinner;
        }
        return currentDisplaying;
    }

    private String resolveReplacedReason(
            ProductOperationState state,
            DisplayCandidate winner,
            DisplayCandidate currentDisplaying,
            List<DisplayCandidate> eligible,
            Map<String, ProductSnapshot> snapshotMap,
            LocalDateTime now) {
        if (winner == null || currentDisplaying == null) {
            return HIDDEN_REASON_REPLACED;
        }
        if (currentDisplaying.state().getId().equals(state.getId())
                && winner.state().getId().equals(currentDisplaying.state().getId())) {
            return HIDDEN_REASON_REPLACED;
        }
        if (currentDisplaying.state().getId().equals(state.getId())) {
            DisplayCandidate replacement = eligible.stream()
                    .filter(candidate -> candidate.state().getId().equals(winner.state().getId()))
                    .findFirst()
                    .orElse(null);
            if (replacement != null && hasAdvantageOver(replacement, currentDisplaying)) {
                return HIDDEN_REASON_REPLACED_BY_ADVANTAGE;
            }
            return HIDDEN_REASON_REPLACED;
        }
        return HIDDEN_REASON_REPLACED;
    }

    private String resolveIneligibleReason(ProductOperationState state, ProductSnapshot snapshot, LocalDateTime now) {
        if (snapshot != null && isPromotionExpired(snapshot, now)) {
            return HIDDEN_REASON_ACTIVITY_EXPIRED;
        }
        if (snapshot != null && !Integer.valueOf(PROMOTING_STATUS).equals(snapshot.getStatus())) {
            return HIDDEN_REASON_NOT_ELIGIBLE;
        }
        return HIDDEN_REASON_NOT_ELIGIBLE;
    }

    private DisplayCandidate findCurrentDisplaying(
            List<ProductOperationState> states,
            Map<String, ProductSnapshot> snapshotMap,
            LocalDateTime now) {
        for (ProductOperationState state : states) {
            if (!ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus())) {
                continue;
            }
            ProductSnapshot snapshot = snapshotMap.get(snapshotKey(state.getActivityId(), state.getProductId()));
            return toCandidate(state, snapshot);
        }
        return null;
    }

    private LocalDateTime resolveProductFirstDisplayedAt(
            List<ProductOperationState> states,
            DisplayCandidate currentDisplaying) {
        if (currentDisplaying != null && currentDisplaying.state().getFirstDisplayedAt() != null) {
            return currentDisplaying.state().getFirstDisplayedAt();
        }
        return states.stream()
                .map(ProductOperationState::getFirstDisplayedAt)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    private Integer resolveProtectionMonthsForCandidate(
            DisplayCandidate currentDisplaying,
            Map<String, ProductSnapshot> snapshotMap) {
        if (currentDisplaying == null) {
            return DEFAULT_PROTECTION_MONTHS;
        }
        ProductSnapshot snapshot = snapshotMap.get(snapshotKey(
                currentDisplaying.state().getActivityId(),
                currentDisplaying.state().getProductId()));
        return resolveProtectionMonths(snapshot == null ? null : snapshot.getMonthsOfProtection());
    }

    private int resolveProtectionMonths(Integer monthsOfProtection) {
        if (monthsOfProtection == null || monthsOfProtection <= 0) {
            return DEFAULT_PROTECTION_MONTHS;
        }
        return monthsOfProtection;
    }

    private int compareCandidates(DisplayCandidate left, DisplayCandidate right) {
        return DISPLAY_CANDIDATE_COMPARATOR.compare(left, right);
    }

    private void persistDisplayDecision(
            ProductOperationState state,
            ProductDisplayStatus nextStatus,
            String hiddenReason,
            String displayReason,
            LocalDateTime productFirstDisplayedAt,
            LocalDateTime now) {
        ProductDisplayStatus current = ProductDisplayStatus.fromCode(state.getDisplayStatus());
        boolean statusChanged = current != nextStatus;
        boolean reasonChanged = !Objects.equals(state.getHiddenReason(), hiddenReason);

        if (!statusChanged && !reasonChanged
                && (nextStatus != ProductDisplayStatus.DISPLAYING || state.getFirstDisplayedAt() != null)) {
            return;
        }

        state.setDisplayStatus(nextStatus.name());
        state.setDisplayRuleVersion(DISPLAY_RULE_VERSION);
        state.setHiddenReason(hiddenReason);
        state.setDisplayReason(displayReason);
        if (nextStatus == ProductDisplayStatus.DISPLAYING) {
            if (productFirstDisplayedAt != null) {
                state.setFirstDisplayedAt(productFirstDisplayedAt);
            } else if (state.getFirstDisplayedAt() == null) {
                state.setFirstDisplayedAt(now);
            }
            state.setLastDisplayedAt(now);
        }
        OptimisticLockSupport.requireUpdated(operationStateMapper.updateById(state));

        if (statusChanged) {
            if (nextStatus == ProductDisplayStatus.DISPLAYING) {
                productDomainEventPublisher.publishProductListed(
                        state.getActivityId(),
                        state.getProductId(),
                        state.getId(),
                        state.getSelectedBy(),
                        DISPLAY_RULE_VERSION,
                        displayReason);
            } else if (current == ProductDisplayStatus.DISPLAYING) {
                productDomainEventPublisher.publishProductHidden(
                        state.getActivityId(),
                        state.getProductId(),
                        state.getId(),
                        hiddenReason,
                        DISPLAY_RULE_VERSION);
            }
        }
    }

    boolean isEligibleForDisplay(ProductOperationState state, ProductSnapshot snapshot, LocalDateTime now) {
        if (state == null || snapshot == null) {
            return false;
        }
        if (Boolean.TRUE.equals(state.getManualDisabled())) {
            return false;
        }
        if (!Boolean.TRUE.equals(state.getSelectedToLibrary())) {
            return false;
        }
        ProductBizStatus bizStatus = productBizStatusService.readBizStatus(state);
        if (bizStatus != ProductBizStatus.APPROVED) {
            return false;
        }
        if (!Integer.valueOf(PROMOTING_STATUS).equals(snapshot.getStatus())) {
            return false;
        }
        return !isPromotionExpired(snapshot, now);
    }

    private boolean isPromotionExpired(ProductSnapshot snapshot, LocalDateTime now) {
        LocalDateTime endTime = parseDateTime(snapshot.getPromotionEndTime());
        return endTime != null && endTime.isBefore(now);
    }

    private DisplayCandidate toCandidate(ProductOperationState state, ProductSnapshot snapshot) {
        return new DisplayCandidate(
                state,
                supportsAds(state, snapshot),
                resolveCommissionRatio(snapshot),
                resolveServiceFeeRatio(snapshot),
                resolveShelfTime(state, snapshot));
    }

    private boolean supportsAds(ProductOperationState state, ProductSnapshot snapshot) {
        Map<String, Object> supplement = parseAuditPayload(state.getAuditPayload());
        if (Boolean.TRUE.equals(readBoolean(supplement, "supportsAds"))) {
            return true;
        }
        if (StringUtils.hasText(state.getPromoteLink()) || StringUtils.hasText(state.getShortLink())) {
            return true;
        }
        return snapshot != null && snapshot.getActivityAdCosRatio() != null && snapshot.getActivityAdCosRatio() > 0;
    }

    private BigDecimal resolveCommissionRatio(ProductSnapshot snapshot) {
        if (snapshot == null) {
            return BigDecimal.ZERO;
        }
        if (snapshot.getActivityCosRatio() != null) {
            return BigDecimal.valueOf(snapshot.getActivityCosRatio());
        }
        String text = snapshot.getActivityCosRatioText();
        if (!StringUtils.hasText(text)) {
            return BigDecimal.ZERO;
        }
        String normalized = text.replace("%", "").trim();
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDateTime resolveShelfTime(ProductOperationState state, ProductSnapshot snapshot) {
        if (state.getSelectedAt() != null) {
            return state.getSelectedAt();
        }
        if (snapshot != null && snapshot.getSyncTime() != null) {
            return snapshot.getSyncTime();
        }
        return state.getCreateTime();
    }

    private Map<String, ProductSnapshot> loadSnapshots(List<ProductOperationState> states) {
        Set<String> activityIds = states.stream()
                .map(ProductOperationState::getActivityId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> productIds = states.stream()
                .map(ProductOperationState::getProductId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (activityIds.isEmpty() || productIds.isEmpty()) {
            return Map.of();
        }
        return snapshotMapper.selectList(new LambdaQueryWrapper<ProductSnapshot>()
                        .in(ProductSnapshot::getActivityId, activityIds)
                        .in(ProductSnapshot::getProductId, productIds))
                .stream()
                .collect(Collectors.toMap(
                        snapshot -> snapshotKey(snapshot.getActivityId(), snapshot.getProductId()),
                        snapshot -> snapshot,
                        (left, right) -> left));
    }

    private void hydrateProtectionMonths(Map<String, ProductSnapshot> snapshotMap) {
        Set<String> activityIds = snapshotMap.values().stream()
                .map(ProductSnapshot::getActivityId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (activityIds.isEmpty()) {
            return;
        }
        Map<String, Integer> protectionMap = new java.util.LinkedHashMap<>();
        for (String activityId : activityIds) {
            ColonelsettlementActivity activity = colonelActivityMapper.selectByActivityId(activityId);
            if (activity != null && activity.getMonthsOfProtection() != null) {
                protectionMap.put(activityId, activity.getMonthsOfProtection());
            }
        }
        snapshotMap.values().forEach(snapshot -> {
            Integer protection = protectionMap.get(snapshot.getActivityId());
            if (protection != null) {
                snapshot.setMonthsOfProtection(protection);
            }
        });
    }

    private Map<String, Object> parseAuditPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(payload, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Boolean readBoolean(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private BigDecimal normalizeRatioNumber(Long raw) {
        if (raw == null || raw <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = BigDecimal.valueOf(raw);
        if (raw >= 1000) {
            return value.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parsePercentValue(String raw) {
        if (!StringUtils.hasText(raw)) {
            return BigDecimal.ZERO;
        }
        String normalized = raw.trim()
                .replace("%", "")
                .replace("％", "")
                .replace(",", "")
                .replace(" ", "");
        try {
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDateTime parseDateTime(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String snapshotKey(String activityId, String productId) {
        return activityId + "::" + productId;
    }

    private static final Comparator<DisplayCandidate> DISPLAY_CANDIDATE_COMPARATOR = Comparator
            .comparing(DisplayCandidate::supportsAds)
            .thenComparing(DisplayCandidate::commissionRatio)
            .thenComparing(DisplayCandidate::serviceFeeRatio, Comparator.reverseOrder())
            .thenComparing(DisplayCandidate::shelfTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(candidate -> candidate.state().getId().toString());

    record DisplayCandidate(
            ProductOperationState state,
            boolean supportsAds,
            BigDecimal commissionRatio,
            BigDecimal serviceFeeRatio,
            LocalDateTime shelfTime) {
    }
}
