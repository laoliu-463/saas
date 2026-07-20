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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
    void releaseExpiredClaims_shouldUseBoundedExistenceCheckInsteadOfLoadingAllOrders() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 2, 15);
        LocalDateTime claimedAt = now.minusDays(35);
        UUID talentId = UUID.randomUUID();
        TalentClaim expiredClaim = expiredClaim(talentId, claimedAt, now.minusDays(1));
        Talent talent = talent(talentId, "dy_output");

        when(talentClaimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(expiredClaim));
        when(talentMapper.selectBatchIds(any())).thenReturn(List.of(talent));
        when(orderReadFacade.existsTalentOrderCreatedSince("dy_output", claimedAt)).thenReturn(true);

        service.releaseExpiredClaims(now);

        verify(talentClaimMapper, never()).updateById(any(TalentClaim.class));
        verify(orderReadFacade, never()).findOrdersCreatedSince(any(LocalDateTime.class), anyLong(), anyLong());
    }

    @Test
    void releaseExpiredClaims_shouldExpireClaimWhenBoundedCheckHasNoOrders() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 2, 15);
        LocalDateTime claimedAt = now.minusDays(35);
        UUID talentId = UUID.randomUUID();
        TalentClaim expiredClaim = expiredClaim(talentId, claimedAt, now.minusDays(1));
        Talent talent = talent(talentId, "dy_no_output");

        when(talentClaimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(expiredClaim));
        when(talentMapper.selectBatchIds(any())).thenReturn(List.of(talent));
        when(orderReadFacade.existsTalentOrderCreatedSince("dy_no_output", claimedAt)).thenReturn(false);
        when(talentClaimMapper.updateById(expiredClaim)).thenReturn(1);

        service.releaseExpiredClaims(now);

        assertThat(expiredClaim.getStatus()).isEqualTo(2);
        verify(talentClaimMapper).updateById(expiredClaim);
        verify(orderReadFacade, never()).findOrdersCreatedSince(any(LocalDateTime.class), anyLong(), anyLong());
    }

    @Test
    void releaseExpiredClaims_shouldUseJobTimeWhenClaimedAtIsMissing() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 2, 15);
        UUID talentId = UUID.randomUUID();
        TalentClaim expiredClaim = expiredClaim(talentId, null, now.minusDays(1));
        Talent talent = talent(talentId, "dy_missing_claim_time");

        when(configDomainFacade.getTalentClaimProtectDays()).thenReturn(7);
        when(talentClaimMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(expiredClaim));
        when(talentMapper.selectBatchIds(any())).thenReturn(List.of(talent));
        when(orderReadFacade.existsTalentOrderCreatedSince(
                "dy_missing_claim_time", now.minusDays(7))).thenReturn(true);

        service.releaseExpiredClaims(now);

        verify(talentClaimMapper, never()).updateById(any(TalentClaim.class));
    }

    private static TalentClaim claim(LocalDateTime protectedUntil) {
        TalentClaim claim = new TalentClaim();
        claim.setId(UUID.randomUUID());
        claim.setProtectedUntil(protectedUntil);
        return claim;
    }

    private static TalentClaim expiredClaim(UUID talentId, LocalDateTime claimedAt, LocalDateTime protectedUntil) {
        TalentClaim claim = claim(protectedUntil);
        claim.setTalentId(talentId);
        claim.setClaimedAt(claimedAt);
        claim.setStatus(1);
        claim.setDeleted(0);
        return claim;
    }

    private static Talent talent(UUID talentId, String douyinUid) {
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid(douyinUid);
        return talent;
    }
}
