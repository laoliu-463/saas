package com.colonel.saas.domain.performance.application;

import com.colonel.saas.domain.performance.domain.ExclusiveMerchantRepository;
import com.colonel.saas.domain.performance.policy.PerformanceAttributionPolicy;
import com.colonel.saas.domain.talent.domain.ExclusiveTalentRepository;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.ExclusiveMerchant;
import com.colonel.saas.entity.ExclusiveTalent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 业绩域默认最终归属解析器。
 *
 * <p>订单只提供默认归属事实；本组件在业绩域读取当前生效的独家覆盖，
 * 再统一交给 {@link PerformanceAttributionPolicy} 计算最终归属。</p>
 */
@Component
public class DefaultPerformanceAttributionResolver implements PerformanceAttributionResolver {

    private final ExclusiveMerchantRepository merchantRepository;
    private final ExclusiveTalentRepository talentRepository;
    private final boolean exclusiveEnabled;

    public DefaultPerformanceAttributionResolver(
            ExclusiveMerchantRepository merchantRepository,
            ExclusiveTalentRepository talentRepository,
            @Value("${exclusive.enabled:false}") boolean exclusiveEnabled) {
        this.merchantRepository = merchantRepository;
        this.talentRepository = talentRepository;
        this.exclusiveEnabled = exclusiveEnabled;
    }

    @Override
    public PerformanceAttributionPolicy.AttributionResult resolve(ColonelsettlementOrder order) {
        if (order == null) {
            return new PerformanceAttributionPolicy.AttributionResult(
                    null, null, null, null, "UNATTRIBUTED", "UNATTRIBUTED");
        }

        PerformanceAttributionPolicy.ExclusiveOwner merchantOwner = null;
        PerformanceAttributionPolicy.ExclusiveOwner talentOwner = null;
        if (exclusiveEnabled) {
            merchantOwner = loadMerchantOwner(order);
            talentOwner = loadTalentOwner(order);
        }

        return PerformanceAttributionPolicy.resolve(new PerformanceAttributionPolicy.AttributionInput(
                order.getChannelUserId(),
                recruiterUserId(order),
                order.getChannelDeptId(),
                order.getDeptId(),
                merchantOwner,
                talentOwner));
    }

    private PerformanceAttributionPolicy.ExclusiveOwner loadMerchantOwner(ColonelsettlementOrder order) {
        if (order.getShopId() == null) {
            return null;
        }
        String merchantId = String.valueOf(order.getShopId());
        Optional<ExclusiveMerchant> match = merchantRepository.findActiveByMerchantIdAndMonth(
                merchantId, YearMonth.now().toString());
        return match.map(owner -> new PerformanceAttributionPolicy.ExclusiveOwner(
                owner.getUserId(), owner.getDeptId())).orElse(null);
    }

    private PerformanceAttributionPolicy.ExclusiveOwner loadTalentOwner(ColonelsettlementOrder order) {
        String talentUid = resolveTalentUid(order.getExtraData());
        if (!StringUtils.hasText(talentUid)) {
            return null;
        }
        Optional<ExclusiveTalent> match = talentRepository.findActiveByTalentUid(
                talentUid, YearMonth.now().toString());
        return match.map(owner -> new PerformanceAttributionPolicy.ExclusiveOwner(
                owner.getUserId(), owner.getDeptId())).orElse(null);
    }

    private UUID recruiterUserId(ColonelsettlementOrder order) {
        return order.getColonelUserId() != null ? order.getColonelUserId() : order.getUserId();
    }

    private String resolveTalentUid(Map<String, Object> extraData) {
        if (extraData == null || extraData.isEmpty()) {
            return null;
        }
        for (String key : List.of("author_id", "authorId", "talent_uid", "talentUid", "talent_id")) {
            Object value = extraData.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }
}
