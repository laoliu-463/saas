package com.colonel.saas.domain.talent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
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
                new CurrentUserPermissionPolicy(),
                new DataScopePolicy(),
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

    private static TalentClaim claim(LocalDateTime protectedUntil) {
        TalentClaim claim = new TalentClaim();
        claim.setId(UUID.randomUUID());
        claim.setProtectedUntil(protectedUntil);
        return claim;
    }
}
