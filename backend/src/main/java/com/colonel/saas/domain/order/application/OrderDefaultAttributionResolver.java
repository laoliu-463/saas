package com.colonel.saas.domain.order.application;

import com.colonel.saas.domain.order.infrastructure.OrderPickSourceMappingAdapter;
import com.colonel.saas.domain.order.infrastructure.OrderPickSourceMappingAdapter.NativeMappingLookup;
import com.colonel.saas.domain.order.policy.OrderAttributionInput;
import com.colonel.saas.domain.order.policy.OrderDefaultAttributionPolicy;
import com.colonel.saas.domain.order.policy.OrderDefaultAttributionPolicy.RecruiterLookup;
import com.colonel.saas.domain.order.policy.OrderDefaultAttributionResult;
import com.colonel.saas.domain.product.facade.ProductDomainFacade;
import com.colonel.saas.domain.talent.facade.TalentDomainFacade;
import com.colonel.saas.domain.talent.facade.dto.TalentReadDTO;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;

/**
 * 默认归因解析器（DDD-ORDER-004）：加载映射与商品负责人后委派 {@link OrderDefaultAttributionPolicy}。
 */
@Service
public class OrderDefaultAttributionResolver {

    private static final Logger log = LoggerFactory.getLogger(OrderDefaultAttributionResolver.class);

    private final OrderPickSourceMappingAdapter pickSourceMappingAdapter;
    private final ProductDomainFacade productDomainFacade;
    private final TalentDomainFacade talentDomainFacade;

    public OrderDefaultAttributionResolver(
            OrderPickSourceMappingAdapter pickSourceMappingAdapter,
            ProductDomainFacade productDomainFacade,
            TalentDomainFacade talentDomainFacade) {
        this.pickSourceMappingAdapter = pickSourceMappingAdapter;
        this.productDomainFacade = productDomainFacade;
        this.talentDomainFacade = talentDomainFacade;
    }

    public OrderDefaultAttributionResult resolve(ColonelsettlementOrder order, Map<String, Object> rawPayload) {
        OrderAttributionInput input = OrderAttributionInput.from(order, rawPayload);
        UUID talentId = input.talentId() != null ? input.talentId() : resolveTalentId(input.talentUid());
        OrderAttributionInput enriched = new OrderAttributionInput(
                input.productId(),
                input.activityId(),
                input.pickSource(),
                input.pickExtra(),
                input.colonelBuyinId(),
                input.secondColonelBuyinId(),
                input.secondActivityId(),
                input.talentUid(),
                talentId);

        PickSourceMapping channelMapping = resolveChannelMapping(enriched);
        RecruiterLookup recruiterLookup = loadRecruiterLookup(enriched.activityId(), enriched.productId());

        return OrderDefaultAttributionPolicy.resolve(enriched, channelMapping, recruiterLookup);
    }

    private PickSourceMapping resolveChannelMapping(OrderAttributionInput input) {
        if (input.hasNativeColonelIdentity()) {
            NativeMappingLookup first = pickSourceMappingAdapter.findByNativeOrder(
                    input.colonelBuyinId(),
                    input.activityId(),
                    input.productId(),
                    !StringUtils.hasText(input.secondActivityId()));
            if (first.mapping() != null || first.ambiguous()) {
                return first.mapping();
            }
            if (StringUtils.hasText(input.secondColonelBuyinId())
                    || StringUtils.hasText(input.secondActivityId())) {
                NativeMappingLookup second = pickSourceMappingAdapter.findByNativeOrder(
                        input.secondColonelBuyinId(),
                        input.secondActivityId(),
                        input.productId(),
                        false);
                return second.mapping();
            }
            return null;
        }
        return pickSourceMappingAdapter.findByPickSourceOrExtra(input.pickSource(), input.pickExtra());
    }

    private RecruiterLookup loadRecruiterLookup(String activityId, String productId) {
        try {
            UUID productAssignee = null;
            UUID activityDefault = null;
            if (StringUtils.hasText(activityId) && StringUtils.hasText(productId)) {
                productAssignee = productDomainFacade.findProductAssigneeId(activityId.trim(), productId.trim());
            }
            if (StringUtils.hasText(activityId)) {
                activityDefault = productDomainFacade.findActivityDefaultRecruiterId(activityId.trim());
            }
            return new RecruiterLookup(productAssignee, activityDefault, false);
        } catch (Exception ex) {
            log.warn("Default recruiter lookup failed: activityId={}, productId={}", activityId, productId, ex);
            return new RecruiterLookup(null, null, true);
        }
    }

    private UUID resolveTalentId(String talentUid) {
        if (!StringUtils.hasText(talentUid)) {
            return null;
        }
        TalentReadDTO talent = talentDomainFacade.findByDouyinUid(talentUid.trim());
        return talent == null ? null : talent.id();
    }
}
