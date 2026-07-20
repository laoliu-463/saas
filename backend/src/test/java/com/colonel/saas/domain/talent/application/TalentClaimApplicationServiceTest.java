package com.colonel.saas.domain.talent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.OperationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentClaimApplicationServiceTest {

    @Mock private TalentClaimMapper talentClaimMapper;
    @Mock private TalentMapper talentMapper;
    @Mock private OrderReadFacade orderReadFacade;
    @Mock private ConfigDomainFacade configDomainFacade;
    @Mock private UserDomainFacade userDomainFacade;
    @Mock private OperationLogService operationLogService;
    @Mock private RedisTemplate<String, Object> redisTemplate;

    private TalentClaimApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TalentClaimApplicationService(
                talentClaimMapper,
                talentMapper,
                orderReadFacade,
                configDomainFacade,
                userDomainFacade,
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy()),
                new DataScopeResolver(new DataScopePolicy()),
                operationLogService,
                new DddRefactorProperties(),
                redisTemplate);
    }

    @Test
    void extendActiveClaimProtectionByTalentUid_shouldOnlyExtendOlderActiveClaims() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 28, 20, 0);
        TalentClaim older = claim(now.plusDays(1));
        TalentClaim newer = claim(now.plusDays(10));

        when(configDomainFacade.getTalentClaimProtectDays()).thenReturn(7);
        when(talentClaimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(older, newer));
        when(talentClaimMapper.updateById(older)).thenReturn(1);

        int updated = service.extendActiveClaimProtectionByTalentUid(" dy_author ", now);

        assertThat(updated).isEqualTo(1);
        assertThat(older.getProtectedUntil()).isEqualTo(now.plusDays(7));
        assertThat(newer.getProtectedUntil()).isEqualTo(now.plusDays(10));
        verify(talentClaimMapper).updateById(older);
        verify(talentClaimMapper, never()).updateById(newer);
    }

    @Test
    void extendActiveClaimProtectionByTalentUid_shouldSkipBlankInput() {
        assertThat(service.extendActiveClaimProtectionByTalentUid(" ", LocalDateTime.now())).isZero();

        verify(talentClaimMapper, never()).selectList(any());
    }

    @Test
    void releaseExpiredClaims_shouldUseAggregateInsteadOfLoadingAllOrders() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 2, 15);
        LocalDateTime claimedAt = now.minusDays(30);
        UUID talentId = UUID.randomUUID();
        TalentClaim claim = claim(now.minusMinutes(1));
        claim.setTalentId(talentId);
        claim.setClaimedAt(claimedAt);
        claim.setStatus(TalentClaimApplicationService.CLAIM_STATUS_ACTIVE);
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_aggregate");

        when(talentClaimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(claim));
        when(talentMapper.selectBatchIds(any())).thenReturn(List.of(talent));
        when(orderReadFacade.summarizeTalentOrdersByDouyinUid(List.of("dy_aggregate"), claimedAt))
                .thenReturn(Map.of("dy_aggregate", new OrderReadFacade.TalentOrderSummary(
                        "dy_aggregate", 1L, 100L, 10L)));

        service.releaseExpiredClaims(now);

        assertThat(claim.getStatus()).isEqualTo(TalentClaimApplicationService.CLAIM_STATUS_ACTIVE);
        verify(talentClaimMapper, never()).updateById(claim);
        verify(orderReadFacade).summarizeTalentOrdersByDouyinUid(List.of("dy_aggregate"), claimedAt);
        verify(orderReadFacade, never()).findOrdersCreatedSince(any(), anyLong(), anyLong());
    }

    @Test
    void releaseExpiredClaims_shouldExpireClaimWhenAggregateHasNoOrders() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 2, 15);
        UUID talentId = UUID.randomUUID();
        TalentClaim claim = claim(now.minusMinutes(1));
        claim.setTalentId(talentId);
        claim.setClaimedAt(now.minusDays(7));
        claim.setStatus(TalentClaimApplicationService.CLAIM_STATUS_ACTIVE);
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("dy_no_output");

        when(talentClaimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(claim));
        when(talentMapper.selectBatchIds(any())).thenReturn(List.of(talent));
        when(orderReadFacade.summarizeTalentOrdersByDouyinUid(eq(List.of("dy_no_output")), any(LocalDateTime.class)))
                .thenReturn(Map.of());
        when(talentClaimMapper.updateById(claim)).thenReturn(1);

        service.releaseExpiredClaims(now);

        assertThat(claim.getStatus()).isEqualTo(TalentClaimApplicationService.CLAIM_STATUS_EXPIRED);
        verify(talentClaimMapper).updateById(claim);
        verify(orderReadFacade, never()).findOrdersCreatedSince(any(), anyLong(), anyLong());
    }

    private static TalentClaim claim(LocalDateTime protectedUntil) {
        TalentClaim claim = new TalentClaim();
        claim.setId(UUID.randomUUID());
        claim.setProtectedUntil(protectedUntil);
        return claim;
    }
}
