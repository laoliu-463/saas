package com.colonel.saas.domain.talent.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.sample.facade.SampleDomainFacade;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.TalentMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 达人独家达标检查应用服务（DDD-TALENT-04 Slice 11）。
 *
 * <p>承接 Controller 的"单个达人独家达标检查"查询入口，自包含 30 天订单分页加载
 * + 比例计算 + 样本数统计 + 达标判定。1:1 等价 TalentService.evaluateExclusive 27 行业务
 * + 4 个 helper (resolveExclusiveOrderScope / Legacy / WithPolicy / matchesTalent / loadOrdersSettledSinceInBatches / OrderScopeFilter)。
 * Legacy {@code TalentService} 保留为薄壳委派壳。</p>
 *
 * <p><b>数据范围策略：</b>
 * <ul>
 *   <li>旧模式 (DddRefactorProperties.DataScopePolicy.Enabled=false): 按 DataScope + userId/deptId 直接构造 OrderScopeFilter</li>
 *   <li>新模式 (Enabled=true): 通过 DataScopeResolver 决策</li>
 * </ul>
 *
 * <p><b>业务域：</b>达人域 — 独家达标检查</p>
 */
@Service
public class ExclusiveTalentCheckApplicationService {

    private static final long ORDER_BATCH_SIZE = 2000L;

    private final TalentMapper talentMapper;
    private final OrderReadFacade orderReadFacade;
    private final SampleDomainFacade sampleDomainFacade;
    private final ConfigDomainFacade configDomainFacade;
    private final DataScopeResolver dataScopeResolver;
    private final DddRefactorProperties dddRefactorProperties;

    public ExclusiveTalentCheckApplicationService(
            TalentMapper talentMapper,
            OrderReadFacade orderReadFacade,
            SampleDomainFacade sampleDomainFacade,
            ConfigDomainFacade configDomainFacade,
            DataScopeResolver dataScopeResolver,
            DddRefactorProperties dddRefactorProperties) {
        this.talentMapper = talentMapper;
        this.orderReadFacade = orderReadFacade;
        this.sampleDomainFacade = sampleDomainFacade;
        this.configDomainFacade = configDomainFacade;
        this.dataScopeResolver = dataScopeResolver;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    /**
     * 独家达标检查。
     * 1:1 等价 TalentService.evaluateExclusive(UUID, DataScope, UUID, UUID) 27 行业务。
     */
    public ExclusiveCheckResult evaluateExclusive(UUID talentId, DataScope dataScope, UUID userId, UUID deptId) {
        Talent talent = talentMapper.selectById(talentId);
        if (talent == null || (talent.getDeleted() != null && talent.getDeleted() == 1)) {
            throw com.colonel.saas.common.exception.BusinessException.notFound("达人不存在");
        }
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        OrderScopeFilter orderScopeFilter = resolveExclusiveOrderScope(dataScope, userId, deptId);
        List<com.colonel.saas.entity.ColonelsettlementOrder> monthOrders = loadOrdersSettledSinceInBatches(
                start,
                orderScopeFilter.userId(),
                orderScopeFilter.deptId());

        long totalServiceFee = 0L;
        long talentServiceFee = 0L;
        for (com.colonel.saas.entity.ColonelsettlementOrder order : monthOrders) {
            long serviceFee = order.getSettleColonelCommission() == null ? 0L : order.getSettleColonelCommission();
            totalServiceFee += serviceFee;
            if (matchesTalent(order, talent.getDouyinUid())) {
                talentServiceFee += serviceFee;
            }
        }
        long serviceRatio = totalServiceFee == 0 ? 0 : (talentServiceFee * 100 / totalServiceFee);
        long monthlySamples = sampleDomainFacade.countSamplesByTalentIdSince(talentId, start);

        boolean eligible = serviceRatio >= configDomainFacade.getExclusiveTalentFeeRatio().longValue()
                && monthlySamples >= configDomainFacade.getExclusiveTalentMonthlySamples();
        return new ExclusiveCheckResult(eligible, serviceRatio, monthlySamples);
    }

    /**
     * 解析独家订单数据范围。1:1 等价 TalentService.resolveExclusiveOrderScope。
     */
    private OrderScopeFilter resolveExclusiveOrderScope(DataScope dataScope, UUID userId, UUID deptId) {
        if (!dddRefactorProperties.getDataScopePolicy().isEnabled()) {
            return resolveExclusiveOrderScopeLegacy(dataScope, userId, deptId);
        }
        return resolveExclusiveOrderScopeWithPolicy(dataScope, userId, deptId);
    }

    /**
     * 旧版订单数据范围。1:1 等价 TalentService.resolveExclusiveOrderScopeLegacy。
     */
    private OrderScopeFilter resolveExclusiveOrderScopeLegacy(DataScope dataScope, UUID userId, UUID deptId) {
        if (dataScope == DataScope.PERSONAL && userId != null) {
            return new OrderScopeFilter(userId, null);
        } else if (dataScope == DataScope.DEPT && deptId != null) {
            return new OrderScopeFilter(null, deptId);
        }
        return OrderScopeFilter.unfiltered();
    }

    /**
     * 新版订单数据范围。1:1 等价 TalentService.resolveExclusiveOrderScopeWithPolicy。
     */
    private OrderScopeFilter resolveExclusiveOrderScopeWithPolicy(DataScope dataScope, UUID userId, UUID deptId) {
        DataScopeResolver.ResolvedDataScope resolvedScope =
                dataScopeResolver.resolve(userId, deptId, dataScope);
        if (!resolvedScope.contextSatisfied()) {
            return OrderScopeFilter.unfiltered();
        }
        if (resolvedScope.filtersUser()) {
            return new OrderScopeFilter(userId, null);
        }
        if (resolvedScope.filtersDept()) {
            return new OrderScopeFilter(null, deptId);
        }
        return OrderScopeFilter.unfiltered();
    }

    /**
     * 分批加载结算订单。1:1 等价 TalentService.loadOrdersSettledSinceInBatches 22 行。
     */
    private List<com.colonel.saas.entity.ColonelsettlementOrder> loadOrdersSettledSinceInBatches(
            LocalDateTime settleStart,
            UUID userId,
            UUID deptId) {
        List<com.colonel.saas.entity.ColonelsettlementOrder> result = new ArrayList<>();
        long current = 1L;
        while (true) {
            OrderReadFacade.OrderPage batch = orderReadFacade.findOrdersSettledSince(
                    settleStart,
                    userId,
                    deptId,
                    current,
                    ORDER_BATCH_SIZE);
            List<com.colonel.saas.entity.ColonelsettlementOrder> records = batch == null ? null : batch.records();
            if (records == null || records.isEmpty()) {
                break;
            }
            result.addAll(records);
            if (current >= batch.pages()) {
                break;
            }
            current++;
        }
        return result;
    }

    /**
     * 判断订单是否匹配指定达人。1:1 等价 TalentService.matchesTalent 12 行。
     */
    private boolean matchesTalent(com.colonel.saas.entity.ColonelsettlementOrder order, String douyinUid) {
        if (!StringUtils.hasText(douyinUid)) {
            return false;
        }
        if (order.getExtraData() == null) {
            return false;
        }
        Object authorId = order.getExtraData().get("author_id");
        if (authorId != null && douyinUid.equals(String.valueOf(authorId))) {
            return true;
        }
        Object talentUid = order.getExtraData().get("talent_uid");
        return talentUid != null && douyinUid.equals(String.valueOf(talentUid));
    }

    /** 订单范围过滤器。1:1 等价 TalentService.OrderScopeFilter 嵌套 record。 */
    private record OrderScopeFilter(UUID userId, UUID deptId) {
        private static OrderScopeFilter unfiltered() {
            return new OrderScopeFilter(null, null);
        }
    }

    /**
     * 独家达标检查结果。1:1 等价 TalentService.ExclusiveCheckResult 嵌套 record。
     */
    public record ExclusiveCheckResult(boolean eligible, long serviceFeeRatio, long monthlySamples) {
    }
}
