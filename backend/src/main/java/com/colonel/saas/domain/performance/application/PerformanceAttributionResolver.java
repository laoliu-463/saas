package com.colonel.saas.domain.performance.application;

import com.colonel.saas.domain.performance.policy.PerformanceAttributionPolicy;
import com.colonel.saas.domain.performance.policy.PerformanceAttributionPolicy.ExclusiveOwner;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.facade.dto.UserOwnershipReference;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.service.AttributionService;
import com.colonel.saas.service.ExclusiveMerchantService;
import com.colonel.saas.service.ExclusiveTalentService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 业绩域最终归属解析器。
 *
 * <p>读取订单默认事实和已生效规则，再委派 {@link PerformanceAttributionPolicy}；不回写订单。
 * 决策快照与规则版本随业绩记录保存，便于历史解释和受控重算。</p>
 */
@Service
public class PerformanceAttributionResolver {

    public static final String RULE_VERSION = "PERFORMANCE_ATTRIBUTION_V1";

    private final ExclusiveMerchantService exclusiveMerchantService;
    private final ExclusiveTalentService exclusiveTalentService;
    private final PerformanceAttributionAdjustmentService adjustmentService;
    private final UserDomainFacade userDomainFacade;

    public PerformanceAttributionResolver(
            ExclusiveMerchantService exclusiveMerchantService,
            ExclusiveTalentService exclusiveTalentService,
            PerformanceAttributionAdjustmentService adjustmentService,
            UserDomainFacade userDomainFacade) {
        this.exclusiveMerchantService = exclusiveMerchantService;
        this.exclusiveTalentService = exclusiveTalentService;
        this.adjustmentService = adjustmentService;
        this.userDomainFacade = userDomainFacade;
    }

    public ResolvedAttribution resolve(ColonelsettlementOrder order) {
        if (order == null) {
            return defaultOnly(null);
        }
        LocalDateTime businessTime = resolveBusinessTime(order);
        LocalDate businessDate = businessTime == null ? LocalDate.now() : businessTime.toLocalDate();
        UUID defaultChannel = order.getChannelUserId();
        UUID defaultRecruiter = order.getColonelUserId() == null ? order.getUserId() : order.getColonelUserId();
        Set<UUID> defaultOwnerIds = new LinkedHashSet<>();
        if (defaultChannel != null) {
            defaultOwnerIds.add(defaultChannel);
        }
        if (defaultRecruiter != null) {
            defaultOwnerIds.add(defaultRecruiter);
        }
        Map<UUID, UserOwnershipReference> ownerships = userDomainFacade == null || defaultOwnerIds.isEmpty()
                ? Map.of()
                : userDomainFacade.loadUserOwnershipReferencesByIds(defaultOwnerIds);
        if (ownerships == null) {
            ownerships = Map.of();
        }
        UUID defaultChannelDept = order.getChannelDeptId() != null
                ? order.getChannelDeptId()
                : deptOf(ownerships, defaultChannel);
        UUID defaultRecruiterDept = deptOf(ownerships, defaultRecruiter);
        ExclusiveOwner merchant = toExclusiveOwner(exclusiveMerchantService == null ? null
                : exclusiveMerchantService.findActiveOwnerByMerchantIdAt(resolveMerchantId(order), businessDate));
        ExclusiveOwner talent = toExclusiveOwner(exclusiveTalentService == null ? null
                : exclusiveTalentService.findActiveOwnerByTalentUidAt(resolveTalentUid(order), businessDate));
        PerformanceAttributionPolicy.ManualOwner manual = adjustmentService == null ? null
                : adjustmentService.findEffectiveOwner(order.getOrderId(), businessTime);

        PerformanceAttributionPolicy.AttributionResult result = PerformanceAttributionPolicy.resolve(
                new PerformanceAttributionPolicy.AttributionInput(
                        defaultChannel, defaultRecruiter, defaultChannelDept, defaultRecruiterDept,
                        merchant, talent, manual));
        return new ResolvedAttribution(result, RULE_VERSION,
                snapshot(order, businessTime, merchant, talent, manual, result),
                defaultChannelDept, defaultRecruiterDept);
    }

    /** 兼容测试与没有规则依赖的离线路径，只保留订单默认事实。 */
    public static ResolvedAttribution defaultOnly(ColonelsettlementOrder order) {
        UUID channel = order == null ? null : order.getChannelUserId();
        UUID recruiter = order == null ? null
                : (order.getColonelUserId() == null ? order.getUserId() : order.getColonelUserId());
        UUID channelDept = order == null ? null : order.getChannelDeptId();
        UUID recruiterDept = order == null ? null : order.getDeptId();
        return new ResolvedAttribution(
                PerformanceAttributionPolicy.resolve(new PerformanceAttributionPolicy.AttributionInput(
                        channel, recruiter, channelDept, recruiterDept, null, null)),
                RULE_VERSION,
                Map.of("source", "ORDER_DEFAULT"),
                channelDept, recruiterDept);
    }

    private static ExclusiveOwner toExclusiveOwner(AttributionService.ExclusiveOwner owner) {
        return owner == null || owner.userId() == null ? null : new ExclusiveOwner(owner.userId(), owner.deptId());
    }

    private static UUID deptOf(Map<UUID, UserOwnershipReference> ownerships, UUID userId) {
        UserOwnershipReference reference = userId == null ? null : ownerships.get(userId);
        return reference == null ? null : reference.deptId();
    }

    private static LocalDateTime resolveBusinessTime(ColonelsettlementOrder order) {
        if (order.getPayTime() != null) {
            return order.getPayTime();
        }
        if (order.getOrderCreateTime() != null) {
            return order.getOrderCreateTime();
        }
        return order.getCreateTime();
    }

    private static String resolveMerchantId(ColonelsettlementOrder order) {
        if (order.getExtraData() != null && order.getExtraData().get("merchant_id") != null) {
            return String.valueOf(order.getExtraData().get("merchant_id"));
        }
        return order.getShopId() == null ? null : String.valueOf(order.getShopId());
    }

    private static String resolveTalentUid(ColonelsettlementOrder order) {
        if (order.getExtraData() == null) {
            return null;
        }
        for (String key : java.util.List.of("author_id", "talent_uid", "talentUid", "authorId")) {
            Object value = order.getExtraData().get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private static Map<String, Object> snapshot(
            ColonelsettlementOrder order,
            LocalDateTime businessTime,
            ExclusiveOwner merchant,
            ExclusiveOwner talent,
            PerformanceAttributionPolicy.ManualOwner manual,
            PerformanceAttributionPolicy.AttributionResult result) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("orderId", order.getOrderId());
        snapshot.put("businessTime", businessTime == null ? null : businessTime.toString());
        snapshot.put("merchantExclusiveOwner", merchant == null ? null : merchant.userId());
        snapshot.put("talentExclusiveOwner", talent == null ? null : talent.userId());
        snapshot.put("manualAdjustment", manual != null);
        snapshot.put("finalChannelAttribution", result.channelAttributionType());
        snapshot.put("finalRecruiterAttribution", result.recruiterAttributionType());
        return snapshot;
    }

    public record ResolvedAttribution(
            PerformanceAttributionPolicy.AttributionResult result,
            String ruleVersion,
            Map<String, Object> decisionSnapshot,
            UUID defaultChannelDeptId,
            UUID defaultRecruiterDeptId) {
        public ResolvedAttribution(
                PerformanceAttributionPolicy.AttributionResult result,
                String ruleVersion,
                Map<String, Object> decisionSnapshot) {
            this(result, ruleVersion, decisionSnapshot, null, null);
        }

        public ResolvedAttribution {
            decisionSnapshot = decisionSnapshot == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(decisionSnapshot));
        }
    }
}
